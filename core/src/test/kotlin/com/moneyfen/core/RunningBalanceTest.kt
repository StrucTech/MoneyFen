package com.moneyfen.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RunningBalanceTest {
    @Test fun forwardBackwardAndGap() {
        val txs = listOf(
            BalTx(1, +500.0, order = 1),                       // before first anchor → back-computed
            BalTx(2, -100.0, balanceAfter = 900.0, order = 2), // anchor
            BalTx(3, -50.0, order = 3),
            BalTx(4, -20.0, balanceAfter = 829.0, order = 4),  // 1 EGP fee missing → gap -1
        )
        val r = RunningBalance.compute(txs, false, 0.0, null, null)
        assertEquals(listOf(1000.0, 900.0, 850.0, 829.0), r.map { it.balance })
        assertEquals(-1.0, r[3].gap, 1e-9)
        assertEquals(BalanceCalc.compute(txs, false, 0.0, null, null), r.last().balance, 1e-9)
    }

    @Test fun manualAndCredit() {
        val txs = listOf(BalTx(1, -100.0, order = 1), BalTx(3, -50.0, order = 2))
        val r = RunningBalance.compute(txs, false, 0.0, 1000.0, 2)
        assertEquals(1000.0 - 50.0, r.last().balance)
        assertEquals(BalanceCalc.compute(txs, false, 0.0, 1000.0, 2), r.last().balance)
        val cc = listOf(BalTx(1, -300.0, availableAfter = 59200.0, order = 1), BalTx(2, 300.0, order = 2))
        assertEquals(listOf(-300.0, 0.0), RunningBalance.compute(cc, true, 59500.0, null, null).map { it.balance })
    }

    /** Same SMS export as CibParserTest.realExport: last running value must equal the account balance. */
    @Test fun realExport() {
        val path = System.getenv("MONEYFEN_SMS_TSV") ?: return
        val bank = mutableListOf<BalTx>()
        java.io.File(path).readLines().forEachIndexed { i, line ->
            val p = line.split('\t'); val r = CibParser.parse(p.getOrNull(2) ?: "")
            if (r !is ParseResult.Tx || r.tx.kind.isCreditCardKind) return@forEachIndexed
            val time = java.time.LocalDateTime.parse(p[0].replace(' ', 'T')).toEpochSecond(java.time.ZoneOffset.UTC)
            bank += BalTx(time, r.tx.amount * r.tx.kind.direction, r.tx.balanceAfter, r.tx.availableAfter, i.toLong())
        }
        val pts = RunningBalance.compute(bank, false, 0.0, null, null)
        assertEquals(BalanceCalc.compute(bank, false, 0.0, null, null), pts.last().balance, 1e-6)
        val gaps = pts.filter { kotlin.math.abs(it.gap) > 0.009 }
        println("HISTORY points=${pts.size} gaps=${gaps.size} totalGap=${"%.2f".format(gaps.sumOf { it.gap })}")
    }
}
