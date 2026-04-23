package com.pesatrack.services

import android.util.Log
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.RecurrenceCandidate
import com.pesatrack.domain.models.AmountPattern
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.domain.models.RecurrenceCycle
import com.pesatrack.domain.models.RecurringExpense
import com.pesatrack.domain.models.RecurringExpenseSummary
import com.pesatrack.domain.models.RecurringPeriodInfo
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Service for detecting recurring expense patterns from historical data.
 *
 * Pure computation layer — no new database tables or schema migrations.
 * Queries existing expenses from [ExpenseDao], groups by recipient,
 * analyses temporal intervals, and produces [RecurringExpense] domain objects.
 *
 * Results are cached in memory with a configurable TTL (default 30 minutes).
 *
 * Used by:
 * - [AnalyticsViewModel] for recurring vs one-time spending split
 * - [ForecastService] for recurring-aware budget projections
 * - [RecurringReminderWorker] for upcoming/overdue notifications
 */
@Singleton
class RecurringExpenseService @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) {

    // ==================== Cache ====================

    @Volatile
    private var cachedResult: RecurringExpenseSummary? = null

    @Volatile
    private var lastRefreshTime: Long = 0

    // ==================== Public API ====================

    /**
     * Get the full recurring expense summary.
     * Returns cached result if fresh (within [CACHE_TTL_MS]), otherwise recomputes.
     */
    suspend fun getRecurringExpenses(forceRefresh: Boolean = false): RecurringExpenseSummary {
        val now = System.currentTimeMillis()
        val cached = cachedResult
        if (!forceRefresh && cached != null && (now - lastRefreshTime) < CACHE_TTL_MS) {
            return cached
        }
        return detectRecurringExpenses(now).also {
            cachedResult = it
            lastRefreshTime = now
        }
    }

    /**
     * Get recurring expenses expected within the next [withinDays] days.
     * Uses cached data if available.
     */
    suspend fun getUpcomingExpenses(withinDays: Int = 7): List<RecurringExpense> {
        val summary = getRecurringExpenses()
        val now = System.currentTimeMillis()
        val windowEnd = now + withinDays * MS_PER_DAY
        return summary.recurringExpenses.filter { it.nextExpected in now..windowEnd }
    }

    /**
     * Get recurring expenses that are overdue (expected date passed, no payment found).
     * Uses cached data if available.
     */
    suspend fun getOverdueExpenses(): List<RecurringExpense> {
        val summary = getRecurringExpenses()
        return summary.overdueExpenses
    }

    /**
     * Get the fixed monthly baseline — sum of FIXED-amount monthly-equivalent recurring expenses.
     * Quick access for forecast improvement without full summary.
     */
    suspend fun getFixedMonthlyBaseline(): Double {
        return getRecurringExpenses().fixedMonthlyTotal
    }

    /**
     * Compute [RecurringPeriodInfo] for a specific budget period.
     *
     * Determines which detected recurring expenses fall within the period,
     * which have already been paid, and which are still upcoming.
     * Used by [ForecastService] for recurring-aware projections.
     *
     * @param periodStart Start timestamp of the budget period (inclusive)
     * @param periodEnd End timestamp of the budget period (exclusive)
     */
    suspend fun getRecurringInfoForPeriod(
        periodStart: Long,
        periodEnd: Long
    ): RecurringPeriodInfo {
        val summary = getRecurringExpenses()
        val now = System.currentTimeMillis()

        var paidThisPeriod = 0.0
        var upcomingThisPeriod = 0.0

        for (recurring in summary.recurringExpenses) {
            // Only consider high-confidence recurring expenses
            if (recurring.confidence < MIN_CONFIDENCE_FOR_FORECAST) continue

            // Check if the recurring expense's expected date falls within this period
            val expectedInPeriod = isExpectedInPeriod(recurring, periodStart, periodEnd)
            if (!expectedInPeriod) continue

            // Check if it has already been paid in this period
            // (lastOccurrence is within the period range)
            if (recurring.lastOccurrence in periodStart until periodEnd) {
                paidThisPeriod += recurring.lastAmount
            } else if (recurring.nextExpected in now until periodEnd) {
                // Expected but not yet paid — add to upcoming
                upcomingThisPeriod += recurring.averageAmount
            }
        }

        return RecurringPeriodInfo(
            paidThisPeriod = paidThisPeriod,
            upcomingThisPeriod = upcomingThisPeriod
        )
    }

