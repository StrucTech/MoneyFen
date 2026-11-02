package com.moneyfen.app

import android.app.Application
import android.content.Context
import com.moneyfen.app.data.AppDb
import com.moneyfen.app.data.Prefs
import com.moneyfen.app.data.Repository
import com.moneyfen.app.data.BackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoneyFenApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val prefs by lazy { Prefs(this) }
    val db by lazy { AppDb.create(this) }
    val repo by lazy { Repository(this, db, prefs) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        appScope.launch { repo.ensureSeeded() }
        BackupWorker.schedule(this)
        com.moneyfen.app.data.PriceWorker.schedule(this)
    }
}

val Context.app: MoneyFenApp get() = applicationContext as MoneyFenApp
