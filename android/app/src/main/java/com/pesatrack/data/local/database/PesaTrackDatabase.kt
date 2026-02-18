package com.pesatrack.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.entities.CategoryEntity
import com.pesatrack.data.local.database.entities.ExpenseEntity

/**
 * PesaTrack Room Database
 * Version 2: Added hierarchical categories with parent-child relationships
 */
@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class PesaTrackDatabase : RoomDatabase() {
    
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
}
