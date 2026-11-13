package com.moneyfen.app.ui

import androidx.compose.foundation.layout.WindowInsets
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyfen.app.app
import com.moneyfen.app.data.AccountType
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.monthRange
import com.moneyfen.app.sms.ImportStats
import com.moneyfen.app.sms.SmsImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(snap: Snapshot) {
    val repo = LocalRepo.current
    val nav = LocalNav.current
    val hide = LocalHide.current
    val (from, to) = monthRange()
    val month = snap.totals(from, to)
    val pct = repo.prefs.savingsPercent

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), 
            title = { Text("MoneyFen") },
            actions = {
                IconButton(onClick = { repo.prefs.hideAmounts = !hide }) {
                    Icon(if (hide) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (snap.accounts.isNotEmpty()) item { SafeCard(snap) }
            item {
                SectionCard(tr("Net worth", "صافي الثروة"), help = "netWorth") {
                    Text(money(snap.netWorth, currency = "EGP"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = amountColor(snap.netWorth))
                    Spacer(Modifier.height(6.dp))
                    LabelValue(tr("Assets", "الأصول"), money(snap.assets), amountColor(1.0))
                    LabelValue(tr("Debts", "الديون"), money(snap.liabilities), amountColor(-1.0))
                    NetWorthToggles(snap)
                }
            }
            val review = snap.toReview
            if (review.isNotEmpty()) item {
                var open by remember { mutableStateOf<com.moneyfen.app.data.TxEntity?>(null) }
                SectionCard(tr("Needs your decision (${review.size})", "محتاج قرارك (${review.size})"), help = "decide") {
                    review.take(10).forEach { t ->
                        val income = com.moneyfen.app.data.kindOf(t).direction > 0
                        ClickRow(t.party ?: "",
                            (if (income) tr("How much to save?", "هتحوش كام؟") else tr("Really spending?", "صرف فعلاً؟")) + " · " + dateFmt(t.time, "dd MMM HH:mm"),
                            (if (income) "+" else "") + money(t.amount) + if (t.currency != "EGP") " ${t.currency}" else "",
                            amountColor(if (income) 1.0 else -1.0), leading = if (income) "💰" else "❔") { open = t }
                    }
                }
                open?.let { t ->
                    if (com.moneyfen.app.data.kindOf(t).direction > 0) SaveDialog(t, snap) { open = null } else ReviewDialog(t) { open = null }
                }
            }
            item {
                SectionCard(tr("This month", "الشهر ده") + " · " + periodLabel(from, to)) {
                    LabelValue(tr("Income", "الدخل"), money(month.income), amountColor(1.0))
                    LabelValue(tr("Spent", "المصروف"), money(month.expense), amountColor(-1.0))
                    LabelValue(tr("Saved", "اتحوش"), money(month.saved), amountColor(month.saved))
                    if (month.income > 0) {
                        val target = month.income * pct / 100
                        Spacer(Modifier.height(6.dp))
                        Text(tr("Savings goal $pct% = ", "هدف الادخار $pct% = ") + money(target), style = MaterialTheme.typography.bodySmall)
                        ProgressLine((month.saved / target).toFloat(), over = month.saved < 0)
                    }
                }
            }
            val dues = snap.statements.groupBy { it.accountId }.mapNotNull { (_, l) -> l.maxByOrNull { it.time } }
                .filter { System.currentTimeMillis() - it.time < 40L * 86_400_000 }
            if (dues.isNotEmpty()) item {
                SectionCard(tr("Credit card statements", "كشوف البطاقة")) {
                    dues.forEach { s ->
                        val acc = snap.accountById[s.accountId]?.account?.name ?: ""
                        Text("💳 $acc: ${money(s.amount)} — " + tr("min", "الحد الأدنى") + " ${money(s.minDue ?: 0.0)} " + tr("before", "قبل") + " ${s.dueDate ?: ""}")
                    }
                }
            }
            val groups = snap.accounts.filter { !it.account.archived }.groupBy { it.account.accountType }
            if (snap.accounts.isEmpty()) item {
                SectionCard(tr("No accounts yet", "لسه مفيش حسابات")) {
                    Text(tr("Import your bank SMS from Settings, or add an account manually.", "استورد رسايل البنك من الإعدادات، أو ضيف حساب بإيدك."))
                }
            }
            AccountType.entries.forEach { type ->
                val list = groups[type] ?: return@forEach
                item(key = "g_${type.name}") {
                    SectionCard("${type.emoji} " + tr(type.en, type.ar)) {
                        list.forEach { av ->
                            val a = av.account
                            val sub = buildString {
                                if (a.accountType == AccountType.CREDIT_CARD && a.creditLimit > 0) append(tr("Available ", "المتاح ") + money(a.creditLimit + av.balance, decimals = false))
                                holdingSummary(av)?.let { append(it) }
                                if (a.currency != "EGP") append(" · ${a.currency} ${money(av.balance)}")
                                if (!a.includeInNetWorth) append(tr(" · not in net worth", " · خارج الثروة"))
                                val r = snap.reserved(a.id)
                                if (r > 0) append(tr("Total ", "الإجمالي ") + money(av.balance, decimals = false) + tr(" · 🎯 goals ", " · 🎯 أهداف ") + money(r, decimals = false))
                            }
                            val hasGoals = snap.reserved(a.id) > 0
                            val shown = if (hasGoals) snap.free(av) else av.balanceEgp
                            val sub2 = if (hasGoals && shown < 0) sub + "\n" + tr("⚠️ Goals are more than the balance by ", "⚠️ الأهداف أكتر من الرصيد بـ ") + money(-shown, decimals = false) else sub
                            // Liabilities are shown as positive debt; free balance after goals keeps its sign
                            val amountText = if (hasGoals) (if (shown < 0) "−" else "") + money(abs(shown)) else money(abs(shown))
                            ClickRow(a.name, sub2, (if (hasGoals) tr("free ", "متاح ") else "") + amountText, amountColor(shown)) {
                                nav.navigate(if (a.isHolding) "account/${a.id}" else "history/${a.id}")
                            }
                        }
                    }
                }
            }
            item { PricesCard(snap.accounts.any { it.account.isHolding }) }
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                    FilledTonalButton(onClick = { nav.navigate("account/0") }) {
                        Icon(Icons.Default.Add, null); Text(tr(" Add account / asset / debt", " ضيف حساب / أصل / دين"))
                    }
                }
            }
        }
    }
}

