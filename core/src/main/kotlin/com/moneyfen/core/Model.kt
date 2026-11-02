package com.moneyfen.core

/** What kind of money movement an SMS describes. */
enum class TxKind(val direction: Int) {
    PURCHASE(-1),          // debit card / instant purchase from bank account
    ATM_WITHDRAWAL(-1),
    TRANSFER_OUT(-1),      // InstaPay / external transfer / account debit
    TRANSFER_IN(1),        // InstaPay received
    SALARY(1),
    DEPOSIT(1),            // cash deposit
    REFUND(1),             // reversal on debit card / bank account
    CC_CHARGE(-1),         // credit card purchase
    CC_PAYMENT(1),         // payment towards the credit card
    CC_REFUND(1),          // refund on credit card
    CASHBACK(1),           // cashback on credit card
    MANUAL_IN(1),
    MANUAL_OUT(-1),
    ADJUSTMENT(0);

    val isCreditCardKind: Boolean
        get() = this == CC_CHARGE || this == CC_PAYMENT || this == CC_REFUND || this == CASHBACK
}

/** Informational (non-transaction) facts that an SMS can carry. */
enum class InfoKind { CC_STATEMENT, CREDIT_LIMIT }

data class ParsedSms(
    val kind: TxKind,
    val amount: Double,
    val currency: String = "EGP",
    /** Last digits of the account or card mentioned in the SMS ("1234", "5678", "4321"). */
    val accountHint: String? = null,
    /** Merchant, ATM location, sender name... */
    val party: String? = null,
    val reference: String? = null,
    /** Bank-reported available balance after this transaction (debit accounts). */
    val balanceAfter: Double? = null,
    /** Bank-reported available limit after this transaction (credit cards). */
    val availableAfter: Double? = null,
)

data class ParsedInfo(
    val kind: InfoKind,
    val accountHint: String?,
    val amount: Double,
    val minDue: Double? = null,
    val dueDate: String? = null,
)

sealed class ParseResult {
    data class Tx(val tx: ParsedSms) : ParseResult()
    data class Info(val info: ParsedInfo) : ParseResult()
    /** Recognised but carries no money movement (OTP, ad, declined...). */
    data object Ignored : ParseResult()
    data object Unknown : ParseResult()
}

internal fun String.toAmount(): Double? = replace(",", "").replace("٫", ".").trim().trimEnd('.').toDoubleOrNull()

internal fun String.normalizeSpaces(): String = replace(Regex("[\\s\\u00A0\\u200F\\u200E]+"), " ").trim()
