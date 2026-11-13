package com.moneyfen.app.ui

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object L {
    fun isArabic(ctx: Context): Boolean {
        val app = AppCompatDelegate.getApplicationLocales()
        if (!app.isEmpty) return app[0]?.language == "ar"
        return ctx.resources.configuration.locales[0].language == "ar"
    }

    /** "" = follow system, "ar", "en" */
    fun current(): String = AppCompatDelegate.getApplicationLocales().let { if (it.isEmpty) "" else it[0]?.language ?: "" }

    fun set(lang: String) {
        AppCompatDelegate.setApplicationLocales(if (lang.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang))
    }
}

@Composable
fun isAr(): Boolean = LocalConfiguration.current.locales[0].language == "ar"

@Composable
fun tr(en: String, ar: String): String = if (isAr()) ar else en

private val moneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private val moneyFormat0 = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))

fun fmt(v: Double): String = moneyFormat.format(v)
fun fmt0(v: Double): String = moneyFormat0.format(v)

/** Money with optional privacy mask. */
@Composable
fun money(v: Double, decimals: Boolean = true, currency: String? = null): String {
    val hide = LocalHide.current
    val s = if (hide) "••••" else if (decimals) fmt(v) else fmt0(v)
    return if (currency != null) "$s $currency" else s
}

val LocalHide = androidx.compose.runtime.compositionLocalOf { false }

/** "October 2026" for calendar months, otherwise "27 Oct – 26 Nov 2026". */
fun periodLabel(from: Long, to: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = from }
    val calendar = c.get(java.util.Calendar.DAY_OF_MONTH) == 1 && c.get(java.util.Calendar.HOUR_OF_DAY) == 0 &&
        java.util.Calendar.getInstance().apply { timeInMillis = to }.get(java.util.Calendar.DAY_OF_MONTH) == 1
    return if (calendar) dateFmt(from, "MMMM yyyy") else dateFmt(from, "d MMM") + " – " + dateFmt(to - 1, "d MMM yyyy")
}

fun dateFmt(t: Long, pattern: String = "dd MMM yyyy"): String = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(t))

fun parseNum(s: String): Double? = s.replace(",", "").replace("٫", ".").trim()
    .map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("").toDoubleOrNull()

val Green = Color(0xFF2E7D32)
val Red = Color(0xFFC62828)

@Composable
fun amountColor(v: Double): Color = when {
    v > 0 -> if (isSystemInDarkTheme()) Color(0xFF81C784) else Green
    v < 0 -> if (isSystemInDarkTheme()) Color(0xFFE57373) else Red
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun MoneyFenTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF80CBC4), secondary = Color(0xFFFFCC80))
        else -> lightColorScheme(primary = Color(0xFF00695C), secondary = Color(0xFFEF6C00))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null, help: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(14.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                        InfoTip(help, title = title)
                    }
                    action?.invoke()
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

/**
 * Card whose body folds behind its title row (tap the row or the arrow). The folded state is kept in
 * prefs as collapsed_<[key]>, so it survives restarts. [summary] is shown next to the title while folded.
 */
@Composable
fun CollapsibleCard(
    title: String, key: String, help: String? = null, defaultCollapsed: Boolean = true,
    summary: String? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val prefs = LocalRepo.current.prefs
    var collapsed by remember(key) { mutableStateOf(prefs.isCollapsed(key, defaultCollapsed)) }
    fun toggle() { collapsed = !collapsed; prefs.setCollapsed(key, collapsed) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    InfoTip(help, title = title)
                }
                if (collapsed && summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    if (collapsed) tr("Show", "افتح") else tr("Hide", "اقفل"),
                    Modifier.padding(start = 4.dp),
                )
            }
            if (!collapsed) Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) { content() }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface, help: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
            InfoTip(help, title = label)
        }
        Text(value, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}

@Composable
fun TextField1(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), singleLine: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Picker(label: String, options: List<T>, selected: T?, text: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (selected != null) text(selected) else "", onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${dateFmt(value)}") }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        // keep the time of day of the original value
                        val tod = ((value % 86_400_000L) + 86_400_000L) % 86_400_000L
                        onChange(picked - (picked % 86_400_000L) + tod)
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Cancel", "إلغاء")) } },
        ) { DatePicker(state) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(tr("Yes", "أيوه")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "إلغاء")) } },
    )
}

@Composable
fun ClickRow(title: String, subtitle: String? = null, trailing: String? = null, trailingColor: Color = MaterialTheme.colorScheme.onSurface, leading: String? = null, help: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { Text(leading, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        InfoTip(help, title = title)
        if (trailing != null) Text(trailing, color = trailingColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, help: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false))
                InfoTip(help, title = title)
            }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun ProgressLine(fraction: Float, over: Boolean = false) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).padding(vertical = 1.dp),
        color = if (over) Red else MaterialTheme.colorScheme.primary,
    )
}

fun signedText(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + fmt(abs(v))

val AppArrangement = Arrangement.spacedBy(8.dp)
