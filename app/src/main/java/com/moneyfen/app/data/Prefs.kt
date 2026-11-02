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

    var hideAmounts: Boolean
        get() = sp.getBoolean("hideAmounts", false)
        set(v) { sp.edit { putBoolean("hideAmounts", v) }; bump() }

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
    )

    fun importMap(m: Map<String, String>) {
        m["senders"]?.let { senders = it }
        m["usdRate"]?.toDoubleOrNull()?.let { usdRate = it }
        m["savingsPercent"]?.toIntOrNull()?.let { savingsPercent = it }
        m["atmToCash"]?.toBooleanStrictOrNull()?.let { atmToCash = it }
        m["notifyOnSms"]?.toBooleanStrictOrNull()?.let { notifyOnSms = it }
    }
}
