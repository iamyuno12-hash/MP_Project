package com.example.snapledger.data.repository

import com.example.snapledger.data.local.Expense
import com.example.snapledger.data.local.ExpenseDao
import com.example.snapledger.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * 화면(ViewModel)이 지출 데이터를 쓰는 유일한 창구.
 * 프론트는 이 인터페이스만 보고 개발하고, 실제 구현(Room/Firestore)은 백엔드가 채운다.
 */
interface ExpenseRepository {
    fun observeAll(): Flow<List<Expense>>
    fun observeBetween(from: Long, to: Long): Flow<List<Expense>>
    fun observe(id: Long): Flow<Expense?>
    suspend fun get(id: Long): Expense?
    suspend fun save(expense: Expense): Long
    suspend fun delete(id: Long)
    suspend fun isDuplicate(expense: Expense): Boolean
}

/**
 * Room을 쓰는 실제 구현.
 * sync가 있으면 저장·삭제 뒤에 Firestore 백업을 뒤에서 처리한다 (화면은 기다리지 않음).
 */
class RoomExpenseRepository(
    private val dao: ExpenseDao,
    private val sync: ExpenseSync? = null,
) : ExpenseRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeBetween(from: Long, to: Long) = dao.observeBetween(from, to)
    override fun observe(id: Long) = dao.observeById(id)
    override suspend fun get(id: Long) = dao.getById(id)
    override suspend fun save(expense: Expense): Long {
        // 새로 저장하거나 고친 내역은 '아직 안 올라감'으로 표시해 두고 백업한다
        val savedId = dao.upsert(expense.copy(synced = false))
        // @Upsert는 수정일 때 -1을 돌려주므로 원래 id를 쓴다
        val id = if (savedId == -1L) expense.id else savedId
        sync?.uploadLater(id)
        return id
    }
    override suspend fun delete(id: Long) {
        val expense = dao.getById(id)
        dao.deleteById(id)
        if (expense != null) sync?.removeLater(expense)
    }
    override suspend fun isDuplicate(expense: Expense): Boolean {
        val bizNo = expense.businessNumber ?: return false
        return dao.countSame(bizNo, expense.paidAt, expense.amount, expense.id) > 0
    }
}

/** 미리보기(Preview)와 화면 개발용 가짜 저장소. 앱을 끄면 데이터가 사라진다. */
class FakeExpenseRepository(initial: List<Expense> = sampleExpenses()) : ExpenseRepository {
    private val items = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll() = items.map { list -> list.sortedByDescending { it.paidAt } }
    override fun observeBetween(from: Long, to: Long) =
        items.map { list -> list.filter { it.paidAt in from..to }.sortedByDescending { it.paidAt } }
    override fun observe(id: Long) = items.map { list -> list.find { it.id == id } }
    override suspend fun get(id: Long) = items.value.find { it.id == id }
    override suspend fun save(expense: Expense): Long {
        val id = if (expense.id == 0L) nextId++ else expense.id
        items.update { list -> list.filterNot { it.id == id } + expense.copy(id = id) }
        return id
    }
    override suspend fun delete(id: Long) = items.update { list -> list.filterNot { it.id == id } }
    override suspend fun isDuplicate(expense: Expense) = false
}

fun sampleExpenses(): List<Expense> {
    val now = System.currentTimeMillis()
    val hour = 60 * 60 * 1000L
    return listOf(
        Expense(1, "모퉁이 편의점", "123-45-67890", now - 2 * hour, 4_800, Category.CONVENIENCE),
        Expense(2, "온기 커피", null, now - 5 * hour, 4_500, Category.CAFE),
        Expense(3, "든든 김밥", null, now - 26 * hour, 7_000, Category.FOOD, memo = "점심"),
    )
}
