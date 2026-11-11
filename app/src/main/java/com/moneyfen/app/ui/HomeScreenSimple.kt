package com.moneyfen.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyfen.app.data.Prices
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.TxEntity
import com.moneyfen.app.data.kindOf
import com.moneyfen.app.data.monthRange
import kotlin.math.abs

/**
 * Simple home (prefs.simpleUi on): one big number first, details folded away.
 * Top to bottom: needs your decision · you can spend today · you can spend (folded) · net worth (folded) ·
 * my accounts · one line of prices. Everything the old [HomeScreen] shows is still here, further down or folded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenSimple(snap: Snapshot) {
    val repo = LocalRepo.current
    val nav = LocalNav.current
    val hide = LocalHide.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text("MoneyFen") },
            actions = {
                IconButton(onClick = { repo.prefs.hideAmounts = !hide }) {
                    Icon(if (hide) Icons.Default.VisibilityOff else Icons.Default.Visibility, tr("Hide amounts", "اخفي المبالغ"))
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (snap.toReview.isNotEmpty()) item(key = "decide") { DecideCard(snap) }

            if (snap.accounts.isEmpty()) item(key = "empty") {
                SectionCard(tr("No accounts yet", "لسه مفيش حسابات")) {
                    Text(tr("Import your bank SMS from Settings, or add an account manually.", "استورد رسايل البنك من الإعدادات، أو ضيف حساب بإيدك."))
                }
            } else {
                item(key = "today") { TodayCard(snap) }
                item(key = "canSpend") {
                    CollapsibleCard(tr("You can spend", "معاك تصرف"), key = "canSpend", help = "canSpend") { SafeDetails(snap) }
                }
                item(key = "netWorth") {
                    CollapsibleCard(tr("Net worth", "صافي الثروة"), key = "netWorth", help = "netWorth") {
                        Text(money(snap.netWorth, currency = "EGP"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = amountColor(snap.netWorth))
                        Spacer(Modifier.height(6.dp))
                        LabelValue(tr("Assets", "الأصول"), money(snap.assets), amountColor(1.0))
                        LabelValue(tr("Debts", "الديون"), money(snap.liabilities), amountColor(-1.0))
                        NetWorthToggles(snap)
                    }
                }
                item(key = "accounts") { AccountsCard(snap) }
            }

            item(key = "prices") { PricesLine(snap.accounts.any { it.account.isHolding }) }

            val dues = snap.statements.groupBy { it.accountId }.mapNotNull { (_, l) -> l.maxByOrNull { it.time } }
                .filter { System.currentTimeMillis() - it.time < 40L * 86_400_000 }
            if (dues.isNotEmpty()) item(key = "dues") {
                SectionCard(tr("Credit card statements", "كشوف البطاقة")) {
                    dues.forEach { s ->
                        val acc = snap.accountById[s.accountId]?.account?.name ?: ""
                        Text("💳 $acc: ${money(s.amount)} — " + tr("min", "الحد الأدنى") + " ${money(s.minDue ?: 0.0)} " + tr("before", "قبل") + " ${s.dueDate ?: ""}")
                    }
                }
            }

            item(key = "month") { MonthCard(snap) }

            item(key = "add") {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                    FilledTonalButton(onClick = { nav.navigate("account/0") }) {
                        Icon(Icons.Default.Add, null); Text(tr(" Add account / asset / debt", " ضيف حساب / أصل / دين"))
                    }
                }
            }
        }
    }
}

/** "Needs your decision (n)": the first few items; the rest behind "show all". Phase 3 replaces the dialogs. */
@Composable
private fun DecideCard(snap: Snapshot) {
    val review = snap.toReview
    var all by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<TxEntity?>(null) }
    SectionCard(tr("Needs your decision (${review.size})", "محتاج قرارك (${review.size})"), help = "decide") {
        (if (all) review else review.take(3)).forEach { t ->
            val income = kindOf(t).direction > 0
            ClickRow(t.party ?: "",
                (if (income) tr("How much to save?", "هتحوش كام؟") else tr("Really spending?", "صرف فعلاً؟")) + " · " + dateFmt(t.time, "dd MMM HH:mm"),
                (if (income) "+" else "") + money(t.amount) + if (t.currency != "EGP") " ${t.currency}" else "",
                amountColor(if (income) 1.0 else -1.0), leading = if (income) "💰" else "❔") { open = t }
        }
        if (review.size > 3) TextButton(onClick = { all = !all }) {
            Text(if (all) tr("Show less", "اعرض أقل") else tr("Show all (${review.size})", "اعرض الكل (${review.size})"))
        }
    }
    open?.let { t ->
        if (kindOf(t).direction > 0) SaveDialog(t, snap) { open = null } else ReviewDialog(t) { open = null }
    }
}

