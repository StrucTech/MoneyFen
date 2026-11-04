package com.moneyfen.core

import kotlin.math.roundToLong

/** InstaPay transfer fee: [pct]% of the amount, clamped to [min]..[max]. Default 0.1%, 0.5 – 20 EGP. */
object InstaPayFee {
    fun calc(amount: Double, pct: Double = 0.1, min: Double = 0.5, max: Double = 20.0): Double {
        if (amount <= 0) return 0.0
        val fee = (amount * pct / 100).coerceIn(min, max)
        return (fee * 100).roundToLong() / 100.0
    }

    /** True for the InstaPay "instant transfer from your account" SMS. */
    fun isInstaPayTransfer(body: String?): Boolean = body != null && body.contains("تحويل لحظي") && body.contains("من حسابك")
}
