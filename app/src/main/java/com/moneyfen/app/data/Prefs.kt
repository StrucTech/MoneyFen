package com.moneyfen.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small key/value settings, observable for the UI. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("moneyfen", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun bump() { _version.value++ }

    var senders: String
        get() = sp.getString("senders", "CIB") ?: "CIB"
        set(v) { sp.edit { putString("senders", v) }; bump() }
    fun senderList(): List<String> = senders.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    var usdRate: Double
        get() = sp.getFloat("usdRate", 50f).toDouble()
        set(v) { sp.edit { putFloat("usdRate", v.toFloat()) }; bump() }

    var savingsPercent: Int
        get() = sp.getInt("savingsPercent", 20)
        set(v) { sp.edit { putInt("savingsPercent", v) }; bump() }

    var atmToCash: Boolean
        get() = sp.getBoolean("atmToCash", false)
        set(v) { sp.edit { putBoolean("atmToCash", v) }; bump() }

    var notifyOnSms: Boolean
        get() = sp.getBoolean("notifyOnSms", true)
        set(v) { sp.edit { putBoolean("notifyOnSms", v) }; bump() }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit { putBoolean("onboarded", v) }; bump() }

    var seedVersion: Int
        get() = sp.getInt("seedVersion", 1)
        set(v) { sp.edit { putInt("seedVersion", v) }; bump() }

    var hideAmounts: Boolean
        get() = sp.getBoolean("hideAmounts", false)
        set(v) { sp.edit { putBoolean("hideAmounts", v) }; bump() }

    // Asking how much of a charge was really spent
    var reviewCredit: Boolean
        get() = sp.getBoolean("reviewCredit", true)
        set(v) { sp.edit { putBoolean("reviewCredit", v) }; bump() }
    var reviewDebit: Boolean
        get() = sp.getBoolean("reviewDebit", false)
        set(v) { sp.edit { putBoolean("reviewDebit", v) }; bump() }
    /** Where the non-personal part goes: CASH, LENT or NONE. */
    var reviewDest: String
        get() = sp.getString("reviewDest", "CASH") ?: "CASH"
        set(v) { sp.edit { putString("reviewDest", v) }; bump() }

    // Saving from income
    var askSalary: Boolean
        get() = sp.getBoolean("askSalary", true)
        set(v) { sp.edit { putBoolean("askSalary", v) }; bump() }
    /** PERCENT or FIXED */
    var salaryMode: String
        get() = sp.getString("salaryMode", "PERCENT") ?: "PERCENT"
        set(v) { sp.edit { putString("salaryMode", v) }; bump() }
    var salaryFixed: Double
        get() = sp.getFloat("salaryFixed", 0f).toDouble()
        set(v) { sp.edit { putFloat("salaryFixed", v.toFloat()) }; bump() }
    var lastSaveGoal: Long
        get() = sp.getLong("lastSaveGoal", 0)
        set(v) { sp.edit { putLong("lastSaveGoal", v) }; bump() }
    /** Account ids NOT counted in "safe to spend". */
    var safeExclude: Set<Long>
        get() = (sp.getString("safeExclude", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { sp.edit { putString("safeExclude", v.joinToString(",")) }; bump() }

    /** When a budget month starts: SALARY (day salary arrives), DAY (fixed day), CALENDAR (1st). */
    var cycleMode: String
        get() = sp.getString("cycleMode", "SALARY") ?: "SALARY"
        set(v) { sp.edit { putString("cycleMode", v) }; bump() }
    var cycleDay: Int
        get() = sp.getInt("cycleDay", 25)
        set(v) { sp.edit { putInt("cycleDay", v.coerceIn(1, 31)) }; bump() }

    // Live prices
    var autoUsd: Boolean
        get() = sp.getBoolean("autoUsd", true)
        set(v) { sp.edit { putBoolean("autoUsd", v) }; bump() }
    /** Extra % added to the international gold price to match the local market. */
    var goldPremium: Double
        get() = sp.getFloat("goldPremium", 0f).toDouble()
        set(v) { sp.edit { putFloat("goldPremium", v.toFloat()) }; bump() }
    var gold24Gram: Double
        get() = sp.getFloat("gold24", 0f).toDouble()
        set(v) { sp.edit { putFloat("gold24", v.toFloat()) }; bump() }
    var pricesAt: Long
        get() = sp.getLong("pricesAt", 0)
        set(v) { sp.edit { putLong("pricesAt", v) }; bump() }
    var pricesError: String
        get() = sp.getString("pricesError", "") ?: ""
        set(v) { sp.edit { putString("pricesError", v) }; bump() }

    fun goldGram(karat: Int): Double = gold24Gram * karat / 24.0

    // Supabase backup
    var sbUrl: String
        get() = sp.getString("sbUrl", "") ?: ""
        set(v) { sp.edit { putString("sbUrl", v.trim().trimEnd('/')) }; bump() }
    var sbKey: String
        get() = sp.getString("sbKey", "") ?: ""
        set(v) { sp.edit { putString("sbKey", v.trim()) }; bump() }
    var sbEmail: String
        get() = sp.getString("sbEmail", "") ?: ""
        set(v) { sp.edit { putString("sbEmail", v.trim()) }; bump() }
    var sbRefreshToken: String
        get() = sp.getString("sbRefresh", "") ?: ""
        set(v) { sp.edit { putString("sbRefresh", v) }; bump() }
    var sbUserId: String
        get() = sp.getString("sbUser", "") ?: ""
        set(v) { sp.edit { putString("sbUser", v) }; bump() }
    var autoBackup: Boolean
        get() = sp.getBoolean("autoBackup", true)
        set(v) { sp.edit { putBoolean("autoBackup", v) }; bump() }
    var lastBackup: Long
        get() = sp.getLong("lastBackup", 0)
        set(v) { sp.edit { putLong("lastBackup", v) }; bump() }

    val supabaseReady: Boolean get() = sbUrl.isNotBlank() && sbKey.isNotBlank() && sbRefreshToken.isNotBlank()

    fun exportMap(): Map<String, String> = mapOf(
        "senders" to senders, "usdRate" to usdRate.toString(), "savingsPercent" to savingsPercent.toString(),
        "atmToCash" to atmToCash.toString(), "notifyOnSms" to notifyOnSms.toString(),
        "reviewCredit" to reviewCredit.toString(), "reviewDebit" to reviewDebit.toString(), "reviewDest" to reviewDest,
        "autoUsd" to autoUsd.toString(), "goldPremium" to goldPremium.toString(),
        "askSalary" to askSalary.toString(), "salaryMode" to salaryMode, "salaryFixed" to salaryFixed.toString(),
        "safeExclude" to safeExclude.joinToString(","), "cycleMode" to cycleMode, "cycleDay" to cycleDay.toString(),
    )

    fun importMap(m: Map<String, String>) {
        m["senders"]?.let { senders = it }
        m["usdRate"]?.toDoubleOrNull()?.let { usdRate = it }
        m["savingsPercent"]?.toIntOrNull()?.let { savingsPercent = it }
        m["atmToCash"]?.toBooleanStrictOrNull()?.let { atmToCash = it }
        m["notifyOnSms"]?.toBooleanStrictOrNull()?.let { notifyOnSms = it }
        m["reviewCredit"]?.toBooleanStrictOrNull()?.let { reviewCredit = it }
        m["reviewDebit"]?.toBooleanStrictOrNull()?.let { reviewDebit = it }
        m["reviewDest"]?.let { reviewDest = it }
        m["autoUsd"]?.toBooleanStrictOrNull()?.let { autoUsd = it }
        m["goldPremium"]?.toDoubleOrNull()?.let { goldPremium = it }
        m["askSalary"]?.toBooleanStrictOrNull()?.let { askSalary = it }
        m["salaryMode"]?.let { salaryMode = it }
        m["salaryFixed"]?.toDoubleOrNull()?.let { salaryFixed = it }
        m["cycleMode"]?.let { cycleMode = it }
        m["cycleDay"]?.toIntOrNull()?.let { cycleDay = it }
        m["safeExclude"]?.let { v -> safeExclude = v.split(',').mapNotNull { it.toLongOrNull() }.toSet() }
    }
}