    /**
     * Invalidate the cached result. Call after a new expense is saved
     * to force recomputation on next access.
     */
    fun invalidateCache() {
        cachedResult = null
        lastRefreshTime = 0
    }

    // ==================== Detection Algorithm ====================

    /**
     * Core detection algorithm. Groups expenses by recipient, analyses intervals,
     * and produces [RecurringExpenseSummary].
     */
    private suspend fun detectRecurringExpenses(now: Long): RecurringExpenseSummary {
        // Load category names for display
        val categoryNames = loadCategoryNames()

        // Query expenses from the last DETECTION_WINDOW_MONTHS months
        val windowStart = getDetectionWindowStart(now)
        val candidates = expenseDao.getExpensesForRecurrenceDetection(windowStart)

        // Group by recipient key
        val grouped = candidates.groupBy { it.recipientKey }

        val detectedRecurring = mutableListOf<RecurringExpense>()

        for ((recipientKey, expenses) in grouped) {
            // Need at least MIN_OCCURRENCES to detect a pattern
            if (expenses.size < MIN_OCCURRENCES) continue

            // Detect the pattern for this recipient
            val recurring = analyzeRecipientPattern(
                recipientKey = recipientKey,
                expenses = expenses,
                categoryNames = categoryNames,
                now = now
            )
            if (recurring != null) {
                detectedRecurring.add(recurring)
            }
        }

        // Sort by confidence descending, then by monthly equivalent amount descending
        detectedRecurring.sortWith(
            compareByDescending<RecurringExpense> { it.confidence }
                .thenByDescending { it.monthlyEquivalent }
        )

        // Compute summary fields
        val totalMonthlyRecurring = detectedRecurring.sumOf { it.monthlyEquivalent }
        val fixedMonthlyTotal = detectedRecurring
            .filter { it.amountPattern == AmountPattern.FIXED }
            .sumOf { it.monthlyEquivalent }

        val upcomingThisWeek = detectedRecurring.filter {
            it.nextExpected in now..(now + 7 * MS_PER_DAY)
        }
        val overdueExpenses = detectedRecurring.filter { it.isOverdue }

        return RecurringExpenseSummary(
            totalMonthlyRecurring = totalMonthlyRecurring,
            fixedMonthlyTotal = fixedMonthlyTotal,
            recurringExpenses = detectedRecurring,
            upcomingThisWeek = upcomingThisWeek,
            overdueExpenses = overdueExpenses
        )
    }

