package com.moneyfen.core

/**
 * A user-defined SMS template (for wallets, other banks, ...).
 * The regex may contain named groups: amount (required), party, balance, account.
 */
data class CustomPattern(
    val senderContains: String,
    val regex: String,
    val kind: TxKind,
    val currency: String = "EGP",
)

object CustomParser {
    fun parse(sender: String, body: String, patterns: List<CustomPattern>): ParsedSms? {
        val text = body.normalizeSpaces()
        for (p in patterns) {
            if (p.senderContains.isNotBlank() && !sender.contains(p.senderContains, ignoreCase = true)) continue
            val re = runCatching { Regex(p.regex, RegexOption.IGNORE_CASE) }.getOrNull() ?: continue
            val m = re.find(text) ?: continue
            val amount = m.named("amount")?.toAmount() ?: continue
            return ParsedSms(
                kind = p.kind,
                amount = amount,
                currency = p.currency,
                accountHint = m.named("account"),
                party = m.named("party"),
                balanceAfter = m.named("balance")?.toAmount(),
            )
        }
        return null
    }

    private fun MatchResult.named(name: String): String? =
        runCatching { groups[name]?.value?.trim() }.getOrNull()?.ifBlank { null }

    /** Validate a pattern against a sample message; returns error text or the parse result. */
    fun test(pattern: CustomPattern, sample: String): Result<ParsedSms> = runCatching {
        Regex(pattern.regex, RegexOption.IGNORE_CASE)
        parse(pattern.senderContains, sample, listOf(pattern)) ?: error("No match / no <amount> group")
    }
}
