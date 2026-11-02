package com.moneyfen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.moneyfen.app.data.CategoryType
import com.moneyfen.app.data.Repository
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.monthRange
import kotlinx.coroutines.launch

/** Pick which senders are real income (salary is always income). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeSourcesScreen(snap: Snapshot) {
    val repo = LocalRepo.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Repository.Source?>(null) }
    val sources = remember(snap) { repo.incomeSources(snap) }
    val incomeCats = snap.categories.filter { it.categoryType == CategoryType.INCOME && it.key != "salary" }
    fun isIncome(s: Repository.Source) = s.rule?.categoryId?.let { snap.categoryById[it]?.categoryType == CategoryType.INCOME } == true
    val (from, to) = monthRange()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(tr("Income sources", "مصادر الدخل")) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr(
                        "Only what you tick counts as income (in analytics and the savings plan). Salary always counts. Other transfers you receive are just money moving, not income.",
                        "اللي تعلّم عليه بس هو اللي يتحسب دخل (في التحليل وخطة الادخار). المرتب دايماً دخل. أي تحويل تاني بيوصلك بيتحسب فلوس داخلة بس مش دخل.",
                    ), style = MaterialTheme.typography.bodySmall)
                    val salary = snap.txs.filter { it.kind == "SALARY" && it.time in from until to }.sumOf { it.amount }
                    LabelValue("💼 " + tr("Salary (automatic)", "المرتب (تلقائي)"), money(salary))
                    LabelValue(tr("Income this month", "الدخل الشهر ده"), money(snap.totals(from, to).income))
                    OutlinedTextField(query, { query = it }, placeholder = { Text(tr("Search name…", "دوّر بالاسم…")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            val list = sources.filter { query.isBlank() || it.party.contains(query, true) }
            val (chosen, others) = list.partition { isIncome(it) }
            if (chosen.isNotEmpty()) item { Header(tr("✅ Income", "✅ دخل")) }
            items(chosen, key = { "c_" + it.party }) { s -> SourceRow(s, true, snap) { editing = s } }
            item { Header(tr("Other people who sent money", "ناس تانية بعتتلك فلوس")) }
            items(others, key = { "o_" + it.party }) { s -> SourceRow(s, false, snap) { editing = s } }
        }
    }

    editing?.let { s ->
        var income by remember(s) { mutableStateOf(isIncome(s)) }
        var cat by remember(s) { mutableStateOf(s.rule?.categoryId?.takeIf { isIncome(s) } ?: incomeCats.firstOrNull { it.key == "freelance" }?.id ?: incomeCats.firstOrNull()?.id) }
        var save by remember(s) { mutableStateOf(s.rule?.savePercent?.let(::fmtPlain) ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(s.party) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${s.count}× · " + money(s.total) + tr(" · last ", " · آخر مرة ") + dateFmt(s.last), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("This is income", "ده دخل"), Modifier.weight(1f)); Switch(income, { income = it })
                    }
                    if (income) Picker(tr("Income type", "نوع الدخل"), incomeCats, incomeCats.firstOrNull { it.id == cat }, { catName(it) }, { cat = it.id })
                    NumberField(tr("When money arrives, ask to save % (empty = don't ask)", "لما تيجي فلوس منه اسألني أحوش كام % (فاضي = متسألش)"), save, { save = it })
                    Text(tr("Applies to old and new transfers from this name.", "بيتطبق على التحويلات القديمة والجديدة من الاسم ده."), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch { repo.setSource(s.party, if (income) cat else null, parseNum(save)) }
                editing = null
            }) { Text(tr("Save", "حفظ")) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(tr("Cancel", "إلغاء")) } },
        )
    }
}

@Composable
private fun Header(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

@Composable
private fun SourceRow(s: Repository.Source, income: Boolean, snap: Snapshot, onClick: () -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val cat = s.rule?.categoryId?.let { snap.categoryById[it] }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Checkbox(income, { on ->
            val freelance = snap.categories.firstOrNull { it.key == "freelance" }?.id
                ?: snap.categories.firstOrNull { it.categoryType == CategoryType.INCOME }?.id
            scope.launch { repo.setSource(s.party, if (on) freelance else null, s.rule?.savePercent) }
        })
        Column(Modifier.weight(1f)) {
            ClickRow(s.party,
                "${s.count}× · " + dateFmt(s.last, "MMM yyyy") + (if (income && cat != null) " · " + catName(cat) else "") +
                    (s.rule?.savePercent?.let { " · 💰 ${fmtPlain(it)}%" } ?: ""),
                money(s.total, decimals = false), onClick = onClick)
        }
        Spacer(Modifier.width(4.dp))
    }
    HorizontalDivider()
}

