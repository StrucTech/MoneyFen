package com.moneyfen.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneyfen.app.data.CategoryType
import com.moneyfen.app.data.Snapshot
import com.moneyfen.app.data.kindOf
import com.moneyfen.app.data.monthRange

val ChartColors = listOf(
    Color(0xFF2A9D8F), Color(0xFFE76F51), Color(0xFFE9C46A), Color(0xFF264653), Color(0xFFF4A261),
    Color(0xFF8E7DBE), Color(0xFF4EA8DE), Color(0xFFD62828), Color(0xFF6A994E), Color(0xFFB5838D),
    Color(0xFF3D5A80), Color(0xFF9C6644),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(snap: Snapshot) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val (from, to) = monthRange(offset)
    val totals = snap.totals(from, to)
    val byCat = totals.expenseByCategory.filter { it.value > 0.5 }.toList().sortedByDescending { it.second }
    val total = byCat.sumOf { it.second }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(tr("Analytics", "التحليل")) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { offset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
            Text(dateFmt(from, "MMMM yyyy"), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (offset < 0) offset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                SectionCard {
                    LabelValue(tr("Income", "الدخل"), money(totals.income), amountColor(1.0))
                    LabelValue(tr("Spent", "المصروف"), money(totals.expense), amountColor(-1.0))
                    LabelValue(tr("Saved", "اتحوش"), money(totals.saved), amountColor(totals.saved))
                    if (totals.income > 0) LabelValue(tr("Savings rate", "نسبة الادخار"), "${(totals.saved / totals.income * 100).toInt()}%")
                }
            }
            item {
                SectionCard(tr("Spending by category", "المصروف حسب التصنيف")) {
                    if (byCat.isEmpty()) Text(tr("No spending this month", "مفيش مصروف الشهر ده"))
                    else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Donut(byCat.map { it.second }, Modifier.size(180.dp))
                            Text(money(total, decimals = false), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(10.dp))
                        byCat.forEachIndexed { i, (cid, v) ->
                            val c = cid?.let { snap.categoryById[it] }
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(ChartColors[i % ChartColors.size], CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(catName(c), Modifier.weight(1f))
                                Text("${(v / total * 100).toInt()}%  ", style = MaterialTheme.typography.bodySmall)
                                Text(money(v, decimals = false), fontWeight = FontWeight.Medium)
                            }
                            c?.monthlyBudget?.takeIf { it > 0 }?.let { b -> ProgressLine((v / b).toFloat(), over = v > b) }
                        }
                    }
                }
            }
            item {
                val months = (-5..0).map { o ->
                    val (f, t) = monthRange(offset + o)
                    Triple(dateFmt(f, "MMM"), snap.totals(f, t).income, snap.totals(f, t).expense)
                }
                SectionCard(tr("Last 6 months", "آخر ٦ شهور")) {
                    Bars(months)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legend(ChartColors[0], tr("Income", "الدخل")); Legend(ChartColors[1], tr("Spent", "المصروف"))
                    }
                }
            }
            item {
                val merchants = snap.txs.asSequence()
                    .filter { it.time in from until to && !it.excluded && kindOf(it).direction < 0 && snap.categoryOf(it)?.categoryType != CategoryType.TRANSFER }
                    .groupBy { it.party ?: "?" }
                    .map { (p, l) -> Triple(p, l.size, l.sumOf { snap.toEgp(it.amount, it.currency) }) }
                    .sortedByDescending { it.third }.take(10).toList()
                SectionCard(tr("Top places", "أكتر أماكن صرفت فيها")) {
                    merchants.forEach { (p, n, v) -> LabelValue("$p  ×$n", money(v, decimals = false)) }
                }
            }
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(c, CircleShape)); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Donut(values: List<Double>, modifier: Modifier) {
    val sum = values.sum().takeIf { it > 0 } ?: 1.0
    Canvas(modifier) {
        val stroke = size.minDimension * 0.18f
        val d = size.minDimension - stroke
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = (v / sum * 360).toFloat()
            drawArc(ChartColors[i % ChartColors.size], start, sweep - 0.8f, false,
                topLeft = Offset((size.width - d) / 2, (size.height - d) / 2), size = Size(d, d), style = Stroke(stroke))
            start += sweep
        }
    }
}

@Composable
fun Bars(data: List<Triple<String, Double, Double>>) {
    val max = data.maxOfOrNull { maxOf(it.second, it.third) }?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        data.forEach { (label, inc, exp) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.height(130.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(12.dp).fillMaxHeightFraction((inc / max).toFloat()).background(ChartColors[0]))
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.width(12.dp).fillMaxHeightFraction((exp / max).toFloat()).background(ChartColors[1]))
                }
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun Modifier.fillMaxHeightFraction(f: Float): Modifier =
    this.fillMaxHeight(f.coerceIn(0.005f, 1f))
