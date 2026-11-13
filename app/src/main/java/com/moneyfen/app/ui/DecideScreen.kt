package com.moneyfen.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyfen.app.Notifier
import com.moneyfen.app.app
import com.moneyfen.app.data.CategoryType
import com.moneyfen.app.data.GoalEntity
import com.moneyfen.app.data.REIMBURSED
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.TxEntity
import com.moneyfen.app.data.kindOf
import com.moneyfen.core.AutoAction
import kotlinx.coroutines.launch

/** Every transaction that needs a word from the user, one card after another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecideScreen(snap: Snapshot) {
    val nav = LocalNav.current
    val review = snap.toReview.sortedByDescending { it.time }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Needs your decision (${review.size})", "محتاج قرارك (${review.size})"))
                    InfoTip("decide", title = tr("Needs your decision", "محتاج قرارك"))
                }
            },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
        )
        if (review.isEmpty()) Text(tr("Nothing waiting — all good 👌", "مفيش حاجة مستنية — كله تمام 👌"), Modifier.padding(24.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(review, key = { it.id }) { t -> SectionCard { DecideItem(t, snap) } }
        }
    }
}

private enum class Step { CHOOSE, LENT, SAVE }

/** One decision: what the transaction is, big buttons, and "always do this with <name>". */
@Composable
fun DecideItem(t: TxEntity, snap: Snapshot) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val incoming = kindOf(t).direction > 0
    val isIncome = snap.categoryOf(t)?.categoryType == CategoryType.INCOME
    var step by remember(t.id) { mutableStateOf(if (incoming && isIncome) Step.SAVE else Step.CHOOSE) }
    var always by remember(t.id) { mutableStateOf(false) }
    var split by remember(t.id) { mutableStateOf(false) }
    var details by remember(t.id) { mutableStateOf(false) }
    val party = t.party?.trim().orEmpty()

    fun decide(action: AutoAction, lentId: Long? = null, newName: String? = null) {
        ctx.app.appScope.launch {
            repo.decide(t.id, action, always, lentId, newName)
            if (action != AutoAction.INCOME) Notifier.cancel(ctx, t.id)
        }
    }

    // What it is
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(party.ifBlank { tr("(no name)", "(من غير اسم)") }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text((if (incoming) "+" else "−") + money(t.amount) + if (t.currency != "EGP") " ${t.currency}" else "",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = amountColor(if (incoming) 1.0 else -1.0))
    }
    Text((snap.accountById[t.accountId]?.account?.name ?: "") + " · " + dateFmt(t.time, "dd MMM HH:mm"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (step) {
            Step.CHOOSE -> {
                if (!incoming) {
                    ChoiceButton("🙋 " + tr("Spent for me", "صرف ليا"), "forMe") { decide(AutoAction.MINE) }
                    ChoiceButton("🤝 " + tr("For someone who'll pay me back", "لحد هيرجعهولي"), "forSomeone") { step = Step.LENT }
                    ChoiceButton("🔁 " + tr("Between my accounts", "بين حساباتي"), "betweenMine") { decide(AutoAction.INTERNAL) }
                    ChoiceButton("✂️ " + tr("Split…", "تقسيم…"), null, primary = false) { split = true }
                } else {
                    ChoiceButton("💰 " + tr("Income", "دخل"), "income") { step = Step.SAVE }
                    ChoiceButton("🤝 " + tr("Repays money owed to me", "سداد فلوس ليا"), "repayment") { step = Step.LENT }
                    ChoiceButton("📥 " + tr("Not income", "مش دخل"), "notIncome") { decide(AutoAction.NOT_INCOME) }
                }
            }
            Step.LENT -> {
                val lents = snap.owedToMe.map { it.account }
                val word = party.split(' ').firstOrNull { it.length >= 3 }
                var lentId by remember(t.id) {
                    mutableStateOf(lents.firstOrNull { word != null && it.name.contains(word, true) }?.id
                        ?: repo.prefs.lastLent.takeIf { id -> lents.any { it.id == id } } ?: lents.firstOrNull()?.id)
                }
                var newLent by remember(t.id) { mutableStateOf(if (lents.isEmpty()) party else "") }
                LentPicker(lents, lentId, { lentId = it }, newLent, { newLent = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { decide(AutoAction.REPAID, if (newLent.isBlank()) lentId else null, newLent.ifBlank { null }) }) { Text(tr("Done", "تمام")) }
                    TextButton(onClick = { step = Step.CHOOSE }) { Text(tr("Back", "رجوع")) }
                }
            }
            Step.SAVE -> SaveStep(t, snap, alreadyIncome = isIncome, always = { always },
                onBack = if (incoming) ({ step = Step.CHOOSE }) else null, onDetails = { details = true })
        }

        // Already income (salary, a known source): only the saving is asked, nothing to remember
        if (party.isNotBlank() && !(step == Step.SAVE && isIncome)) {
            Row(Modifier.fillMaxWidth().clickable { always = !always }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(always, { always = it })
                Text(tr("Always do this with ", "اعمل كده دايماً مع ") + party, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
                InfoTip("alwaysThis", title = tr("Always do this", "اعمل كده دايماً"))
            }
        }
    }
    if (split) ReviewDialog(t) { split = false }
    if (details) SaveDialog(t, snap) { details = false }
}

@Composable
private fun ChoiceButton(label: String, help: String?, primary: Boolean = true, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (primary) Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
        else OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
        InfoTip(help, title = label)
    }
}