/** The one big number: what you can spend today, with one line about the days after. */
@Composable
private fun TodayCard(snap: Snapshot) {
    val d = snap.daily()
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("You can spend today", "تقدر تصرف النهارده"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoTip("today", title = tr("You can spend today", "تقدر تصرف النهارده"))
        }
        Text(money(maxOf(0.0, d.allowance), decimals = false, currency = "EGP"), style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold, color = amountColor(d.allowance))
        if (d.allowance > 0) ProgressLine((d.spentToday / d.allowance).toFloat(), over = d.spentToday > d.allowance)
        val parts = buildList {
            if (d.daysLeft > 1) add(tr("From tomorrow ", "من بكره ") + money(maxOf(0.0, d.fromTomorrow), decimals = false) + tr("/day", "/يوم"))
            add(tr("Until salary ", "لحد المرتب ") + money(d.safeNow, decimals = false))
            add(tr("${d.daysLeft} days", "${d.daysLeft} يوم"))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false))
            if (d.daysLeft > 1) InfoTip("fromTomorrow", title = tr("From tomorrow", "من بكره"))
        }
    }
}

/** Short list: name + amount; tap opens the account. */
@Composable
private fun AccountsCard(snap: Snapshot) {
    val nav = LocalNav.current
    val list = snap.accounts.filter { !it.account.archived }
        .sortedBy { it.account.accountType.ordinal }
    SectionCard(tr("My accounts", "حساباتي")) {
        list.forEach { av ->
            val a = av.account
            val hasGoals = snap.reserved(a.id) > 0
            val shown = if (hasGoals) snap.free(av) else av.balanceEgp
            // Same rule as the old home: debts shown as positive, free balance after goals keeps its sign
            val amountText = if (hasGoals && shown < 0) "−" + money(abs(shown), decimals = false) else money(abs(shown), decimals = false)
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate("account/${a.id}") }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.accountType.emoji + "  " + a.name, Modifier.weight(1f), maxLines = 1)
                Text(amountText, color = amountColor(shown), fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** One small line (gold 21 · USD · last update); tap opens the full prices card. Refreshes when older than an hour. */
@Composable
private fun PricesLine(hasHoldings: Boolean) {
    val repo = LocalRepo.current
    val prefs = repo.prefs
    var open by remember { mutableStateOf(!prefs.isCollapsed("prices")) }
    if (open) {
        PricesCard(hasHoldings)
        TextButton(onClick = { open = false; prefs.setCollapsed("prices", true) }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(tr("Hide prices", "اقفل الأسعار"))
        }
        return
    }
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - prefs.pricesAt > 60 * 60_000L && (hasHoldings || prefs.autoUsd)) Prices.refreshAll(repo)
    }
    val parts = buildList {
        if (prefs.gold24Gram > 0) add(tr("Gold 21k ", "دهب ٢١ ") + fmt0(prefs.goldGram(21)))
        add(tr("USD ", "دولار ") + fmt(prefs.usdRate))
        if (prefs.pricesAt > 0) add(dateFmt(prefs.pricesAt, "dd MMM HH:mm"))
    }
    Text(
        "📈 " + parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (prefs.pricesError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { open = true; prefs.setCollapsed("prices", false) }.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** "This month" from the old home, folded. */
@Composable
private fun MonthCard(snap: Snapshot) {
    val (from, to) = monthRange()
    val month = snap.totals(from, to)
    val pct = LocalRepo.current.prefs.savingsPercent
    CollapsibleCard(tr("This month", "الشهر ده") + " · " + periodLabel(from, to), key = "month", help = "salaryDay") {
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
