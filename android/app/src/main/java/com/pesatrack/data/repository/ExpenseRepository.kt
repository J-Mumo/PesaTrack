package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.CategoryMonthlyTotal
import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.DailyTotal
import com.pesatrack.data.local.database.dao.DateRangeResult
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.PaymentTypeTotal
import com.pesatrack.data.local.database.dao.RecipientGroup
import com.pesatrack.data.local.database.dao.TopSpender
import com.pesatrack.data.local.database.entities.ExpenseEntity
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for expense data operations
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {

    /**
     * Get all expenses as Flow
     */
    fun getAllExpenses(): Flow<List<Expense>> {
        return expenseDao.getAllExpenses().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get expenses for current month
     */
    fun getExpensesForCurrentMonth(): Flow<List<Expense>> {
        val (start, end) = getCurrentMonthRange()
        return expenseDao.getExpensesForMonth(start, end).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get uncategorized expenses
     */
    fun getUncategorizedExpenses(): Flow<List<Expense>> {
        return expenseDao.getUncategorizedExpenses().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get total for current month
     */
    fun getTotalForCurrentMonth(): Flow<Double> {
        val (start, end) = getCurrentMonthRange()
        return expenseDao.getTotalForMonth(start, end)
    }

    /**
     * Save a new expense
     */
    suspend fun saveExpense(expense: Expense): Long {
        return expenseDao.insert(expense.toEntity())
    }

    /**
     * Update expense category
     */
    suspend fun updateCategory(expenseId: Long, categoryId: Long) {
        expenseDao.updateCategory(expenseId, categoryId)
    }

    /**
     * Check if transaction already exists
     */
    suspend fun transactionExists(transactionId: String): Boolean {
        return expenseDao.transactionExists(transactionId)
    }

    /**
     * Get expense by ID
     */
    suspend fun getExpenseById(id: Long): Expense? {
        return expenseDao.getById(id)?.toDomain()
    }

    /**
     * Delete an expense
     */
    suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(expense.toEntity())
    }

    // ==================== Bulk Operations (Historical Import) ====================

    /**
     * Insert multiple expenses at once, ignoring duplicates.
     * Returns list of inserted row IDs (-1 for ignored duplicates).
     */
    suspend fun saveExpenses(expenses: List<Expense>): List<Long> {
        return expenseDao.insertAll(expenses.map { it.toEntity() })
    }

    /**
     * Get existing transaction IDs from a list (for batch deduplication)
     */
    suspend fun getExistingTransactionIds(ids: List<String>): List<String> {
        // Room IN queries have a limit of ~999 items; chunk if needed
        return ids.chunked(500).flatMap { chunk ->
            expenseDao.getExistingTransactionIds(chunk)
        }
    }

    /**
     * Bulk update category for all uncategorized expenses from a specific recipient.
     * Returns count of updated expenses.
     */
    suspend fun updateCategoryByRecipient(recipient: String, categoryId: Long): Int {
        return expenseDao.updateCategoryByRecipient(recipient, categoryId)
    }

    /**
     * Bulk update category by recipientName.
     * Returns count of updated expenses.
     */
    suspend fun updateCategoryByRecipientName(recipientName: String, categoryId: Long): Int {
        return expenseDao.updateCategoryByRecipientName(recipientName, categoryId)
    }

    /**
     * Get uncategorized expenses grouped by recipient for batch categorize screen.
     */
    suspend fun getUncategorizedGroupedByRecipient(): List<RecipientGroup> {
        return expenseDao.getUncategorizedGroupedByRecipient()
    }

    /**
     * Get individual uncategorized expenses for a specific recipient key.
     * Used by the expandable review UI in batch categorize.
     */
    suspend fun getUncategorizedByRecipientKey(recipientKey: String): List<Expense> {
        return expenseDao.getUncategorizedByRecipientKey(recipientKey).map { it.toDomain() }
    }

    /**
     * Get total expense count
     */
    suspend fun getTotalExpenseCount(): Int {
        return expenseDao.getTotalExpenseCount()
    }

    /**
     * Toggle the isExcluded flag on an expense (for pass-through money)
     */
    suspend fun setExcluded(expenseId: Long, isExcluded: Boolean) {
        expenseDao.setExcluded(expenseId, isExcluded)
    }

    /**
     * Bulk exclude/ignore all uncategorized expenses matching a recipient.
     * Used by batch categorize "Ignore" action to dismiss an entire group.
     *
     * @return Total number of expenses excluded
     */
    suspend fun excludeByRecipientGroup(recipient: String, recipientName: String?): Int {
        var excluded = 0
        if (!recipientName.isNullOrBlank()) {
            excluded += expenseDao.excludeByRecipientName(recipientName)
        }
        if (recipient.isNotBlank()) {
            excluded += expenseDao.excludeByRecipient(recipient)
        }
        return excluded
    }

    // ==================== Excel Import Matching ====================

    /**
     * Get the min/max timestamps of SMS-imported expenses.
     * Returns null if no SMS expenses exist.
     */
    suspend fun getSmsCoveredDateRange(): DateRangeResult? {
        return expenseDao.getSmsCoveredDateRange()
    }

    /**
     * Find an uncategorized expense matching amount (±tolerance) within a date window.
     * Used by Excel import to match Excel rows to SMS-imported expenses.
     */
    suspend fun findMatchByAmountAndDate(
        amount: Double,
        tolerance: Double,
        dayStartMs: Long,
        dayEndMs: Long
    ): Expense? {
        return expenseDao.findMatchByAmountAndDate(amount, tolerance, dayStartMs, dayEndMs)
            ?.toDomain()
    }

    /**
     * Check if any expense exists at a given amount+date.
     * Used to avoid importing standalone Excel duplicates.
     */
    suspend fun expenseExistsAtAmountAndDate(
        amount: Double,
        tolerance: Double,
        dayStartMs: Long,
        dayEndMs: Long
    ): Boolean {
        return expenseDao.expenseExistsAtAmountAndDate(amount, tolerance, dayStartMs, dayEndMs)
    }

    // ==================== Analytics ====================

    /**
     * Get monthly totals for the last N months (for trend chart).
     * Returns list ordered chronologically.
     */
    suspend fun getMonthlyTotals(monthsBack: Int = 6): List<MonthlyTotal> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -(monthsBack - 1))
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return expenseDao.getMonthlyTotals(calendar.timeInMillis)
    }

    /**
     * Get category totals for a specific month.
     */
    suspend fun getCategoryTotalsForMonth(year: Int, month: Int): List<CategoryTotal> {
        val (start, end) = getMonthRange(year, month)
        return expenseDao.getCategoryTotalsForMonth(start, end)
    }

    /**
     * Get daily totals for a specific month.
     */
    suspend fun getDailyTotalsForMonth(year: Int, month: Int): List<DailyTotal> {
        val (start, end) = getMonthRange(year, month)
        return expenseDao.getDailyTotalsForMonth(start, end)
    }

    /**
     * Get top spenders for a specific month.
     */
    suspend fun getTopSpendersForMonth(year: Int, month: Int, limit: Int = 10): List<TopSpender> {
        val (start, end) = getMonthRange(year, month)
        return expenseDao.getTopSpendersForMonth(start, end, limit)
    }

    /**
     * Get payment type breakdown for a specific month.
     */
    suspend fun getPaymentTypeBreakdownForMonth(year: Int, month: Int): List<PaymentTypeTotal> {
        val (start, end) = getMonthRange(year, month)
        return expenseDao.getPaymentTypeBreakdownForMonth(start, end)
    }

    /**
     * Get monthly totals grouped by category for the last N months.
     * Used for variable-spend category trend detection (CV analysis).
     */
    suspend fun getCategoryMonthlyTrend(monthsBack: Int = 6): List<CategoryMonthlyTotal> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -(monthsBack - 1))
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return expenseDao.getCategoryMonthlyTotals(calendar.timeInMillis)
    }

    /**
     * Get total for a specific month (non-Flow, for analytics).
     */
    suspend fun getTotalForMonth(year: Int, month: Int): Double {
        val (start, end) = getMonthRange(year, month)
        // Reuse the DAO query — but we need a suspend version.
        // For simplicity, sum from category totals:
        return getCategoryTotalsForMonth(year, month).sumOf { it.total }
    }

    /**
     * Get start and end timestamps for a specific year/month.
     * Month is 1-based (January = 1).
     */
    fun getMonthRange(year: Int, month: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1) // Calendar.MONTH is 0-based
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        val end = calendar.timeInMillis
        return Pair(start, end)
    }

    /**
     * Get start and end timestamps for current month
     */
    private fun getCurrentMonthRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()

        // Start of current month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        // Start of next month
        calendar.add(Calendar.MONTH, 1)
        val end = calendar.timeInMillis

        return Pair(start, end)
    }

    // Extension functions for mapping

    private fun ExpenseEntity.toDomain(): Expense {
        return Expense(
            id = id,
            transactionId = transactionId,
            amount = amount,
            recipient = recipient,
            recipientName = recipientName,
            categoryId = categoryId,
            paymentType = PaymentType.fromString(paymentType),
            source = ExpenseSource.fromString(source),
            notes = notes,
            rawSms = rawSms,
            timestamp = timestamp,
            createdAt = createdAt,
            isCategorized = isCategorized,
            isExcluded = isExcluded
        )
    }

    private fun Expense.toEntity(): ExpenseEntity {
        return ExpenseEntity(
            id = id,
            transactionId = transactionId,
            amount = amount,
            recipient = recipient,
            recipientName = recipientName,
            categoryId = categoryId,
            paymentType = paymentType.name,
            source = source.name,
            notes = notes,
            rawSms = rawSms,
            timestamp = timestamp,
            createdAt = createdAt,
            isCategorized = isCategorized,
            isExcluded = isExcluded
        )
    }
}
