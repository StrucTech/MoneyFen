package com.moneyfen.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyfen.app.app
import com.moneyfen.app.data.AccountEntity
import com.moneyfen.app.data.AccountType
import com.moneyfen.app.data.Snapshot
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(snap: Snapshot, id: Long) {
    val repo = LocalRepo.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val existing = snap.accountById[id]
    if (id != 0L && existing == null) { Text("…"); return }
    val a0 = existing?.account ?: AccountEntity(name = "", type = AccountType.CASH.name)

    var name by remember(id) { mutableStateOf(a0.name) }
    var type by remember(id) { mutableStateOf(a0.accountType) }
    var currency by remember(id) { mutableStateOf(a0.currency) }
    var bank by remember(id) { mutableStateOf(a0.bank ?: "") }
    var hints by remember(id) { mutableStateOf(a0.smsHints) }
    var limit by remember(id) { mutableStateOf(if (a0.creditLimit > 0) fmtPlain(a0.creditLimit) else "") }
    var qty by remember(id) { mutableStateOf(a0.quantity?.let(::fmtPlain) ?: "") }
    var unit by remember(id) { mutableStateOf(a0.unitPrice?.let(::fmtPlain) ?: "") }
    var rate by remember(id) { mutableStateOf(a0.annualRate?.let(::fmtPlain) ?: "") }
    var maturity by remember(id) { mutableStateOf(a0.maturity) }
    var note by remember(id) { mutableStateOf(a0.note ?: "") }
    var inNet by remember(id) { mutableStateOf(a0.includeInNetWorth) }
    var archived by remember(id) { mutableStateOf(a0.archived) }
    var balanceText by remember(id) { mutableStateOf("") }
    var karat by remember(id) { mutableStateOf(a0.karat ?: 21) }
    var symbol by remember(id) { mutableStateOf(a0.symbol ?: "") }
    var cost by remember(id) { mutableStateOf(a0.costBasis?.let(::fmtPlain) ?: "") }
    var auto by remember(id) { mutableStateOf(a0.autoPrice) }
    var quote by remember(id) { mutableStateOf<String?>(null) }
    var buyMore by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    val isGold = type == AccountType.GOLD
    val isStock = type == AccountType.STOCK
    val isHolding = isGold || isStock
    androidx.compose.runtime.LaunchedEffect(karat, auto, type) {
        if (isGold && auto && repo.prefs.gold24Gram > 0) unit = fmtPlain(Math.round(repo.prefs.goldGram(karat) * 100) / 100.0)
    }
    val hasRate = type in setOf(AccountType.CERTIFICATE, AccountType.DEPOSIT, AccountType.INVESTMENT, AccountType.BANK)

    val defaultName = tr0(type)
    fun build(): AccountEntity = a0.copy(
        name = name.ifBlank { defaultName }, type = type.name, currency = currency, bank = bank.ifBlank { null }?.uppercase(),
        smsHints = hints, creditLimit = parseNum(limit) ?: 0.0, quantity = parseNum(qty), unitPrice = parseNum(unit),
        annualRate = parseNum(rate), maturity = maturity, note = note.ifBlank { null }, includeInNetWorth = inNet, archived = archived,
        karat = if (isGold) karat else a0.karat, symbol = symbol.trim().uppercase().ifBlank { null },
        costBasis = parseNum(cost), autoPrice = auto,
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), 
            title = { Text(if (id == 0L) tr("New account", "حساب جديد") else a0.name) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, null) } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing != null) {
                val bal = existing.balance
                SectionCard(tr("Balance", "الرصيد")) {
                    Text(money(abs(bal), currency = currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = amountColor(bal))
                    if (type == AccountType.CREDIT_CARD && a0.creditLimit > 0) Text(tr("Available: ", "المتاح: ") + money(a0.creditLimit + bal))
                    val r = a0.annualRate
                    if (r != null && r > 0 && bal > 0) Text(tr("Monthly return ≈ ", "العائد الشهري ≈ ") + money(bal * r / 100 / 12))
                    if (isHolding) {
                        val c = a0.costBasis
                        if (c != null && c > 0) {
                            LabelValue(tr("Bought for", "اشتريته بـ"), money(c))
                            LabelValue(tr("Profit / loss", "المكسب / الخسارة"), signedText(bal - c) + "  (${"%.1f".format((bal - c) / c * 100)}%)", amountColor(bal - c))
                        }
                        a0.priceAt?.let { Text(tr("Price updated ", "السعر اتحدث ") + dateFmt(it, "dd MMM HH:mm"), style = MaterialTheme.typography.bodySmall) }
                        OutlinedButton(onClick = { buyMore = true }) { Text(tr("+ Bought more", "+ اشتريت تاني")) }
                    }
                    if (!isHolding) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                if (type.liability) tr("Correct amount owed", "عدّل المديونية") else tr("Correct balance", "عدّل الرصيد"),
                                balanceText, { balanceText = it }, Modifier.weight(1f),
                            )
                            Button(onClick = {
                                val v = parseNum(balanceText) ?: return@Button
                                scope.launch { repo.setBalance(a0, if (type.liability) -abs(v) else v); balanceText = "" }
                            }) { Text(tr("Set", "حفظ")) }
                        }
                        Text(tr(
                            "The balance is read from bank SMS when available and calculated from transactions otherwise. Setting it here overrides it from now on.",
                            "الرصيد بيتقري من رسايل البنك لو موجود وبيتحسب من المعاملات غير كده. لو عدلته هنا هيتعتمد من دلوقتي.",
                        ), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { nav.navigate("accountTxs/$id") }) { Text(tr("Transactions", "المعاملات")) }
                        OutlinedButton(onClick = { nav.navigate("tx/0?account=$id") }) { Text(tr("+ Transaction", "+ معاملة")) }
                    }
                }
            }
            Picker(tr("Type", "النوع"), AccountType.entries, type, { "${it.emoji} " + tr0(it) }, { type = it })
            TextField1(tr("Name", "الاسم"), name, { name = it })
            Picker(tr("Currency", "العملة"), listOf("EGP", "USD"), currency, { it }, { currency = it })
            if (id == 0L && !isHolding) NumberField(
                if (type.liability) tr("Amount owed now", "المديونية الحالية") else tr("Current balance", "الرصيد الحالي"),
                balanceText, { balanceText = it },
            )
            if (type == AccountType.CREDIT_CARD) NumberField(tr("Credit limit", "الحد الائتماني"), limit, { limit = it })
            if (isGold) {
                Picker(tr("Karat", "العيار"), listOf(24, 21, 18), karat, { "$it" }, { karat = it })
                NumberField(tr("Grams (gold pound = 8 g of 21k)", "الجرامات (الجنيه الدهب = ٨ جرام عيار ٢١)"), qty, { qty = it })
            }
            if (isStock) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField1(tr("Symbol (COMI.CA, AAPL…)", "الرمز (COMI.CA، AAPL…)"), symbol, { symbol = it }, Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        quote = "…"
                        scope.launch {
                            quote = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.moneyfen.app.data.Prices.yahoo(symbol) } }
                                .fold({ q ->
                                    if (q.currency == "EGP" || q.currency == "USD") currency = q.currency
                                    unit = fmtPlain(q.price); "✔ ${q.price} ${q.currency}"
                                }, { "✘ ${it.message}" })
                        }
                    }) { Text(tr("Check", "اتأكد")) }
                }
                Text(tr("Egyptian stocks end with .CA (e.g. COMI.CA = CIB). Search the symbol on finance.yahoo.com.",
                    "أسهم البورصة المصرية بتنتهي بـ .CA (مثلاً COMI.CA = CIB). دوّر على الرمز في finance.yahoo.com."), style = MaterialTheme.typography.bodySmall)
                quote?.let { Text(it) }
                NumberField(tr("Number of shares", "عدد الأسهم"), qty, { qty = it })
            }
            if (isHolding) {
                NumberField(tr("Total paid ($currency)", "إجمالي اللي دفعته ($currency)"), cost, { cost = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Live price", "سعر لايف"))
                        Text(tr("Updated from the internet automatically", "بيتحدث من النت أوتوماتيك"), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(auto, { auto = it })
                }
                NumberField(if (isGold) tr("Price per gram", "سعر الجرام") else tr("Price per share", "سعر السهم"), unit, { unit = it; auto = false })
                val q = parseNum(qty) ?: 0.0
                val v = q * (parseNum(unit) ?: 0.0)
                val c = parseNum(cost)
                Text(tr("Value now: ", "قيمته دلوقتي: ") + money(v, currency = currency), fontWeight = FontWeight.SemiBold)
                if (c != null && c > 0) {
                    Text(tr("Profit / loss: ", "المكسب / الخسارة: ") + signedText(v - c) + "  (${"%.1f".format((v - c) / c * 100)}%)", color = amountColor(v - c))
                    if (q > 0) Text(tr("Avg buy price: ", "متوسط سعر الشراء: ") + fmt(c / q), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (hasRate) NumberField(tr("Annual return %", "العائد السنوي %"), rate, { rate = it })
            if (type == AccountType.CERTIFICATE || type == AccountType.DEPOSIT) {
                DateField(tr("Maturity", "تاريخ الاستحقاق"), maturity ?: System.currentTimeMillis(), { maturity = it })
            }
            if (type in setOf(AccountType.BANK, AccountType.CREDIT_CARD, AccountType.WALLET)) {
                TextField1(tr("SMS sender (e.g. CIB)", "مرسل الرسايل (مثلاً CIB)"), bank, { bank = it })
                TextField1(tr("Last digits in SMS (comma separated)", "آخر أرقام الحساب/الكارت في الرسايل (مفصولة بفاصلة)"), hints, { hints = it })
            }
            TextField1(tr("Note", "ملاحظة"), note, { note = it }, singleLine = false)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Include in net worth", "يدخل في حساب الثروة"), Modifier.weight(1f)); Switch(inNet, { inNet = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Archived (hidden)", "مؤرشف (مخفي)"), Modifier.weight(1f)); Switch(archived, { archived = it })
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    val a = build()
                    val newId = repo.saveAccount(a)
                    val init = parseNum(balanceText)
                    if (id == 0L && init != null && !isHolding) {
                        repo.setBalance(a.copy(id = newId), if (type.liability) -abs(init) else init)
                    }
                    if (isHolding && auto) ctx.app.appScope.launch { com.moneyfen.app.data.Prices.refreshAll(repo) }
                    nav.popBackStack()
                }
            }) { Text(tr("Save", "حفظ")) }
        }
    }

    if (buyMore && existing != null) {
        var bq by remember { mutableStateOf("") }
        var bc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { buyMore = false },
            title = { Text(tr("Bought more", "اشتريت تاني")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(if (isGold) tr("Grams", "الجرامات") else tr("Shares", "الأسهم"), bq, { bq = it })
                    NumberField(tr("Paid", "دفعت"), bc, { bc = it })
                }
            },
            confirmButton = { TextButton(onClick = {
                val q = parseNum(bq); val c = parseNum(bc)
                if (q != null && c != null) scope.launch {
                    repo.addToHolding(a0, q, c)
                    qty = fmtPlain((a0.quantity ?: 0.0) + q); cost = fmtPlain((a0.costBasis ?: 0.0) + c)
                }
                buyMore = false
            }) { Text(tr("Add", "ضيف")) } },
            dismissButton = { TextButton(onClick = { buyMore = false }) { Text(tr("Cancel", "إلغاء")) } },
        )
    }

    if (confirmDelete && existing != null) {
        val others = snap.accounts.map { it.account }.filter { it.id != id }
        var moveTo by remember { mutableStateOf<AccountEntity?>(null) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete account?", "تمسح الحساب؟")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Its transactions will be deleted, or moved to:", "معاملاته هتتمسح، أو تتنقل لـ:"))
                    Picker(tr("Move transactions to", "انقل المعاملات لـ"), listOf<AccountEntity?>(null) + others, moveTo,
                        { it?.name ?: tr("— delete them —", "— امسحها —") }, { moveTo = it })
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch { repo.deleteAccount(a0, moveTo?.id); confirmDelete = false; nav.popBackStack() }
            }) { Text(tr("Delete", "امسح")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel", "إلغاء")) } },
        )
    }
}

fun fmtPlain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

@Composable
fun tr0(t: AccountType): String = tr(t.en, t.ar)
