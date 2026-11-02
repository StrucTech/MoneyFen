package com.moneyfen.app.data

import android.content.Context
import androidx.room.withTransaction
import com.moneyfen.app.Notifier
import com.moneyfen.core.BalTx
import com.moneyfen.core.BalanceCalc
import com.moneyfen.core.CibParser
import com.moneyfen.core.CustomParser
import com.moneyfen.core.CustomPattern
import com.moneyfen.core.InfoKind
import com.moneyfen.core.ParseResult
import com.moneyfen.core.ParsedInfo
import com.moneyfen.core.ParsedSms
import com.moneyfen.core.RuleMatcher
import com.moneyfen.core.TxKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.Calendar
import kotlin.math.abs

data class AccountView(
    val account: AccountEntity,
    /** Balance in the account's own currency (liabilities negative). */
    val balance: Double,
    /** Balance converted to EGP. */
    val balanceEgp: Double,
)

data class Snapshot(
    val accounts: List<AccountView> = emptyList(),
    val txs: List<TxEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val patterns: List<SmsPatternEntity> = emptyList(),
    val statements: List<StatementEntity> = emptyList(),
    val usdRate: Double = 50.0,
    val loaded: Boolean = false,
) {
    val categoryById: Map<Long, CategoryEntity> = categories.associateBy { it.id }
    val accountById: Map<Long, AccountView> = accounts.associateBy { it.account.id }
    private val visible get() = accounts.filter { !it.account.archived && it.account.includeInNetWorth }
    val assets: Double get() = visible.filter { it.balanceEgp > 0 }.sumOf { it.balanceEgp }
    val liabilities: Double get() = visible.filter { it.balanceEgp < 0 }.sumOf { -it.balanceEgp }
    val netWorth: Double get() = assets - liabilities

    fun toEgp(amount: Double, currency: String): Double = if (currency == "USD") amount * usdRate else amount

    /** Signed EGP amount of a transaction (+ in / - out). */
    fun signedEgp(t: TxEntity): Double = toEgp(t.amount, t.currency) * kindOf(t).direction

    /** Signed EGP amount that counts in statistics (only my personal part of a split charge). */
    fun statsEgp(t: TxEntity): Double = toEgp(t.personalAmount ?: t.amount, t.currency) * kindOf(t).direction

    val toReview: List<TxEntity> get() = txs.filter { it.needsReview }

    /** Money reserved for goals inside an account. */
    fun reserved(accountId: Long): Double = goals.filter { it.accountId == accountId && !it.wholeAccount }.sumOf { it.saved }

    /** Balance minus what is reserved for goals. */
    fun free(av: AccountView): Double = av.balance - reserved(av.account.id)

    fun goalCurrent(g: GoalEntity): Double =
        if (g.wholeAccount && g.accountId != null) accountById[g.accountId]?.balanceEgp ?: 0.0 else g.saved

    fun categoryOf(t: TxEntity): CategoryEntity? = t.categoryId?.let { categoryById[it] }

    /** Income / expense of a period, ignoring excluded and between-accounts transactions. */
    fun totals(from: Long, to: Long): Totals {
        var income = 0.0; var expense = 0.0
        val byCat = mutableMapOf<Long?, Double>()
        for (t in txs) {
            if (t.time < from || t.time >= to || t.excluded) continue
            val k = kindOf(t); if (k == TxKind.ADJUSTMENT) continue
            val c = categoryOf(t)
            val type = c?.categoryType ?: if (k.direction > 0) CategoryType.INCOME else CategoryType.EXPENSE
            val s = statsEgp(t)
            when (type) {
                CategoryType.TRANSFER -> {}
                CategoryType.INCOME -> income += s
                CategoryType.EXPENSE -> { expense -= s; byCat[c?.id] = (byCat[c?.id] ?: 0.0) - s }
            }
        }
        return Totals(income, expense, byCat)
    }
}

