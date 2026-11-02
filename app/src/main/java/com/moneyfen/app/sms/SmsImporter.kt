package com.moneyfen.app.sms

import android.content.Context
import android.net.Uri
import com.moneyfen.app.data.IngestResult
import com.moneyfen.app.data.Repository

data class ImportStats(val scanned: Int = 0, val added: Int = 0, val duplicates: Int = 0, val ignored: Int = 0, val info: Int = 0)

/** Reads the phone's SMS inbox (all history) from the watched senders. */
object SmsImporter {
    data class Sms(val address: String, val body: String, val date: Long)

    fun readInbox(ctx: Context, repo: Repository, since: Long = 0): List<Sms> {
        val out = mutableListOf<Sms>()
        ctx.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"),
            "date >= ?", arrayOf(since.toString()), "date ASC",
        )?.use { c ->
            val ia = c.getColumnIndexOrThrow("address"); val ib = c.getColumnIndexOrThrow("body"); val id = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                val addr = c.getString(ia) ?: continue
                if (!repo.isWatchedSender(addr)) continue
                out += Sms(addr, c.getString(ib) ?: "", c.getLong(id))
            }
        }
        return out
    }

    suspend fun import(ctx: Context, repo: Repository, since: Long = 0, progress: (Int, Int) -> Unit = { _, _ -> }): ImportStats {
        val list = readInbox(ctx, repo, since)
        var s = ImportStats(scanned = list.size)
        list.forEachIndexed { i, m ->
            s = when (repo.ingestSms(m.address, m.body, m.date, live = false)) {
                is IngestResult.Added -> s.copy(added = s.added + 1)
                IngestResult.Duplicate -> s.copy(duplicates = s.duplicates + 1)
                IngestResult.Info -> s.copy(info = s.info + 1)
                IngestResult.Ignored -> s.copy(ignored = s.ignored + 1)
            }
            if (i % 10 == 0 || i == list.lastIndex) progress(i + 1, list.size)
        }
        return s
    }
}
