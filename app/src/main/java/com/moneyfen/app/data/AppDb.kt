package com.moneyfen.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, TxEntity::class, CategoryEntity::class, RuleEntity::class,
        GoalEntity::class, SmsPatternEntity::class, StatementEntity::class,
    ],
    version = 2,
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
            Room.databaseBuilder(context, AppDb::class.java, "moneyfen.db").addMigrations(M1_2).build()

        private val M1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN symbol TEXT")
                db.execSQL("ALTER TABLE accounts ADD COLUMN karat INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN costBasis REAL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN autoPrice INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE accounts ADD COLUMN priceAt INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN personalAmount REAL")
                db.execSQL("ALTER TABLE transactions ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
