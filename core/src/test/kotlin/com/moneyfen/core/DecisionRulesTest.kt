package com.moneyfen.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecisionRulesTest {
    private val rules = listOf(
        AutoRule("Food Shop", AutoAction.MINE),
        AutoRule("Client A", AutoAction.INCOME),
        AutoRule("Friend B", AutoAction.REPAID),
    )

    @Test fun alwaysMineForAShop() {
        assertEquals(AutoAction.MINE, DecisionRules.pick(rules, "FOOD SHOP CAIRO", -1))
        // Money from the same name isn't touched by an expense rule
        assertNull(DecisionRules.pick(rules, "Food Shop", 1))
    }

    @Test fun alwaysIncomeForAClient() {
        assertEquals(AutoAction.INCOME, DecisionRules.pick(rules, "Client A", 1))
        assertNull(DecisionRules.pick(rules, "Client A", -1))
    }

    @Test fun repaidWorksBothWays() {
        assertEquals(AutoAction.REPAID, DecisionRules.pick(rules, "friend b", -1))
        assertEquals(AutoAction.REPAID, DecisionRules.pick(rules, "friend b", 1))
    }

    @Test fun firstFittingRuleWins() {
        val r = listOf(AutoRule("Shop", AutoAction.INCOME), AutoRule("Shop", AutoAction.INTERNAL), AutoRule("Shop", AutoAction.MINE))
        assertEquals(AutoAction.INTERNAL, DecisionRules.pick(r, "Shop", -1))
        assertEquals(AutoAction.INCOME, DecisionRules.pick(r, "Shop", 1))
    }

    @Test fun noNameOrNoRuleAsks() {
        assertNull(DecisionRules.pick(rules, null, -1))
        assertNull(DecisionRules.pick(rules, "  ", -1))
        assertNull(DecisionRules.pick(rules, "Someone new", -1))
        assertNull(DecisionRules.pick(rules, "Food Shop", 0))
    }

    @Test fun choicesByDirection() {
        assertEquals(listOf(AutoAction.MINE, AutoAction.REPAID, AutoAction.INTERNAL), DecisionRules.choices(-1))
        assertEquals(listOf(AutoAction.REPAID, AutoAction.INCOME, AutoAction.NOT_INCOME), DecisionRules.choices(1))
    }

    @Test fun parse() {
        assertEquals(AutoAction.NOT_INCOME, AutoAction.parse("NOT_INCOME"))
        assertNull(AutoAction.parse(null))
        assertNull(AutoAction.parse("nonsense"))
    }

    @Test fun askIncomingOnlyForUnknownTransfers() {
        assertTrue(DecisionRules.askIncoming(TxKind.TRANSFER_IN, "New Person", known = false))
        assertFalse(DecisionRules.askIncoming(TxKind.TRANSFER_IN, "Client A", known = true))
        assertFalse(DecisionRules.askIncoming(TxKind.TRANSFER_IN, null, known = false))
        assertFalse(DecisionRules.askIncoming(TxKind.SALARY, "Employer", known = false))
        assertFalse(DecisionRules.askIncoming(TxKind.DEPOSIT, "ATM", known = false))
    }
}
