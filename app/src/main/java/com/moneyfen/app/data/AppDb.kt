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
        GoalEntity::class, SmsPatternEntity::class, StatementEntity::class, GoalMoveEntity::class, GoalAllocEntity::class,
    ],
    version = 7,
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
    abstract fun goalMoves(): GoalMoveDao
    abstract fun goalAllocs(): GoalAllocDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "moneyfen.db").addMigrations(M1_2, M2_3, M3_4, M4_5, M5_6, M6_7).build()

        /** "Always do this" decisions on rules (simplify phase 3). */
        private val M6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN autoAction TEXT")
                db.execSQL("ALTER TABLE rules ADD COLUMN autoAccountId INTEGER")
            }
        }

        private val M5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_allocs` (`goalId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `amount` REAL NOT NULL, PRIMARY KEY(`goalId`, `accountId`))")
            }
        }

        private val M4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN savePercent REAL")
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_moves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `time` INTEGER NOT NULL, `amount` REAL NOT NULL, `txId` INTEGER, `note` TEXT)")
            }
        }

        private val M3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN wholeAccount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE goals SET wholeAccount = 1 WHERE accountId IS NOT NULL")
            }
        }

        private val M2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN priceUrl TEXT")
                db.execSQL("ALTER TABLE accounts ADD COLUMN priceAnchor TEXT")
            }
        }

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
