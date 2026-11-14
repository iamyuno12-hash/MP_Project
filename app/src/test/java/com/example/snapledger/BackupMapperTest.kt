package com.example.snapledger

import com.example.snapledger.data.firebase.BackupMapper
import com.example.snapledger.data.local.Expense
import com.example.snapledger.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Firestore 백업 문서 ↔ 지출 내역 변환 테스트 (Firebase 없이 실행) */
class BackupMapperTest {

    private val expense = Expense(
        id = 7,
        storeName = "모퉁이 편의점",
        businessNumber = "123-45-67890",
        paidAt = 1_793_000_000_000,
        amount = 4_800,
        category = Category.CONVENIENCE,
        memo = "간식",
        receiptImagePath = "/data/receipt.jpg",
        createdAt = 1_793_000_100_000,
    )

    @Test
    fun 올렸다가_받으면_같은_내역() {
        val restored = BackupMapper.fromMap(BackupMapper.toMap(expense))!!

        // 새 기기에서는 id를 새로 받고, 사진 경로는 없으며, 이미 백업된 상태
        assertEquals(expense.copy(id = 0, receiptImagePath = null, synced = true), restored)
    }

    @Test
    fun Firestore는_숫자를_Long_Double로_줄_수_있다() {
        val map = BackupMapper.toMap(expense).toMutableMap().apply { put("amount", 4800.0) }
        assertEquals(4_800L, BackupMapper.fromMap(map)!!.amount)
    }

    @Test
    fun 모르는_카테고리는_기타() {
        val map = BackupMapper.toMap(expense).toMutableMap().apply { put("category", "PET") }
        assertEquals(Category.ETC, BackupMapper.fromMap(map)!!.category)
    }

    @Test
    fun 필수값이_빠지면_건너뜀() {
        val map = BackupMapper.toMap(expense).toMutableMap().apply { remove("paidAt") }
        assertNull(BackupMapper.fromMap(map))
    }
}
