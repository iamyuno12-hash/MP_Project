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
}

@Dao
interface CategoryRuleDao {

    @Query("SELECT * FROM category_rules WHERE matchKey = :matchKey")
    suspend fun find(matchKey: String): CategoryRule?

    @Upsert
    suspend fun upsert(rule: CategoryRule)
}