    /**
     * Analyze a single recipient's expense history for recurring patterns.
     * Returns null if no recurring pattern is detected with sufficient confidence.
     */
    private fun analyzeRecipientPattern(
        recipientKey: String,
        expenses: List<RecurrenceCandidate>,
        categoryNames: Map<Long, String>,
        now: Long
    ): RecurringExpense? {
        // Expenses are already sorted by timestamp ASC from the DAO query

        // Step 1: Compute intervals between consecutive expenses (in days)
        val intervals = mutableListOf<Double>()
        for (i in 1 until expenses.size) {
            val diffMs = expenses[i].timestamp - expenses[i - 1].timestamp
            val diffDays = diffMs.toDouble() / MS_PER_DAY
            intervals.add(diffDays)
        }

        if (intervals.isEmpty()) return null

        // Step 2: Detect the dominant cycle by checking each cycle type
        val bestMatch = detectDominantCycle(intervals) ?: return null
        val (cycle, confidence) = bestMatch

        // Only accept if confidence meets minimum threshold
        if (confidence < MIN_CONFIDENCE) return null

        // Step 3: Analyze amount pattern
        val amounts = expenses.map { it.amount }
        val amountMean = amounts.average()
        val amountStdDev = stdDev(amounts, amountMean)
        val cv = if (amountMean > 0) amountStdDev / amountMean else 0.0
        val amountPattern = AmountPattern.fromCoefficientOfVariation(cv)

        // Step 4: Determine the most common category (if any)
        val categoryCounts = expenses
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { it.value.size }
        val primaryCategoryId = categoryCounts.maxByOrNull { it.value }?.key
        val categoryName = primaryCategoryId?.let { categoryNames[it] }

        // Step 5: Predict next occurrence
        val lastExpense = expenses.last()
        val nextExpected = predictNextOccurrence(cycle, expenses, now)
        val expectedDayOfMonth = if (cycle == RecurrenceCycle.MONTHLY) {
            detectExpectedDayOfMonth(expenses)
        } else null

        // Step 6: Determine if overdue
        val isOverdue = nextExpected < now

        // Step 7: Determine display name
        val displayName = lastExpense.recipientName
            ?: lastExpense.recipient

        return RecurringExpense(
            recipientKey = recipientKey,
            recipientDisplayName = displayName,
            categoryId = primaryCategoryId,
            categoryName = categoryName,
            cycle = cycle,
            averageAmount = amountMean,
            lastAmount = lastExpense.amount,
            amountPattern = amountPattern,
            confidence = confidence,
            occurrenceCount = expenses.size,
            lastOccurrence = lastExpense.timestamp,
            nextExpected = nextExpected,
            expectedDayOfMonth = expectedDayOfMonth,
            paymentType = PaymentType.fromString(lastExpense.paymentType),
            isOverdue = isOverdue
        )
    }

    /**
     * Detect the dominant recurrence cycle from a list of intervals.
     * Tests each [RecurrenceCycle] and returns the best match with its confidence.
     * Returns null if no cycle matches with sufficient confidence.
     */
    private fun detectDominantCycle(intervals: List<Double>): Pair<RecurrenceCycle, Double>? {
        var bestCycle: RecurrenceCycle? = null
        var bestConfidence = 0.0

        for (cycle in RecurrenceCycle.detectOrder) {
            val expected = cycle.expectedDays.toDouble()
            val tolerance = cycle.toleranceDays.toDouble()

            // Count how many intervals fall within the tolerance window
            val matchCount = intervals.count { interval ->
                abs(interval - expected) <= tolerance
            }

            val confidence = matchCount.toDouble() / intervals.size

            if (confidence > bestConfidence) {
                bestConfidence = confidence
                bestCycle = cycle
            }
        }

        return if (bestCycle != null && bestConfidence >= MIN_CONFIDENCE) {
            Pair(bestCycle, bestConfidence)
        } else {
            null
        }
    }

    /**
     * Predict the next occurrence timestamp for a recurring expense.
     *
     * For MONTHLY expenses: uses day-of-month from most common occurrence day.
     * For other cycles: adds the expected interval to the last occurrence.
     */
    private fun predictNextOccurrence(
        cycle: RecurrenceCycle,
        expenses: List<RecurrenceCandidate>,
        now: Long
    ): Long {
        val lastTimestamp = expenses.last().timestamp

        if (cycle == RecurrenceCycle.MONTHLY) {
            // Use the most common day of month
            val expectedDay = detectExpectedDayOfMonth(expenses) ?: 1
            val cal = Calendar.getInstance()
            cal.timeInMillis = lastTimestamp

            // Move to next month, set the expected day
            cal.add(Calendar.MONTH, 1)
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, expectedDay.coerceAtMost(maxDay))
            cal.set(Calendar.HOUR_OF_DAY, 12) // Noon to avoid timezone edge cases
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            var prediction = cal.timeInMillis

            // If prediction is still in the past, move forward another month
            while (prediction < now - OVERDUE_GRACE_DAYS * MS_PER_DAY) {
                cal.add(Calendar.MONTH, 1)
                val newMaxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, expectedDay.coerceAtMost(newMaxDay))
                prediction = cal.timeInMillis
            }

