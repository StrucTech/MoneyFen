package com.moneyfen.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moneyfen.app.data.BackupJson
import com.moneyfen.app.data.CategoryEntity
import com.moneyfen.app.data.CategoryType
import com.moneyfen.app.data.RuleEntity
import com.moneyfen.app.data.SmsPatternEntity
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.SupabaseBackup
import com.moneyfen.app.sms.SmsImporter
import com.moneyfen.core.CibParser
import com.moneyfen.core.CustomParser
import com.moneyfen.core.CustomPattern
import com.moneyfen.core.ParseResult
import com.moneyfen.core.TxKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, fab: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(title) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        Box(Modifier.fillMaxSize()) {
            content()
            if (fab != null) FloatingActionButton(onClick = fab, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, null) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(snap: Snapshot) {
    val repo = LocalRepo.current
    val prefs = repo.prefs
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var senders by remember { mutableStateOf(prefs.senders) }
    var usd by remember { mutableStateOf(fmtPlain(prefs.usdRate)) }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val lang = L.current()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(tr("Settings", "الإعدادات")) })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionCard(tr("Language", "اللغة")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(lang == "", { L.set("") }, { Text(tr("System", "النظام")) })
                    FilterChip(lang == "ar", { L.set("ar") }, { Text("العربية") })
                    FilterChip(lang == "en", { L.set("en") }, { Text("English") })
                }
                SwitchRow(tr("Hide amounts", "اخفي المبالغ"), null, prefs.hideAmounts) { prefs.hideAmounts = it }
            }
            SectionCard(tr("Bank SMS", "رسايل البنك")) {
                ImportSmsBlock()
                OutlinedTextField(senders, { senders = it }, label = { Text(tr("SMS senders (comma separated)", "أسماء المرسلين (مفصولة بفاصلة)")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = { TextButton(onClick = { prefs.senders = senders }) { Text(tr("Save", "حفظ")) } })
                SwitchRow(tr("Notify on each transaction", "إشعار مع كل معاملة"), null, prefs.notifyOnSms) { prefs.notifyOnSms = it }
                SwitchRow(tr("ATM withdrawals go to Cash account", "السحب من ATM يروح لحساب الكاش"),
                    tr("Otherwise they count as spending (Cash withdrawal)", "غير كده بيتحسب مصروف (سحب كاش)"), prefs.atmToCash) { prefs.atmToCash = it }
            }
            SectionCard(tr("Saving from income", "التحويش من الدخل")) {
                SwitchRow(tr("Ask how much to save when salary arrives", "اسألني هحوش كام لما المرتب ينزل"), null, prefs.askSalary) { prefs.askSalary = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(prefs.salaryMode == "PERCENT", { prefs.salaryMode = "PERCENT" }, { Text(tr("Percent", "نسبة") + " ${prefs.savingsPercent}%") })
                    FilterChip(prefs.salaryMode == "FIXED", { prefs.salaryMode = "FIXED" }, { Text(tr("Fixed amount", "مبلغ ثابت")) })
                }
                var fixedTxt by remember { mutableStateOf(if (prefs.salaryFixed > 0) fmtPlain(prefs.salaryFixed) else "") }
                if (prefs.salaryMode == "FIXED") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(tr("Fixed amount to save", "المبلغ الثابت"), fixedTxt, { fixedTxt = it }, Modifier.weight(1f))
                    Button(onClick = { parseNum(fixedTxt)?.let { prefs.salaryFixed = it } }) { Text(tr("Save", "حفظ")) }
                }
                Text(tr("The percent is set in Plan. For freelance / other income: open the transaction → \"Always ask me to save\", or add a rule with a save %.",
                    "النسبة بتتظبط من الخطة. للفريلانس أو أي دخل تاني: افتح المعاملة ← \"اسألني دايماً أحوش\"، أو اعمل قاعدة فيها نسبة تحويش."),
                    style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(tr("Card charges", "مصاريف الكارت")) {
                SwitchRow(tr("Ask after each credit card charge", "اسألني بعد كل عملية على الكريديت"),
                    tr("How much was really spent; the rest isn't counted as spending but stays on the card debt",
                        "كام منها صرف فعلاً؛ الباقي مش بيتحسب مصروف بس بيفضل على مديونية الكارت"), prefs.reviewCredit) { prefs.reviewCredit = it }
                SwitchRow(tr("Also ask for debit card purchases", "اسألني كمان في مشتريات الديبت"), null, prefs.reviewDebit) { prefs.reviewDebit = it }
            }
            SectionCard(tr("Prices", "الأسعار")) {
                SwitchRow(tr("Update USD rate automatically", "حدّث سعر الدولار أوتوماتيك"), null, prefs.autoUsd) { prefs.autoUsd = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(tr("USD rate (EGP)", "سعر الدولار"), usd, { usd = it }, Modifier.weight(1f))
                    Button(onClick = { parseNum(usd)?.let { prefs.usdRate = it; prefs.autoUsd = false } }) { Text(tr("Save", "حفظ")) }
                }
                var premium by remember { mutableStateOf(fmtPlain(prefs.goldPremium)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(tr("Gold local premium %", "فرق سعر الدهب المحلي %"), premium, { premium = it }, Modifier.weight(1f))
                    Button(onClick = { parseNum(premium)?.let { prefs.goldPremium = it; prefs.pricesAt = 0 } }) { Text(tr("Save", "حفظ")) }
                }
                Text(tr(
                    "Gold price = world price × USD rate. If the local shop price differs, put the difference here (e.g. 1.5 or -0.5).",
                    "سعر الدهب = السعر العالمي × سعر الدولار. لو سعر محل الدهب مختلف حط الفرق هنا (مثلاً 1.5 أو -0.5).",
                ), style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(tr("Customize", "تخصيص")) {
                ClickRow(tr("Categories & budgets", "التصنيفات والميزانيات"), "${snap.categories.size}", leading = "🗂️") { nav.navigate("categories") }
                ClickRow(tr("Income sources", "مصادر الدخل"), tr("Choose which transfers are real income", "اختار التحويلات اللي هي دخل فعلاً"), leading = "💼") { nav.navigate("sources") }
                ClickRow(tr("Auto-categorize rules", "قواعد التصنيف التلقائي"), "${snap.rules.size}", leading = "⚙️") { nav.navigate("rules") }
                ClickRow(tr("Custom SMS templates (wallets, other banks)", "قوالب رسايل مخصصة (محافظ، بنوك تانية)"), "${snap.patterns.size}", leading = "✉️") { nav.navigate("patterns") }
                OutlinedButton(onClick = { scope.launch { val n = repo.reapplyRules(); msg = "✔ $n" } }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Re-apply rules to all transactions", "طبّق القواعد على كل المعاملات"))
                }
                msg?.let { Text(it) }
            }
            SectionCard(tr("Backup", "النسخ الاحتياطي")) {
                ClickRow(tr("Supabase & file backup", "Supabase وملف احتياطي"),
                    if (prefs.lastBackup > 0) tr("Last: ", "آخر نسخة: ") + dateFmt(prefs.lastBackup, "dd MMM HH:mm") else tr("Not configured", "مش متظبط"),
                    leading = "☁️") { nav.navigate("backup") }
            }
            SectionCard(tr("Danger zone", "منطقة خطر")) {
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Delete all accounts & transactions", "امسح كل الحسابات والمعاملات"), color = MaterialTheme.colorScheme.error)
                }
            }
            Text("MoneyFen ${com.moneyfen.app.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        }
    }
    if (confirmClear) ConfirmDialog(tr("Delete everything?", "تمسح كل حاجة؟"),
        tr("Categories, rules and goals are kept. You can re-import SMS after.", "التصنيفات والقواعد والأهداف هتفضل. تقدر تستورد الرسايل تاني بعدها."),
        { scope.launch { repo.clearAll() } }, { confirmClear = false })
}

// ------------------------------------------------------------------ Categories

@Composable
fun CategoriesScreen(snap: Snapshot) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    SubScreen(tr("Categories", "التصنيفات"), fab = { editing = CategoryEntity(nameEn = "", nameAr = "", sortOrder = 50) }) {
        LazyColumn {
            CategoryType.entries.forEach { type ->
                item { Text(typeLabel(type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 8.dp)) }
                items(snap.categories.filter { it.categoryType == type }, key = { it.id }) { c ->
                    ClickRow(tr(c.nameEn, c.nameAr), c.monthlyBudget?.let { tr("Budget ", "ميزانية ") + fmt0(it) }, leading = c.emoji) { editing = c }
                }
            }
        }
    }
    editing?.let { c ->
        var emoji by remember(c) { mutableStateOf(c.emoji) }
        var en by remember(c) { mutableStateOf(c.nameEn) }
        var ar by remember(c) { mutableStateOf(c.nameAr) }
        var type by remember(c) { mutableStateOf(c.categoryType) }
        var budget by remember(c) { mutableStateOf(c.monthlyBudget?.let(::fmtPlain) ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(tr("Category", "تصنيف")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextField1(tr("Icon (emoji)", "أيقونة (إيموجي)"), emoji, { emoji = it.take(2) })
                    TextField1("English name", en, { en = it })
                    TextField1("الاسم بالعربي", ar, { ar = it })
                    Picker(tr("Type", "النوع"), CategoryType.entries, type, { typeLabel(it) }, { type = it })
                    if (type == CategoryType.EXPENSE) NumberField(tr("Monthly budget", "ميزانية شهرية"), budget, { budget = it })
                }
            },
            confirmButton = { TextButton(onClick = {
                val name = en.ifBlank { ar }
                if (name.isNotBlank()) scope.launch {
                    repo.saveCategory(c.copy(emoji = emoji.ifBlank { "📁" }, nameEn = name, nameAr = ar.ifBlank { name }, type = type.name, monthlyBudget = parseNum(budget)?.takeIf { it > 0 }))
                }
                editing = null
            }) { Text(tr("Save", "حفظ")) } },
            dismissButton = {
                Row {
                    if (c.id != 0L) TextButton(onClick = { scope.launch { repo.deleteCategory(c) }; editing = null }) { Text(tr("Delete", "امسح"), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text(tr("Cancel", "إلغاء")) }
                }
            },
        )
    }
}

@Composable
fun typeLabel(t: CategoryType) = when (t) {
    CategoryType.EXPENSE -> tr("Expense", "مصروف")
    CategoryType.INCOME -> tr("Income", "دخل")
    CategoryType.TRANSFER -> tr("Transfer (not counted)", "تحويل (مش بيتحسب)")
}

// ------------------------------------------------------------------ Rules

@Composable
fun RulesScreen(snap: Snapshot) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<RuleEntity?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    SubScreen(tr("Rules", "القواعد"), fab = { editing = RuleEntity(pattern = "", priority = (snap.rules.maxOfOrNull { it.priority } ?: 0) + 1) }) {
        LazyColumn {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(tr(
                        "If the merchant / person name contains the text, the category is set automatically. Use | for alternatives (talabat|elmenus). Rules higher in the list win.",
                        "لو اسم التاجر / الشخص فيه الكلمة، التصنيف بيتحط أوتوماتيك. استخدم | للبدائل (talabat|elmenus). القواعد اللي فوق ليها الأولوية.",
                    ), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { scope.launch { msg = "✔ ${repo.reapplyRules()}" } }) { Text(tr("Apply to all transactions", "طبّق على كل المعاملات")) }
                    msg?.let { Text(it) }
                }
            }
            items(snap.rules, key = { it.id }) { r ->
                val c = r.categoryId?.let { snap.categoryById[it] }
                ClickRow(r.pattern, (if (r.categoryId != null) "→ " + catName(c) else "") + (r.renameTo?.let { " · \"$it\"" } ?: "") +
                    (if (r.exclude) tr(" · excluded", " · مستبعدة") else "") + (r.savePercent?.let { " · 💰 ${fmtPlain(it)}%" } ?: ""),
                    leading = c?.emoji ?: "⚙️") { editing = r }
                HorizontalDivider()
            }
        }
    }
    editing?.let { r ->
        var pattern by remember(r) { mutableStateOf(r.pattern) }
        var cat by remember(r) { mutableStateOf(r.categoryId) }
        var rename by remember(r) { mutableStateOf(r.renameTo ?: "") }
        var exclude by remember(r) { mutableStateOf(r.exclude) }
        var priority by remember(r) { mutableStateOf(r.priority.toString()) }
        var savePct by remember(r) { mutableStateOf(r.savePercent?.let(::fmtPlain) ?: "") }
        val matches = if (pattern.isBlank()) 0 else snap.txs.count { com.moneyfen.core.RuleMatcher.matches(pattern, it.party) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(tr("Rule", "قاعدة")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextField1(tr("Name contains", "الاسم فيه"), pattern, { pattern = it })
                    Text(tr("Matches $matches transactions", "بتطابق $matches معاملة"), style = MaterialTheme.typography.bodySmall)
                    Picker(tr("Category", "التصنيف"), snap.categories, snap.categories.firstOrNull { it.id == cat }, { catName(it) }, { cat = it.id })
                    TextField1(tr("Rename to (optional)", "غيّر الاسم لـ (اختياري)"), rename, { rename = it })
                    SwitchRow(tr("Exclude from stats", "استبعد من الإحصائيات"), null, exclude) { exclude = it }
                    NumberField(tr("Income from here: ask to save % (empty = no)", "دخل من هنا: اسألني أحوش كام % (فاضي = لأ)"), savePct, { savePct = it })
                    NumberField(tr("Priority", "الأولوية"), priority, { priority = it })
                }
            },
            confirmButton = { TextButton(onClick = {
                if (pattern.isNotBlank()) scope.launch {
                    repo.saveRule(r.copy(pattern = pattern.trim(), categoryId = cat, renameTo = rename.ifBlank { null }, exclude = exclude,
                        priority = priority.toIntOrNull() ?: r.priority, savePercent = parseNum(savePct)))
                }
                editing = null
            }) { Text(tr("Save", "حفظ")) } },
            dismissButton = {
                Row {
                    if (r.id != 0L) TextButton(onClick = { scope.launch { repo.deleteRule(r) }; editing = null }) { Text(tr("Delete", "امسح"), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text(tr("Cancel", "إلغاء")) }
                }
            },
        )
    }
}

// ------------------------------------------------------------------ Custom SMS templates

@Composable
fun PatternsScreen(snap: Snapshot) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Pair<SmsPatternEntity, String>?>(null) }
    var unknown by remember { mutableStateOf<List<SmsImporter.Sms>?>(null) }
    val blank = SmsPatternEntity(name = "", senderContains = "", regex = "", kind = TxKind.PURCHASE.name)

    SubScreen(tr("SMS templates", "قوالب الرسايل"), fab = { editing = blank to "" }) {
        LazyColumn {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr(
                        "CIB messages are understood out of the box. For other senders (wallets, other banks) add a template: a regular expression with named groups (?<amount>…), optionally (?<party>…), (?<balance>…), (?<account>…). Remember to add the sender in Settings.",
                        "رسايل CIB مفهومة جاهزة. لأي مرسل تاني (محافظ، بنوك) ضيف قالب: Regex فيه جروبات بأسماء (?<amount>…) واختياري (?<party>…) و (?<balance>…) و (?<account>…). متنساش تضيف اسم المرسل في الإعدادات.",
                    ), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        scope.launch {
                            unknown = withContext(Dispatchers.IO) {
                                runCatching {
                                    SmsImporter.readInbox(ctx, repo).filter { m ->
                                        CibParser.parse(m.body) == ParseResult.Unknown && Regex("\\d+[.,]\\d{2}|EGP|جم|جنيه").containsMatchIn(m.body)
                                    }.takeLast(50).reversed()
                                }.getOrDefault(emptyList())
                            }
                        }
                    }) { Text(tr("Show unrecognized messages with amounts", "اعرض الرسايل اللي فيها مبالغ ومتفهمتش")) }
                }
            }
            items(snap.patterns, key = { it.id }) { p ->
                ClickRow(p.name.ifBlank { p.regex }, "${p.senderContains} · ${p.kind}", leading = "✉️") { editing = p to "" }
            }
            unknown?.let { list ->
                item { Text(tr("Unrecognized (tap to make a template)", "متفهمتش (اضغط علشان تعمل قالب)"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp)) }
                if (list.isEmpty()) item { Text(tr("Nothing — all good 👌", "مفيش — كله تمام 👌"), Modifier.padding(16.dp)) }
                items(list) { m ->
                    ClickRow(m.address + " · " + dateFmt(m.date), m.body.take(160)) { editing = blank.copy(senderContains = m.address) to m.body }
                    HorizontalDivider()
                }
            }
        }
    }
    editing?.let { (p, sample0) ->
        var name by remember(p) { mutableStateOf(p.name) }
        var sender by remember(p) { mutableStateOf(p.senderContains) }
        var regex by remember(p) { mutableStateOf(p.regex) }
        var kind by remember(p) { mutableStateOf(runCatching { TxKind.valueOf(p.kind) }.getOrDefault(TxKind.PURCHASE)) }
        var currency by remember(p) { mutableStateOf(p.currency) }
        var account by remember(p) { mutableStateOf(p.accountId) }
        var sample by remember(p) { mutableStateOf(sample0) }
        val accounts = snap.accounts.map { it.account }
        val test = if (regex.isBlank() || sample.isBlank()) null else CustomParser.test(CustomPattern(sender, regex, kind, currency), sample)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(tr("SMS template", "قالب رسالة")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextField1(tr("Name", "الاسم"), name, { name = it })
                    TextField1(tr("Sender contains", "المرسل فيه"), sender, { sender = it })
                    OutlinedTextField(regex, { regex = it }, label = { Text("Regex") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
                    Picker(tr("Type", "النوع"), TxKind.entries.filter { it != TxKind.ADJUSTMENT }, kind, { kindLabel(it) }, { kind = it })
                    Picker(tr("Currency", "العملة"), listOf("EGP", "USD"), currency, { it }, { currency = it })
                    Picker(tr("Account", "الحساب"), listOf<com.moneyfen.app.data.AccountEntity?>(null) + accounts, accounts.firstOrNull { it.id == account },
                        { it?.name ?: tr("— auto by sender —", "— تلقائي حسب المرسل —") }, { account = it?.id })
                    OutlinedTextField(sample, { sample = it }, label = { Text(tr("Sample message to test", "رسالة للتجربة")) }, modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall)
                    test?.let { r ->
                        r.fold(
                            { Text("✔ ${it.amount} ${it.currency} · ${it.party ?: ""} · bal ${it.balanceAfter ?: "-"}", color = Green) },
                            { Text("✘ ${it.message}", color = MaterialTheme.colorScheme.error) },
                        )
                    }
                    Text(tr("Example: ", "مثال: ") + "تم خصم (?<amount>[\\d,.]+) جنيه .*? (?<party>.+?) رصيدك (?<balance>[\\d,.]+)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            },
            confirmButton = { TextButton(onClick = {
                if (regex.isNotBlank()) scope.launch {
                    repo.savePattern(p.copy(name = name, senderContains = sender.trim(), regex = regex, kind = kind.name, currency = currency, accountId = account))
                    val s = sender.trim()
                    if (s.isNotEmpty() && repo.prefs.senderList().none { it.equals(s, true) }) repo.prefs.senders = (repo.prefs.senderList() + s).joinToString(",")
                }
                editing = null
            }) { Text(tr("Save", "حفظ")) } },
            dismissButton = {
                Row {
                    if (p.id != 0L) TextButton(onClick = { scope.launch { repo.deletePattern(p) }; editing = null }) { Text(tr("Delete", "امسح"), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text(tr("Cancel", "إلغاء")) }
                }
            },
        )
    }
}

// ------------------------------------------------------------------ Backup

@Composable
fun BackupScreen() {
    val repo = LocalRepo.current
    val prefs = repo.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sb = remember { SupabaseBackup(prefs) }
    var url by remember { mutableStateOf(prefs.sbUrl) }
    var key by remember { mutableStateOf(prefs.sbKey) }
    var email by remember { mutableStateOf(prefs.sbEmail) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun act(block: suspend () -> String) {
        busy = true; status = null
        scope.launch {
            status = try { block() } catch (e: Exception) { "✘ " + (e.message ?: e.toString()) }
            busy = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) act {
            val text = BackupJson.export(repo)
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            "✔ Exported"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) act {
            val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("empty")
            val d = BackupJson.import(repo, text)
            "✔ ${d.transactions.size} transactions restored"
        }
    }

    SubScreen(tr("Backup", "النسخ الاحتياطي")) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionCard("Supabase") {
                Text(tr(
                    "Free cloud backup. Create a free project on supabase.com, run supabase/schema.sql in the SQL editor, then paste the Project URL and anon key here.",
                    "نسخة احتياطية مجانية على السحابة. اعمل مشروع مجاني على supabase.com، شغّل ملف supabase/schema.sql في SQL Editor، وبعدين حط هنا Project URL و anon key.",
                ), style = MaterialTheme.typography.bodySmall)
                TextField1("Project URL (https://xxxx.supabase.co)", url, { url = it })
                TextField1("anon public key", key, { key = it })
                if (!prefs.supabaseReady) {
                    TextField1(tr("Email", "الإيميل"), email, { email = it })
                    OutlinedTextField(password, { password = it }, label = { Text(tr("Password", "الباسورد")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { prefs.sbUrl = url; prefs.sbKey = key; act { sb.login(email, password, false); password = ""; "✔ Signed in" } }) { Text(tr("Sign in", "دخول")) }
                        OutlinedButton(enabled = !busy, onClick = { prefs.sbUrl = url; prefs.sbKey = key; act { sb.login(email, password, true); password = ""; "✔ Account created" } }) { Text(tr("Create account", "حساب جديد")) }
                    }
                } else {
                    Text(tr("Signed in as ", "داخل بـ ") + prefs.sbEmail)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { act { sb.backup(repo); "✔ " + dateFmt(System.currentTimeMillis(), "HH:mm") } }) { Text(tr("Backup now", "انسخ دلوقتي")) }
                        OutlinedButton(enabled = !busy, onClick = { confirmRestore = true }) { Text(tr("Restore", "استرجع")) }
                    }
                    SwitchRow(tr("Daily automatic backup", "نسخة تلقائية كل يوم"), null, prefs.autoBackup) { prefs.autoBackup = it }
                    if (prefs.lastBackup > 0) Text(tr("Last backup: ", "آخر نسخة: ") + dateFmt(prefs.lastBackup, "dd MMM yyyy HH:mm"), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { sb.logout() }) { Text(tr("Sign out", "خروج")) }
                }
            }
            SectionCard(tr("File", "ملف")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { exportLauncher.launch("moneyfen-${dateFmt(System.currentTimeMillis(), "yyyyMMdd")}.json") }) { Text(tr("Export", "تصدير")) }
                    OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text(tr("Import", "استيراد")) }
                }
                Text(tr("Import replaces all data in the app.", "الاستيراد بيستبدل كل البيانات اللي في الأبلكيشن."), style = MaterialTheme.typography.bodySmall)
            }
            status?.let { Text(it, Modifier.padding(16.dp)) }
        }
    }
    if (confirmRestore) ConfirmDialog(tr("Restore from Supabase?", "تسترجع من Supabase؟"),
        tr("This replaces all data on the phone.", "ده هيستبدل كل البيانات اللي على الموبايل."),
        { act { val d = sb.restore(repo); "✔ ${d.transactions.size} transactions restored" } }, { confirmRestore = false })
}