/** Import-from-inbox block (used in onboarding and settings). */
@Composable
fun ImportSmsBlock(onDone: (ImportStats) -> Unit = {}) {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var result by remember { mutableStateOf<ImportStats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val perms = buildList {
        add(Manifest.permission.READ_SMS); add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun run() {
        running = true; error = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { SmsImporter.import(ctx, repo) { a, b -> progress = a to b } }
                result = r; onDone(r)
            } catch (e: Exception) {
                error = e.message
            } finally { running = false }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.READ_SMS] == true) run() else error = "SMS permission denied"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(enabled = !running, onClick = { launcher.launch(perms) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Import all bank SMS from phone", "استورد كل رسايل البنك من الموبايل"))
        }
        if (running) {
            val (a, b) = progress
            LinearProgressIndicator(progress = { if (b == 0) 0f else a / b.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("$a / $b")
        }
        result?.let {
            Text(tr(
                "Scanned ${it.scanned} messages · added ${it.added} transactions · ${it.duplicates} already existed · ${it.ignored} not money",
                "اتقرأ ${it.scanned} رسالة · اتضاف ${it.added} معاملة · ${it.duplicates} موجودة قبل كده · ${it.ignored} مش معاملات",
            ))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun OnboardingScreen(snap: Snapshot) {
    val repo = LocalRepo.current
    var done by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("💰 MoneyFen", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(tr(
            "Your personal money tracker. It reads your bank SMS on the phone (nothing leaves the phone unless you enable Supabase backup).",
            "متابع فلوسك الشخصي. بيقرا رسايل البنك على الموبايل (مفيش حاجة بتخرج من الموبايل إلا لو فعّلت النسخ الاحتياطي على Supabase).",
        ))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { L.set("ar") }) { Text("العربية") }
            OutlinedButton(onClick = { L.set("en") }) { Text("English") }
        }
        HorizontalDivider()
        Text(tr("1) Import your history", "١) استورد التاريخ كله"), fontWeight = FontWeight.SemiBold)
        Text(tr("Bank SMS senders: ", "أسماء مرسلين البنك: ") + repo.prefs.senders + tr(" (change later in Settings)", " (تقدر تغيرها من الإعدادات)"), style = MaterialTheme.typography.bodySmall)
        ImportSmsBlock { done = true }
        if (snap.accounts.isNotEmpty()) {
            Text(tr("Accounts found:", "الحسابات اللي اتلقت:"), fontWeight = FontWeight.SemiBold)
            snap.accounts.forEach { Text("${it.account.accountType.emoji} ${it.account.name}: ${fmt(it.balance)}") }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { repo.prefs.onboarded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (done) tr("Start", "يلا نبدأ") else tr("Skip for now", "بعدين"))
        }
    }
}

/** Chips to add / remove whole asset types (gold, funds, stocks…) from net worth with one tap. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NetWorthToggles(snap: Snapshot) {
    val ctx = LocalContext.current
    val repo = LocalRepo.current
    val byType = snap.accounts.filter { !it.account.archived }.groupBy { it.account.accountType }
    if (byType.size < 2) return
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Counted in net worth (tap to add / remove):", "داخل في الثروة (دوس عشان تضيف / تشيل):"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
        InfoTip("netWorthChips")
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AccountType.entries.forEach { type ->
            val list = byType[type] ?: return@forEach
            val included = list.all { it.account.includeInNetWorth }
            val value = list.sumOf { it.balanceEgp }
            androidx.compose.material3.FilterChip(
                selected = included,
                onClick = {
                    ctx.app.appScope.launch { list.forEach { repo.saveAccount(it.account.copy(includeInNetWorth = !included)) } }
                },
                label = { Text("${type.emoji} " + tr(type.en, type.ar) + " " + money(abs(value), decimals = false)) },
            )
        }
    }
}
