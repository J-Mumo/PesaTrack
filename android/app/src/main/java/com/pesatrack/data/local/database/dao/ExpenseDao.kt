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

    // ==================== Analytics Queries ====================

    /**
     * Get monthly totals since a given timestamp.
     * Groups by year-month and returns totals ordered chronologically.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS monthKey,
            COALESCE(SUM(amount), 0.0) AS total
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :sinceTimestamp
        GROUP BY monthKey
        ORDER BY monthKey ASC
    """)
    suspend fun getMonthlyTotals(sinceTimestamp: Long): List<MonthlyTotal>

    /**
     * Get category totals for a specific month.
     * Joins with categories table to get name/color.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            c.id AS categoryId,
            COALESCE(c.name, 'Uncategorized') AS categoryName,
            c.color AS categoryColor,
            c.parentId AS parentId,
            COALESCE(SUM(e.amount), 0.0) AS total,
            COUNT(e.id) AS transactionCount
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.isExcluded = 0
          AND e.timestamp >= :startOfMonth AND e.timestamp < :endOfMonth
        GROUP BY e.categoryId
        ORDER BY total DESC
    """)
    suspend fun getCategoryTotalsForMonth(
        startOfMonth: Long,
        endOfMonth: Long
    ): List<CategoryTotal>

    /**
     * Get daily totals for a specific month.
     * Returns one row per day with spending total.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            CAST(strftime('%d', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS dayOfMonth,
            COALESCE(SUM(amount), 0.0) AS total
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startOfMonth AND timestamp < :endOfMonth
        GROUP BY dayOfMonth
        ORDER BY dayOfMonth ASC
    """)
    suspend fun getDailyTotalsForMonth(
        startOfMonth: Long,
        endOfMonth: Long
    ): List<DailyTotal>

    /**
     * Get top spenders (recipients) for a specific month.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            COALESCE(recipientName, recipient) AS recipientKey,
            COALESCE(SUM(amount), 0.0) AS total,
            COUNT(*) AS transactionCount
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startOfMonth AND timestamp < :endOfMonth
        GROUP BY recipientKey
        ORDER BY total DESC
        LIMIT :limit
    """)
    suspend fun getTopSpendersForMonth(
        startOfMonth: Long,
        endOfMonth: Long,
        limit: Int = 10
    ): List<TopSpender>

    /**
     * Get payment type breakdown for a specific month.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            paymentType,
            COALESCE(SUM(amount), 0.0) AS total,
            COUNT(*) AS transactionCount
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startOfMonth AND timestamp < :endOfMonth
        GROUP BY paymentType
        ORDER BY total DESC
    """)
    suspend fun getPaymentTypeBreakdownForMonth(
        startOfMonth: Long,
        endOfMonth: Long
    ): List<PaymentTypeTotal>

    /**
     * Get monthly totals grouped by category since a given timestamp.
     * Used for variable-spend category trend detection (CV analysis).
     * Excludes pass-through and uncategorized expenses.
     */
    @Query("""
        SELECT
            e.categoryId,
            COALESCE(c.name, 'Uncategorized') AS categoryName,
            c.color AS categoryColor,
            strftime('%Y-%m', e.timestamp / 1000, 'unixepoch', 'localtime') AS monthKey,
            COALESCE(SUM(e.amount), 0.0) AS total
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.isExcluded = 0
          AND e.timestamp >= :sinceTimestamp
          AND e.categoryId IS NOT NULL
        GROUP BY e.categoryId, monthKey
        ORDER BY e.categoryId, monthKey ASC
    """)
    suspend fun getCategoryMonthlyTotals(sinceTimestamp: Long): List<CategoryMonthlyTotal>
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

// ==================== Analytics Result Classes ====================

/**
 * Monthly spending total (for trend chart)
 */
data class MonthlyTotal(
    val monthKey: String,  // "2026-03"
    val total: Double
)

/**
 * Category spending total (for category breakdown)
 */
data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String,
    val categoryColor: String?,
    val parentId: Long?,
    val total: Double,
    val transactionCount: Int
)

/**
 * Daily spending total (for daily chart)
 */
data class DailyTotal(
    val dayOfMonth: Int,
    val total: Double
)

/**
 * Top spender / recipient (for top spenders list)
 */
data class TopSpender(
    val recipientKey: String,
    val total: Double,
    val transactionCount: Int
)

/**
 * Payment type total (for payment type breakdown)
 */
data class PaymentTypeTotal(
    val paymentType: String,
    val total: Double,
    val transactionCount: Int
)

/**
 * Category monthly total (for variable-spend trend detection).
 * One row per category per month.
 */
data class CategoryMonthlyTotal(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String?,
    val monthKey: String,   // "2026-03"
    val total: Double
)
