package com.example.snapledger.data.repository

import android.util.Log
import com.example.snapledger.data.firebase.AuthRepository
import com.example.snapledger.data.firebase.ExpenseBackup
import com.example.snapledger.data.local.Expense
import com.example.snapledger.data.local.ExpenseDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Room(기기) ↔ Firestore(클라우드) 백업 담당.
 *  - 저장/수정/삭제하면 뒤에서 자동으로 올린다 (화면은 기다리지 않음)
 *  - 인터넷이 없거나 로그인 전이라 못 올린 내역은 synced=false로 남겨 두고,
 *    다음에 로그인하거나 설정에서 '지금 백업'을 누르면 한꺼번에 올린다.
 */
class ExpenseSync(
    private val dao: ExpenseDao,
    private val backup: ExpenseBackup,
    auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    val isAvailable: Boolean get() = backup.isAvailable

    /** 아직 클라우드에 안 올라간 내역 수 (설정 화면 표시용) */
    fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    init {
        // 로그인하면(앱 시작 시 자동 로그인 포함) 밀린 내역을 올린다
        scope.launch {
            auth.currentUserId.filterNotNull().distinctUntilChanged().collect {
                if (isAvailable) safely("로그인 후 자동 백업") { backupAll() }
            }
        }
    }

    /** 저장 직후 호출: 한 건을 뒤에서 올린다 */
    fun uploadLater(id: Long) {
        if (!isAvailable) return
        scope.launch {
            safely("자동 백업") {
                val expense = dao.getById(id) ?: return@safely
                backup.upload(expense)
                dao.markSynced(id, expense.paidAt, expense.amount)
            }
        }
    }

    /** 삭제 직후 호출: 클라우드에서도 지운다 */
    fun removeLater(expense: Expense) {
        if (!isAvailable) return
        scope.launch { safely("백업 삭제") { backup.remove(expense.createdAt) } }
    }

    /** 밀린 내역 전부 올리기. 올린 개수를 돌려준다 */
    suspend fun backupAll(): Int {
        check(isAvailable) { "Firebase 로그인 후 사용할 수 있어요" }
        val pending = dao.getUnsynced()
        for (expense in pending) {
            backup.upload(expense)
            dao.markSynced(expense.id, expense.paidAt, expense.amount)
        }
        return pending.size
    }

    /** 클라우드 백업을 기기로 가져오기. 기기에 없는 내역만 추가하고, 추가한 개수를 돌려준다 */
    suspend fun restore(): Int {
        check(isAvailable) { "Firebase 로그인 후 사용할 수 있어요" }
        var added = 0
        for (remote in backup.downloadAll()) {
            if (dao.countByCreatedAt(remote.createdAt) == 0) {
                dao.upsert(remote.copy(id = 0, synced = true))
                added++
            }
        }
        return added
    }

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Backup", "$what 실패 (다음에 다시 시도): ${e.message}")
        }
    }
}