/** Income: how much to save from it, in the same card. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SaveStep(t: TxEntity, snap: Snapshot, alreadyIncome: Boolean, always: () -> Boolean, onBack: (() -> Unit)?, onDetails: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val total = snap.toEgp(t.amount, t.currency)
    var amount by remember(t.id) { mutableStateOf("") }
    var suggested by remember(t.id) { mutableStateOf<Double?>(null) }
    LaunchedEffect(t.id) {
        val s = repo.suggestedSave(t)
        suggested = s
        if (amount.isEmpty()) amount = fmtPlain(Math.round(s * 100) / 100.0)
    }
    val pots = snap.goals
    var goalId by remember(t.id) { mutableStateOf(repo.prefs.lastSaveGoal.takeIf { id -> pots.any { it.id == id } }) }
    val repaid = snap.txs.any { it.linkedTxId == t.id && it.note == REIMBURSED }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("How much do you want to save from it?", "هتحوش كام منهم؟"), Modifier.weight(1f, fill = false))
        InfoTip("savePercent")
    }
    NumberField(tr("Amount to save", "المبلغ اللي هيتحوش"), amount, { amount = it })
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        suggested?.let { sg -> AssistChip(onClick = { amount = fmtPlain(Math.round(sg * 100) / 100.0) }, label = { Text(tr("Suggested ", "المقترح ") + fmt0(sg)) }) }
        AssistChip(onClick = { amount = fmtPlain(total) }, label = { Text(tr("All", "الكل")) })
        AssistChip(onClick = { amount = "0" }, label = { Text(tr("Nothing", "ولا حاجة")) })
    }
    Picker(tr("Put it in", "حطه في"), listOf<GoalEntity?>(null) + pots, pots.firstOrNull { it.id == goalId },
        { g -> g?.let { "${it.emoji} ${it.name}" } ?: tr("🐷 Savings pot (auto)", "🐷 التحويش (تلقائي)") }, { goalId = it?.id })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            val v = (parseNum(amount) ?: 0.0).coerceIn(0.0, total)
            val alw = always()
            ctx.app.appScope.launch {
                if (!alreadyIncome || alw) repo.decide(t.id, AutoAction.INCOME, alw)
                repo.saveFromIncome(t.id, v, goalId)
                Notifier.cancel(ctx, t.id)
            }
        }) { Text(tr("Save", "حفظ")) }
        // Partial repayment and the other details of the old dialog
        if (!repaid) TextButton(onClick = onDetails) { Text(tr("More…", "تفاصيل…")) }
        if (onBack != null) TextButton(onClick = onBack) { Text(tr("Not income?", "مش دخل؟")) }
    }
}

/** Short label of an "always" action, for the rules screen. */
@Composable
fun autoActionLabel(a: AutoAction): String = when (a) {
    AutoAction.MINE -> tr("spent for me", "صرف ليا")
    AutoAction.REPAID -> tr("will be repaid / repayment", "هيترد / سداد")
    AutoAction.INTERNAL -> tr("between my accounts", "بين حساباتي")
    AutoAction.INCOME -> tr("income", "دخل")
    AutoAction.NOT_INCOME -> tr("not income", "مش دخل")
}
