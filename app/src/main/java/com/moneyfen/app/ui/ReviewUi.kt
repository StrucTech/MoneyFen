package com.moneyfen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyfen.app.Notifier
import com.moneyfen.app.data.AccountView
import com.moneyfen.app.data.Prices
import com.moneyfen.app.data.Repository
import com.moneyfen.app.data.TxEntity
import kotlinx.coroutines.launch

/** "How much of this charge was really my spending?" */
@Composable
fun ReviewDialog(t: TxEntity, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mine by remember(t.id) { mutableStateOf(fmtPlain(t.personalAmount ?: t.amount)) }
    var dest by remember(t.id) { mutableStateOf(runCatching { Repository.Dest.valueOf(repo.prefs.reviewDest) }.getOrDefault(Repository.Dest.CASH)) }
    val mineV = (parseNum(mine) ?: 0.0).coerceIn(0.0, t.amount)
    val rest = t.amount - mineV

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t.party ?: ""}  ${fmt(t.amount)} ${t.currency}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("How much of it was really your spending?", "كام منهم كان صرف ليك فعلاً؟"))
                NumberField(tr("My spending", "صرفي أنا"), mine, { mine = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { mine = fmtPlain(t.amount) }, label = { Text(tr("All", "كله")) })
                    AssistChip(onClick = { mine = fmtPlain(t.amount / 2) }, label = { Text(tr("Half", "النص")) })
                    AssistChip(onClick = { mine = "0" }, label = { Text(tr("None", "ولا حاجة")) })
                }
                if (rest > 0.004) {
                    Text(tr("Not spending: ", "مش صرف: ") + fmt(rest) + tr(" — still counted in the card debt.", " — بس لسه محسوب على الكارت."),
                        style = MaterialTheme.typography.bodySmall)
                    Text(tr("Where did the rest go?", "الباقي راح فين؟"), fontWeight = FontWeight.SemiBold)
                    val opts = listOf(
                        Repository.Dest.CASH to tr("I got cash", "خدته كاش"),
                        Repository.Dest.LENT to tr("Owed to me", "ليا عند حد"),
                        Repository.Dest.NONE to tr("Nothing", "ولا حاجة"),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        opts.forEachIndexed { i, (d, label) ->
                            SegmentedButton(dest == d, { dest = d }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(label, maxLines = 1) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { repo.resolveReview(t.id, mineV, dest); Notifier.cancel(ctx, t.id) }
                onDismiss()
            }) { Text(tr("Save", "حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Later", "بعدين")) } },
    )
}

