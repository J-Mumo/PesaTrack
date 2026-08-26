package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.CategoryMonthlyTotal
import com.pesatrack.data.local.database.dao.CategoryTotal
import com.pesatrack.data.local.database.dao.DailyTotal
import com.pesatrack.data.local.database.dao.DateRangeResult
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.MonthlyTotal
import com.pesatrack.data.local.database.dao.PaymentTypeTotal
import com.pesatrack.data.local.database.dao.RecipientGroup
import com.pesatrack.data.local.database.dao.TopCategoryResult
import com.pesatrack.data.local.database.dao.TopSpender
import com.pesatrack.data.local.database.dao.YearMonthTotal
import com.pesatrack.data.local.database.entities.ExpenseEntity
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.utils.MonthPeriod
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
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val appPreferences: AppPreferences
) {

    /**
     * Cached user preference: which day of the month starts the budget cycle
     * (mirrors the pattern in `BudgetRepository` / `IncomeRepository`). Kept in
     * sync via [refreshMonthStartDay] on ViewModel init / screen resume so that
     * synchronous callers of [getCurrentMonthRange] don't need to suspend.
     * Default `1` = standard calendar month.
     */
    private var _monthStartDay: Int = 1
    val monthStartDay: Int get() = _monthStartDay

    suspend fun refreshMonthStartDay() {
        _monthStartDay = appPreferences.getMonthStartDay()
    }

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
     * Get investment total (Investment & Savings group 18) for current month
     */
    fun getInvestmentTotalForCurrentMonth(): Flow<Double> {
        val (start, end) = getCurrentMonthRange()
        return expenseDao.getInvestmentTotalForMonth(start, end)
    }

    /**
     * Get total expenses for the last 7 days (rolling window).
     * Uses a reactive Flow so the card updates in real time.
     */
    fun getTotalLast7Days(): Flow<Double> {
        val sevenDaysAgoMs = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        return expenseDao.getTotalSince(sevenDaysAgoMs)
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

    // ==================== Weekly Snapshot ====================

    /**
     * Get total expenses for a date range (suspend version for weekly snapshot).
     */
    suspend fun getTotalInRange(startMs: Long, endMs: Long): Double {
        return expenseDao.getTotalSpendingInRange(startMs, endMs)
    }

    /**
     * Get the top spending category (group name) within a date range.
     */
    suspend fun getTopCategoryInRange(startMs: Long, endMs: Long): TopCategoryResult? {
        return expenseDao.getTopCategoryInRange(startMs, endMs)
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
     * Get the [limit] categories with the most recent activity in [month] of [year],
     * ordered by MAX(timestamp) DESC.
     */
    suspend fun getRecentlyActiveCategoryTotalsForMonth(
        year: Int,
        month: Int,
        limit: Int
    ): List<CategoryTotal> {
        val (start, end) = getMonthRange(year, month)
        return expenseDao.getRecentlyActiveCategoryTotalsForMonth(start, end, limit)
    }

    /**
     * Get the [limit] categories with the most recent activity in the current
     * budget-cycle period (honors [monthStartDay]).
     */
    suspend fun getRecentlyActiveCategoryTotalsForCurrentMonth(
        limit: Int
    ): List<CategoryTotal> {
        val (start, end) = getCurrentMonthRange()
        return expenseDao.getRecentlyActiveCategoryTotalsForMonth(start, end, limit)
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
     * Total spending in an arbitrary date range. Used by savings-rate and
     * income surfaces that operate on offset "budget month" bounds rather
     * than calendar months.
     */
    suspend fun getSpendingInRange(startMs: Long, endMs: Long): Double =
        expenseDao.getTotalSpendingInRange(startMs, endMs)

    /**
     * Total money moved into the Investment & Savings group (18) in an
     * arbitrary date range. This is the true "saved" figure — money the user
     * deliberately set aside — used by the Home and Analytics savings-rate
     * surfaces instead of the misleading `(income − spend) / income` formula.
     */
    suspend fun getInvestmentInRange(startMs: Long, endMs: Long): Double =
        expenseDao.getInvestmentTotalInRange(startMs, endMs)

    /**
     * Period-aware variants of the `*ForMonth` queries above.
     *
     * The Analytics Monthly tab uses these so its month bucket aligns with
     * the user's `monthStartDay` budget cycle (e.g. salary on the 25th)
     * instead of always running 1st-to-last-of-the-calendar-month.
     */
    suspend fun getCategoryTotalsInRange(startMs: Long, endMs: Long): List<CategoryTotal> =
        expenseDao.getCategoryTotalsForMonth(startMs, endMs)

    suspend fun getTopSpendersInRange(
        startMs: Long,
        endMs: Long,
        limit: Int = 10
    ): List<TopSpender> = expenseDao.getTopSpendersForMonth(startMs, endMs, limit)

    suspend fun getPaymentTypeBreakdownInRange(
        startMs: Long,
        endMs: Long
    ): List<PaymentTypeTotal> = expenseDao.getPaymentTypeBreakdownForMonth(startMs, endMs)

    suspend fun searchRecipientSpendingInRange(
        query: String,
        startMs: Long,
        endMs: Long
    ): List<TopSpender> = expenseDao.searchRecipientSpendingForMonth(query, startMs, endMs)

    // ==================== Yearly Analytics ====================

    /**
     * Get total spending for an entire year.
     */
    suspend fun getAnnualTotal(year: Int): Double {
        val (start, end) = getYearRange(year)
        return expenseDao.getAnnualTotal(start, end)
    }

    /**
     * Get monthly totals for a specific year (12 data points for YoY overlay).
     */
    suspend fun getMonthlyTotalsForYear(year: Int): List<YearMonthTotal> {
        val (start, end) = getYearRange(year)
        return expenseDao.getMonthlyTotalsForYear(start, end)
    }

    /**
     * Get category totals for a full year.
     */
    suspend fun getCategoryTotalsForYear(year: Int): List<CategoryTotal> {
        val (start, end) = getYearRange(year)
        return expenseDao.getCategoryTotalsForYear(start, end)
    }

    /**
     * Get top spenders for a full year.
     */
    suspend fun getTopSpendersForYear(year: Int, limit: Int = 10): List<TopSpender> {
        val (start, end) = getYearRange(year)
        return expenseDao.getTopSpendersForYear(start, end, limit)
    }

    /**
     * Get payment type breakdown for a full year.
     */
    suspend fun getPaymentTypeBreakdownForYear(year: Int): List<PaymentTypeTotal> {
        val (start, end) = getYearRange(year)
        return expenseDao.getPaymentTypeBreakdownForYear(start, end)
    }

    // ==================== Category × Month Grid ====================

    /**
     * Build a full-year Category × Month grid for the Analytics
     * "Yearly → Grid" sub-tab. Periods honour the user's `monthStartDay`
     * (12 periods anchored on `year`, month 1 through 12), so a user on the
     * 25th sees columns like "Jan 25 – Feb 24" not calendar Jan.
     *
     * One `getCategoryTotalsForMonth` call per period — cheap because the
     * DAO query is indexed on `timestamp` and each aggregate is small.
     * Building the pivot in Kotlin is simpler than a strftime-with-offset
     * SQL trick and correctly handles the case where `monthStartDay ≠ 1`.
     *
     * Rows are grouped by parent: every group (`parentId == null`) is
     * followed by its sub-categories, both sorted by year total desc.
     * Uncategorized (`categoryId == null` from the DAO's LEFT JOIN) is
     * dropped — the query already restricts to `categoryId IS NOT NULL`,
     * but the DAO's result class marks it nullable, so we defend anyway.
     *
     * @param year        Anchor year (1..).
     * @param includeFees When false, transaction-fee category (606) is
     *                    excluded so the grid reflects only "money the
     *                    user chose to spend", matching the honest-numbers
     *                    principle.
     */
    suspend fun getCategoryMonthGridForYear(
        year: Int,
        includeFees: Boolean = false
    ): com.pesatrack.domain.models.CategoryMonthGrid {
        val startDay = _monthStartDay
        // Compute 12 (startMs, endMs) windows anchored on `year`.
        val periods: List<Triple<Long, Long, String>> = (1..12).map { month ->
            val (start, end) = MonthPeriod.rangeForPeriodStart(year, month, startDay)
            val label = shortPeriodLabel(start, startDay)
            Triple(start, end, label)
        }
        val periodLabels = periods.map { it.third }

        // Pull totals for each period; rows are per-category totals.
        val perPeriod: List<List<CategoryTotal>> = periods.map { (s, e, _) ->
            expenseDao.getCategoryTotalsForMonth(s, e)
        }

        // Determine which columns are partial. The current period (containing
        // `now`) is partial. A leading period is also partial when the user
        // has no expenses in it and no earlier expenses either (pre-history).
        val nowMs = System.currentTimeMillis()
        val partial = mutableSetOf<Int>()
        periods.forEachIndexed { idx, (s, e, _) ->
            if (nowMs in s until e) partial.add(idx)
        }

        // Transpose: categoryId → array of 12 nullable totals + metadata.
        data class RawRow(
            val categoryId: Long,
            val name: String,
            val color: String?,
            val parentId: Long?,
            val values: DoubleArray,
            val hasValue: BooleanArray,
        )

        val rowsById = linkedMapOf<Long, RawRow>()
        perPeriod.forEachIndexed { idx, totals ->
            totals.forEach { t ->
                val id = t.categoryId ?: return@forEach
                if (!includeFees && id == 606L) return@forEach
                val row = rowsById.getOrPut(id) {
                    RawRow(
                        categoryId = id,
                        name = t.categoryName,
                        color = t.categoryColor,
                        parentId = t.parentId,
                        values = DoubleArray(12),
                        hasValue = BooleanArray(12),
                    )
                }
                row.values[idx] = t.total
                row.hasValue[idx] = true
            }
        }

        // Split into groups vs children and compute totals.
        val leafRows = rowsById.values.map { raw ->
            val yearTotal = raw.values.sum()
            val monthly: List<Double?> = raw.values.mapIndexed { i, v ->
                if (raw.hasValue[i]) v else null
            }
            com.pesatrack.domain.models.GridRow(
                categoryId = raw.categoryId,
                label = raw.name,
                color = raw.color,
                depth = if (raw.parentId == null) 0 else 1,
                parentId = raw.parentId,
                monthlyValues = monthly,
                yearTotal = yearTotal,
                isExpandable = false // set below for groups that have children
            )
        }

        // Directly-emitted group rows (a group with its own expenses — rare)
        // vs sub-category rows.
        val directGroupRows = leafRows.filter { it.depth == 0 }
        val childRows = leafRows.filter { it.depth == 1 }
        val childrenByParent: Map<Long, List<com.pesatrack.domain.models.GridRow>> = childRows
            .groupBy { it.parentId!! }
            .mapValues { (_, list) -> list.sortedByDescending { it.yearTotal } }

        // Synthesize a group row for every parentId, summing the children's
        // per-period values. If the group also has direct expenses, merge
        // them in. In PesaTrack most groups have no direct expenses, so the
        // synthesized row is what the user actually sees. Group metadata
        // (name, color) is fetched from `CategoryDao` — one call, indexed.
        val allCats = categoryDao.getAllCategoriesSync().associateBy { it.id }
        val allGroupIds = (directGroupRows.map { it.categoryId } + childrenByParent.keys).toSet()
        val groups: List<com.pesatrack.domain.models.GridRow> = allGroupIds.map { gid ->
            val direct = directGroupRows.firstOrNull { it.categoryId == gid }
            val children = childrenByParent[gid].orEmpty()
            val summedValues = DoubleArray(12)
            val summedHas = BooleanArray(12)
            direct?.let { d ->
                d.monthlyValues.forEachIndexed { i, v ->
                    if (v != null) { summedValues[i] += v; summedHas[i] = true }
                }
            }
            children.forEach { c ->
                c.monthlyValues.forEachIndexed { i, v ->
                    if (v != null) { summedValues[i] += v; summedHas[i] = true }
                }
            }
            val cat = allCats[gid]
            val label = direct?.label?.takeIf { it.isNotBlank() } ?: cat?.name ?: "Group $gid"
            val color = direct?.color ?: cat?.color
            com.pesatrack.domain.models.GridRow(
                categoryId = gid,
                label = label,
                color = color,
                depth = 0,
                parentId = null,
                monthlyValues = summedValues.mapIndexed { i, v ->
                    if (summedHas[i]) v else null
                },
                yearTotal = summedValues.sum(),
                isExpandable = children.isNotEmpty()
            )
        }.sortedByDescending { it.yearTotal }

        // Emit: each group, followed by its sub-categories.
        val orderedRows = buildList {
            groups.forEach { g ->
                add(g)
                childrenByParent[g.categoryId]?.forEach { add(it) }
            }
        }

        val periodTotals: List<Double> = (0 until 12).map { idx ->
            groups.sumOf { it.monthlyValues[idx] ?: 0.0 }
        }
        val grandTotal = periodTotals.sum()

        return com.pesatrack.domain.models.CategoryMonthGrid(
            year = year,
            periodLabels = periodLabels,
            rows = orderedRows,
            periodTotals = periodTotals,
            grandTotal = grandTotal,
            partialPeriodIndexes = partial,
            includesFees = includeFees
        )
    }

    /**
     * Build the Home "Trend by group" preview: top [topN] groups by combined
     * total across the last [monthsBack] periods (honouring `monthStartDay`),
     * one row per group with amounts + trend direction. Sub-categories are
     * intentionally excluded — the preview must fit on a phone screen
     * without horizontal scroll.
     *
     * Returns `null` when there is insufficient data to draw a signal
     * (fewer than 2 of the last [monthsBack] periods have any spend at all).
     */
    suspend fun getGroupTrendPreview(
        monthsBack: Int = 3,
        topN: Int = 5,
        includeFees: Boolean = false
    ): com.pesatrack.domain.models.GroupTrendPreview? {
        require(monthsBack >= 2) { "monthsBack must be >= 2 for a trend signal" }
        val startDay = _monthStartDay
        val (currentStart, _) = MonthPeriod.currentRange(startDay)
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentStart }

        // Newest last so callers render left→right chronologically.
        data class Window(val start: Long, val end: Long, val label: String, val isPartial: Boolean)
        val windows: List<Window> = (0 until monthsBack)
            .map { offset ->
                val cal = (currentCal.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                val (s, e) = MonthPeriod.rangeForPeriodStart(y, m, startDay)
                Window(s, e, shortPeriodLabel(s, startDay), isPartial = offset == 0)
            }
            .reversed()

        val perWindow: List<List<CategoryTotal>> = windows.map { w ->
            expenseDao.getCategoryTotalsForMonth(w.start, w.end)
        }

        val windowsWithAnySpend = perWindow.count { it.isNotEmpty() }
        if (windowsWithAnySpend < 2) return null

        // Aggregate to groups only. A row keyed by parent group id: if the
        // category has no parent, it *is* a group (its own id); otherwise
        // roll up under `parentId`. Uncategorized / group 606 handled via
        // includeFees toggle.
        data class RawGroup(
            var name: String,
            val color: String?,
            val values: DoubleArray,
            val hasValue: BooleanArray,
        )
        val groupsById = linkedMapOf<Long, RawGroup>()
        perWindow.forEachIndexed { idx, totals ->
            totals.forEach { t ->
                val leafId = t.categoryId ?: return@forEach
                if (!includeFees && leafId == 606L) return@forEach
                val groupId = t.parentId ?: leafId
                val row = groupsById.getOrPut(groupId) {
                    RawGroup(
                        name = if (t.parentId == null) t.categoryName else "",
                        color = if (t.parentId == null) t.categoryColor else null,
                        values = DoubleArray(monthsBack),
                        hasValue = BooleanArray(monthsBack),
                    )
                }
                row.values[idx] += t.total
                row.hasValue[idx] = true
            }
        }

        // Backfill group name/color for rows first seen via a sub-category.
        // In PesaTrack, groups (e.g. "Food & Drink") rarely have direct
        // expenses — leaf sub-categories do — so most group rows land here
        // with a blank name. One `getAllCategoriesSync()` call is cheaper
        // than N `getById` calls.
        val missingNameGroupIds = groupsById.filterValues { it.name.isBlank() }.keys
        if (missingNameGroupIds.isNotEmpty()) {
            val allCats = categoryDao.getAllCategoriesSync().associateBy { it.id }
            missingNameGroupIds.forEach { id ->
                val row = groupsById[id] ?: return@forEach
                val cat = allCats[id]
                if (cat != null) {
                    groupsById[id] = row.copy(name = cat.name, color = cat.color)
                }
            }
            // Anything still nameless — drop rather than surface "??".
            groupsById.entries.removeAll { it.value.name.isBlank() }
        }

        // Rank by combined-window total desc, take top N.
        val ranked = groupsById.entries
            .map { (id, r) -> id to r }
            .sortedByDescending { (_, r) -> r.values.sum() }
            .take(topN)

        val rows = ranked.map { (id, r) ->
            val amounts: List<Double?> = r.values.mapIndexed { i, v ->
                if (r.hasValue[i]) v else null
            }
            com.pesatrack.domain.models.GroupTrendRow(
                categoryId = id,
                label = r.name,
                color = r.color,
                amounts = amounts,
                direction = computeTrendDirection(amounts),
                isInvestment = id == INVESTMENT_SAVINGS_GROUP_ID
            )
        }

        return com.pesatrack.domain.models.GroupTrendPreview(
            periodLabels = windows.map { it.label },
            currentPeriodIsPartial = windows.last().isPartial,
            rows = rows
        )
    }

    /** Group id 18 = Investment & Savings. See `Category.kt` seed data. */
    private val INVESTMENT_SAVINGS_GROUP_ID = 18L

    /**
     * Direction of the last non-null period vs the mean of the earlier
     * non-null periods. Returns [com.pesatrack.domain.models.TrendDirection.INSUFFICIENT]
     * when we can't build the comparison honestly.
     */
    private fun computeTrendDirection(
        amounts: List<Double?>
    ): com.pesatrack.domain.models.TrendDirection {
        val last = amounts.lastOrNull() ?: return com.pesatrack.domain.models.TrendDirection.INSUFFICIENT
        val earlier = amounts.dropLast(1).filterNotNull()
        if (earlier.size < 2) return com.pesatrack.domain.models.TrendDirection.INSUFFICIENT
        val mean = earlier.average()
        if (mean <= 0.0) return com.pesatrack.domain.models.TrendDirection.INSUFFICIENT
        val delta = (last - mean) / mean
        return when {
            delta >= 0.25 -> com.pesatrack.domain.models.TrendDirection.UP2
            delta >= 0.05 -> com.pesatrack.domain.models.TrendDirection.UP
            delta <= -0.25 -> com.pesatrack.domain.models.TrendDirection.DOWN2
            delta <= -0.05 -> com.pesatrack.domain.models.TrendDirection.DOWN
            else -> com.pesatrack.domain.models.TrendDirection.FLAT
        }
    }

    /**
     * Compact per-column period label used by the Grid and preview.
     * - `monthStartDay = 1`  → `"Jan"`, `"Feb"`, …
     * - `monthStartDay ≠ 1`  → `"Jan 25"`, `"Feb 25"`, … (short month + start day)
     */
    private fun shortPeriodLabel(startMs: Long, monthStartDay: Int): String {
        val cal = Calendar.getInstance().apply { timeInMillis = startMs }
        val month = SHORT_MONTHS[cal.get(Calendar.MONTH)]
        val startDay = monthStartDay.coerceIn(1, 28)
        return if (startDay == 1) month else "$month $startDay"
    }

    private val SHORT_MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    // ==================== Recipient Search ====================

    /**
     * Search for recipients matching a query within a specific month.
     * Returns all matching recipients (no limit) with total and transaction count.
     */
    suspend fun searchRecipientSpendingForMonth(query: String, year: Int, month: Int): List<TopSpender> {
        val (start, end) = getMonthRange(year, month)
        return expenseDao.searchRecipientSpendingForMonth(query, start, end)
    }

    /**
     * Search for recipients matching a query within a specific year.
     * Returns all matching recipients (no limit) with total and transaction count.
     */
    suspend fun searchRecipientSpendingForYear(query: String, year: Int): List<TopSpender> {
        val (start, end) = getYearRange(year)
        return expenseDao.searchRecipientSpendingForYear(query, start, end)
    }

    /**
     * Get start and end timestamps for a specific year.
     * Returns Pair(Jan 1 00:00:00, Jan 1 next year 00:00:00).
     */
    fun getYearRange(year: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.YEAR, 1)
        val end = calendar.timeInMillis
        return Pair(start, end)
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
     * Get start and end timestamps for current month.
     * Honors the user's [monthStartDay] preference so a salary-on-the-25th user
     * sees the same Jun 25 – Jul 24 cycle Budgets and Income already use.
     */
    private fun getCurrentMonthRange(): Pair<Long, Long> {
        return MonthPeriod.currentRange(_monthStartDay)
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
