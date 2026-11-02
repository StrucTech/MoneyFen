package com.moneyfen.app.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyfen.app.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

@Serializable
data class BackupData(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TxEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val patterns: List<SmsPatternEntity> = emptyList(),
    val statements: List<StatementEntity> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

object BackupJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(repo: Repository): String {
        val db = repo.db
        val data = BackupData(
            accounts = db.accounts().all(), transactions = db.txs().all(), categories = db.categories().all(),
            rules = db.rules().all(), goals = db.goals().all(), patterns = db.patterns().all(),
            statements = db.statements().all(), settings = repo.prefs.exportMap(),
        )
        return json.encodeToString(BackupData.serializer(), data)
    }

    suspend fun import(repo: Repository, text: String): BackupData {
        val data = json.decodeFromString(BackupData.serializer(), text)
        val db = repo.db
        db.withTransaction {
            db.txs().clear(); db.accounts().clear(); db.categories().clear(); db.rules().clear()
            db.goals().clear(); db.patterns().clear(); db.statements().clear()
            db.accounts().insertAll(data.accounts); db.categories().insertAll(data.categories)
            db.txs().insertAll(data.transactions); db.rules().insertAll(data.rules)
            db.goals().insertAll(data.goals); db.patterns().insertAll(data.patterns)
            db.statements().insertAll(data.statements)
        }
        repo.prefs.importMap(data.settings)
        return data
    }
}

/**
 * Minimal Supabase client (Auth + PostgREST) using plain HTTP. Free tier is enough.
 * Table: moneyfen_backups(user_id uuid pk, data jsonb, updated_at timestamptz) with RLS (see supabase/schema.sql).
 */
class SupabaseBackup(private val prefs: Prefs) {
    private val json = BackupJson.json

    private fun http(method: String, path: String, body: String?, token: String?, extra: Map<String, String> = emptyMap()): Pair<Int, String> {
        val conn = URL(prefs.sbUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20_000; conn.readTimeout = 60_000
        conn.setRequestProperty("apikey", prefs.sbKey)
        conn.setRequestProperty("Authorization", "Bearer ${token ?: prefs.sbKey}")
        conn.setRequestProperty("Content-Type", "application/json")
        extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) { conn.doOutput = true; conn.outputStream.use { it.write(body.toByteArray()) } }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to text
    }

    private fun saveSession(text: String) {
        val o = json.parseToJsonElement(text).jsonObject
        o["refresh_token"]?.jsonPrimitive?.content?.let { prefs.sbRefreshToken = it }
        o["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.let { prefs.sbUserId = it }
    }

    private fun err(code: Int, text: String): Nothing {
        val msg = runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            (o["error_description"] ?: o["msg"] ?: o["message"] ?: o["error"])?.jsonPrimitive?.content
        }.getOrNull()
        throw IllegalStateException("HTTP $code: ${msg ?: text.take(200)}")
    }

    /** Sign in (or sign up if [create]) with email + password. Only the refresh token is stored. */
    suspend fun login(email: String, password: String, create: Boolean): Unit = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("email", email); put("password", password) }.toString()
        val (code, text) = if (create) http("POST", "/auth/v1/signup", body, null)
        else http("POST", "/auth/v1/token?grant_type=password", body, null)
        if (code !in 200..299) err(code, text)
        if (create && !text.contains("refresh_token")) {
            throw IllegalStateException("Account created. Confirm the e-mail (or disable 'Confirm email' in Supabase Auth settings) then sign in.")
        }
        prefs.sbEmail = email
        saveSession(text)
    }

    private fun accessToken(): String {
        val body = buildJsonObject { put("refresh_token", prefs.sbRefreshToken) }.toString()
        val (code, text) = http("POST", "/auth/v1/token?grant_type=refresh_token", body, null)
        if (code !in 200..299) err(code, text)
        saveSession(text)
        return json.parseToJsonElement(text).jsonObject["access_token"]!!.jsonPrimitive.content
    }

    suspend fun backup(repo: Repository): Unit = withContext(Dispatchers.IO) {
        val token = accessToken()
        val data = json.parseToJsonElement(BackupJson.export(repo))
        val row = JsonArray(listOf(buildJsonObject {
            put("user_id", prefs.sbUserId)
            put("data", data)
            put("updated_at", java.time.Instant.now().toString())
        }))
        val (code, text) = http("POST", "/rest/v1/moneyfen_backups?on_conflict=user_id", row.toString(), token,
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
        if (code !in 200..299) err(code, text)
        prefs.lastBackup = System.currentTimeMillis()
    }

    suspend fun restore(repo: Repository): BackupData = withContext(Dispatchers.IO) {
        val token = accessToken()
        val (code, text) = http("GET", "/rest/v1/moneyfen_backups?select=data,updated_at&limit=1", null, token)
        if (code !in 200..299) err(code, text)
        val arr = json.parseToJsonElement(text) as JsonArray
        if (arr.isEmpty()) throw IllegalStateException("No backup found / لا توجد نسخة احتياطية")
        val data = (arr[0] as JsonObject)["data"]!!
        BackupJson.import(repo, data.toString())
    }

    fun logout() { prefs.sbRefreshToken = ""; prefs.sbUserId = "" }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        if (!app.prefs.supabaseReady || !app.prefs.autoBackup) return Result.success()
        return runCatching { SupabaseBackup(app.prefs).backup(app.repo) }
            .fold({ Result.success() }, { if (runAttemptCount < 3) Result.retry() else Result.failure() })
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("supabase-backup", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
