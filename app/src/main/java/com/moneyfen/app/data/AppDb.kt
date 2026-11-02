package com.moneyfen.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class, TxEntity::class, CategoryEntity::class, RuleEntity::class,
        GoalEntity::class, SmsPatternEntity::class, StatementEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun txs(): TxDao
    abstract fun categories(): CategoryDao
    abstract fun rules(): RuleDao
    abstract fun goals(): GoalDao
    abstract fun patterns(): SmsPatternDao
    abstract fun statements(): StatementDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "moneyfen.db").build()
    }
}
