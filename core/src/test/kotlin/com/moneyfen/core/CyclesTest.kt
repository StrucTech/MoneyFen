package com.moneyfen.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class CyclesTest {
    private val z = ZoneId.of("Africa/Cairo")
    private fun t(s: String) = LocalDate.parse(s).atTime(13, 0).atZone(z).toInstant().toEpochMilli()
    private fun d(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(z).toLocalDate().toString()

    private val salaries = listOf("2026-02-26", "2026-03-15", "2026-03-17", "2026-03-29", "2026-04-27", "2026-08-26", "2026-08-30",
        "2026-09-27", "2026-10-13", "2026-10-27").map(::t)

    @Test fun ignoresExtraPayments() {
        val s = Cycles.salaryStarts(salaries, 20, z).map(::d)
        assertEquals(listOf("2026-02-26", "2026-03-29", "2026-04-27", "2026-08-26", "2026-09-27", "2026-10-27"), s)
    }

    @Test fun currentAndPrevious() {
        val s = Cycles.salaryStarts(salaries, 20, z)
        val now = t("2026-11-02")
        val (a, b) = Cycles.range("SALARY", 1, s, 0, now, z)
        assertEquals("2026-10-27" to "2026-11-27", d(a) to d(b))
        val (c, e) = Cycles.range("SALARY", 1, s, -1, now, z)
        assertEquals("2026-09-27" to "2026-10-27", d(c) to d(e))
        val (f, g) = Cycles.range("SALARY", 1, s, -5, now, z)
        assertEquals("2026-02-26" to "2026-03-29", d(f) to d(g))
        val (h, k) = Cycles.range("SALARY", 1, s, -8, now, z)
        assertEquals("2025-11-26" to "2025-12-26", d(h) to d(k))
    }

    @Test fun lateSalaryKeepsPeriodOpen() {
        val s = Cycles.salaryStarts(salaries, 20, z)
        val (a, b) = Cycles.range("SALARY", 1, s, 0, t("2026-11-29"), z)
        assertEquals("2026-10-27" to "2026-11-30", d(a) to d(b))
    }

    @Test fun fixedDayAndCalendar() {
        val (a, b) = Cycles.range("DAY", 25, emptyList(), 0, t("2026-11-02"), z)
        assertEquals("2026-10-25" to "2026-11-25", d(a) to d(b))
        val (c, e) = Cycles.range("CALENDAR", 1, emptyList(), -1, t("2026-11-02"), z)
        assertEquals("2026-10-01" to "2026-11-01", d(c) to d(e))
    }
}
