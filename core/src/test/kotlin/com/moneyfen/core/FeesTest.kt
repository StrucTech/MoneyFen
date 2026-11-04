package com.moneyfen.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FeesTest {
    /** Checked against real balances in the user's SMS history. */
    @Test fun realCases() {
        assertEquals(3.0, InstaPayFee.calc(3000.0))
        assertEquals(0.63, InstaPayFee.calc(630.0))
        assertEquals(0.5, InstaPayFee.calc(50.0))
        assertEquals(0.5, InstaPayFee.calc(372.0))
        assertEquals(20.0, InstaPayFee.calc(50000.0))
        val day = listOf(650.0, 2000.0, 140.0, 430.0, 3000.0, 460.0, 2300.0, 8500.0, 1440.0)
        assertEquals(19.39, (day.sumOf { InstaPayFee.calc(it) } * 100).let { Math.round(it) / 100.0 })
    }
}
