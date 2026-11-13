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

/** One line of an account's history: the balance right after a transaction. */
data class BalancePoint(
    val index: Int,          // index in the input list (-1 for a manual balance correction)
    val time: Long,
    val balance: Double,
    /** Balance confirmed by the bank SMS (or set by hand), not just calculated. */
    val confirmed: Boolean,
    /** When confirmed: reported − calculated (fees / missing messages). 0 otherwise. */
    val gap: Double = 0.0,
)

object RunningBalance {
    /**
     * Balance after every transaction, consistent with [BalanceCalc.compute]:
     * walks forward, and each bank-reported balance (or manual correction) resets the running value.
     * Before the first known balance, values are worked out backwards from it.
     */
    fun compute(
        txs: List<BalTx>,
        isCreditCard: Boolean,
        creditLimit: Double,
        manualBalance: Double?,
        manualAt: Long?,
    ): List<BalancePoint> {
        data class Ev(val idx: Int, val time: Long, val order: Long, val signed: Double, val anchor: Double?)
        val evs = txs.mapIndexed { i, t ->
            val a = when {
                isCreditCard && t.availableAfter != null && creditLimit > 0 -> -(creditLimit - t.availableAfter)
                !isCreditCard && t.balanceAfter != null -> t.balanceAfter
                else -> null
            }
            Ev(i, t.time, t.order, t.signed, a)
        }.toMutableList()
        // Manual correction: applies after everything at or before manualAt (same rule as BalanceCalc)
        if (manualBalance != null && manualAt != null) evs += Ev(-1, manualAt, Long.MAX_VALUE, 0.0, manualBalance)
        evs.sortWith(compareBy<Ev> { it.time }.thenBy { it.order })
        // A manual value only wins over a later-or-equal SMS anchor the way BalanceCalc does: drop it if an SMS anchor is newer.
        val lastSmsAnchor = evs.filter { it.idx >= 0 && it.anchor != null }.maxOfOrNull { it.time }
        if (manualAt != null && lastSmsAnchor != null && lastSmsAnchor > manualAt) evs.removeAll { it.idx == -1 }

        val firstAnchor = evs.indexOfFirst { it.anchor != null }
        val out = ArrayList<BalancePoint>(evs.size)
        var bal: Double
        if (firstAnchor < 0) {
            bal = 0.0
            for (e in evs) { bal += e.signed; out += BalancePoint(e.idx, e.time, bal, false) }
            return out
        }
        // Backwards before the first known balance
        val back = DoubleArray(firstAnchor)
        var b = evs[firstAnchor].anchor!! - evs[firstAnchor].signed
        for (i in firstAnchor - 1 downTo 0) { back[i] = b; b -= evs[i].signed }
        for (i in 0 until firstAnchor) out += BalancePoint(evs[i].idx, evs[i].time, back[i], false)
        bal = evs[firstAnchor].anchor!!
        out += BalancePoint(evs[firstAnchor].idx, evs[firstAnchor].time, bal, true)
        for (i in firstAnchor + 1 until evs.size) {
            val e = evs[i]
            val calc = bal + e.signed
            if (e.anchor != null) {
                out += BalancePoint(e.idx, e.time, e.anchor, true, if (e.idx >= 0) e.anchor - calc else 0.0)
                bal = e.anchor
            } else {
                bal = calc
                out += BalancePoint(e.idx, e.time, bal, false)
            }
        }
        return out
    }
}
