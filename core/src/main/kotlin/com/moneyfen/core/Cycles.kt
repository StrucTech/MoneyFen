package com.moneyfen.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Budget "months". Modes:
 *  - SALARY: a month starts on the day salary arrives (extra employer payments < [minGapDays] after a start are ignored)
 *  - DAY: a month starts on a fixed day of the month (e.g. 25)
 *  - CALENDAR: 1st of the month
 */
object Cycles {
    private fun day(t: Long, z: ZoneId): LocalDate = Instant.ofEpochMilli(t).atZone(z).toLocalDate()
    private fun ms(d: LocalDate, z: ZoneId): Long = d.atStartOfDay(z).toInstant().toEpochMilli()

    /** Start-of-day times of salary cycles. */
    fun salaryStarts(salaryTimes: List<Long>, minGapDays: Int = 20, z: ZoneId = ZoneId.systemDefault()): List<Long> {
        val out = mutableListOf<LocalDate>()
        for (d in salaryTimes.sorted().map { day(it, z) }) {
            val last = out.lastOrNull()
            if (last == null || java.time.temporal.ChronoUnit.DAYS.between(last, d) >= minGapDays) out += d
        }
        return out.map { ms(it, z) }
    }

    private fun dayRange(dayOfMonth: Int, offset: Int, now: Long, z: ZoneId): Pair<Long, Long> {
        fun at(ym: java.time.YearMonth) = ym.atDay(dayOfMonth.coerceIn(1, ym.lengthOfMonth()))
        val today = day(now, z)
        var ym = java.time.YearMonth.from(today)
        if (at(ym).isAfter(today)) ym = ym.minusMonths(1)
        val s = ym.plusMonths(offset.toLong())
        return ms(at(s), z) to ms(at(s.plusMonths(1)), z)
    }

    fun range(mode: String, dayOfMonth: Int, starts: List<Long>, offset: Int, now: Long, z: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        return when (mode) {
            "DAY" -> dayRange(dayOfMonth, offset, now, z)
            "SALARY" -> {
                val i = starts.indexOfLast { it <= now }
                if (i < 0) return dayRange(1, offset, now, z)
                val j = i + offset
                when {
                    j < 0 -> {
                        val first = day(starts[0], z)
                        ms(first.plusMonths(j.toLong()), z) to ms(first.plusMonths(j.toLong() + 1), z)
                    }
                    j < starts.size - 1 -> starts[j] to starts[j + 1]
                    else -> {
                        // Last known salary: the period runs one month (or until today if salary is late)
                        val start = day(starts[starts.size - 1], z).plusMonths((j - (starts.size - 1)).toLong())
                        val end = ms(start.plusMonths(1), z)
                        ms(start, z) to if (j == i) maxOf(end, ms(day(now, z).plusDays(1), z)) else end
                    }
                }
            }
            else -> dayRange(1, offset, now, z)
        }
    }
}
