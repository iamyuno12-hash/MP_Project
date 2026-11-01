package com.example.snapledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Expense::class, CategoryRule::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "snapledger.db")
                // 개발 중 스키마가 바뀌면 DB를 새로 만든다. 배포 전에는 Migration으로 바꿀 것.
                .fallbackToDestructiveMigration()
                .build()
    }
}
