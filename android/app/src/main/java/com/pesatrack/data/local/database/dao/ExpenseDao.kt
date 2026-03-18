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
     * Get uncategorized expenses (excludes pass-through money)
     */
    @Query("SELECT * FROM expenses WHERE isCategorized = 0 AND isExcluded = 0 ORDER BY timestamp DESC")
    fun getUncategorizedExpenses(): Flow<List<ExpenseEntity>>
    
    /**
     * Get total expenses for a month (excludes pass-through money)
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses
        WHERE timestamp >= :startOfMonth AND timestamp < :endOfMonth
        AND isExcluded = 0
    """)
    fun getTotalForMonth(startOfMonth: Long, endOfMonth: Long): Flow<Double>
    
    /**
     * Get total expenses by category for a month (excludes pass-through money)
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses
        WHERE categoryId = :categoryId
        AND timestamp >= :startOfMonth AND timestamp < :endOfMonth
        AND isExcluded = 0
    """)
    fun getTotalByCategoryForMonth(categoryId: Long, startOfMonth: Long, endOfMonth: Long): Flow<Double>
    
    /**
     * Toggle the isExcluded flag on an expense (for pass-through money)
     */
    @Query("UPDATE expenses SET isExcluded = :isExcluded WHERE id = :expenseId")
    suspend fun setExcluded(expenseId: Long, isExcluded: Boolean)
    
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

    // ==================== Bulk Operations (Historical Import) ====================

    /**
     * Insert multiple expenses at once, ignoring duplicates (by transactionId unique index)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long>

    /**
     * Get existing transaction IDs from a list (for batch deduplication before insert)
     */
    @Query("SELECT transactionId FROM expenses WHERE transactionId IN (:ids)")
    suspend fun getExistingTransactionIds(ids: List<String>): List<String>

    /**
     * Bulk update category for multiple expenses matching a recipient.
     * Used by batch categorize screen to apply category to all expenses from a recipient.
     */
    @Query("""
        UPDATE expenses
        SET categoryId = :categoryId, isCategorized = 1
        WHERE recipient = :recipient AND isCategorized = 0 AND isExcluded = 0
    """)
    suspend fun updateCategoryByRecipient(recipient: String, categoryId: Long): Int

    /**
     * Bulk update category for multiple expenses matching a recipientName.
     * Used when the normalized name is in recipientName rather than recipient.
     */
    @Query("""
        UPDATE expenses
        SET categoryId = :categoryId, isCategorized = 1
        WHERE recipientName = :recipientName AND isCategorized = 0 AND isExcluded = 0
    """)
    suspend fun updateCategoryByRecipientName(recipientName: String, categoryId: Long): Int

    /**
     * Get uncategorized expenses grouped by recipient for batch categorize.
     * Returns distinct recipients with count and total amount.
     * Excludes ignored/excluded expenses.
     */
    @Query("""
        SELECT
            COALESCE(recipientName, recipient) as recipientKey,
            recipient,
            recipientName,
            paymentType,
            COUNT(*) as transactionCount,
            SUM(amount) as totalAmount
        FROM expenses
        WHERE isCategorized = 0 AND isExcluded = 0
        GROUP BY COALESCE(recipientName, recipient)
        ORDER BY transactionCount DESC
    """)
    suspend fun getUncategorizedGroupedByRecipient(): List<RecipientGroup>

    /**
     * Get individual uncategorized expenses for a specific recipient key.
     * Used by the expandable review UI in batch categorize.
     * Excludes ignored/excluded expenses.
     */
    @Query("""
        SELECT * FROM expenses
        WHERE isCategorized = 0 AND isExcluded = 0
        AND COALESCE(recipientName, recipient) = :recipientKey
        ORDER BY timestamp DESC
    """)
    suspend fun getUncategorizedByRecipientKey(recipientKey: String): List<ExpenseEntity>

    /**
     * Bulk exclude/ignore all uncategorized expenses matching a recipient.
     * Used by batch categorize "Ignore" action.
     */
    @Query("""
        UPDATE expenses
        SET isExcluded = 1
        WHERE recipient = :recipient AND isCategorized = 0 AND isExcluded = 0
    """)
    suspend fun excludeByRecipient(recipient: String): Int

    /**
     * Bulk exclude/ignore all uncategorized expenses matching a recipientName.
     * Used by batch categorize "Ignore" action.
     */
    @Query("""
        UPDATE expenses
        SET isExcluded = 1
        WHERE recipientName = :recipientName AND isCategorized = 0 AND isExcluded = 0
    """)
    suspend fun excludeByRecipientName(recipientName: String): Int

    /**
     * Get total count of expenses
     */
    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getTotalExpenseCount(): Int

    // ==================== Excel Import Matching ====================

    /**
     * Get the min and max timestamps of SMS-imported expenses.
     * Used to determine the date range covered by SMS imports,
     * so Excel rows outside this range are not imported as standalone.
     */
    @Query("""
        SELECT MIN(timestamp) as minTimestamp, MAX(timestamp) as maxTimestamp
        FROM expenses
        WHERE source IN ('SMS_PARSED', 'SMS_BANK')
    """)
    suspend fun getSmsCoveredDateRange(): DateRangeResult?

    /**
     * Find an uncategorized expense matching an amount (±tolerance) within a date window.
     * Used by Excel import to match Excel rows to SMS-imported expenses.
     * Returns the closest amount match first.
     */
    @Query("""
        SELECT * FROM expenses
        WHERE isCategorized = 0
          AND isExcluded = 0
          AND ABS(amount - :amount) < :tolerance
          AND timestamp >= :dayStartMs
          AND timestamp <= :dayEndMs
        ORDER BY ABS(amount - :amount) ASC
        LIMIT 1
    """)
    suspend fun findMatchByAmountAndDate(
        amount: Double,
        tolerance: Double,
        dayStartMs: Long,
        dayEndMs: Long
    ): ExpenseEntity?

    /**
     * Check if any expense (categorized or not) exists at a given amount+date.
     * Used to avoid importing standalone Excel duplicates.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM expenses
            WHERE ABS(amount - :amount) < :tolerance
              AND timestamp >= :dayStartMs
              AND timestamp <= :dayEndMs
        )
    """)
    suspend fun expenseExistsAtAmountAndDate(
        amount: Double,
        tolerance: Double,
        dayStartMs: Long,
        dayEndMs: Long
    ): Boolean
}

/**
 * Result class for SMS date range query
 */
data class DateRangeResult(
    val minTimestamp: Long?,
    val maxTimestamp: Long?
)

/**
 * Result class for grouped uncategorized expenses query
 */
data class RecipientGroup(
    val recipientKey: String,
    val recipient: String,
    val recipientName: String?,
    val paymentType: String,
    val transactionCount: Int,
    val totalAmount: Double
)