data class Totals(val income: Double, val expense: Double, val expenseByCategory: Map<Long?, Double>) {
    val saved get() = income - expense
}

fun kindOf(t: TxEntity): TxKind = runCatching { TxKind.valueOf(t.kind) }.getOrDefault(TxKind.MANUAL_OUT)

fun monthRange(offset: Int = 0, ref: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val c = Calendar.getInstance().apply {
        timeInMillis = ref
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, offset)
    }
    val from = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return from to c.timeInMillis
}

sealed class IngestResult {
    data class Added(val tx: TxEntity) : IngestResult()
    data object Info : IngestResult()
    data object Duplicate : IngestResult()
    data object Ignored : IngestResult()
}

const val REIMBURSED = "reimbursed"

class Repository(private val ctx: Context, val db: AppDb, val prefs: Prefs) {
    private val mutex = Mutex()
    private val day = 86_400_000L

    val snapshot: Flow<Snapshot> = combine(
        combine(db.accounts().observe(), db.txs().observe(), db.categories().observe(), prefs.version) { a, t, c, _ -> Triple(a, t, c) },
        combine(db.rules().observe(), db.goals().observe(), db.patterns().observe(), db.statements().observe()) { r, g, p, s ->
            listOf(r, g, p, s)
        },
    ) { (accounts, txs, cats), rest ->
        @Suppress("UNCHECKED_CAST")
        val base = Snapshot(
            txs = txs, categories = cats,
            rules = rest[0] as List<RuleEntity>, goals = rest[1] as List<GoalEntity>,
            patterns = rest[2] as List<SmsPatternEntity>, statements = rest[3] as List<StatementEntity>,
            usdRate = prefs.usdRate, loaded = true,
        )
        val byAccount = txs.groupBy { it.accountId }
        base.copy(accounts = accounts.map { a ->
            val bal = balanceOf(a, byAccount[a.id].orEmpty(), prefs.usdRate)
            AccountView(a, bal, if (a.currency == "USD") bal * prefs.usdRate else bal)
        })
    }

