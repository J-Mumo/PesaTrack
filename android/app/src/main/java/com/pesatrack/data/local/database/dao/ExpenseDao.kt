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
     * Delete all expenses
     */
    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
    
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
     * Get total expenses since a given timestamp (excludes pass-through money).
     * Used for "last 7 days" rolling total on Home screen.
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses
        WHERE timestamp >= :sinceMs
        AND isExcluded = 0
    """)
    fun getTotalSince(sinceMs: Long): Flow<Double>
    
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
     * Load all expenses within a date range for in-memory matching.
     * Used by Excel import to batch-load instead of per-row queries.
     */
    @Query("""
        SELECT * FROM expenses
        WHERE timestamp >= :startMs AND timestamp <= :endMs
    """)
    suspend fun getExpensesInRange(startMs: Long, endMs: Long): List<ExpenseEntity>

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

    // ==================== Yearly Analytics Queries ====================

    /**
     * Get total spending for an entire year.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0)
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startOfYear AND timestamp < :endOfYear
    """)
    suspend fun getAnnualTotal(startOfYear: Long, endOfYear: Long): Double

    /**
     * Get monthly totals for a specific year (12 data points for overlay chart).
     * Returns one row per month with spending total.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            CAST(strftime('%m', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS monthNumber,
            COALESCE(SUM(amount), 0.0) AS total
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startOfYear AND timestamp < :endOfYear
        GROUP BY monthNumber
        ORDER BY monthNumber ASC
    """)
    suspend fun getMonthlyTotalsForYear(
        startOfYear: Long,
        endOfYear: Long
    ): List<YearMonthTotal>

    /**
     * Get category totals for a full year.
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
          AND e.timestamp >= :startOfYear AND e.timestamp < :endOfYear
        GROUP BY e.categoryId
        ORDER BY total DESC
    """)
    suspend fun getCategoryTotalsForYear(
        startOfYear: Long,
        endOfYear: Long
    ): List<CategoryTotal>

    /**
     * Get top spenders (recipients) for a full year.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            COALESCE(recipientName, recipient) AS recipientKey,
            COALESCE(SUM(amount), 0.0) AS total,
            COUNT(*) AS transactionCount
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startOfYear AND timestamp < :endOfYear
        GROUP BY recipientKey
        ORDER BY total DESC
        LIMIT :limit
    """)
    suspend fun getTopSpendersForYear(
        startOfYear: Long,
        endOfYear: Long,
        limit: Int = 10
    ): List<TopSpender>

    /**
     * Get payment type breakdown for a full year.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            paymentType,
            COALESCE(SUM(amount), 0.0) AS total,
            COUNT(*) AS transactionCount
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startOfYear AND timestamp < :endOfYear
        GROUP BY paymentType
        ORDER BY total DESC
    """)
    suspend fun getPaymentTypeBreakdownForYear(
        startOfYear: Long,
        endOfYear: Long
    ): List<PaymentTypeTotal>

    // ==================== Recipient Search Queries ====================

    /**
     * Search for recipients matching a query string within a specific month.
     * Returns all matching recipients with total amount and transaction count.
     * Used by the recipient search feature in the Analytics screen.
     * No LIMIT — shows all matches so the user can find anyone.
     */
    @Query("""
        SELECT
            COALESCE(recipientName, recipient) AS recipientKey,
            COALESCE(SUM(amount), 0.0) AS total,
            COUNT(*) AS transactionCount
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startMs AND timestamp < :endMs
          AND COALESCE(recipientName, recipient) LIKE '%' || :query || '%'
        GROUP BY recipientKey
        ORDER BY total DESC
    """)
    suspend fun searchRecipientSpendingForMonth(
        query: String,
        startMs: Long,
        endMs: Long
    ): List<TopSpender>

    /**
     * Search for recipients matching a query string within a specific year.
     * Returns all matching recipients with total amount and transaction count.
     */
    @Query("""
        SELECT
            COALESCE(recipientName, recipient) AS recipientKey,
            COALESCE(SUM(amount), 0.0) AS total,
            COUNT(*) AS transactionCount
        FROM expenses
        WHERE isExcluded = 0
          AND timestamp >= :startMs AND timestamp < :endMs
          AND COALESCE(recipientName, recipient) LIKE '%' || :query || '%'
        GROUP BY recipientKey
        ORDER BY total DESC
    """)
    suspend fun searchRecipientSpendingForYear(
        query: String,
        startMs: Long,
        endMs: Long
    ): List<TopSpender>

    // ==================== Investment Queries ====================

    /**
     * Get total investment spending for a month.
     * Sums expenses categorized under Investment & Savings group (ID 18)
     * by joining categories where parentId = 18 or id = 18.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0.0)
        FROM expenses e
        INNER JOIN categories c ON e.categoryId = c.id
        WHERE e.isExcluded = 0
        AND e.timestamp >= :startOfMonth AND e.timestamp < :endOfMonth
        AND (c.parentId = 18 OR c.id = 18)
    """)
    fun getInvestmentTotalForMonth(startOfMonth: Long, endOfMonth: Long): Flow<Double>

    // ==================== Weekly Snapshot Queries ====================

    /**
     * Get the top spending category (by group name) within a date range.
     * Returns the parent group name and total for the group with the highest spend.
     * Excludes pass-through expenses.
     */
    @Query("""
        SELECT
            COALESCE(pg.name, c.name, 'Uncategorized') AS categoryName,
            COALESCE(SUM(e.amount), 0.0) AS total
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.id
        LEFT JOIN categories pg ON c.parentId = pg.id
        WHERE e.isExcluded = 0
          AND e.timestamp >= :startMs AND e.timestamp < :endMs
        GROUP BY COALESCE(pg.id, c.id)
        ORDER BY total DESC
        LIMIT 1
    """)
    suspend fun getTopCategoryInRange(startMs: Long, endMs: Long): TopCategoryResult?

    // ==================== Budget Queries ====================

    /**
     * Get total spending (all non-excluded expenses) in a date range.
     * Used by the "Total Spending" budget to compute actual spending.
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses
        WHERE isExcluded = 0
        AND timestamp >= :startMs AND timestamp < :endMs
    """)
    suspend fun getTotalSpendingInRange(startMs: Long, endMs: Long): Double

    /**
     * Get spending for a specific category group in a date range.
     * Joins categories to sum all sub-categories belonging to a group.
     * Also includes expenses categorized directly to the group ID (safety).
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0.0)
        FROM expenses e
        INNER JOIN categories c ON e.categoryId = c.id
        WHERE e.isExcluded = 0
        AND e.timestamp >= :startMs AND timestamp < :endMs
        AND (c.parentId = :groupId OR c.id = :groupId)
    """)
    suspend fun getGroupSpendingInRange(groupId: Long, startMs: Long, endMs: Long): Double

    /**
     * Get spending for a specific sub-category in a date range.
     * Only counts expenses with exactly this categoryId (not the whole group).
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM expenses
        WHERE isExcluded = 0
        AND categoryId = :categoryId
        AND timestamp >= :startMs AND timestamp < :endMs
    """)
    suspend fun getSubcategorySpendingInRange(categoryId: Long, startMs: Long, endMs: Long): Double

    /**
     * Get count of categorized (non-excluded) expenses.
     * Used for budget prompt trigger logic (show prompt after ≥20 categorized expenses).
     */
    @Query("SELECT COUNT(*) FROM expenses WHERE isCategorized = 1 AND isExcluded = 0")
    suspend fun getCategorizedExpenseCount(): Int

    // ==================== Export Queries ====================

    /**
     * Get all expenses joined with category name for CSV export.
     * Returns all expenses ordered by date descending.
     */
    @Query("""
        SELECT
            e.id,
            e.transactionId,
            e.amount,
            e.recipient,
            e.recipientName,
            COALESCE(c.name, 'Uncategorized') AS categoryName,
            COALESCE(pc.name, '') AS groupName,
            e.paymentType,
            e.source,
            e.notes,
            e.timestamp,
            e.isCategorized,
            e.isExcluded
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.id
        LEFT JOIN categories pc ON c.parentId = pc.id
        ORDER BY e.timestamp DESC
        """)
        suspend fun getAllExpensesForExport(): List<ExportExpense>
    
        // ==================== Recurring Expense Detection ====================
    
        /**
         * Get all non-excluded expenses from a date range for recurring expense detection.
         * Returns raw rows ordered by recipient key then timestamp for grouping.
         * No schema migration required — query-only addition.
         */
        @Query("""
            SELECT
                COALESCE(recipientName, recipient) as recipientKey,
                recipient,
                recipientName,
                paymentType,
                categoryId,
                amount,
                timestamp
            FROM expenses
            WHERE isExcluded = 0
              AND timestamp >= :sinceTimestamp
            ORDER BY recipientKey, timestamp ASC
        """)
        suspend fun getExpensesForRecurrenceDetection(sinceTimestamp: Long): List<RecurrenceCandidate>
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

