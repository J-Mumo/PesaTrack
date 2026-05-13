package com.pesatrack.di

import android.content.Context
import androidx.room.Room
import com.pesatrack.data.local.database.PesaTrackDatabase
import com.pesatrack.data.local.database.dao.BudgetDao
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.CategoryRuleDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.IncomeDao
import com.pesatrack.data.local.database.dao.RecipientCategoryMappingDao
import com.pesatrack.data.local.database.dao.ReportSnapshotDao
import com.pesatrack.services.SampleDataService
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
                PesaTrackDatabase.MIGRATION_8_9,
                PesaTrackDatabase.MIGRATION_9_10,
                PesaTrackDatabase.MIGRATION_10_11,
                PesaTrackDatabase.MIGRATION_11_12,
                PesaTrackDatabase.MIGRATION_12_13,
                PesaTrackDatabase.MIGRATION_13_14,
                PesaTrackDatabase.MIGRATION_14_15,
                PesaTrackDatabase.MIGRATION_15_16
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

    @Provides
    @Singleton
    fun provideCategoryRuleDao(database: PesaTrackDatabase): CategoryRuleDao {
        return database.categoryRuleDao()
    }

    @Provides
    @Singleton
    fun provideIncomeDao(database: PesaTrackDatabase): IncomeDao {
        return database.incomeDao()
    }

    @Provides
    @Singleton
    fun provideReportSnapshotDao(database: PesaTrackDatabase): ReportSnapshotDao {
        return database.reportSnapshotDao()
    }

    @Provides
    @Singleton
    fun provideSampleDataService(
        expenseDao: ExpenseDao,
        categoryDao: CategoryDao,
        budgetDao: BudgetDao,
        incomeDao: IncomeDao
    ): SampleDataService {
        return SampleDataService(expenseDao, categoryDao, budgetDao, incomeDao)
    }
}
