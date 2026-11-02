package com.moneyfen.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.moneyfen.app.data.CategoryEntity
import com.moneyfen.app.data.CategoryType
import com.moneyfen.app.data.RuleEntity
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.TxEntity
import com.moneyfen.app.data.kindOf
import com.moneyfen.app.data.monthRange
import com.moneyfen.core.TxKind
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun kindLabel(k: TxKind): String = when (k) {
    TxKind.PURCHASE -> tr("Purchase", "شراء")
    TxKind.ATM_WITHDRAWAL -> tr("ATM withdrawal", "سحب ATM")
    TxKind.TRANSFER_OUT -> tr("Transfer out", "تحويل صادر")
    TxKind.TRANSFER_IN -> tr("Transfer in", "تحويل وارد")
    TxKind.SALARY -> tr("Salary", "مرتب")
    TxKind.DEPOSIT -> tr("Deposit", "إيداع")
    TxKind.REFUND -> tr("Refund", "استرداد")
    TxKind.CC_CHARGE -> tr("Card purchase", "شراء بالفيزا")
    TxKind.CC_PAYMENT -> tr("Card payment", "سداد البطاقة")
    TxKind.CC_REFUND -> tr("Card refund", "استرداد على البطاقة")
    TxKind.CASHBACK -> tr("Cashback", "كاش باك")
    TxKind.MANUAL_IN -> tr("Money in", "فلوس داخلة")
    TxKind.MANUAL_OUT -> tr("Money out", "فلوس خارجة")
    TxKind.ADJUSTMENT -> tr("Adjustment", "تسوية")
}