            return prediction
        }

        // For non-monthly cycles: add the expected interval
        var prediction = lastTimestamp + cycle.expectedDays * MS_PER_DAY
        // If prediction is far in the past, advance by multiples of the cycle
        while (prediction < now - OVERDUE_GRACE_DAYS * MS_PER_DAY) {
            prediction += cycle.expectedDays * MS_PER_DAY
        }
        return prediction
    }

    /**
     * Detect the most common day of month across a set of expenses.
     * Returns the mode of day-of-month values using Calendar.
     */
    private fun detectExpectedDayOfMonth(expenses: List<RecurrenceCandidate>): Int? {
        val cal = Calendar.getInstance()
        val dayCounts = mutableMapOf<Int, Int>()

        for (expense in expenses) {
            cal.timeInMillis = expense.timestamp
            val day = cal.get(Calendar.DAY_OF_MONTH)
            dayCounts[day] = (dayCounts[day] ?: 0) + 1
        }

        return dayCounts.maxByOrNull { it.value }?.key
    }

    /**
     * Check if a recurring expense is expected to occur within a specific period.
     * Handles both past (already paid) and future (upcoming) occurrences.
     */
    private fun isExpectedInPeriod(
        recurring: RecurringExpense,
        periodStart: Long,
        periodEnd: Long
    ): Boolean {
        // Check if the last occurrence falls in the period
        if (recurring.lastOccurrence in periodStart until periodEnd) return true

        // Check if the next expected date falls in the period
        if (recurring.nextExpected in periodStart until periodEnd) return true

        // For monthly recurring on a specific day: check if that day falls in the period
        if (recurring.cycle == RecurrenceCycle.MONTHLY && recurring.expectedDayOfMonth != null) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = periodStart
            // Check each month that overlaps with the period
            while (cal.timeInMillis < periodEnd) {
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(
                    Calendar.DAY_OF_MONTH,
                    recurring.expectedDayOfMonth.coerceAtMost(maxDay)
                )
                if (cal.timeInMillis in periodStart until periodEnd) return true
                cal.add(Calendar.MONTH, 1)
                cal.set(Calendar.DAY_OF_MONTH, 1) // Reset before next iteration
            }
        }

        return false
    }

    // ==================== Helpers ====================

    /**
     * Load all category names into a map for O(1) lookup during detection.
     */
    private suspend fun loadCategoryNames(): Map<Long, String> {
        return try {
            val categories = categoryDao.getAllCategoriesSync()
            categories.associate { it.id to it.name }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load category names", e)
            emptyMap()
        }
    }

    /**
     * Get the start timestamp for the detection window.
     * Looks back [DETECTION_WINDOW_MONTHS] months from [now].
     */
    private fun getDetectionWindowStart(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.MONTH, -DETECTION_WINDOW_MONTHS)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Compute standard deviation of a list of doubles.
     */
    private fun stdDev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(variance)
    }

    companion object {
        private const val TAG = "RecurringExpenseService"

        /** Milliseconds in one day */
        const val MS_PER_DAY = 86_400_000L

        /** Cache time-to-live: 30 minutes */
        const val CACHE_TTL_MS = 30 * 60 * 1000L

        /** How far back to look for recurring patterns (months) */
        const val DETECTION_WINDOW_MONTHS = 6

        /** Minimum number of occurrences to consider a pattern */
        const val MIN_OCCURRENCES = 3

        /** Minimum confidence score to classify as recurring (0.0–1.0) */
        const val MIN_CONFIDENCE = 0.5

        /** Minimum confidence for forecast integration (higher bar) */
        const val MIN_CONFIDENCE_FOR_FORECAST = 0.7

        /** Grace period in days before marking as overdue */
        const val OVERDUE_GRACE_DAYS = 2
    }
}