/**
 * Monthly total within a specific year (for YoY overlay chart).
 * monthNumber is 1-12 (January=1, December=12).
 */
data class YearMonthTotal(
    val monthNumber: Int,
    val total: Double
)

/**
 * Expense row joined with category/group names for CSV export.
 */
data class ExportExpense(
    val id: Long,
    val transactionId: String?,
    val amount: Double,
    val recipient: String,
    val recipientName: String?,
    val categoryName: String,
    val groupName: String,
    val paymentType: String,
    val source: String,
    val notes: String?,
    val timestamp: Long,
    val isCategorized: Boolean,
    val isExcluded: Boolean
)

// ==================== Weekly Snapshot Result Classes ====================

/**
 * Top category result for a date range (weekly snapshot card)
 */
data class TopCategoryResult(
    val categoryName: String,
    val total: Double
)

// ==================== Recurring Expense Detection Result Classes ====================

/**
 * Raw expense row for recurring expense detection.
 * One row per expense, ordered by recipientKey then timestamp.
 * Grouped in-memory by [com.pesatrack.services.RecurringExpenseService].
 */
data class RecurrenceCandidate(
    val recipientKey: String,
    val recipient: String,
    val recipientName: String?,
    val paymentType: String,
    val categoryId: Long?,
    val amount: Double,
    val timestamp: Long
)
