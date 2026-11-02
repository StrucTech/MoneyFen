package com.moneyfen.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.moneyfen.app.app
import kotlinx.coroutines.launch

/** Receives every new SMS; bank messages are parsed and recorded immediately. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val app = context.app
        val repo = app.repo
        // Multipart messages arrive as several parts from the same sender: join them.
        val grouped = msgs.groupBy { it.originatingAddress ?: "" }
        val pending = goAsync()
        app.appScope.launch {
            try {
                for ((sender, parts) in grouped) {
                    if (!repo.isWatchedSender(sender)) continue
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val time = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                    repo.ingestSms(sender, body, time, live = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
