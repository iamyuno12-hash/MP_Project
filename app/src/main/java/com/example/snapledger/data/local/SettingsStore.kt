package com.example.snapledger.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 월 예산 등 간단한 설정값 (SharedPreferences 대신 DataStore 사용) */
class SettingsStore(private val context: Context) {

    private val monthlyBudgetKey = longPreferencesKey("monthly_budget")

    /** 월 예산 (원). 0이면 미설정 */
    val monthlyBudget: Flow<Long> = context.dataStore.data.map { it[monthlyBudgetKey] ?: 0L }

    suspend fun setMonthlyBudget(amount: Long) {
        context.dataStore.edit { it[monthlyBudgetKey] = amount }
    }
}
