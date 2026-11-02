package com.moneyfen.core

import kotlin.math.max

/** Minimal view of a transaction for balance computation. */
data class BalTx(
    val time: Long,
    /** Signed amount in the account currency (+ in, - out). */
    val signed: Double,
    val balanceAfter: Double? = null,
    val availableAfter: Double? = null,
    val order: Long = 0,
)

object BalanceCalc {
    /**
     * Balance = latest anchor + sum of later transactions.
     * Anchors are: a manual balance the user typed (at [manualAt]) or a bank-reported balance in an SMS.
     * For credit cards the balance is negative debt = -(limit - available).
     */
    fun compute(
        txs: List<BalTx>,
        isCreditCard: Boolean,
        creditLimit: Double,
        manualBalance: Double?,
        manualAt: Long?,
    ): Double {
        val sorted = txs.sortedWith(compareBy<BalTx> { it.time }.thenBy { it.order })
        var anchorIdx = -1
        var anchorValue = 0.0
        var anchorTime = Long.MIN_VALUE
        sorted.forEachIndexed { i, t ->
            val v = when {
                isCreditCard && t.availableAfter != null && creditLimit > 0 -> -(creditLimit - t.availableAfter)
                !isCreditCard && t.balanceAfter != null -> t.balanceAfter
                else -> null
            }
            if (v != null) { anchorIdx = i; anchorValue = v; anchorTime = t.time }
        }
        if (manualBalance != null && manualAt != null && manualAt >= anchorTime) {
            return manualBalance + sorted.filter { it.time > manualAt }.sumOf { it.signed }
        }
        if (anchorIdx < 0) return (manualBalance ?: 0.0) + sorted.sumOf { it.signed }
        return anchorValue + sorted.drop(anchorIdx + 1).sumOf { it.signed }
    }
}

object PlanMath {
    /** Whole months remaining (at least 1) between now and deadline. */
    fun monthsLeft(nowMs: Long, deadlineMs: Long): Int {
        val days = (deadlineMs - nowMs) / 86_400_000.0
        return max(1, kotlin.math.ceil(days / 30.4375).toInt())
    }

    fun monthlyNeeded(target: Double, current: Double, nowMs: Long, deadlineMs: Long): Double =
        max(0.0, target - current) / monthsLeft(nowMs, deadlineMs)
}

/** Simple "contains" rule matching for auto-categorisation. */
object RuleMatcher {
    fun matches(pattern: String, vararg fields: String?): Boolean {
        val keys = pattern.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        return keys.any { k -> fields.any { f -> f != null && f.contains(k, ignoreCase = true) } }
    }
}
