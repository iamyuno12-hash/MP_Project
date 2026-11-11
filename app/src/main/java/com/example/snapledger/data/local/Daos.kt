package com.example.snapledger.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY paidAt DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE paidAt BETWEEN :from AND :to ORDER BY paidAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    /** 새 내역이면 추가, id가 같으면 수정. 저장된 id를 돌려준다. */
    @Upsert
    suspend fun upsert(expense: Expense): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 중복 방지: 사업자번호·결제일시·금액이 모두 같은 내역 수 */
    @Query(
        "SELECT COUNT(*) FROM expenses " +
            "WHERE businessNumber = :businessNumber AND paidAt = :paidAt AND amount = :amount AND id != :exceptId"
    )
    suspend fun countSame(businessNumber: String, paidAt: Long, amount: Long, exceptId: Long): Int

    // ---------- Firestore 백업용 ----------

    @Query("SELECT * FROM expenses WHERE synced = 0")
    suspend fun getUnsynced(): List<Expense>

    @Query("SELECT COUNT(*) FROM expenses WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    /** 올린 뒤 표시. 올리는 사이에 사용자가 고쳤으면(값이 달라졌으면) 표시하지 않아 다음에 다시 올라간다 */
    @Query("UPDATE expenses SET synced = 1 WHERE id = :id AND paidAt = :paidAt AND amount = :amount")
    suspend fun markSynced(id: Long, paidAt: Long, amount: Long)

    /** 복원할 때 이미 기기에 있는 내역인지 (처음 저장한 시각으로 판별) */
    @Query("SELECT COUNT(*) FROM expenses WHERE createdAt = :createdAt")
    suspend fun countByCreatedAt(createdAt: Long): Int
}

@Dao
interface CategoryRuleDao {

    @Query("SELECT * FROM category_rules WHERE matchKey = :matchKey")
    suspend fun find(matchKey: String): CategoryRule?

    @Upsert
    suspend fun upsert(rule: CategoryRule)
}
