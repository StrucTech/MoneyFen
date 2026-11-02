package com.moneyfen.app.data

import android.content.Context
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Quote(val price: Double, val currency: String)
data class SymbolHit(val symbol: String, val name: String, val exchange: String, val type: String)

/**
 * Free live prices, no API key:
 *  - Yahoo Finance chart API: stocks (EGX tickers end with .CA, e.g. COMI.CA), gold futures GC=F, USD/EGP (EGP=X)
 *  - Fallbacks: api.gold-api.com (gold), open.er-api.com (USD/EGP)
 */
object Prices {
    private const val TROY_OUNCE_GRAMS = 31.1034768
    private val json = Json { ignoreUnknownKeys = true }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) MoneyFen")
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code == 404) throw NoSuchElementException("Not found (404)")
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${URL(url).host}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    fun yahoo(symbol: String): Quote {
        val enc = URLEncoder.encode(symbol.trim().uppercase(), "UTF-8")
        var last: Exception? = null
        for (host in listOf("query1", "query2")) {
            try {
                val o = json.parseToJsonElement(get("https://$host.finance.yahoo.com/v8/finance/chart/$enc?range=1d&interval=1d")).jsonObject
                val res = o["chart"]!!.jsonObject["result"]!!.jsonArray[0].jsonObject
                val meta = res["meta"]!!.jsonObject
                val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull
                    ?: meta["previousClose"]?.jsonPrimitive?.doubleOrNull
                    ?: error("no price for $symbol")
                val cur = meta["currency"]?.jsonPrimitive?.content ?: "USD"
                // Some markets quote in sub-units (e.g. GBp)
                return if (cur == "GBp") Quote(price / 100, "GBP") else Quote(price, cur.uppercase())
            } catch (e: NoSuchElementException) {
                throw NoSuchElementException("\"$symbol\" not on Yahoo / مش موجود على Yahoo. Use Search, or 'Web page' / 'Manual' for funds.")
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("Yahoo failed")
    }

    /** Search Yahoo symbols by name ("Commercial International", "Fawry", "Apple"). */
    fun search(q: String): List<SymbolHit> {
        val enc = URLEncoder.encode(q.trim(), "UTF-8")
        val o = json.parseToJsonElement(get("https://query2.finance.yahoo.com/v1/finance/search?q=$enc&quotesCount=15&newsCount=0")).jsonObject
        return (o["quotes"]?.jsonArray ?: return emptyList()).mapNotNull { e ->
            val m = e.jsonObject
            val sym = m["symbol"]?.jsonPrimitive?.content ?: return@mapNotNull null
            SymbolHit(
                sym,
                (m["longname"] ?: m["shortname"])?.jsonPrimitive?.content ?: "",
                (m["exchDisp"] ?: m["exchange"])?.jsonPrimitive?.content ?: "",
                (m["typeDisp"] ?: m["quoteType"])?.jsonPrimitive?.content ?: "",
            )
        }
    }

    /**
     * Reads a price from any web page: finds [anchor] text in the page (tags removed) and takes the first number after it.
     * If [anchor] contains a "(" it is used as a regex and group 1 is the price.
     */
    fun fromUrl(url: String, anchor: String?): Double {
        val c = URL(url.trim()).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        val html = try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
        return extractPrice(html, anchor)
    }

    fun extractPrice(html: String, anchor: String?): Double = com.moneyfen.core.PriceText.extract(html, anchor)

    fun usdEgp(): Double = runCatching { yahoo("EGP=X").price }.getOrElse {
        val o = json.parseToJsonElement(get("https://open.er-api.com/v6/latest/USD")).jsonObject
        (o["rates"] as JsonObject)["EGP"]!!.jsonPrimitive.doubleOrNull!!
    }

    /** Gold USD per troy ounce. */
    fun goldUsdOunce(): Double = runCatching { yahoo("GC=F").price }.getOrElse {
        json.parseToJsonElement(get("https://api.gold-api.com/price/XAU")).jsonObject["price"]!!.jsonPrimitive.doubleOrNull!!
    }

    /**
     * Refreshes USD rate, gold price and every holding with auto price on.
     * Returns a short error text (empty if everything worked).
     */
    suspend fun refreshAll(repo: Repository): String = withContext(Dispatchers.IO) {
        val prefs = repo.prefs
        val errors = mutableListOf<String>()
        val usd = runCatching { usdEgp() }.onFailure { errors += "USD: ${it.message}" }.getOrNull()
        if (usd != null && prefs.autoUsd) prefs.usdRate = usd
        val rate = usd ?: prefs.usdRate
        val accounts = repo.db.accounts().all()
        val needGold = accounts.any { it.accountType == AccountType.GOLD && it.autoPrice }
        if (needGold || prefs.gold24Gram == 0.0) {
            runCatching { goldUsdOunce() }.onSuccess { oz ->
                prefs.gold24Gram = oz / TROY_OUNCE_GRAMS * rate * (1 + prefs.goldPremium / 100)
            }.onFailure { errors += "Gold: ${it.message}" }
        }
        val now = System.currentTimeMillis()
        for (a in accounts) {
            if (!a.autoPrice) continue
            when (a.accountType) {
                AccountType.GOLD -> if (prefs.gold24Gram > 0) {
                    val g = prefs.goldGram(a.karat ?: 21)
                    repo.db.accounts().upsert(a.copy(unitPrice = if (a.currency == "USD") g / rate else g, priceAt = now))
                }
                AccountType.STOCK, AccountType.FUND -> {
                    val url = a.priceUrl?.takeIf { it.isNotBlank() }
                    if (url != null) {
                        runCatching { fromUrl(url, a.priceAnchor) }.onSuccess { p ->
                            repo.db.accounts().upsert(a.copy(unitPrice = p, priceAt = now))
                        }.onFailure { errors += "${a.name}: ${it.message}" }
                        continue
                    }
                    val sym = a.symbol?.takeIf { it.isNotBlank() } ?: continue
                    runCatching { yahoo(sym) }.onSuccess { q ->
                        val cur = if (q.currency == "EGP" || q.currency == "USD") q.currency else a.currency
                        repo.db.accounts().upsert(a.copy(unitPrice = q.price, currency = cur, priceAt = now))
                    }.onFailure { errors += "$sym: ${it.message}" }
                }
                else -> {}
            }
        }
        prefs.pricesAt = now
        prefs.pricesError = errors.joinToString("\n")
        prefs.pricesError
    }
}

class PriceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Prices.refreshAll(applicationContext.app.repo)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<PriceWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("prices", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