@Composable
fun catName(c: CategoryEntity?): String = c?.let { "${it.emoji} " + tr(it.nameEn, it.nameAr) } ?: tr("Uncategorized", "بدون تصنيف")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(snap: Snapshot, accountId: Long?) {
    val nav = LocalNav.current
    var query by rememberSaveable { mutableStateOf("") }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var allTime by rememberSaveable { mutableStateOf(accountId != null) }
    var catFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val (from, to) = monthRange(monthOffset)

    val list = snap.txs.asSequence()
        .filter { accountId == null || it.accountId == accountId }
        .filter { allTime || (it.time in from until to) }
        .filter { catFilter == null || it.categoryId == catFilter }
        .filter { t ->
            query.isBlank() || listOf(t.party, t.note, t.smsBody, snap.categoryOf(t)?.nameEn, snap.categoryOf(t)?.nameAr, fmt(t.amount))
                .any { it?.contains(query, ignoreCase = true) == true }
        }.toList()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), 
            title = { Text(accountId?.let { snap.accountById[it]?.account?.name } ?: tr("Transactions", "المعاملات")) },
            navigationIcon = { if (accountId != null) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
        )
        OutlinedTextField(
            query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text(tr("Search merchant, note, amount…", "دوّر بالتاجر، الملاحظة، المبلغ…")) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(allTime, { allTime = !allTime }, label = { Text(tr("All time", "كل الوقت")) })
            if (!allTime) {
                IconButton(onClick = { monthOffset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                Text(dateFmt(from, "MMM yyyy"))
                IconButton(onClick = { if (monthOffset < 0) monthOffset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
            }
            Box(Modifier.weight(1f))
            if (catFilter != null) FilterChip(true, { catFilter = null }, label = { Text(catName(snap.categoryById[catFilter])) })
        }
        val (inc, out) = list.filter { !it.excluded }.partition { kindOf(it).direction > 0 }
        Text(
            tr("In ", "داخل ") + money(inc.sumOf { snap.toEgp(it.amount, it.currency) }) + "   " +
                tr("Out ", "خارج ") + money(out.sumOf { snap.toEgp(it.amount, it.currency) }) + "   (${list.size})",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                var lastDay = ""
                list.forEach { t ->
                    val d = dateFmt(t.time, "EEE dd MMM yyyy")
                    if (d != lastDay) {
                        lastDay = d
                        item(key = "d_${t.id}") {
                            Text(d, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }
                    item(key = t.id) { TxRow(snap, t, showAccount = accountId == null) { nav.navigate("tx/${t.id}") } }
                }
            }
            FloatingActionButton(onClick = { nav.navigate("tx/0?account=${accountId ?: 0}") }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Default.Add, null)
            }
        }
    }
}

@Composable
fun TxRow(snap: Snapshot, t: TxEntity, showAccount: Boolean, onClick: () -> Unit) {
    val k = kindOf(t)
    val c = snap.categoryOf(t)
    val signed = t.amount * k.direction
    val sub = buildList {
        add(catName(c))
        if (showAccount) snap.accountById[t.accountId]?.let { add(it.account.name) }
        add(dateFmt(t.time, "HH:mm"))
        if (t.note != null) add(t.note)
        t.personalAmount?.let { add(tr("mine ", "صرفي ") + fmt(it)) }
        if (t.needsReview) add("❔")
    }.joinToString(" · ")
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            ClickRow(
                title = t.party ?: kindLabel(k),
                subtitle = sub,
                trailing = (if (signed > 0) "+" else if (signed < 0) "−" else "") + money(abs(t.amount)) + if (t.currency != "EGP") " ${t.currency}" else "",
                trailingColor = if (t.excluded) Color.Gray else amountColor(signed),
                leading = c?.emoji ?: "•",
                onClick = onClick,
            )
        }
    }
    if (t.excluded) Text(tr("excluded from stats", "مش داخلة في الإحصائيات"), style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.None),
        color = Color.Gray, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxEditScreen(snap: Snapshot, id: Long, accountId: Long) {
    val repo = LocalRepo.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val existing = snap.txs.firstOrNull { it.id == id }
    if (id != 0L && existing == null) { Text("…"); return }
    val accounts = snap.accounts.map { it.account }
    val t0 = existing ?: TxEntity(
        accountId = if (accountId != 0L) accountId else accounts.firstOrNull()?.id ?: 0L,
        time = System.currentTimeMillis(), amount = 0.0, kind = TxKind.MANUAL_OUT.name,
        categoryId = snap.categories.firstOrNull { it.key == "other" }?.id,
    )
    var amount by remember(id) { mutableStateOf(if (t0.amount > 0) fmtPlain(t0.amount) else "") }
    var kind by remember(id) { mutableStateOf(kindOf(t0)) }
    var acc by remember(id) { mutableStateOf(t0.accountId) }
    var cat by remember(id) { mutableStateOf(t0.categoryId) }
    var party by remember(id) { mutableStateOf(t0.party ?: "") }
    var note by remember(id) { mutableStateOf(t0.note ?: "") }
    var time by remember(id) { mutableStateOf(t0.time) }
    var currency by remember(id) { mutableStateOf(t0.currency) }
    var excluded by remember(id) { mutableStateOf(t0.excluded) }
    var showRule by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), 
            title = { Text(if (id == 0L) tr("New transaction", "معاملة جديدة") else tr("Edit transaction", "تعديل المعاملة")) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, null) } },
        )
        if (accounts.isEmpty()) {
            Text(tr("Add an account first.", "ضيف حساب الأول."), Modifier.padding(16.dp)); return
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing == null || existing.smsHash == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(kind.direction < 0, { kind = TxKind.MANUAL_OUT }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(tr("Out", "خارج")) }
                    SegmentedButton(kind.direction > 0, { kind = TxKind.MANUAL_IN }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(tr("In", "داخل")) }
                }
            } else {
                Picker(tr("Type", "النوع"), TxKind.entries.filter { it != TxKind.ADJUSTMENT }, kind, { kindLabel(it) + if (it.direction > 0) " (+)" else " (−)" }, { kind = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(tr("Amount", "المبلغ"), amount, { amount = it }, Modifier.weight(2f))
                Picker(tr("Currency", "العملة"), listOf("EGP", "USD"), currency, { it }, { currency = it }, Modifier.weight(1f))
            }
            Picker(tr("Account", "الحساب"), accounts, accounts.firstOrNull { it.id == acc }, { "${it.accountType.emoji} ${it.name}" }, { acc = it.id })
            val cats = snap.categories.sortedBy { it.sortOrder }
            Picker(tr("Category", "التصنيف"), cats, cats.firstOrNull { it.id == cat }, { catName(it) }, { cat = it.id })
            TextField1(tr("Merchant / person", "التاجر / الشخص"), party, { party = it })
            TextField1(tr("Note", "ملاحظة"), note, { note = it }, singleLine = false)
            DateField(tr("Date", "التاريخ"), time, { time = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Exclude from statistics", "استبعدها من الإحصائيات"))
                    Text(tr("Still counts in the balance", "بس لسه بتأثر على الرصيد"), style = MaterialTheme.typography.bodySmall)
                }
                Switch(excluded, { excluded = it })
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val amt = parseNum(amount) ?: return@Button
                scope.launch {
                    repo.saveTx(t0.copy(
                        amount = abs(amt), kind = kind.name, accountId = acc, categoryId = cat,
                        party = party.ifBlank { null }, note = note.ifBlank { null }, time = time, currency = currency,
                        excluded = excluded,
                        categoryLocked = t0.categoryLocked || cat != t0.categoryId || excluded != t0.excluded || id == 0L,
                    ))
                    nav.popBackStack()
                }
            }) { Text(tr("Save", "حفظ")) }
            if (existing != null && !existing.party.isNullOrBlank()) {
                OutlinedButton(onClick = { showRule = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Always categorize \"${existing.party}\" like this", "صنّف \"${existing.party}\" كده على طول"))
                }
            }
            if (existing != null && kindOf(existing).direction < 0) {
                var split by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { split = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        existing.personalAmount?.let { tr("My spending: ", "صرفي منها: ") + fmt(it) + tr(" (change)", " (تعديل)") }
                            ?: tr("Split: how much was really my spending?", "تقسيم: كام منها صرفي فعلاً؟"),
                    )
                }
                if (split) ReviewDialog(existing) { split = false }
            }
            if (existing != null && kindOf(existing).direction > 0) {
                var saveOpen by remember { mutableStateOf(false) }
                val savedFrom = snap.moves.filter { it.txId == existing.id }.sumOf { it.amount }
                OutlinedButton(onClick = { saveOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (savedFrom > 0) tr("Saved from it: ", "اتحوش منه: ") + fmt(savedFrom) + tr(" (change)", " (تعديل)")
                        else tr("💰 Save from this income", "💰 حوّش من الدخل ده"))
                }
                if (saveOpen) SaveDialog(existing, snap) { saveOpen = false }
                val party = existing.party
                if (!party.isNullOrBlank() && snap.rules.none { it.savePercent != null && com.moneyfen.core.RuleMatcher.matches(it.pattern, party) }) {
                    TextButton(onClick = {
                        scope.launch { repo.saveRule(RuleEntity(pattern = party, savePercent = 100.0, priority = (snap.rules.maxOfOrNull { it.priority } ?: 0) + 1)) }
                    }) { Text(tr("Always ask me to save money from \"$party\"", "اسألني دايماً أحوش من فلوس \"$party\"")) }
                }
            }
            existing?.linkedTxId?.let { lid ->
                TextButton(onClick = { nav.navigate("tx/$lid") }) { Text(tr("Linked transaction →", "المعاملة المرتبطة ←")) }
            }
            existing?.balanceAfter?.let { Text(tr("Balance after (from SMS): ", "الرصيد بعدها (من الرسالة): ") + fmt(it), style = MaterialTheme.typography.bodySmall) }
            existing?.availableAfter?.let { Text(tr("Available limit after (from SMS): ", "المتاح بعدها (من الرسالة): ") + fmt(it), style = MaterialTheme.typography.bodySmall) }
            existing?.smsBody?.let {
                SectionCard(tr("Original SMS", "الرسالة الأصلية")) { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
            }
        }
    }

    if (showRule && existing != null) {
        var pattern by remember { mutableStateOf(existing.party ?: "") }
        var ruleCat by remember { mutableStateOf(cat) }
        var rename by remember { mutableStateOf("") }
        var applied by remember { mutableStateOf<Int?>(null) }
        AlertDialog(
            onDismissRequest = { showRule = false },
            title = { Text(tr("New rule", "قاعدة جديدة")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField1(tr("If merchant contains", "لو اسم التاجر فيه"), pattern, { pattern = it })
                    val cats = snap.categories
                    Picker(tr("Category", "التصنيف"), cats, cats.firstOrNull { it.id == ruleCat }, { catName(it) }, { ruleCat = it.id })
                    TextField1(tr("Rename to (optional)", "غيّر الاسم لـ (اختياري)"), rename, { rename = it })
                    applied?.let { Text(tr("Updated $it transactions", "اتعدلت $it معاملة")) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val maxP = (snap.rules.maxOfOrNull { it.priority } ?: 0) + 1
                        repo.saveRule(RuleEntity(pattern = pattern.trim(), categoryId = ruleCat, renameTo = rename.ifBlank { null }, priority = maxP))
                        applied = repo.reapplyRules()
                        showRule = false
                    }
                }) { Text(tr("Save & apply", "احفظ وطبّق")) }
            },
            dismissButton = { TextButton(onClick = { showRule = false }) { Text(tr("Cancel", "إلغاء")) } },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(tr("Delete transaction?", "تمسح المعاملة؟"), existing.party ?: "", {
            scope.launch { repo.deleteTx(existing); nav.popBackStack() }
        }, { confirmDelete = false })
    }
}

@Suppress("unused")
private fun isExpense(c: CategoryEntity?) = c?.categoryType == CategoryType.EXPENSE
