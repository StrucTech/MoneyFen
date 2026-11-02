package com.moneyfen.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id") fun observe(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id") suspend fun all(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun get(id: Long): AccountEntity?
    @Upsert suspend fun upsert(a: AccountEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<AccountEntity>)
    @Delete suspend fun delete(a: AccountEntity)
    @Query("DELETE FROM accounts") suspend fun clear()
}

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions ORDER BY time DESC, id DESC") fun observe(): Flow<List<TxEntity>>
    @Query("SELECT * FROM transactions ORDER BY time, id") suspend fun all(): List<TxEntity>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun get(id: Long): TxEntity?
    @Query("SELECT COUNT(*) FROM transactions WHERE smsHash = :hash AND time BETWEEN :from AND :to")
    suspend fun countHashNear(hash: String, from: Long, to: Long): Int
    @Query("SELECT * FROM transactions WHERE time BETWEEN :from AND :to ORDER BY time")
    suspend fun between(from: Long, to: Long): List<TxEntity>
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND time BETWEEN :from AND :to ORDER BY time")
    suspend fun forAccountBetween(accountId: Long, from: Long, to: Long): List<TxEntity>
    @Upsert suspend fun upsert(t: TxEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<TxEntity>)
    @Delete suspend fun delete(t: TxEntity)
    @Query("DELETE FROM transactions WHERE accountId = :accountId") suspend fun deleteForAccount(accountId: Long)
    @Query("UPDATE transactions SET accountId = :to WHERE accountId = :from") suspend fun moveAccount(from: Long, to: Long)
    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId") suspend fun clearCategory(categoryId: Long)
    @Query("DELETE FROM transactions") suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun observe(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") suspend fun all(): List<CategoryEntity>
    @Upsert suspend fun upsert(c: CategoryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<CategoryEntity>)
    @Delete suspend fun delete(c: CategoryEntity)
    @Query("DELETE FROM categories") suspend fun clear()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY priority DESC, id DESC") fun observe(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM rules ORDER BY priority DESC, id DESC") suspend fun all(): List<RuleEntity>
    @Upsert suspend fun upsert(r: RuleEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<RuleEntity>)
    @Delete suspend fun delete(r: RuleEntity)
    @Query("DELETE FROM rules") suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY deadline") fun observe(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals") suspend fun all(): List<GoalEntity>
    @Upsert suspend fun upsert(g: GoalEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<GoalEntity>)
    @Delete suspend fun delete(g: GoalEntity)
    @Query("DELETE FROM goals") suspend fun clear()
}

@Dao
interface SmsPatternDao {
    @Query("SELECT * FROM sms_patterns ORDER BY id") fun observe(): Flow<List<SmsPatternEntity>>
    @Query("SELECT * FROM sms_patterns ORDER BY id") suspend fun all(): List<SmsPatternEntity>
    @Upsert suspend fun upsert(p: SmsPatternEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<SmsPatternEntity>)
    @Delete suspend fun delete(p: SmsPatternEntity)
    @Query("DELETE FROM sms_patterns") suspend fun clear()
}

@Dao
interface StatementDao {
    @Query("SELECT * FROM statements ORDER BY time DESC") fun observe(): Flow<List<StatementEntity>>
    @Query("SELECT * FROM statements") suspend fun all(): List<StatementEntity>
    @Query("SELECT COUNT(*) FROM statements WHERE accountId = :accountId AND amount = :amount AND dueDate = :dueDate")
    suspend fun count(accountId: Long, amount: Double, dueDate: String?): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(s: StatementEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<StatementEntity>)
    @Query("DELETE FROM statements") suspend fun clear()
}
