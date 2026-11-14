package com.example.snapledger.data.firebase

import com.example.snapledger.data.local.Expense
import com.example.snapledger.model.Category
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * 지출 내역 클라우드 백업 창구.
 * Firestore 구조:  users/{로그인 uid}/expenses/{createdAt}
 *  - 문서 id를 Room id가 아니라 createdAt(처음 저장한 시각, ms)으로 쓴다.
 *    폰을 바꿔 복원하면 Room id는 새로 매겨지지만 createdAt은 그대로라 같은 내역을 알아볼 수 있다.
 *  - 영수증 사진은 올리지 않는다 (Firebase Storage는 유료 요금제 필요). 내역 값만 백업.
 */
interface ExpenseBackup {
    /** Firebase로 로그인돼 있어 백업할 수 있는지 (테스트 모드 로그인이면 false) */
    val isAvailable: Boolean
    suspend fun upload(expense: Expense)
    suspend fun remove(createdAt: Long)
    suspend fun downloadAll(): List<Expense>
}

class FirestoreExpenseBackup(private val auth: AuthRepository) : ExpenseBackup {

    override val isAvailable: Boolean
        get() = auth.usesFirebase && auth.currentUserId.value != null

    private fun expenses() = FirebaseFirestore.getInstance()
        .collection("users")
        .document(auth.currentUserId.value ?: throw IllegalStateException("로그인이 필요해요"))
        .collection("expenses")

    override suspend fun upload(expense: Expense) {
        expenses().document(expense.createdAt.toString())
            .set(BackupMapper.toMap(expense), SetOptions.merge())
            .awaitTask()
    }

    override suspend fun remove(createdAt: Long) {
        expenses().document(createdAt.toString()).delete().awaitTask()
    }

    override suspend fun downloadAll(): List<Expense> =
        expenses().get().awaitTask().documents.mapNotNull { doc -> doc.data?.let(BackupMapper::fromMap) }
}

/** Expense ↔ Firestore 문서(Map) 변환. Firebase 없이 단위 테스트할 수 있게 따로 뺐다 */
object BackupMapper {

    fun toMap(expense: Expense): Map<String, Any?> = mapOf(
        "storeName" to expense.storeName,
        "businessNumber" to expense.businessNumber,
        "paidAt" to expense.paidAt,
        "amount" to expense.amount,
        "category" to expense.category.name,
        "memo" to expense.memo,
        "createdAt" to expense.createdAt,
        "updatedAt" to System.currentTimeMillis(),
    )

    /** 값이 빠졌거나 이상한 문서는 null (복원에서 건너뜀) */
    fun fromMap(map: Map<String, Any?>): Expense? {
        val storeName = (map["storeName"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val paidAt = (map["paidAt"] as? Number)?.toLong() ?: return null
        val amount = (map["amount"] as? Number)?.toLong()?.takeIf { it > 0 } ?: return null
        val createdAt = (map["createdAt"] as? Number)?.toLong() ?: return null
        val category = (map["category"] as? String)
            ?.let { name -> Category.entries.find { it.name == name } } ?: Category.ETC
        return Expense(
            id = 0,
            storeName = storeName,
            businessNumber = map["businessNumber"] as? String,
            paidAt = paidAt,
            amount = amount,
            category = category,
            memo = map["memo"] as? String ?: "",
            receiptImagePath = null,
            createdAt = createdAt,
            synced = true,
        )
    }
}
