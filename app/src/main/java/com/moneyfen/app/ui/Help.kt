package com.moneyfen.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Small ⓘ icon; tapping it opens a short explanation (Egyptian Arabic / English).
 * Shows nothing when [key] is null or has no text in [HelpTexts], so there is never an empty ⓘ.
 */
@Composable
fun InfoTip(key: String?, modifier: Modifier = Modifier, title: String? = null) {
    val text = key?.let { HelpTexts.items[it] } ?: return
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(28.dp)) {
        Icon(Icons.Outlined.Info, tr("What is this?", "إيه ده؟"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val titleSlot: (@Composable () -> Unit)? = if (title != null) { { Text(title) } } else null
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = titleSlot,
        text = { Text(tr(text.first, text.second)) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(tr("OK", "تمام")) } },
    )
}

object HelpTexts {
    /** key -> (English, عربي). Each text is 1–3 lines in plain words, with a numbers example where it helps. */
    val items: Map<String, Pair<String, String>> = mapOf(
        "today" to (
            "What you can spend today. Worked out in the morning: (what you can spend ÷ days until salary). It stays fixed all day." to
                "المسموح تصرفه النهارده. بيتحسب الصبح: (اللي معاك تصرفه ÷ الأيام لحد المرتب). ثابت طول اليوم."),
        "fromTomorrow" to (
            "If you spend more or less today, this is what you'll be allowed each day starting tomorrow." to
                "لو صرفت أكتر/أقل النهارده، ده اللي هيبقى مسموح كل يوم من بكره."),
        "canSpend" to (
            "You can spend = money in your accounts + what people owe you (if enabled) − saved in jars − credit card debt." to
                "معاك تصرف = فلوس حساباتك + اللي ليك عند الناس (لو مفعّل) − المتحوّش − مديونية الكريديت."),
        "saved" to (
            "Saved: money set aside in your jars. It's still in your account but isn't counted in \"you can spend\"." to
                "المتحوّش: فلوس محجوزة في حصّالاتك. لسه في حسابك بس مش محسوبة في \"معاك تصرف\"."),
        "owedToMe" to (
            "Owed to you: expenses you paid for someone who will pay you back." to
                "ليك عند الناس: مصاريف دفعتها لحد وهيرجعهالك."),
        "cardDebt" to (
            "What you owe on the credit card = limit − available (from the bank's message)." to
                "اللي عليك للكريديت = الحد − المتاح (من رسالة البنك)."),
        "netWorth" to (
            "Net worth = everything you own (accounts, cash, gold, funds…) − everything you owe." to
                "صافي الثروة = كل اللي تملكه (حسابات، كاش، دهب، صناديق…) − كل اللي عليك."),
        "netWorthChips" to (
            "Tap a type to add it to or remove it from the net worth total." to
                "دوس على نوع عشان تضيفه أو تشيله من حساب الثروة."),
        "decide" to (
            "Transactions that need a word from you: was the expense for you or for someone else? Is incoming money income or a repayment?" to
                "معاملات محتاجة منك كلمة: مصروف ليك ولا لحد؟ فلوس داخلة دخل ولا سداد؟"),
        "alwaysThis" to (
            "The app will make the same choice by itself for the same name from now on. You can cancel it from Rules." to
                "البرنامج هيعمل نفس الاختيار لوحده مع نفس الاسم بعد كده. تقدر تلغيها من القواعد."),
        "forMe" to (
            "Spent for me = a normal expense." to
                "صرف ليا = مصروف عادي."),
        "forSomeone" to (
            "For someone who'll pay me back = not an expense; it's recorded as money they owe you." to
                "لحد هيرجعهولي = مش مصروف، بيتسجل إنه ليك عنده."),
        "betweenMine" to (
            "Between my accounts = moving money, not an expense." to
                "بين حساباتي = نقل فلوس، مش مصروف."),
        "income" to (
            "Income = counted in your income and in your saving percent." to
                "دخل = بيتحسب في دخلك ونسبة التحويش."),
        "repayment" to (
            "Repayment = reduces what that person owes you." to
                "سداد = بيقلل اللي ليك عند الشخص."),
        "notIncome" to (
            "Not income = money that came in, nothing more." to
                "مش دخل = فلوس داخلة وخلاص."),
        "jars" to (
            "Jars: money you set aside for a goal. It stays in your account but the app doesn't count it in what you can spend." to
                "الحصّالات: فلوس بتحجزها لهدف. بتفضل في حسابك بس البرنامج مش بيحسبها معاك تصرف."),
        "salaryDay" to (
            "A month in the app starts the day your salary arrives, not on the 1st." to
                "الشهر في البرنامج بيبدأ يوم نزول المرتب، مش يوم ١."),
        "savePercent" to (
            "When your salary arrives the app will suggest saving this percent of it." to
                "لما المرتب ينزل البرنامج هيقترح تحوش النسبة دي منه."),
        "incomeSources" to (
            "Choose whose money is real income (salary is automatic). Any other transfer isn't counted as income." to
                "اختار مين اللي فلوسه دخل حقيقي (المرتب أوتوماتيك). أي تحويل تاني مش بيتحسب دخل."),
        "ipnFee" to (
            "InstaPay fee: 0.1% of the amount, at least 0.5 and at most 20 EGP. Recorded as its own transaction." to
                "رسوم انستاباي: ٠٫١٪ من المبلغ، أقل حاجة ٠٫٥ وأقصى ٢٠ جنيه. بتتسجل معاملة لوحدها."),
        "reviewThreshold" to (
            "The app won't ask about credit card charges unless the merchant is new or the amount is bigger than this." to
                "البرنامج مش هيسألك على مصاريف الكريديت غير لو التاجر جديد أو المبلغ أكبر من ده."),
        "livePrice" to (
            "Gold / fund prices update from the internet by themselves (each time you open the app and every 6 hours)." to
                "سعر الدهب/الصندوق بيتحدث من النت لوحده (كل ما تفتح وكل ٦ ساعات)."),
        "oldUi" to (
            "Takes you back to the old look of the app without deleting anything." to
                "ترجعك للشكل القديم للبرنامج من غير ما يتمسح أي حاجة."),
        "preUpdateBackup" to (
            "Before an update changes how your data is stored, the app keeps a copy on the phone. This puts that copy back, replacing what's in the app now." to
                "قبل أي تحديث بيغيّر شكل تخزين البيانات، البرنامج بيحتفظ بنسخة على الموبايل. الزرار ده بيرجّع النسخة دي مكان اللي موجود دلوقتي."),
    )
}
