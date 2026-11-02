package com.moneyfen.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneyfen.app.data.AccountType
import com.moneyfen.app.data.CategoryType
import com.moneyfen.app.data.Repository
import com.moneyfen.app.data.TxEntity
import com.moneyfen.app.data.kindOf
import com.moneyfen.app.data.monthRange
import com.moneyfen.app.ui.MainActivity
import com.moneyfen.app.ui.L
import com.moneyfen.app.ui.fmt
import kotlinx.coroutines.flow.first

object Notifier {
    private const val CH_TX = "tx"
    private const val CH_BUDGET = "budget"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TX, "Transactions", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, "Budgets", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun can(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
        android.os.Build.VERSION.SDK_INT < 33

    private fun open(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    suspend fun transaction(ctx: Context, repo: Repository, t: TxEntity) {
        if (!can(ctx)) return
        val snap = repo.snapshot.first()
        val ar = L.isArabic(ctx)
        val cat = snap.categoryOf(t)
        val acc = snap.accountById[t.accountId]
        val sign = if (kindOf(t).direction >= 0) "+" else "−"
        val title = "$sign${fmt(t.amount)} ${t.currency} · ${t.party ?: ""}"
        val catName = cat?.let { "${it.emoji} ${if (ar) it.nameAr else it.nameEn}" } ?: ""
        val bal = acc?.let {
            val label = if (it.account.accountType == AccountType.CREDIT_CARD) (if (ar) "المديونية" else "Debt") else (if (ar) "الرصيد" else "Balance")
            "$label: ${fmt(kotlin.math.abs(it.balance))}"
        } ?: ""
        if (repo.prefs.notifyOnSms) {
            val n = NotificationCompat.Builder(ctx, CH_TX)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(listOf(catName, bal).filter { it.isNotBlank() }.joinToString(" | "))
                .setContentIntent(open(ctx)).setAutoCancel(true).build()
            runCatching { NotificationManagerCompat.from(ctx).notify(t.id.toInt(), n) }
        }
        // Budget check
        if (cat != null && cat.categoryType == CategoryType.EXPENSE && cat.monthlyBudget != null && cat.monthlyBudget > 0 && !t.excluded) {
            val (from, to) = monthRange()
            val spent = snap.totals(from, to).expenseByCategory[cat.id] ?: 0.0
            val before = spent - snap.toEgp(t.amount, t.currency) * (-kindOf(t).direction)
            val budget = cat.monthlyBudget
            val crossed = when {
                spent > budget && before <= budget -> if (ar) "تخطيت ميزانية ${cat.nameAr}" else "Over budget: ${cat.nameEn}"
                spent >= budget * 0.8 && before < budget * 0.8 -> if (ar) "وصلت 80% من ميزانية ${cat.nameAr}" else "80% of ${cat.nameEn} budget used"
                else -> null
            }
            if (crossed != null) {
                val n = NotificationCompat.Builder(ctx, CH_BUDGET)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("${cat.emoji} $crossed")
                    .setContentText("${fmt(spent)} / ${fmt(budget)}")
                    .setContentIntent(open(ctx)).setAutoCancel(true).build()
                runCatching { NotificationManagerCompat.from(ctx).notify(100000 + cat.id.toInt(), n) }
            }
        }
    }
}
