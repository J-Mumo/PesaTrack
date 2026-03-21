package com.pesatrack.di

import android.content.Context
import android.telephony.TelephonyManager
import androidx.room.Room
import com.pesatrack.data.local.database.PesaTrackDatabase
import com.pesatrack.data.local.database.dao.BudgetDao
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.RecipientCategoryMappingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module for app-wide dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ==================== Database ====================

    @Provides
    @Singleton
    fun providePesaTrackDatabase(
        @ApplicationContext context: Context
    ): PesaTrackDatabase {
        return Room.databaseBuilder(
            context,
            PesaTrackDatabase::class.java,
            "pesatrack_database"
        )
            .addMigrations(
                PesaTrackDatabase.MIGRATION_2_3,
                PesaTrackDatabase.MIGRATION_3_4,
                PesaTrackDatabase.MIGRATION_4_5,
                PesaTrackDatabase.MIGRATION_5_6,
                PesaTrackDatabase.MIGRATION_6_7,
                PesaTrackDatabase.MIGRATION_7_8,
                PesaTrackDatabase.MIGRATION_8_9
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideExpenseDao(database: PesaTrackDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(database: PesaTrackDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    @Singleton
    fun provideRecipientCategoryMappingDao(database: PesaTrackDatabase): RecipientCategoryMappingDao {
        return database.recipientCategoryMappingDao()
    }

    @Provides
    @Singleton
    fun provideBudgetDao(database: PesaTrackDatabase): BudgetDao {
        return database.budgetDao()
    }

    // ==================== System Services ====================

    @Provides
    @Singleton
    fun provideTelephonyManager(
        @ApplicationContext context: Context
    ): TelephonyManager {
        return context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
}
