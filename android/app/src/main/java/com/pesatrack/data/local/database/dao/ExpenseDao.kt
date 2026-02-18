package com.pesatrack.data.local.database.dao

import androidx.room.*
import com.pesatrack.data.local.database.entities.ExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Expense operations
 */
@Dao
interface ExpenseDao {
    
    /**
     * Insert a new expense
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long
    
    /**
     * Update an existing expense
     */
    @Update
    suspend fun update(expense: ExpenseEntity)
    
    /**
     * Delete an expense
     */
    @Delete
    suspend fun delete(expense: ExpenseEntity)
    
    /**
     * Get expense by ID
     */
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?
    
    /**
     * Get expense by M-PESA transaction ID
     */
    @Query("SELECT * FROM expenses WHERE transactionId = :transactionId")
    suspend fun getByTransactionId(transactionId: String): ExpenseEntity?
    
    /**
     * Get all expenses ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    
    /**
     * Get expenses for a specific month
     */
    @Query("""
        SELECT * FROM expenses 
        WHERE timestamp >= :startOfMonth AND timestamp < :endOfMonth 
        ORDER BY timestamp DESC
    """)
    fun getExpensesForMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<ExpenseEntity>>
    
    /**
     * Get expenses by category
     */
    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY timestamp DESC")
    fun getExpensesByCategory(categoryId: Long): Flow<List<ExpenseEntity>>
    
    /**
     * Get uncategorized expenses
     */
    @Query("SELECT * FROM expenses WHERE isCategorized = 0 ORDER BY timestamp DESC")
    fun getUncategorizedExpenses(): Flow<List<ExpenseEntity>>
    
    /**
     * Get total expenses for a month
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses 
        WHERE timestamp >= :startOfMonth AND timestamp < :endOfMonth
    """)
    fun getTotalForMonth(startOfMonth: Long, endOfMonth: Long): Flow<Double>
    
    /**
     * Get total expenses by category for a month
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses 
        WHERE categoryId = :categoryId 
        AND timestamp >= :startOfMonth AND timestamp < :endOfMonth
    """)
    fun getTotalByCategoryForMonth(categoryId: Long, startOfMonth: Long, endOfMonth: Long): Flow<Double>
    
    /**
     * Update expense category
     */
    @Query("UPDATE expenses SET categoryId = :categoryId, isCategorized = 1 WHERE id = :expenseId")
    suspend fun updateCategory(expenseId: Long, categoryId: Long)
    
    /**
     * Check if transaction ID already exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE transactionId = :transactionId)")
    suspend fun transactionExists(transactionId: String): Boolean
}
