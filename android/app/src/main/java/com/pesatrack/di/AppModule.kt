package com.pesatrack.di

import android.content.Context
import androidx.room.Room
import com.pesatrack.BuildConfig
import com.pesatrack.data.local.database.PesaTrackDatabase
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.remote.api.PesaTrackApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
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
    
    // ==================== Networking ====================
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun providePesaTrackApi(retrofit: Retrofit): PesaTrackApi {
        return retrofit.create(PesaTrackApi::class.java)
    }
}
