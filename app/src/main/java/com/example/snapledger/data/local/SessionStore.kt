package com.example.snapledger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * 로그인 유지 설정을 기기에 저장한다 (DataStore).
 * - Firebase 로그인은 Firebase가 기기에 알아서 저장하므로, 여기서는 '유지할지'만 기억한다.
 * - 테스트 모드(가짜 로그인)는 저장해 주는 곳이 없어서, 로그인한 계정도 여기에 저장한다.
 */
class SessionStore(private val context: Context) {

    private val keepSignedInKey = booleanPreferencesKey("keep_signed_in")
    private val testUserKey = stringPreferencesKey("test_user_id")

    /** 로그인 상태 유지 (자동 로그인). 처음에는 켜져 있다 */
    val keepSignedIn: Flow<Boolean> = context.sessionDataStore.data.map { it[keepSignedInKey] ?: true }

    suspend fun setKeepSignedIn(keep: Boolean) {
        context.sessionDataStore.edit { it[keepSignedInKey] = keep }
    }

    /** 테스트 모드에서 로그인한 사용자. 로그아웃 상태면 null */
    suspend fun testUserId(): String? = context.sessionDataStore.data.first()[testUserKey]

    suspend fun setTestUserId(userId: String?) {
        context.sessionDataStore.edit { prefs ->
            if (userId == null) prefs.remove(testUserKey) else prefs[testUserKey] = userId
        }
    }
}
