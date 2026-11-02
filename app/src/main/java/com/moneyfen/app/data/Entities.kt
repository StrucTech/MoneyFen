package com.moneyfen.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class AccountType(val liability: Boolean, val emoji: String, val en: String, val ar: String) {
    BANK(false, "🏦", "Bank account", "حساب بنكي"),
    CREDIT_CARD(true, "💳", "Credit card", "بطاقة ائتمان"),
    CASH(false, "💵", "Cash", "كاش"),
    WALLET(false, "📱", "Wallet", "محفظة"),
    GOLD(false, "🥇", "Gold", "دهب"),
    CERTIFICATE(false, "📜", "Certificate", "شهادة"),
    DEPOSIT(false, "🔒", "Deposit", "وديعة"),
    INVESTMENT(false, "📈", "Investment", "استثمار"),
    LENT(false, "🤝", "Money I lent", "فلوس ليا عند حد"),
    BORROWED(true, "🧾", "Money I owe", "فلوس عليا لحد"),
    OTHER_ASSET(false, "📦", "Other asset", "أصل آخر"),
    OTHER_LIABILITY(true, "⚠️", "Other debt", "دين آخر");
}

enum class CategoryType { EXPENSE, INCOME, TRANSFER }

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = AccountType.BANK.name,
    val currency: String = "EGP",
    /** SMS sender this account belongs to (e.g. "CIB"). */
    val bank: String? = null,
    /** Comma separated last digits that identify this account in SMS ("1234,5678"). */
    val smsHints: String = "",
    /** User-entered balance and when it was entered (anchor). Liabilities are stored negative. */
    val manualBalance: Double? = null,
    val manualAt: Long? = null,
    val creditLimit: Double = 0.0,
    /** Gold grams / units, with price per unit. */
    val quantity: Double? = null,
    val unitPrice: Double? = null,
    val annualRate: Double? = null,
    val maturity: Long? = null,
    val note: String? = null,
    val includeInNetWorth: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
) {
    val accountType: AccountType get() = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.OTHER_ASSET)
    fun hints(): List<String> = smsHints.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
}

@Serializable
@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("time"), Index("smsHash"), Index("categoryId")],
)
data class TxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val time: Long,
    /** Positive amount in [currency]. Direction comes from [kind]. */
    val amount: Double,
    val currency: String = "EGP",
    val kind: String,
    val categoryId: Long? = null,
    val party: String? = null,
    val note: String? = null,
    val reference: String? = null,
    val balanceAfter: Double? = null,
    val availableAfter: Double? = null,
    val smsHash: String? = null,
    val smsBody: String? = null,
    /** Hidden from income/expense statistics (still affects balance). */
    val excluded: Boolean = false,
    /** User picked the category by hand: rules won't override it. */
    val categoryLocked: Boolean = false,
    val linkedTxId: Long? = null,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String? = null,
    val nameEn: String,
    val nameAr: String,
    val emoji: String = "📁",
    val type: String = CategoryType.EXPENSE.name,
    val monthlyBudget: Double? = null,
    val sortOrder: Int = 0,
) {
    val categoryType: CategoryType get() = runCatching { CategoryType.valueOf(type) }.getOrDefault(CategoryType.EXPENSE)
}

@Serializable
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Text to look for (case-insensitive). Several alternatives separated by | */
    val pattern: String,
    val categoryId: Long? = null,
    val renameTo: String? = null,
    val exclude: Boolean = false,
    val priority: Int = 0,
)

@Serializable
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎯",
    val target: Double,
    val deadline: Long,
    /** Manually tracked saved amount (used when no account is linked). */
    val saved: Double = 0.0,
    val accountId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "sms_patterns")
data class SmsPatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val senderContains: String,
    val regex: String,
    val kind: String,
    val currency: String = "EGP",
    val accountId: Long? = null,
)

@Serializable
@Entity(tableName = "statements")
data class StatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val time: Long,
    val amount: Double,
    val minDue: Double?,
    val dueDate: String?,
)
