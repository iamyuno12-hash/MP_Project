package com.example.snapledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.snapledger.model.Category

/**
 * 지출 내역 한 건. 앱 전체에서 이 모양을 같이 쓴다.
 * 필드를 바꾸면 AppDatabase의 version을 올려야 한다(개발 중에는 기존 데이터가 지워짐).
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeName: String,
    val businessNumber: String? = null,     // 사업자등록번호 000-00-00000 (같은 가게 판별용)
    val paidAt: Long,                       // 결제일시 (epoch millis)
    val amount: Long,                       // 금액 (원)
    val category: Category = Category.ETC,
    val memo: String = "",
    val receiptImagePath: String? = null,   // 기기에 저장한 영수증 원본 경로 (직접 입력이면 null)
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,            // Firestore 백업 여부
)

/** 사용자가 카테고리를 고치면 저장되는 규칙. matchKey = "biz:사업자번호" 또는 "name:상호명" */
@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey val matchKey: String,
    val category: Category,
)
