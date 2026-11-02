package com.moneyfen.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyfen.app.data.AccountEntity
import com.moneyfen.app.data.CategoryType
import com.moneyfen.app.data.GoalEntity
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.monthRange
import com.moneyfen.core.PlanMath
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(snap: Snapshot) {
    val repo = LocalRepo.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val (from, to) = monthRange()
    val month = snap.totals(from, to)
    var editBudgets by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf<Pair<GoalEntity, Int>?>(null) }
    var pct by remember { mutableFloatStateOf(repo.prefs.savingsPercent.toFloat()) }

    // Average of the last 3 complete months, for context
    val avg = (-3..-1).map { o -> monthRange(o).let { (f, t) -> snap.totals(f, t) } }
    val avgIncome = avg.map { it.income }.average()
    val avgExpense = avg.map { it.expense }.average()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(tr("Savings plan", "خطة الادخار")) })
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SectionCard(tr("Save a fixed % of income", "ادخر نسبة ثابتة من الدخل")) {
                    Text("${pct.toInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Slider(pct, { pct = it }, valueRange = 0f..80f, steps = 15, onValueChangeFinished = { repo.prefs.savingsPercent = pct.toInt() })
                    val target = month.income * pct / 100
                    ClickRow(tr("Income this month", "دخل الشهر ده"), tr("Choose income sources ›", "اختار مصادر الدخل ›"), money(month.income)) { nav.navigate("sources") }
                    LabelValue(tr("Should save", "المفروض تحوش"), money(target))
                    val setAside = snap.savedInto(from, to)
                    LabelValue(tr("Set aside in goals this month", "اتحجز في الأهداف الشهر ده"), money(setAside), amountColor(setAside - target))
                    LabelValue(tr("Income − spending", "الدخل − المصروف"), money(month.saved), amountColor(month.saved))
                    LabelValue(tr("Left to spend this month", "تقدر تصرف باقي الشهر"), money(month.income - target - month.expense), amountColor(month.income - target - month.expense))
                    if (target > 0) ProgressLine((setAside / target).toFloat())
                    Spacer(Modifier.height(6.dp))
                    Text(tr("Avg last 3 months: income ", "متوسط آخر ٣ شهور: دخل ") + money(avgIncome, false) +
                        tr(" · spent ", " · مصروف ") + money(avgExpense, false), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                SectionCard(tr("Goals", "الأهداف"), action = {
                    IconButton(onClick = { nav.navigate("goal/0") }) { Icon(Icons.Default.Add, null) }
                }) {
                    if (snap.goals.isEmpty()) Text(tr("Add a goal: amount + date, and see how much to save monthly.", "ضيف هدف: مبلغ + تاريخ، وهتعرف تحوش كام كل شهر."))
                    snap.goals.forEach { g ->
                        val current = goalCurrent(snap, g)
                        val pot = g.target <= 0
                        val monthly = if (pot) 0.0 else PlanMath.monthlyNeeded(g.target, current, System.currentTimeMillis(), g.deadline)
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            if (pot) ClickRow("${g.emoji} ${g.name}", tr("Open savings pot", "تحويش مفتوح"), money(current, false)) { nav.navigate("goal/${g.id}") }
                            else ClickRow("${g.emoji} ${g.name}", "${money(current, false)} / ${money(g.target, false)} · ${dateFmt(g.deadline)}",
                                if (current >= g.target) "✅" else "${(current / g.target * 100).toInt()}%") { nav.navigate("goal/${g.id}") }
                            if (!pot) ProgressLine((current / g.target).toFloat())
                            g.accountId?.let { aid -> snap.accountById[aid] }?.let { av ->
                                Text((if (g.wholeAccount) tr("Whole balance of ", "كل رصيد ") else tr("Reserved in ", "محجوز في ")) + av.account.name,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (!g.wholeAccount) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.OutlinedButton(onClick = { move = g to 1 }) { Text(tr("+ Add", "+ حط فيه")) }
                                androidx.compose.material3.OutlinedButton(onClick = { move = g to -1 }) { Text(tr("− Take out", "− خد منه")) }
                            }
                            if (!pot && current < g.target) Text(tr("Save ", "حوّش ") + money(monthly, false) + tr(" / month", " في الشهر") +
                                " (${PlanMath.monthsLeft(System.currentTimeMillis(), g.deadline)} " + tr("months", "شهر") + ")",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                val budgeted = snap.categories.filter { it.categoryType == CategoryType.EXPENSE && (it.monthlyBudget ?: 0.0) > 0 }
                SectionCard(tr("Monthly budgets", "ميزانية الشهر"), action = {
                    IconButton(onClick = { editBudgets = true }) { Icon(Icons.Default.Edit, null) }
                }) {
                    if (budgeted.isEmpty()) Text(tr("Set a monthly limit per category (tap ✎).", "حط حد شهري لكل تصنيف (اضغط ✎)."))
                    var totalB = 0.0; var totalS = 0.0
                    budgeted.forEach { c ->
                        val spent = month.expenseByCategory[c.id] ?: 0.0
                        val b = c.monthlyBudget ?: 0.0
                        totalB += b; totalS += spent
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row { Text(catName(c), Modifier.weight(1f)); Text("${money(spent, false)} / ${money(b, false)}", color = amountColor(b - spent)) }
                            ProgressLine((spent / b).toFloat(), over = spent > b)
                        }
                    }
                    if (budgeted.isNotEmpty()) LabelValue(tr("Total", "الإجمالي"), "${money(totalS, false)} / ${money(totalB, false)}", amountColor(totalB - totalS))
                }
            }
        }
    }

    move?.let { (g, sign) ->
        var amt by remember(g.id, sign) { mutableStateOf("") }
        val av = g.accountId?.let { snap.accountById[it] }
        val v = parseNum(amt) ?: 0.0
        AlertDialog(
            onDismissRequest = { move = null },
            title = { Text("${g.emoji} ${g.name}: " + if (sign > 0) tr("add", "حط فيه") else tr("take out", "خد منه")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(tr("Amount", "المبلغ"), amt, { amt = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (sign > 0 && av != null) androidx.compose.material3.AssistChip(onClick = { amt = fmtPlain(maxOf(0.0, snap.free(av))) },
                            label = { Text(tr("All free", "كل المتاح")) })
                        if (sign > 0) androidx.compose.material3.AssistChip(onClick = { amt = fmtPlain(maxOf(0.0, g.target - g.saved)) },
                            label = { Text(tr("What's left", "الباقي للهدف")) })
                        if (sign < 0) androidx.compose.material3.AssistChip(onClick = { amt = fmtPlain(g.saved) }, label = { Text(tr("All", "كله")) })
                    }
                    Text(tr("In goal: ", "في الهدف: ") + money(g.saved) + " → " + money(maxOf(0.0, g.saved + sign * v)))
                    if (av != null) {
                        val freeAfter = snap.free(av) - sign * v
                        Text(av.account.name + tr(" free to spend: ", " المتاح للصرف: ") + money(freeAfter), color = amountColor(freeAfter))
                        if (freeAfter < 0) Text(tr("More than the free balance!", "أكتر من المتاح في الحساب!"), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch { repo.moveGoal(g, sign * v) }
                move = null
            }) { Text(tr("Save", "حفظ")) } },
            dismissButton = { TextButton(onClick = { move = null }) { Text(tr("Cancel", "إلغاء")) } },
        )
    }

    if (editBudgets) {
        val cats = snap.categories.filter { it.categoryType == CategoryType.EXPENSE }
        val values = remember { mutableStateMapOf<Long, String>().apply { cats.forEach { put(it.id, it.monthlyBudget?.let(::fmtPlain) ?: "") } } }
        AlertDialog(
            onDismissRequest = { editBudgets = false },
            title = { Text(tr("Monthly budgets", "ميزانية الشهر")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { c ->
                        val avgSpent = (-3..-1).map { o -> monthRange(o).let { (f, t) -> snap.totals(f, t).expenseByCategory[c.id] ?: 0.0 } }.average()
                        NumberField(catName(c) + tr(" (avg ", " (متوسط ") + fmt0(avgSpent) + ")", values[c.id] ?: "", { values[c.id] = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { cats.forEach { c -> repo.saveCategory(c.copy(monthlyBudget = parseNum(values[c.id] ?: "")?.takeIf { it > 0 })) } }
                    editBudgets = false
                }) { Text(tr("Save", "حفظ")) }
            },
            dismissButton = { TextButton(onClick = { editBudgets = false }) { Text(tr("Cancel", "إلغاء")) } },
        )
    }
}

fun goalCurrent(snap: Snapshot, g: GoalEntity): Double = snap.goalCurrent(g)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(snap: Snapshot, id: Long) {
    val repo = LocalRepo.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val g0 = snap.goals.firstOrNull { it.id == id } ?: GoalEntity(name = "", target = 0.0, deadline = System.currentTimeMillis() + 365L * 86_400_000)
    var name by remember(id) { mutableStateOf(g0.name) }
    var emoji by remember(id) { mutableStateOf(g0.emoji) }
    var target by remember(id) { mutableStateOf(if (g0.target > 0) fmtPlain(g0.target) else "") }
    var deadline by remember(id) { mutableStateOf(g0.deadline) }
    var saved by remember(id) { mutableStateOf(fmtPlain(g0.saved)) }
    var account by remember(id) { mutableStateOf(g0.accountId) }
    var whole by remember(id) { mutableStateOf(g0.wholeAccount) }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), 
            title = { Text(if (id == 0L) tr("New goal", "هدف جديد") else g0.name) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { if (id != 0L) IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, null) } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField1(tr("Icon", "أيقونة"), emoji, { emoji = it.take(2) }, Modifier.weight(1f))
                TextField1(tr("Name", "الاسم"), name, { name = it }, Modifier.weight(4f))
            }
            NumberField(tr("Target amount (empty = open savings pot)", "المبلغ المطلوب (فاضي = تحويش مفتوح)"), target, { target = it })
            if ((parseNum(target) ?: 0.0) > 0) DateField(tr("By", "قبل"), if (deadline > 0) deadline else System.currentTimeMillis() + 365L * 86_400_000, { deadline = it })
            val accounts = snap.accounts.map { it.account }.filter { !it.accountType.liability }
            Picker(tr("Money is in account", "الفلوس في حساب"), listOf<AccountEntity?>(null) + accounts, accounts.firstOrNull { it.id == account },
                { it?.let { a -> "${a.accountType.emoji} ${a.name}" } ?: tr("— manual amount —", "— مبلغ يدوي —") }, { account = it?.id })
            val av = account?.let { snap.accountById[it] }
            if (av != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Whole account balance counts", "كل رصيد الحساب يتحسب للهدف"))
                        Text(tr("Off = reserve only the amount below from the account", "مقفول = احجز المبلغ اللي تحت بس من الحساب"), style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(whole, { whole = it })
                }
            }
            if (av == null || !whole) {
                NumberField(if (av != null) tr("Amount reserved from the account", "المبلغ المحجوز من الحساب") else tr("Saved so far", "حوشت لحد دلوقتي"), saved, { saved = it })
                if (av != null) {
                    val otherReserved = snap.reserved(av.account.id) - (if (g0.accountId == av.account.id && !g0.wholeAccount) g0.saved else 0.0)
                    val freeAfter = av.balance - otherReserved - (parseNum(saved) ?: 0.0)
                    Text(tr("Balance ", "الرصيد ") + money(av.balance) + tr(" · free to spend after this ", " · المتاح للصرف بعد كده ") + money(freeAfter),
                        color = amountColor(freeAfter), style = MaterialTheme.typography.bodySmall)
                }
            }
            val t = parseNum(target) ?: 0.0
            if (t > 0) {
                val cur = if (av != null && whole) av.balanceEgp else parseNum(saved) ?: 0.0
                Text(tr("Needed per month: ", "محتاج تحوش في الشهر: ") + money(PlanMath.monthlyNeeded(t, cur, System.currentTimeMillis(), deadline)),
                    fontWeight = FontWeight.SemiBold)
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val tv = parseNum(target) ?: 0.0
                scope.launch {
                    repo.saveGoal(g0.copy(name = name.ifBlank { "Goal" }, emoji = emoji.ifBlank { "🎯" }, target = tv,
                        deadline = if (tv > 0 && deadline <= 0) System.currentTimeMillis() + 365L * 86_400_000 else deadline,
                        accountId = account, saved = parseNum(saved) ?: 0.0, wholeAccount = account != null && whole))
                    nav.popBackStack()
                }
            }) { Text(tr("Save", "حفظ")) }
            val history = snap.moves.filter { it.goalId == id }
            if (history.isNotEmpty()) SectionCard(tr("History", "السجل")) {
                history.take(50).forEach { m ->
                    LabelValue(dateFmt(m.time, "dd MMM yyyy") + (m.note?.let { " · $it" } ?: ""), signedText(m.amount), amountColor(m.amount))
                }
            }
        }
    }
    if (confirm) ConfirmDialog(tr("Delete goal?", "تمسح الهدف؟"), g0.name, { scope.launch { repo.deleteGoal(g0); nav.popBackStack() } }, { confirm = false })
}
