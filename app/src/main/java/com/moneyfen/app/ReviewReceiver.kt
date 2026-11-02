package com.moneyfen.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moneyfen.app.data.Repository
import kotlinx.coroutines.launch

/** Handles the quick buttons on a new-charge notification. */
class ReviewReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_TX = "tx"
        const val ALL = "com.moneyfen.REVIEW_ALL"
        const val NONE_CASH = "com.moneyfen.REVIEW_NONE"
        const val SAVE_DEFAULT = "com.moneyfen.SAVE_DEFAULT"
        const val SAVE_ALL = "com.moneyfen.SAVE_ALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TX, 0L).takeIf { it > 0 } ?: return
        val app = context.app
        val pending = goAsync()
        app.appScope.launch {
            try {
                val t = app.db.txs().get(id) ?: return@launch
                when (intent.action) {
                    ALL -> app.repo.resolveReview(id, t.amount, Repository.Dest.NONE)
                    NONE_CASH -> app.repo.resolveReview(id, 0.0, Repository.Dest.CASH)
                    SAVE_DEFAULT, SAVE_ALL -> {
                        val amt = if (intent.action == SAVE_ALL) (if (t.currency == "USD") t.amount * app.prefs.usdRate else t.amount)
                        else app.repo.suggestedSave(t)
                        val goal = app.prefs.lastSaveGoal.takeIf { gid -> gid > 0 && app.db.goals().all().any { it.id == gid } }
                        app.repo.saveFromIncome(id, amt, goal)
                    }
                }
                Notifier.cancel(context, id)
            } finally { pending.finish() }
        }
    }
}
