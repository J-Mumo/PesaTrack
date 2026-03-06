package com.pesatrack.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.entities.CategoryEntity
import com.pesatrack.data.local.database.entities.ExpenseEntity

/**
 * PesaTrack Room Database
 * Version 3: Moved Seed category from Shopping to Faith & Giving
 */
@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class PesaTrackDatabase : RoomDatabase() {
    
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    
    companion object {
        /**
         * Migration from version 2 to 3:
         * Move "Seed" category from Shopping (parentId=5) to Faith & Giving (parentId=9)
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Delete old Seed entry under Shopping
                database.execSQL("DELETE FROM categories WHERE id = 506 AND name = 'Seed'")
                // Insert new Seed entry under Faith & Giving
                database.execSQL(
                    """INSERT OR REPLACE INTO categories (id, name, icon, color, parentId, isGroup, isDefault, sortOrder) 
                       VALUES (905, 'Seed', 'grass', '#673AB7', 9, 0, 1, 5)"""
                )
            }
        }
    }
}
