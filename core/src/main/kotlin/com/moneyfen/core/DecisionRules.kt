package com.moneyfen.core

/**
 * What the app does by itself with a transaction instead of asking ("always do this with <name>").
 * REPAID depends on the direction: money out = someone will pay me back; money in = a repayment of what I'm owed.
 */
enum class AutoAction(val forOut: Boolean, val forIn: Boolean) {
    MINE(forOut = true, forIn = false),
    REPAID(forOut = true, forIn = true),
    INTERNAL(forOut = true, forIn = false),
    INCOME(forOut = false, forIn = true),
    NOT_INCOME(forOut = false, forIn = true);

    /** [direction] as in [TxKind.direction]: -1 out, +1 in. */
    fun fits(direction: Int): Boolean = if (direction < 0) forOut else if (direction > 0) forIn else false

    companion object {
        fun parse(s: String?): AutoAction? = s?.let { v -> entries.firstOrNull { it.name == v } }
    }
}

/** An "always" rule as the decision logic sees it: name pattern (same syntax as [RuleMatcher]) + action. */
data class AutoRule(val pattern: String, val action: AutoAction)

object DecisionRules {
    /** Choices offered for a transaction going in [direction]. */
    fun choices(direction: Int): List<AutoAction> = AutoAction.entries.filter { it.fits(direction) }

    /**
     * The action to apply by itself, or null to ask the user.
     * [rules] are in priority order (first wins); a rule only counts when its action fits the direction,
     * so "always income" for a client never touches money sent to that client.
     */
    fun pick(rules: List<AutoRule>, party: String?, direction: Int): AutoAction? {
        if (party.isNullOrBlank()) return null
        return rules.firstOrNull { it.action.fits(direction) && RuleMatcher.matches(it.pattern, party) }?.action
    }

    /**
     * Should money coming in be asked about? Only transfers from someone the rules don't know yet
     * ([known] = a rule already decides what it is: a category or an "always" action).
     */
    fun askIncoming(kind: TxKind, party: String?, known: Boolean): Boolean =
        kind == TxKind.TRANSFER_IN && !party.isNullOrBlank() && !known
}
