package com.moneyfen.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyfen.app.data.AccountType
import com.moneyfen.app.data.Repository
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.kindOf
import com.moneyfen.app.data.monthRange
import kotlin.math.abs

/** Every money movement of one account with the balance right after it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountHistoryScreen(snap: Snapshot, accountId: Long) {
    val nav = LocalNav.current
    val av = snap.accountById[accountId]
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var help by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(tr("History", "السجل") + (av?.let { " · " + it.account.name } ?: "")) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IconButton(onClick = { nav.navigate("account/$accountId") }) { Icon(Icons.Default.Edit, null) } })
        if (av == null) return
        val a = av.account
        if (a.isHolding) {
            Text(tr("Gold / stocks / funds have no money movements; their value follows the price.",
                "الدهب والأسهم والصناديق مفيهاش حركة فلوس؛ قيمتهم بتمشي مع السعر."), Modifier.padding(16.dp)); return
        }
        val isCard = a.accountType == AccountType.CREDIT_CARD
        val all = remember(snap, accountId) { Repository.history(a, snap.txs, snap.usdRate) }
        val (from, to) = monthRange(offset)
        val inPeriod = if (allTime) all else all.filter { it.second.time in from until to }
        val opening = if (allTime) null else all.lastOrNull { it.second.time < from }?.second?.balance
        // credit cards: show debt as a positive number
        fun shown(v: Double) = if (isCard) -v else v
        val label = if (isCard) tr("Debt", "المديونية") else tr("Balance", "الرصيد")

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(allTime, { allTime = !allTime }, label = { Text(tr("All time", "كل الوقت")) })
                    if (!allTime) {
                        IconButton(onClick = { offset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                        Text(periodLabel(from, to), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { if (offset < 0) offset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                    }
                }
            }
            item {
                val ins = inPeriod.mapNotNull { it.first }.filter { kindOf(it).direction > 0 }.sumOf { snap.toEgp(it.amount, it.currency) }
                val outs = inPeriod.mapNotNull { it.first }.filter { kindOf(it).direction < 0 }.sumOf { snap.toEgp(it.amount, it.currency) }
                val gaps = inPeriod.sumOf { it.second.gap }
                SectionCard(label + tr(" now: ", " دلوقتي: ") + money(abs(av.balance))) {
                    if (opening != null) LabelValue(tr("At the start", "في الأول"), money(shown(opening)))
                    LabelValue(tr("Money in", "دخل للحساب"), "+" + money(ins), amountColor(1.0))
                    LabelValue(tr("Money out", "خرج من الحساب"), "−" + money(outs), amountColor(-1.0))
                    if (abs(gaps) > 0.009) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Bank differences", "فروق البنك"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { help = "gap" }) { Icon(Icons.Outlined.Info, null) }
                        Text(signedText(gaps), color = amountColor(gaps), fontWeight = FontWeight.Medium)
                    }
                    inPeriod.lastOrNull()?.let { LabelValue(tr("At the end", "في الآخر"), money(shown(it.second.balance))) }
                    if (inPeriod.size >= 2) BalanceChart(inPeriod.map { shown(it.second.balance) })
                }
            }
            if (inPeriod.isEmpty()) item { Text(tr("No movements in this period", "مفيش حركة في الفترة دي"), Modifier.padding(16.dp)) }
            var lastDay = ""
            inPeriod.asReversed().forEachIndexed { i, (t, p) ->
                val d = dateFmt(p.time, "EEE dd MMM yyyy")
                if (d != lastDay) {
                    lastDay = d
                    item(key = "d$i") {
                        Text(d, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
                item(key = "r$i") { HistoryRow(snap, t, p, isCard, onHelp = { help = it }) { t?.let { nav.navigate("tx/${it.id}") } } }
            }
        }
    }

    help?.let { k ->
        AlertDialog(
            onDismissRequest = { help = null },
            title = { Text(if (k == "gap") tr("Bank difference", "فرق البنك") else tr("Confirmed balance", "رصيد مؤكد")) },
            text = { Text(if (k == "gap") tr(
                "The bank's message said a different balance than the app calculated. Usually fees, interest, or a movement that didn't send an SMS. The bank's number is used from then on.",
                "رسالة البنك قالت رصيد مختلف عن اللي البرنامج حسبه. غالباً رسوم أو فوايد أو حركة مجتلهاش رسالة. البرنامج بيعتمد رقم البنك من ساعتها.")
                else tr("✓ means this balance came from the bank's SMS (or you set it by hand), not calculated.",
                "✓ معناها إن الرصيد ده جاي من رسالة البنك (أو انت عدلته بإيدك)، مش محسوب.")) },
            confirmButton = { TextButton(onClick = { help = null }) { Text(tr("OK", "تمام")) } },
        )
    }
}

@Composable
private fun HistoryRow(snap: Snapshot, t: com.moneyfen.app.data.TxEntity?, p: com.moneyfen.core.BalancePoint, isCard: Boolean,
                       onHelp: (String) -> Unit, onClick: () -> Unit) {
    val bal = if (isCard) -p.balance else p.balance
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (t == null) {
                Text("✏️ " + tr("Balance set by hand", "تعديل يدوي للرصيد"), Modifier.weight(1f))
            } else {
                val k = kindOf(t)
                val c = snap.categoryOf(t)
                Text((c?.emoji ?: "•") + " " + (t.party ?: kindLabel(k)), Modifier.weight(1f), maxLines = 1)
                val signed = t.amount * k.direction
                Text((if (signed > 0) "+" else if (signed < 0) "−" else "") + money(abs(t.amount)) + if (t.currency != "EGP") " ${t.currency}" else "",
                    color = amountColor(signed), fontWeight = FontWeight.Medium)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dateFmt(p.time, "HH:mm") + (t?.note?.let { " · $it" } ?: ""), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((if (isCard) tr("debt ", "مديونية ") else tr("balance ", "الرصيد ")) + money(bal) + if (p.confirmed) " ✓" else "",
                style = MaterialTheme.typography.bodySmall, fontWeight = if (p.confirmed) FontWeight.SemiBold else FontWeight.Normal,
                modifier = if (p.confirmed) Modifier.clickable { onHelp("ok") } else Modifier)
        }
        if (abs(p.gap) > 0.009) Text(
            "⚠️ " + tr("Bank difference ", "فرق البنك ") + signedText(if (isCard) -p.gap else p.gap) + tr(" (fees / unrecorded) ⓘ", " (رسوم / حركة مش متسجلة) ⓘ"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clickable { onHelp("gap") })
    }
    HorizontalDivider()
}

/** Simple line chart of the balance across the period. */
@Composable
private fun BalanceChart(values: List<Double>) {
    val line = MaterialTheme.colorScheme.primary
    val zero = MaterialTheme.colorScheme.outline
    val min = values.min(); val max = values.max()
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(tr("max ", "أعلى ") + fmt0(max), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text(tr("min ", "أقل ") + fmt0(min), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 4.dp)) {
            val range = (max - min).takeIf { it > 0 } ?: 1.0
            fun y(v: Double) = (size.height * (1 - (v - min) / range)).toFloat()
            if (min < 0 && max > 0) drawLine(zero, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1f)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = if (values.size == 1) 0f else size.width * i / (values.size - 1)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, line, style = Stroke(width = 4f))
        }
    }
}

