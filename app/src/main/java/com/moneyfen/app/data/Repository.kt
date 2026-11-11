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
    val moves: List<GoalMoveEntity> = emptyList(),
    val allocs: List<GoalAllocEntity> = emptyList(),
    val safeExclude: Set<Long> = emptySet(),
    /** Count "money I lent" (receivables) in safe to spend. */
    val includeLent: Boolean = false,
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
    fun reserved(accountId: Long): Double = goals.filter { !it.wholeAccount && it.accountId != null }.sumOf { g ->
        goalSplit(g).filter { it.first == accountId }.sumOf { it.second }
    }

    /** Where a goal's saved money is: (accountId, amount). The main account holds what isn't allocated elsewhere. */
    fun goalSplit(g: GoalEntity): List<Pair<Long, Double>> {
        val main = g.accountId ?: return emptyList()
        if (g.wholeAccount) return emptyList()
        val others = allocs.filter { it.goalId == g.id && it.accountId != main && it.amount > 0.004 }
        val mainPart = g.saved - others.sumOf { it.amount }
        return listOf(main to mainPart) + others.map { it.accountId to it.amount }
    }

    /** Balance minus what is reserved for goals. */
    fun free(av: AccountView): Double = av.balance - reserved(av.account.id)

    /** Accounts that can count in "safe to spend". */
    fun safeCandidates(): List<AccountView> = accounts.filter {
        !it.account.archived && it.account.accountType in setOf(AccountType.BANK, AccountType.CASH, AccountType.WALLET, AccountType.CREDIT_CARD)
    }

    data class Safe(val cash: Double, val reserved: Double, val cardDebt: Double, val owed: Double = 0.0) {
        val free get() = cash + owed - reserved
        val safe get() = free - cardDebt
    }

    /** Money I can spend without ending up in debt: free balances (after goals) minus credit card debt. */
    fun safeToSpend(): Safe {
        var cash = 0.0; var reserved = 0.0; var debt = 0.0; var owed = 0.0
        if (includeLent) for (av in owedToMe) {
            if (av.account.id in safeExclude || !av.account.includeInNetWorth) continue
            owed += maxOf(0.0, av.balanceEgp)
        }
        for (av in safeCandidates()) {
            if (av.account.id in safeExclude) continue
            if (av.account.accountType == AccountType.CREDIT_CARD) debt += maxOf(0.0, -av.balanceEgp)
            else {
                cash += av.balanceEgp
                val r = reserved(av.account.id)
                reserved += if (av.account.currency == "USD") r * usdRate else r
            }
        }
        return Safe(cash, reserved, debt, owed)
    }

    data class Daily(val allowance: Double, val spentToday: Double, val daysLeft: Int, val periodEnd: Long, val safeNow: Double = 0.0) {
        val leftToday get() = allowance - spentToday
        /** What's left now spread over the days after today (today's spending already taken out). */
        val fromTomorrow get() = if (daysLeft > 1) safeNow / (daysLeft - 1) else safeNow
    }

    /** Daily allowance: what is safe to spend (as of this morning) spread over the days left until the month ends. */
    fun daily(now: Long = System.currentTimeMillis()): Daily {
        val (_, end) = monthRange(0, now)
        val c = java.util.Calendar.getInstance().apply {
            timeInMillis = now; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val today = c.timeInMillis
        val daysLeft = maxOf(1, kotlin.math.ceil((end - today) / 86_400_000.0).toInt())
        val spent = maxOf(0.0, totals(today, today + 86_400_000L).expense)
        val safeNow = safeToSpend().safe
        val allowance = (safeNow + spent) / daysLeft
        return Daily(allowance, spent, daysLeft, end, safeNow)
    }

    /** Receivables: what people / clients owe me. */
    val owedToMe: List<AccountView> get() = accounts.filter { it.account.accountType == AccountType.LENT && !it.account.archived }

    /** Total put into goals in a period (minus taken out). */
    fun savedInto(from: Long, to: Long): Double = moves.filter { it.time in from until to }.sumOf { it.amount }

    val incomeToReview: List<TxEntity> get() = toReview.filter { kindOf(it).direction > 0 }

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

/** Current budget-month settings, refreshed with every snapshot. */
object Cycle {
    @Volatile var mode: String = "SALARY"
    @Volatile var day: Int = 25
    @Volatile var starts: List<Long> = emptyList()
}

/** Budget "month" [offset] months from the current one (salary day / fixed day / calendar). */
fun monthRange(offset: Int = 0, ref: Long = System.currentTimeMillis()): Pair<Long, Long> =
    com.moneyfen.core.Cycles.range(Cycle.mode, Cycle.day, Cycle.starts, offset, ref)

fun calendarMonth(offset: Int = 0, ref: Long = System.currentTimeMillis()): Pair<Long, Long> {
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

private data class Base4(val a: List<AccountEntity>, val t: List<TxEntity>, val c: List<CategoryEntity>, val al: List<GoalAllocEntity>)
const val FEE = "fee"

class Repository(val ctx: Context, val db: AppDb, val prefs: Prefs) {
    private val mutex = Mutex()
    private val day = 86_400_000L

    val snapshot: Flow<Snapshot> = combine(
        combine(db.accounts().observe(), db.txs().observe(), db.categories().observe(), prefs.version, db.goalAllocs().observe()) { a, t, c, _, al ->
            Base4(a, t, c, al)
        },
        combine(db.rules().observe(), db.goals().observe(), db.patterns().observe(), db.statements().observe(), db.goalMoves().observe()) { r, g, p, s, m ->
            listOf(r, g, p, s, m)
        },
    ) { (accounts, txs, cats, allocs), rest ->
        @Suppress("UNCHECKED_CAST")
        val base = Snapshot(
            txs = txs, categories = cats,
            rules = rest[0] as List<RuleEntity>, goals = rest[1] as List<GoalEntity>,
            patterns = rest[2] as List<SmsPatternEntity>, statements = rest[3] as List<StatementEntity>,
            moves = rest[4] as List<GoalMoveEntity>, allocs = allocs, safeExclude = prefs.safeExclude, includeLent = prefs.includeLent,
            usdRate = prefs.usdRate, loaded = true,
        )
        val salaryCat = cats.firstOrNull { it.key == "salary" }?.id
        Cycle.mode = prefs.cycleMode
        Cycle.day = prefs.cycleDay
        Cycle.starts = com.moneyfen.core.Cycles.salaryStarts(
            txs.filter { it.kind == TxKind.SALARY.name || (salaryCat != null && it.categoryId == salaryCat) }.map { it.time })
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
        if (db.categories().all().isNotEmpty()) { upgradeSeed(); return }
        prefs.seedVersion = 3
        db.categories().insertAll(Seed.categories)
        val cats = db.categories().all().associateBy { it.key }
        val n = Seed.rules.size
        db.rules().insertAll(Seed.rules.mapIndexed { i, (p, key) -> RuleEntity(pattern = p, categoryId = cats[key]?.id, priority = n - i) })
    }

    /** v2: only chosen sources are income. Received transfers & deposits aren't income, refunds reduce spending. */
    private suspend fun upgradeSeed() {
        if (prefs.seedVersion < 2) upgradeSeedV2()
        if (prefs.seedVersion < 3) {
            if (db.categories().all().none { it.key == "fees" }) db.categories().upsert(Seed.categories.first { it.key == "fees" })
            prefs.seedVersion = 3
            mutex.withLock { addFeesLocked(prefs.ipnFeeSince) }
        }
    }

    private suspend fun upgradeSeedV2() {
        val cats = db.categories().all()
        fun upd(key: String, f: (CategoryEntity) -> CategoryEntity) = cats.firstOrNull { it.key == key }?.let(f)
        listOfNotNull(
            upd("received") { it.copy(type = CategoryType.TRANSFER.name, nameEn = "Received (not income)", nameAr = "تحويلات واردة (مش دخل)", sortOrder = 31) },
            upd("deposit") { it.copy(type = CategoryType.TRANSFER.name, sortOrder = 32) },
            upd("refund") { it.copy(type = CategoryType.EXPENSE.name, sortOrder = 13) },
        ).forEach { db.categories().upsert(it) }
        if (cats.none { it.key == "freelance" }) db.categories().upsert(Seed.categories.first { it.key == "freelance" })
        prefs.seedVersion = 2
    }

    /** People/companies that sent me money, with the rule that makes them income (if any). */
    data class Source(val party: String, val count: Int, val total: Double, val last: Long, val rule: RuleEntity?)

    fun incomeSources(snap: Snapshot): List<Source> {
        val kinds = setOf(TxKind.TRANSFER_IN, TxKind.DEPOSIT, TxKind.MANUAL_IN)
        return snap.txs.filter { kindOf(it) in kinds && !it.party.isNullOrBlank() && it.note != REIMBURSED }
            .groupBy { it.party!!.trim() }
            .map { (p, l) ->
                Source(p, l.size, l.sumOf { snap.toEgp(it.amount, it.currency) }, l.maxOf { it.time },
                    snap.rules.firstOrNull { it.pattern.equals(p, true) })
            }.sortedByDescending { it.total }
    }

    /** Make a sender an income source (category) and/or ask-to-save; removes the rule when neither. */
    suspend fun setSource(party: String, incomeCategory: Long?, savePercent: Double?) {
        val existing = db.rules().all().filter { it.pattern.equals(party, true) }
        val keep = existing.firstOrNull()
        existing.drop(1).forEach { db.rules().delete(it) }
        if (incomeCategory == null && savePercent == null && (keep?.renameTo.isNullOrBlank()) && keep?.exclude != true) {
            keep?.let { db.rules().delete(it) }
        } else {
            val maxP = (db.rules().all().maxOfOrNull { it.priority } ?: 0) + 1
            db.rules().upsert((keep ?: RuleEntity(pattern = party, priority = maxP)).copy(categoryId = incomeCategory, savePercent = savePercent))
        }
        reapplyRules()
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
        val rule = if (ruleable) rules.firstOrNull {
            (it.categoryId != null || !it.renameTo.isNullOrBlank() || it.exclude) && RuleMatcher.matches(it.pattern, t.party)
        } else null
        val catId = rule?.categoryId ?: cats.firstOrNull { it.key == defaultCategoryKey(k) }?.id
        return t.copy(
            categoryId = catId,
            party = rule?.renameTo?.takeIf { it.isNotBlank() } ?: t.party,
            excluded = t.excluded || (rule?.exclude == true),
        )
    }

    private fun saveRuleFor(k: TxKind, party: String?, rules: List<RuleEntity>): RuleEntity? =
        if (k.direction <= 0) null else rules.firstOrNull { it.savePercent != null && RuleMatcher.matches(it.pattern, party) }

    /** Suggested amount to save from an income (rule %, or salary % / fixed amount). */
    suspend fun suggestedSave(t: TxEntity): Double {
        val amt = if (t.currency == "USD") t.amount * prefs.usdRate else t.amount
        saveRuleFor(kindOf(t), t.party, db.rules().all())?.savePercent?.let { return amt * it / 100 }
        return if (prefs.salaryMode == "FIXED" && prefs.salaryFixed > 0) minOf(prefs.salaryFixed, amt)
        else amt * prefs.savingsPercent / 100
    }

    private suspend fun savingsPot(accountId: Long): GoalEntity {
        db.goals().all().firstOrNull { it.target == 0.0 && it.accountId == accountId && !it.wholeAccount }?.let { return it }
        val g = GoalEntity(name = "Savings · التحويش", emoji = "🐷", target = 0.0, deadline = 0, accountId = accountId)
        return g.copy(id = db.goals().upsert(g))
    }

    /** Put [amount] of an income into a goal (or the savings pot of that account). Re-deciding replaces the old choice. */
    suspend fun saveFromIncome(txId: Long, amount: Double, goalId: Long?) = mutex.withLock {
        val t = db.txs().get(txId) ?: return@withLock
        for (m in db.goalMoves().forTx(txId)) {
            db.goals().all().firstOrNull { it.id == m.goalId }?.let { g -> db.goals().upsert(g.copy(saved = maxOf(0.0, g.saved - m.amount))) }
            db.goalMoves().delete(m)
        }
        if (amount > 0.004) {
            val g = goalId?.let { id -> db.goals().all().firstOrNull { it.id == id } } ?: savingsPot(t.accountId)
            db.goals().upsert(g.copy(saved = g.saved + amount))
            db.goalMoves().insert(GoalMoveEntity(goalId = g.id, time = t.time, amount = amount, txId = t.id, note = t.party))
            if (g.accountId != null && g.accountId != t.accountId && !g.wholeAccount) {
                val cur = db.goalAllocs().forGoal(g.id).firstOrNull { it.accountId == t.accountId }?.amount ?: 0.0
                db.goalAllocs().upsert(GoalAllocEntity(g.id, t.accountId, cur + amount))
            }
            prefs.lastSaveGoal = g.id
        }
        db.goals().all().map { it.id }.forEach { normalizeAllocs(it) }
        db.txs().upsert(t.copy(needsReview = false))
        Unit
    }

    /** Manual + / - on a goal, logged in history. [accountId] = which account the money is added to / taken from. */
    suspend fun moveGoal(g: GoalEntity, delta: Double, note: String? = null, accountId: Long? = null) {
        val newSaved = maxOf(0.0, g.saved + delta)
        db.goals().upsert(g.copy(saved = newSaved))
        db.goalMoves().insert(GoalMoveEntity(goalId = g.id, amount = newSaved - g.saved, note = note))
        if (accountId != null && accountId != g.accountId && !g.wholeAccount) {
            val cur = db.goalAllocs().forGoal(g.id).firstOrNull { it.accountId == accountId }?.amount ?: 0.0
            db.goalAllocs().upsert(GoalAllocEntity(g.id, accountId, maxOf(0.0, cur + (newSaved - g.saved))))
        }
        normalizeAllocs(g.id)
    }

    /** Keeps allocations valid: the main account's part never goes below zero. */
    private suspend fun normalizeAllocs(goalId: Long) {
        val g = db.goals().all().firstOrNull { it.id == goalId } ?: return
        val allocs = db.goalAllocs().forGoal(goalId)
        if (g.accountId == null || g.wholeAccount) { db.goalAllocs().deleteForGoal(goalId); return }
        allocs.filter { it.accountId == g.accountId || it.amount <= 0.004 }.forEach { db.goalAllocs().delete(it) }
        var over = allocs.filter { it.accountId != g.accountId && it.amount > 0.004 }.sumOf { it.amount } - g.saved
        for (a in allocs.filter { it.accountId != g.accountId && it.amount > 0.004 }.sortedByDescending { it.amount }) {
            if (over <= 0.004) break
            val cut = minOf(a.amount, over)
            if (a.amount - cut <= 0.004) db.goalAllocs().delete(a) else db.goalAllocs().upsert(a.copy(amount = a.amount - cut))
            over -= cut
        }
    }

    /** Move part of a goal's money from one account to another (the goal's total doesn't change). Returns the amount moved. */
    suspend fun moveGoalBetween(g: GoalEntity, from: Long, to: Long, amount: Double): Double = mutex.withLock {
        if (from == to || g.accountId == null || g.wholeAccount) return@withLock 0.0
        val allocs = db.goalAllocs().forGoal(g.id).associateBy { it.accountId }
        val mainPart = g.saved - allocs.values.filter { it.accountId != g.accountId }.sumOf { it.amount }
        val available = if (from == g.accountId) mainPart else allocs[from]?.amount ?: 0.0
        val amt = minOf(amount, maxOf(0.0, available))
        if (amt <= 0.004) return@withLock 0.0
        if (from != g.accountId) db.goalAllocs().upsert(GoalAllocEntity(g.id, from, (allocs[from]?.amount ?: 0.0) - amt))
        if (to != g.accountId) db.goalAllocs().upsert(GoalAllocEntity(g.id, to, (allocs[to]?.amount ?: 0.0) + amt))
        val names = db.accounts().all().associate { it.id to it.name }
        db.goalMoves().insert(GoalMoveEntity(goalId = g.id, amount = 0.0, note = "${names[from]} → ${names[to]}: ${"%.2f".format(amt)}"))
        normalizeAllocs(g.id)
        amt
    }

    private fun shouldReview(k: TxKind): Boolean =
        (k == TxKind.CC_CHARGE && prefs.reviewCredit) || (k == TxKind.PURCHASE && prefs.reviewDebit)

    private suspend fun insertParsed(sender: String, p: ParsedSms, time: Long, body: String, h: String, forced: Long?, live: Boolean): TxEntity {
        val acc = resolveAccount(sender, p, forced)
        val rules = db.rules().all(); val cats = db.categories().all()
        var t = TxEntity(
            accountId = acc.id, time = time, amount = p.amount, currency = p.currency, kind = p.kind.name,
            party = p.party, reference = p.reference, balanceAfter = p.balanceAfter, availableAfter = p.availableAfter,
            smsHash = h, smsBody = body,
            needsReview = live && (shouldReview(p.kind) || saveRuleFor(p.kind, p.party, rules) != null || (p.kind == TxKind.SALARY && prefs.askSalary)),
        )
        t = categorize(t, rules, cats)
        t = t.copy(id = db.txs().upsert(t))
        if (prefs.ipnFee && p.kind == TxKind.TRANSFER_OUT && time >= prefs.ipnFeeSince && com.moneyfen.core.InstaPayFee.isInstaPayTransfer(body)) addFee(t, cats)
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

    // ---------------------------------------------------------------- InstaPay fees

    private suspend fun addFee(t: TxEntity, cats: List<CategoryEntity>) {
        val fee = com.moneyfen.core.InstaPayFee.calc(t.amount, prefs.ipnFeePct, prefs.ipnFeeMin, prefs.ipnFeeMax)
        if (fee <= 0) return
        db.txs().upsert(TxEntity(
            accountId = t.accountId, time = t.time + 1, amount = fee, currency = t.currency, kind = TxKind.MANUAL_OUT.name,
            categoryId = cats.firstOrNull { it.key == "fees" }?.id, party = "InstaPay fee", note = FEE, linkedTxId = t.id,
        ))
    }

    /** Adds the fee to every InstaPay transfer since [since] that doesn't have one yet. Returns how many were added. */
    private suspend fun addFeesLocked(since: Long): Int {
        if (!prefs.ipnFee) return 0
        val cats = db.categories().all()
        val all = db.txs().all()
        val hasFee = all.filter { it.note == FEE }.mapNotNull { it.linkedTxId }.toSet()
        var n = 0
        db.withTransaction {
            all.filter { it.time >= since && it.kind == TxKind.TRANSFER_OUT.name && it.id !in hasFee &&
                com.moneyfen.core.InstaPayFee.isInstaPayTransfer(it.smsBody) }
                .forEach { addFee(it, cats); n++ }
        }
        return n
    }

    /** Recalculate all automatic fees since [since] with the current settings. */
    suspend fun recalcFees(since: Long): Int = mutex.withLock {
        db.withTransaction { db.txs().all().filter { it.note == FEE && it.time >= since }.forEach { db.txs().delete(it) } }
        addFeesLocked(since)
    }

    /** Remove every automatic fee. */
    suspend fun removeFees(): Int = mutex.withLock {
        val fees = db.txs().all().filter { it.note == FEE }
        db.withTransaction { fees.forEach { db.txs().delete(it) } }
        fees.size
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
     * Splits a transaction. [personal] is the part that is really mine (my spending / my income); the rest isn't counted in stats.
     * The full amount still affects the card/account balance.
     *  - Outflow: the rest goes to Cash (got it back in cash) or to a receivable account (someone / a client owes me).
     *  - Inflow: the rest repays a receivable (reduces what someone owes me).
     */
    suspend fun resolveReview(txId: Long, personal: Double, dest: Dest, lentAccountId: Long? = null, newLentName: String? = null) {
        mutex.withLock { resolveLocked(txId, personal, dest, lentAccountId, newLentName) }
    }

    private suspend fun lentAccount(party: String?, id: Long?, newName: String?): AccountEntity {
        val all = db.accounts().all()
        id?.let { i -> all.firstOrNull { it.id == i }?.let { return it } }
        if (!newName.isNullOrBlank()) {
            val a = AccountEntity(name = newName.trim(), type = AccountType.LENT.name, sortOrder = 4)
            return a.copy(id = db.accounts().upsert(a))
        }
        val lent = all.filter { it.accountType == AccountType.LENT && !it.archived }
        val word = party?.split(' ')?.firstOrNull { it.length >= 3 }
        return lent.firstOrNull { word != null && it.name.contains(word, true) }
            ?: lent.firstOrNull()
            ?: AccountEntity(name = "Owed to me", type = AccountType.LENT.name, sortOrder = 4).let { it.copy(id = db.accounts().upsert(it)) }
    }

    private suspend fun resolveLocked(txId: Long, personal: Double, dest: Dest, lentAccountId: Long?, newLentName: String?) {
        val t = db.txs().get(txId) ?: return
        val incoming = kindOf(t).direction > 0
        val p = personal.coerceIn(0.0, t.amount)
        db.txs().byLinked(t.id).filter { it.note == REIMBURSED }.forEach { db.txs().delete(it) }
        db.txs().upsert(t.copy(personalAmount = if (abs(p - t.amount) < 0.005) null else p, needsReview = if (incoming) t.needsReview else false))
        val rest = t.amount - p
        if (rest < 0.005 || dest == Dest.NONE) return
        if (!incoming) prefs.reviewDest = dest.name
        val cats = db.categories().all()
        val acc = if (dest == Dest.LENT || incoming) lentAccount(t.party, lentAccountId, newLentName).also { prefs.lastLent = it.id }
        else db.accounts().all().firstOrNull { it.accountType == AccountType.CASH && !it.archived }
            ?: AccountEntity(name = "Cash", type = AccountType.CASH.name, sortOrder = 3).let { it.copy(id = db.accounts().upsert(it)) }
        val amountInAcc = if (t.currency == acc.currency) rest else if (t.currency == "USD") rest * prefs.usdRate else rest
        db.txs().upsert(TxEntity(
            accountId = acc.id, time = t.time, amount = amountInAcc, currency = acc.currency,
            kind = (if (incoming) TxKind.MANUAL_OUT else TxKind.MANUAL_IN).name,
            categoryId = internalId(cats), party = t.party, note = REIMBURSED, linkedTxId = t.id,
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
    suspend fun saveGoal(g: GoalEntity): Long {
        val id = db.goals().upsert(g).let { if (g.id == 0L) it else g.id }
        normalizeAllocs(id)
        return id
    }
    suspend fun deleteGoal(g: GoalEntity) { db.goalMoves().deleteForGoal(g.id); db.goalAllocs().deleteForGoal(g.id); db.goals().delete(g) }
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