/** Gold / USD prices with refresh. Refreshes automatically when older than an hour. */
@Composable
fun PricesCard(hasHoldings: Boolean) {
    val repo = LocalRepo.current
    val prefs = repo.prefs
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun refresh() { busy = true; scope.launch { Prices.refreshAll(repo); busy = false } }
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - prefs.pricesAt > 60 * 60_000L && (hasHoldings || prefs.autoUsd)) refresh()
    }
    SectionCard(tr("Live prices", "الأسعار دلوقتي"), action = {
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, null) }
    }) {
        if (prefs.gold24Gram > 0) {
            LabelValue(tr("Gold 24k / g", "دهب ٢٤ / جرام"), fmt0(prefs.goldGram(24)))
            LabelValue(tr("Gold 21k / g", "دهب ٢١ / جرام"), fmt0(prefs.goldGram(21)))
            LabelValue(tr("Gold 18k / g", "دهب ١٨ / جرام"), fmt0(prefs.goldGram(18)))
        }
        LabelValue(tr("USD", "الدولار"), fmt(prefs.usdRate))
        if (prefs.pricesAt > 0) Text(tr("Updated ", "اتحدث ") + dateFmt(prefs.pricesAt, "dd MMM HH:mm"), style = MaterialTheme.typography.bodySmall)
        if (prefs.pricesError.isNotBlank()) Text(prefs.pricesError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}


/** Profit / loss line for gold and stocks. */
@Composable
fun holdingSummary(av: AccountView): String? {
    val a = av.account
    if (!a.isHolding) return null
    val qty = a.quantity ?: return null
    val cost = a.costBasis
    val unitTxt = if (a.accountType == com.moneyfen.app.data.AccountType.GOLD) tr("g", "جم") + " ${a.karat ?: 21}k" else tr("shares", "سهم")
    val base = "${fmtPlain(qty)} $unitTxt"
    if (cost == null || cost <= 0) return base
    val pl = av.balance - cost
    val pct = pl / cost * 100
    return base + " · " + (if (pl >= 0) "▲ " else "▼ ") + money(kotlin.math.abs(pl), decimals = false) + " (${"%.1f".format(pct)}%)"
}

/** "How much of this income do you want to save, and where?" */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SaveDialog(t: TxEntity, snap: com.moneyfen.app.data.Snapshot, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val total = snap.toEgp(t.amount, t.currency)
    val already = snap.moves.filter { it.txId == t.id }
    var amount by remember(t.id) { mutableStateOf(if (already.isNotEmpty()) fmtPlain(already.sumOf { it.amount }) else "") }
    var suggested by remember(t.id) { mutableStateOf<Double?>(null) }
    LaunchedEffect(t.id) {
        val s = repo.suggestedSave(t)
        suggested = s
        if (amount.isEmpty()) amount = fmtPlain(Math.round(s * 100) / 100.0)
    }
    val pots = snap.goals
    var goalId by remember(t.id) {
        mutableStateOf(already.firstOrNull()?.goalId ?: repo.prefs.lastSaveGoal.takeIf { id -> pots.any { it.id == id } })
    }
    val pct = repo.prefs.savingsPercent
    val fixed = repo.prefs.salaryFixed

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💰 ${t.party ?: ""}  +${fmt(total)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("How much do you want to save from it?", "هتحوش كام منهم؟"))
                NumberField(tr("Amount to save", "المبلغ اللي هيتحوش"), amount, { amount = it })
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { amount = fmtPlain(total) }, label = { Text(tr("All", "الكل")) })
                    suggested?.let { sg -> AssistChip(onClick = { amount = fmtPlain(Math.round(sg * 100) / 100.0) }, label = { Text(tr("Suggested ", "المقترح ") + fmt0(sg)) }) }
                    AssistChip(onClick = { amount = fmtPlain(Math.round(total * pct) / 100.0) }, label = { Text("$pct%") })
                    if (fixed > 0) AssistChip(onClick = { amount = fmtPlain(minOf(fixed, total)) }, label = { Text(fmt0(fixed)) })
                    AssistChip(onClick = { amount = "0" }, label = { Text(tr("Nothing", "ولا حاجة")) })
                }
                Picker(tr("Put it in", "حطه في"), listOf<com.moneyfen.app.data.GoalEntity?>(null) + pots, pots.firstOrNull { it.id == goalId },
                    { g -> g?.let { "${it.emoji} ${it.name}" } ?: tr("🐷 Savings pot (auto)", "🐷 التحويش (تلقائي)") }, { goalId = it?.id })
                Text(tr("It stays in the account but is reserved, so it's removed from \"safe to spend\".",
                    "الفلوس بتفضل في الحساب بس بتتحجز، فبتتشال من \"الآمن تصرفه\"."), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = (parseNum(amount) ?: 0.0).coerceIn(0.0, total)
                scope.launch { repo.saveFromIncome(t.id, v, goalId); Notifier.cancel(ctx, t.id) }
                onDismiss()
            }) { Text(tr("Save", "حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Later", "بعدين")) } },
    )
}

/** Safe to spend = free money in accounts (after goals) − credit card debt. */
@Composable
fun SafeCard(snap: com.moneyfen.app.data.Snapshot) {
    val s = snap.safeToSpend()
    SectionCard(tr("✅ Safe to spend", "✅ الآمن تصرفه")) {
        Text(money(s.safe, currency = "EGP"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = amountColor(s.safe))
        LabelValue(tr("In accounts", "في الحسابات"), money(s.cash))
        if (s.reserved > 0) LabelValue(tr("− Reserved for goals", "− محجوز للأهداف"), money(s.reserved))
        if (s.cardDebt > 0) LabelValue(tr("− Credit card debt", "− مديونية الكريديت"), money(s.cardDebt))
        if (s.safe < 0) Text(tr("You're spending money you don't have — pay the card from savings or cut spending.",
            "انت بتصرف فلوس مش معاك — الكارت أكتر من المتاح بعد التحويش."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        val pending = snap.incomeToReview.size
        if (pending > 0) Text(tr("$pending income(s) waiting: decide how much to save", "فيه $pending دخل مستني تقرر هتحوش منه كام"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}