    companion object {
        fun balanceOf(a: AccountEntity, txs: List<TxEntity>, usdRate: Double): Double {
            if (a.isHolding && a.quantity != null && a.unitPrice != null) return a.quantity * a.unitPrice
            val bal = txs.map { t ->
                val amt = when {
                    t.currency == a.currency -> t.amount
                    t.currency == "USD" -> t.amount * usdRate
                    a.currency == "USD" -> t.amount / usdRate
                    else -> t.amount
                }
                BalTx(t.time, amt * kindOf(t).direction, t.balanceAfter, t.availableAfter, t.id)
            }
            return BalanceCalc.compute(bal, a.accountType == AccountType.CREDIT_CARD, a.creditLimit, a.manualBalance, a.manualAt)
        }

        fun hash(sender: String, body: String): String {
            val norm = sender.trim().lowercase() + "|" + body.replace(Regex("\\s+"), " ").trim()
            return MessageDigest.getInstance("SHA-1").digest(norm.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun ensureSeeded() {
        if (db.categories().all().isNotEmpty()) return
        db.categories().insertAll(Seed.categories)
        val cats = db.categories().all().associateBy { it.key }
        val n = Seed.rules.size
        db.rules().insertAll(Seed.rules.mapIndexed { i, (p, key) -> RuleEntity(pattern = p, categoryId = cats[key]?.id, priority = n - i) })
    }

    // ---------------------------------------------------------------- SMS ingestion

    fun isWatchedSender(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        return prefs.senderList().any { address.equals(it, true) || address.contains(it, true) } ||
            prefs.senderList().isEmpty()
    }

    suspend fun ingestSms(sender: String, body: String, time: Long, live: Boolean): IngestResult {
        val (res, tx) = mutex.withLock { ingestLocked(sender, body, time, live) }
        if (live && tx != null) Notifier.transaction(ctx, this, tx)
        return res
    }

    private suspend fun ingestLocked(sender: String, body: String, time: Long, live: Boolean): Pair<IngestResult, TxEntity?> {
        val h = hash(sender, body)
        if (db.txs().countHashNear(h, time - 15 * 60_000, time + 15 * 60_000) > 0) return IngestResult.Duplicate to null

        val patterns = db.patterns().all()
        var patternAccount: Long? = null
        val custom = patterns.firstNotNullOfOrNull { p ->
            val kind = runCatching { TxKind.valueOf(p.kind) }.getOrNull() ?: return@firstNotNullOfOrNull null
            CustomParser.parse(sender, body, listOf(CustomPattern(p.senderContains, p.regex, kind, p.currency)))
                ?.also { patternAccount = p.accountId }
        }
        val result: ParseResult = if (custom != null) ParseResult.Tx(custom) else CibParser.parse(body)
        return when (result) {
            is ParseResult.Info -> { handleInfo(sender, result.info, time); IngestResult.Info to null }
            is ParseResult.Tx -> {
                val tx = insertParsed(sender, result.tx, time, body, h, patternAccount, live)
                IngestResult.Added(tx) to tx
            }
            else -> IngestResult.Ignored to null
        }
    }

    private fun bankName(sender: String) = sender.trim().uppercase()

    private suspend fun handleInfo(sender: String, info: ParsedInfo, time: Long) {
        val bank = bankName(sender)
        val cards = db.accounts().all().filter { it.accountType == AccountType.CREDIT_CARD && (it.bank == bank || it.bank == null) }
        when (info.kind) {
            InfoKind.CREDIT_LIMIT -> {
                if (cards.isEmpty()) {
                    db.accounts().upsert(AccountEntity(name = "$bank Credit", type = AccountType.CREDIT_CARD.name, bank = bank, creditLimit = info.amount, sortOrder = 2))
                } else cards.forEach { db.accounts().upsert(it.copy(creditLimit = info.amount)) }
            }
            InfoKind.CC_STATEMENT -> {
                val card = cards.firstOrNull { info.accountHint != null && info.accountHint in it.hints() } ?: cards.firstOrNull() ?: return
                if (db.statements().count(card.id, info.amount, info.dueDate) == 0) {
                    db.statements().insert(StatementEntity(accountId = card.id, time = time, amount = info.amount, minDue = info.minDue, dueDate = info.dueDate))
                }
            }
        }
    }

    private suspend fun resolveAccount(sender: String, p: ParsedSms, forced: Long?): AccountEntity {
        forced?.let { id -> db.accounts().get(id)?.let { return it } }
        val all = db.accounts().all()
        val hint = p.accountHint
        if (hint != null) all.firstOrNull { hint in it.hints() }?.let { return it }
        val bank = bankName(sender)
        if (p.kind.isCreditCardKind) {
            val reuse = all.firstOrNull { it.accountType == AccountType.CREDIT_CARD && it.bank == bank && it.hints().isEmpty() }
            if (reuse != null) {
                val upd = if (hint != null) reuse.copy(smsHints = hint, name = "$bank Credit •$hint") else reuse
                db.accounts().upsert(upd); return upd
            }
            val a = AccountEntity(name = "$bank Credit" + (hint?.let { " •$it" } ?: ""), type = AccountType.CREDIT_CARD.name, bank = bank, smsHints = hint ?: "", sortOrder = 2)
            return a.copy(id = db.accounts().upsert(a))
        }
        val bankAcc = all.firstOrNull { it.accountType == AccountType.BANK && it.bank == bank }
        if (bankAcc != null) {
            if (hint == null) return bankAcc
            val upd = bankAcc.copy(smsHints = (bankAcc.hints() + hint).joinToString(","))
            db.accounts().upsert(upd); return upd
        }
        val a = AccountEntity(name = bank + (hint?.let { " •$it" } ?: ""), type = AccountType.BANK.name, bank = bank, smsHints = hint ?: "", sortOrder = 1)
        return a.copy(id = db.accounts().upsert(a))
    }

    private fun defaultCategoryKey(k: TxKind): String = when (k) {
        TxKind.PURCHASE, TxKind.CC_CHARGE, TxKind.MANUAL_OUT -> "other"
        TxKind.ATM_WITHDRAWAL -> "cash"
        TxKind.TRANSFER_OUT -> "sent"
        TxKind.TRANSFER_IN -> "received"
        TxKind.SALARY -> "salary"
        TxKind.DEPOSIT -> "deposit"
        TxKind.REFUND, TxKind.CC_REFUND, TxKind.CASHBACK -> "refund"
        TxKind.CC_PAYMENT, TxKind.ADJUSTMENT -> "internal"
        TxKind.MANUAL_IN -> "other_income"
    }

    /** Applies rules; returns updated tx (category / party / excluded). */
    private fun categorize(t: TxEntity, rules: List<RuleEntity>, cats: List<CategoryEntity>): TxEntity {
        if (t.categoryLocked) return t
        val k = kindOf(t)
        val ruleable = k !in setOf(TxKind.SALARY, TxKind.CC_PAYMENT, TxKind.CASHBACK, TxKind.ADJUSTMENT)
        val rule = if (ruleable) rules.firstOrNull { RuleMatcher.matches(it.pattern, t.party) } else null
        val catId = rule?.categoryId ?: cats.firstOrNull { it.key == defaultCategoryKey(k) }?.id
        return t.copy(
            categoryId = catId,
            party = rule?.renameTo?.takeIf { it.isNotBlank() } ?: t.party,
            excluded = t.excluded || (rule?.exclude == true),
        )
    }

    private fun shouldReview(k: TxKind): Boolean =
        (k == TxKind.CC_CHARGE && prefs.reviewCredit) || (k == TxKind.PURCHASE && prefs.reviewDebit)

    private suspend fun insertParsed(sender: String, p: ParsedSms, time: Long, body: String, h: String, forced: Long?, live: Boolean): TxEntity {
        val acc = resolveAccount(sender, p, forced)
        val rules = db.rules().all(); val cats = db.categories().all()
        var t = TxEntity(
            accountId = acc.id, time = time, amount = p.amount, currency = p.currency, kind = p.kind.name,
            party = p.party, reference = p.reference, balanceAfter = p.balanceAfter, availableAfter = p.availableAfter,
            smsHash = h, smsBody = body, needsReview = live && shouldReview(p.kind),
        )
        t = categorize(t, rules, cats)
        t = t.copy(id = db.txs().upsert(t))
        t = matchRefund(t)
        t = matchCardPayment(t, cats)
        t = atmToCash(t, cats)
        return t
    }

    private fun internalId(cats: List<CategoryEntity>) = cats.firstOrNull { it.key == "internal" }?.id
        ?: cats.firstOrNull { it.categoryType == CategoryType.TRANSFER }?.id

    /** A refund cancels an earlier identical charge: both are hidden from stats. */
    private suspend fun matchRefund(t: TxEntity): TxEntity {
        val k = kindOf(t)
        if (k != TxKind.REFUND && k != TxKind.CC_REFUND) return t
        val cand = db.txs().forAccountBetween(t.accountId, t.time - 7 * day, t.time)
            .filter { it.id != t.id && kindOf(it).direction < 0 && !it.excluded && it.linkedTxId == null && it.currency == t.currency && abs(it.amount - t.amount) < 0.01 }
            .lastOrNull() ?: return t
        db.txs().upsert(cand.copy(excluded = true, linkedTxId = t.id))
        val upd = t.copy(excluded = true, linkedTxId = cand.id, categoryId = cand.categoryId ?: t.categoryId)
        db.txs().upsert(upd)
        return upd
    }

    /** Bank debit that pays a credit card = transfer between my accounts (not spending). */
    private suspend fun matchCardPayment(t: TxEntity, cats: List<CategoryEntity>): TxEntity {
        val internal = internalId(cats) ?: return t
        val k = kindOf(t)
        val accounts = db.accounts().all()
        val cards = accounts.filter { it.accountType == AccountType.CREDIT_CARD }.map { it.id }.toSet()
        if (cards.isEmpty()) return t
        val near = db.txs().between(t.time - 7 * day, t.time + 7 * day)
        if (k == TxKind.TRANSFER_OUT && t.accountId !in cards && !t.categoryLocked) {
            val payment = near.firstOrNull { kindOf(it) == TxKind.CC_PAYMENT && it.accountId in cards && abs(it.amount - t.amount) < 0.01 && it.linkedTxId == null }
            if (payment != null) {
                db.txs().upsert(payment.copy(linkedTxId = t.id))
                return t.copy(categoryId = internal, linkedTxId = payment.id).also { db.txs().upsert(it) }
            }
            val stmt = db.statements().all().firstOrNull { abs(it.amount - t.amount) < 0.01 && t.time - it.time in 0..(45 * day) }
            if (stmt != null) {
                val synthetic = TxEntity(
                    accountId = stmt.accountId, time = t.time, amount = t.amount, kind = TxKind.CC_PAYMENT.name,
                    categoryId = internal, party = "Card payment", note = "auto", linkedTxId = t.id,
                )
                val sid = db.txs().upsert(synthetic)
                return t.copy(categoryId = internal, linkedTxId = sid).also { db.txs().upsert(it) }
            }
        }
        if (k == TxKind.CC_PAYMENT) {
            near.filter { kindOf(it) == TxKind.CC_PAYMENT && it.smsHash == null && it.id != t.id && it.accountId == t.accountId && abs(it.amount - t.amount) < 0.01 }
                .forEach { db.txs().delete(it) }
            val debit = near.firstOrNull { kindOf(it) == TxKind.TRANSFER_OUT && it.accountId !in cards && abs(it.amount - t.amount) < 0.01 && !it.categoryLocked }
            if (debit != null) {
                db.txs().upsert(debit.copy(categoryId = internal, linkedTxId = t.id))
                return t.copy(linkedTxId = debit.id).also { db.txs().upsert(it) }
            }
        }
        return t
    }

    private suspend fun atmToCash(t: TxEntity, cats: List<CategoryEntity>): TxEntity {
        if (!prefs.atmToCash || kindOf(t) != TxKind.ATM_WITHDRAWAL) return t
        val internal = internalId(cats) ?: return t
        val cash = db.accounts().all().firstOrNull { it.accountType == AccountType.CASH && !it.archived }
            ?: AccountEntity(name = "Cash", type = AccountType.CASH.name, sortOrder = 3).let { it.copy(id = db.accounts().upsert(it)) }
        val inId = db.txs().upsert(TxEntity(accountId = cash.id, time = t.time, amount = t.amount, currency = t.currency, kind = TxKind.MANUAL_IN.name, categoryId = internal, party = t.party, note = "ATM", linkedTxId = t.id))
        return t.copy(categoryId = internal, linkedTxId = inId).also { db.txs().upsert(it) }
    }

    // ---------------------------------------------------------------- Editing

    suspend fun saveAccount(a: AccountEntity): Long = db.accounts().upsert(a)

    suspend fun deleteAccount(a: AccountEntity, moveTo: Long?) {
        db.withTransaction {
            if (moveTo != null) db.txs().moveAccount(a.id, moveTo) else db.txs().deleteForAccount(a.id)
            db.accounts().delete(a)
        }
    }

    /** User corrects the balance: becomes the new anchor from now on. */
    suspend fun setBalance(a: AccountEntity, value: Double) {
        db.accounts().upsert(a.copy(manualBalance = value, manualAt = System.currentTimeMillis()))
    }

    suspend fun saveTx(t: TxEntity): Long = db.txs().upsert(t)
    suspend fun deleteTx(t: TxEntity) = db.txs().delete(t)

    enum class Dest { CASH, LENT, NONE }

    /**
     * Splits a charge: [personal] counts as my spending, the rest is not spending.
     * The full amount still affects the card/account balance. The rest can be added to Cash
     * (I got it back in cash) or to "money people owe me".
     */
    suspend fun resolveReview(txId: Long, personal: Double, dest: Dest) { mutex.withLock { resolveLocked(txId, personal, dest) } }

    private suspend fun resolveLocked(txId: Long, personal: Double, dest: Dest) {
        val t = db.txs().get(txId) ?: return
        val p = personal.coerceIn(0.0, t.amount)
        db.txs().byLinked(t.id).filter { it.note == REIMBURSED }.forEach { db.txs().delete(it) }
        db.txs().upsert(t.copy(personalAmount = if (abs(p - t.amount) < 0.005) null else p, needsReview = false))
        val rest = t.amount - p
        if (rest < 0.005 || dest == Dest.NONE) return
        prefs.reviewDest = dest.name
        val cats = db.categories().all()
        val type = if (dest == Dest.CASH) AccountType.CASH else AccountType.LENT
        val acc = db.accounts().all().firstOrNull { it.accountType == type && !it.archived }
            ?: AccountEntity(
                name = if (dest == Dest.CASH) "Cash" else "Owed to me",
                type = type.name, sortOrder = 3,
            ).let { it.copy(id = db.accounts().upsert(it)) }
        val amountInAcc = if (t.currency == acc.currency) rest else if (t.currency == "USD") rest * prefs.usdRate else rest
        db.txs().upsert(TxEntity(
            accountId = acc.id, time = t.time, amount = amountInAcc, currency = acc.currency,
            kind = TxKind.MANUAL_IN.name, categoryId = internalId(cats), party = t.party, note = REIMBURSED, linkedTxId = t.id,
        ))
    }

    /** Buying more gold / shares: adds quantity and cost. */
    suspend fun addToHolding(a: AccountEntity, qty: Double, cost: Double) {
        db.accounts().upsert(a.copy(quantity = (a.quantity ?: 0.0) + qty, costBasis = (a.costBasis ?: 0.0) + cost))
    }

    suspend fun saveCategory(c: CategoryEntity) = db.categories().upsert(c)
    suspend fun deleteCategory(c: CategoryEntity) { db.txs().clearCategory(c.id); db.categories().delete(c) }
    suspend fun saveRule(r: RuleEntity) = db.rules().upsert(r)
    suspend fun deleteRule(r: RuleEntity) = db.rules().delete(r)
    suspend fun saveGoal(g: GoalEntity) = db.goals().upsert(g)
    suspend fun deleteGoal(g: GoalEntity) = db.goals().delete(g)
    suspend fun savePattern(p: SmsPatternEntity) = db.patterns().upsert(p)
    suspend fun deletePattern(p: SmsPatternEntity) = db.patterns().delete(p)

    /** Re-run rules on every transaction whose category wasn't picked by hand. Returns changed count. */
    suspend fun reapplyRules(): Int = mutex.withLock { reapplyLocked() }

    private suspend fun reapplyLocked(): Int {
        val rules = db.rules().all(); val cats = db.categories().all()
        val internal = internalId(cats)
        var n = 0
        db.withTransaction {
            for (t in db.txs().all()) {
                if (t.categoryLocked || t.smsHash == null) continue
                if (t.linkedTxId != null && t.categoryId == internal) continue
                val u = categorize(t.copy(excluded = t.excluded && t.linkedTxId != null), rules, cats)
                if (u != t) { db.txs().upsert(u); n++ }
            }
        }
        return n
    }

    suspend fun clearAll() = db.withTransaction {
        db.txs().clear(); db.accounts().clear(); db.statements().clear()
    }
}
