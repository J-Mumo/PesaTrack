package com.pesatrack.services

import com.pesatrack.data.local.database.dao.BudgetDao
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.dao.IncomeTransactionDao
import com.pesatrack.data.local.database.dao.MonthlyIncomeBudgetDao
import com.pesatrack.data.local.database.entities.BudgetEntity
import com.pesatrack.data.local.database.entities.DefaultCategories
import com.pesatrack.data.local.database.entities.ExpenseEntity
import com.pesatrack.data.local.database.entities.IncomeTransactionEntity
import com.pesatrack.data.local.database.entities.MonthlyIncomeBudgetEntity
import com.pesatrack.domain.models.IncomeSource
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Populates the database with 12 months of realistic sample data for demos,
 * screenshots, and manual QA of downstream features:
 *
 *  - Weekly / Monthly / Quarterly / Year-in-Review reports
 *  - YoY and monthly analytics
 *  - Recurring expense detection (needs 3+ occurrences at regular intervals)
 *  - Savings-rate + per-source income breakdown (income_transactions table)
 *  - Budget burn-down (group + sub-category + weekly budgets)
 *  - Home uncategorized alert + BatchCategorize + CategorizeIncome flows
 *  - Transaction cost surfacing (category 606)
 *  - Multi-source filtering (SMS_PARSED / SMS_BANK / EXCEL_IMPORT / MANUAL / STATEMENT_IMPORT)
 */
@Singleton
class SampleDataService @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val monthlyIncomeBudgetDao: MonthlyIncomeBudgetDao,
    private val incomeTransactionDao: IncomeTransactionDao
) {

    private val rng = Random(SEED)

    suspend fun populateSampleData() {
        rng.nextInt() // touch to force init; keeps generation deterministic per install

        // 1. Ensure default categories exist (FK targets)
        categoryDao.insertAll(DefaultCategories.categories)

        val monthAnchors = buildMonthAnchors(MONTHS_OF_HISTORY)
        val currentMonth = monthAnchors.last()

        // 2. Income — transaction rows (SMS-style) + manual monthly overrides
        val incomeRows = mutableListOf<IncomeTransactionEntity>()
        for (anchor in monthAnchors) {
            // Primary salary on ~day 25 of each month
            val salaryDay = anchor.dayInMonth(25).jitterMinutes(rng, 0..600)
            incomeRows += salaryEntry(
                amount = 135_000.0 + rng.nextInt(-5_000, 15_000),
                timestamp = salaryDay,
                yearMonth = anchor.yearMonth
            )
            // A quarterly bonus for the current + last-year same quarter (bump YoY)
            if (anchor.month % 3 == 0) {
                incomeRows += bonusEntry(
                    amount = 45_000.0 + rng.nextInt(0, 20_000),
                    timestamp = anchor.dayInMonth(28).jitterMinutes(rng, 0..600),
                    label = "Q${anchor.month / 3} Bonus"
                )
            }
            // Occasional side / freelance income (60% of months)
            if (rng.nextDouble() < 0.6) {
                incomeRows += businessEntry(
                    amount = 8_000.0 + rng.nextInt(0, 20_000).toDouble(),
                    timestamp = anchor.dayInMonth(rng.nextInt(6, 20)).jitterMinutes(rng, 0..600)
                )
            }
            // One or two self-transfers (M-Shwari → M-PESA) — counted as pass-through, not new income
            if (rng.nextDouble() < 0.5) {
                incomeRows += selfTransferEntry(
                    amount = 5_000.0 + rng.nextInt(0, 10_000).toDouble(),
                    timestamp = anchor.dayInMonth(rng.nextInt(10, 27)).jitterMinutes(rng, 0..600)
                )
            }
        }
        // A few most-recent detected inflows left UNCATEGORIZED so CategorizeIncomeScreen has content
        repeat(3) { i ->
            val ts = currentMonth.dayInMonth(currentMonth.today() - i).jitterMinutes(rng, 0..600)
            incomeRows += uncategorizedIncomeEntry(
                amount = 2_500.0 + rng.nextInt(0, 12_000).toDouble(),
                timestamp = ts
            )
        }
        incomeRows.forEach { incomeTransactionDao.insertIgnoreOnConflict(it) }

        // Manual monthly income overrides for the last 3 months (drives fallback UI)
        for (anchor in monthAnchors.takeLast(3)) {
            monthlyIncomeBudgetDao.upsert(
                MonthlyIncomeBudgetEntity(
                    amount = 150_000.0,
                    yearMonth = anchor.yearMonth,
                    note = "Expected salary + side"
                )
            )
        }

        // 3. Budgets — mix of group, sub-category, weekly, yearly
        val budgets = listOf(
            BudgetEntity(categoryId = 7, amount = 25_000.0, period = "MONTHLY", isGroupBudget = true),   // Food & Dining (group)
            BudgetEntity(categoryId = 16, amount = 15_000.0, period = "MONTHLY", isGroupBudget = true),  // Transport (group)
            BudgetEntity(categoryId = 10, amount = 45_000.0, period = "MONTHLY", isGroupBudget = true),  // Home (group)
            BudgetEntity(categoryId = 4, amount = 8_000.0, period = "MONTHLY", isGroupBudget = true),    // Entertainment (group)
            BudgetEntity(categoryId = 18, amount = 40_000.0, period = "MONTHLY", isGroupBudget = true),  // Investment & Savings (group)
            BudgetEntity(categoryId = 702, amount = 6_000.0, period = "MONTHLY", isGroupBudget = false), // Eating Out (sub)
            BudgetEntity(categoryId = 1608, amount = 4_000.0, period = "WEEKLY", isGroupBudget = false), // Uber/Bolt weekly
            BudgetEntity(categoryId = 5, amount = 60_000.0, period = "YEARLY", isGroupBudget = true)     // Faith & Giving yearly
        )
        budgets.forEach { budgetDao.insert(it) }

        // 4. Expenses — 12 months of recurring + variable spend, mixed sources
        val expenses = mutableListOf<ExpenseEntity>()
        for (anchor in monthAnchors) {
            expenses += monthlyRecurring(anchor)
            expenses += monthlyVariable(anchor)
            expenses += monthlyDiscretionary(anchor)
        }
        // A handful of uncategorized recent expenses to demo the Home alert + BatchCategorize
        expenses += uncategorizedRecent(currentMonth)
        // A pass-through / excluded example
        expenses += createExpense(
            name = "Reimbursed – event tickets",
            amount = 4_500.0,
            catId = null,
            type = "SEND_MONEY",
            time = currentMonth.dayInMonth(currentMonth.today() - 4).jitterMinutes(rng, 0..600),
            source = "SMS_PARSED",
            isCategorized = false,
            isExcluded = true,
            notes = "Split-paid, reimbursed same day"
        )
        expenseDao.insertAll(expenses)
    }

    // ==================== Expense generators ====================

    private fun monthlyRecurring(anchor: MonthAnchor): List<ExpenseEntity> {
        val list = mutableListOf<ExpenseEntity>()

        // Rent — 1st of month, always paybill (recurring detector needs regular cadence)
        list += createExpense(
            "Rent Payment", 35_000.0, catId = 1009, type = "PAY_BILL",
            time = anchor.dayInMonth(1).jitterMinutes(rng, -30..30),
            source = "SMS_PARSED"
        )
        // Electricity KPLC — day 2, ±day 1, amount jitters
        list += withTxnCost(
            createExpense(
                "KPLC PREPAID", 2_500.0 + rng.nextInt(0, 3_500),
                catId = 1002, type = "PAY_BILL",
                time = anchor.dayInMonth(2).jitterMinutes(rng, 0..600),
                source = "SMS_PARSED"
            )
        )
        // Water — day 3
        list += withTxnCost(
            createExpense(
                "Nairobi Water", 900.0 + rng.nextInt(0, 800),
                catId = 1012, type = "PAY_BILL",
                time = anchor.dayInMonth(3).jitterMinutes(rng, 0..600),
                source = "SMS_PARSED"
            )
        )
        // WiFi — day 5
        list += createExpense(
            "Faiba Home WiFi", 3_500.0, catId = 1007, type = "PAY_BILL",
            time = anchor.dayInMonth(5).jitterMinutes(rng, -60..60),
            source = "SMS_PARSED"
        )
        // Netflix — day 7
        list += createExpense(
            "Netflix", 1_100.0, catId = 210, type = "PAY_BILL",
            time = anchor.dayInMonth(7).jitterMinutes(rng, 0..600),
            source = "SMS_PARSED"
        )
        // SACCO — day 3
        list += createExpense(
            "SACCO Deposit", 20_000.0, catId = 1809, type = "PAY_BILL",
            time = anchor.dayInMonth(3).jitterMinutes(rng, 0..600),
            source = "SMS_PARSED"
        )
        // MMF top-up — day 15
        list += createExpense(
            "CIC MMF Topup", 10_000.0 + rng.nextInt(0, 8_000),
            catId = 1805, type = "PAY_BILL",
            time = anchor.dayInMonth(15).jitterMinutes(rng, 0..600),
            source = "SMS_PARSED"
        )
        // Tithe — day 26 (after salary)
        list += createExpense(
            "Tithe – Local Church", 13_500.0, catId = 506, type = "PAY_BILL",
            time = anchor.dayInMonth(26).jitterMinutes(rng, 0..600),
            source = "SMS_PARSED"
        )
        // Airtime top-ups — 2/month
        repeat(2) { i ->
            list += createExpense(
                "Safaricom Airtime", 500.0 + rng.nextInt(0, 700),
                catId = 202, type = "AIRTIME",
                time = anchor.dayInMonth(10 + i * 10).jitterMinutes(rng, 0..600),
                source = "SMS_PARSED"
            )
        }
        return list
    }

    private fun monthlyVariable(anchor: MonthAnchor): List<ExpenseEntity> {
        val list = mutableListOf<ExpenseEntity>()
        val daysInMonth = anchor.daysInMonth()

        // Groceries — 4-6 shops
        val groceryPlaces = listOf("Naivas Supermarket", "Zucchini Fresh", "Carrefour", "Quickmart", "Chandarana")
        repeat(4 + rng.nextInt(0, 3)) {
            list += withTxnCost(
                createExpense(
                    name = groceryPlaces.random(rng),
                    amount = 1_800.0 + rng.nextInt(0, 8_000),
                    catId = 703, type = "BUY_GOODS",
                    time = anchor.dayInMonth(rng.nextInt(1, daysInMonth + 1)).jitterMinutes(rng, 0..1200),
                    source = pickSource()
                )
            )
        }

        // Eating out — 5-9
        val eateries = listOf("Java House", "Artcaffe", "Big Square", "Pizza Inn", "CJs", "Urban Burger", "Onami")
        repeat(5 + rng.nextInt(0, 5)) {
            list += createExpense(
                name = eateries.random(rng),
                amount = 600.0 + rng.nextInt(0, 3_400),
                catId = 702, type = "BUY_GOODS",
                time = anchor.dayInMonth(rng.nextInt(1, daysInMonth + 1)).jitterMinutes(rng, 0..1200),
                source = pickSource()
            )
        }

        // Takeaway/delivery — 2-4
        val delivery = listOf("KFC Delivery", "Glovo – Pizza", "Bolt Food", "Jumia Food")
        repeat(2 + rng.nextInt(0, 3)) {
            list += createExpense(
                name = delivery.random(rng),
                amount = 900.0 + rng.nextInt(0, 2_200),
                catId = 705, type = "BUY_GOODS",
                time = anchor.dayInMonth(rng.nextInt(1, daysInMonth + 1)).jitterMinutes(rng, 0..1200),
                source = pickSource()
            )
        }

        // Uber / Bolt — 6-12/month (weekly-ish for recurring detection)
        repeat(6 + rng.nextInt(0, 7)) {
            val svc = if (rng.nextBoolean()) "Uber Trip" else "Bolt Ride"
            list += withTxnCost(
                createExpense(
                    name = svc,
                    amount = 250.0 + rng.nextInt(0, 900),
                    catId = 1608, type = "PAY_BILL",
                    time = anchor.dayInMonth(rng.nextInt(1, daysInMonth + 1)).jitterMinutes(rng, 0..1200),
                    source = pickSource()
                )
            )
        }

        // Fuel — 2-4 per month
        repeat(2 + rng.nextInt(0, 3)) {
            list += createExpense(
                name = listOf("Shell Fuel", "Total Energies", "Rubis").random(rng),
                amount = 3_000.0 + rng.nextInt(0, 4_000),
                catId = 1712, type = "BUY_GOODS",
                time = anchor.dayInMonth(rng.nextInt(1, daysInMonth + 1)).jitterMinutes(rng, 0..1200),
                source = pickSource()
            )
        }
        return list
    }

    private fun monthlyDiscretionary(anchor: MonthAnchor): List<ExpenseEntity> {
        val list = mutableListOf<ExpenseEntity>()
        val daysInMonth = anchor.daysInMonth()

        // Occasional health / personal-care / shopping — 3-6 rows
        val pool = listOf(
            Triple("Goodlife Pharmacy", 906L, "BUY_GOODS"),
            Triple("Barber – Haircut", 1301L, "BUY_GOODS"),
            Triple("Ashley's Salon", 1303L, "BUY_GOODS"),
            Triple("Bata Shoes", 1503L, "BUY_GOODS"),
            Triple("Text Book Centre", 1502L, "BUY_GOODS"),
            Triple("iHub Coworking", 303L, "PAY_BILL"),
            Triple("Sportpesa Gym", 902L, "PAY_BILL"),
            Triple("Amazon.co.uk Import", 1504L, "PAY_BILL"),
            Triple("Betika Wager", 402L, "PAY_BILL")
        )
        repeat(3 + rng.nextInt(0, 4)) {
            val (name, cat, type) = pool.random(rng)
            list += createExpense(
                name = name,
                amount = 500.0 + rng.nextInt(0, 6_500),
                catId = cat, type = type,
                time = anchor.dayInMonth(rng.nextInt(1, daysInMonth + 1)).jitterMinutes(rng, 0..1200),
                source = pickSource()
            )
        }

        // Family/friend send-money — 1-3
        repeat(1 + rng.nextInt(0, 3)) {
            list += withTxnCost(
                createExpense(
                    name = listOf("James", "Wanjiku", "Kevin", "Achieng").random(rng),
                    amount = 1_000.0 + rng.nextInt(0, 9_000),
                    catId = 507, type = "SEND_MONEY", // Family & Friends Support
                    time = anchor.dayInMonth(rng.nextInt(1, daysInMonth + 1)).jitterMinutes(rng, 0..1200),
                    source = pickSource()
                )
            )
        }

        // Cash withdrawals — 1-2
        repeat(1 + rng.nextInt(0, 2)) {
            list += withTxnCost(
                createExpense(
                    name = "Agent Withdrawal",
                    amount = 2_000.0 + rng.nextInt(0, 8_000),
                    catId = null, // uncategorized withdrawals happen in real life
                    type = "WITHDRAW",
                    time = anchor.dayInMonth(rng.nextInt(1, daysInMonth + 1)).jitterMinutes(rng, 0..1200),
                    source = "SMS_PARSED",
                    isCategorized = false
                )
            )
        }
        return list
    }

    private fun uncategorizedRecent(anchor: MonthAnchor): List<ExpenseEntity> {
        val today = anchor.today()
        val samples = listOf(
            Triple("Kilimall Order", 2_800.0, "PAY_BILL"),
            Triple("Unknown Till 4489201", 1_450.0, "BUY_GOODS"),
            Triple("Peter", 3_200.0, "SEND_MONEY"),
            Triple("Paybill 400200", 6_700.0, "PAY_BILL")
        )
        return samples.mapIndexed { i, (name, amt, type) ->
            createExpense(
                name = name, amount = amt, catId = null, type = type,
                time = anchor.dayInMonth((today - i).coerceAtLeast(1)).jitterMinutes(rng, 0..600),
                source = "SMS_PARSED",
                isCategorized = false
            )
        }
    }

    // ==================== Helpers ====================

    private fun withTxnCost(main: ExpenseEntity): List<ExpenseEntity> {
        // ~70% chance of a transaction cost row alongside SMS-parsed M-PESA expenses
        if (main.source == "MANUAL" || rng.nextDouble() > 0.7) return listOf(main)
        val cost = when {
            main.amount <= 100 -> 0.0
            main.amount <= 1_500 -> 12.0 + rng.nextInt(0, 15)
            main.amount <= 10_000 -> 30.0 + rng.nextInt(0, 25)
            else -> 55.0 + rng.nextInt(0, 50)
        }
        if (cost <= 0) return listOf(main)
        return listOf(
            main,
            createExpense(
                name = "M-PESA Transaction Cost",
                amount = cost,
                catId = 606,
                type = "TRANSACTION_COST",
                time = main.timestamp + 1_000L,
                source = main.source,
                isCategorized = true
            )
        )
    }

    private fun pickSource(): String = when (rng.nextInt(10)) {
        in 0..5 -> "SMS_PARSED"
        6 -> "SMS_BANK"
        7 -> "EXCEL_IMPORT"
        8 -> "STATEMENT_IMPORT"
        else -> "MANUAL"
    }

    private fun createExpense(
        name: String,
        amount: Double,
        catId: Long?,
        type: String,
        time: Long,
        source: String = "SMS_PARSED",
        isCategorized: Boolean = catId != null,
        isExcluded: Boolean = false,
        notes: String? = null
    ): ExpenseEntity {
        return ExpenseEntity(
            transactionId = txnId(),
            amount = amount,
            recipient = name,
            recipientName = name,
            categoryId = catId,
            paymentType = type,
            source = source,
            timestamp = time,
            isCategorized = isCategorized,
            isExcluded = isExcluded,
            notes = notes
        )
    }

    // ---------- Income row builders ----------

    private fun salaryEntry(amount: Double, timestamp: Long, yearMonth: String) =
        IncomeTransactionEntity(
            transactionId = txnId(),
            amount = amount,
            timestamp = timestamp,
            source = IncomeSource.SALARY.name,
            sender = "ACME LTD PAYROLL",
            parserSource = "MPESA",
            note = "Salary $yearMonth",
            isCategorized = true
        )

    private fun bonusEntry(amount: Double, timestamp: Long, label: String) =
        IncomeTransactionEntity(
            transactionId = txnId(),
            amount = amount,
            timestamp = timestamp,
            source = IncomeSource.SALARY.name,
            sender = "ACME LTD PAYROLL",
            parserSource = "MPESA",
            note = label,
            isCategorized = true
        )

    private fun businessEntry(amount: Double, timestamp: Long) =
        IncomeTransactionEntity(
            transactionId = txnId(),
            amount = amount,
            timestamp = timestamp,
            source = IncomeSource.BUSINESS.name,
            sender = listOf("Client – Freelance", "Consulting Retainer", "Design Gig").random(rng),
            parserSource = "MPESA",
            isCategorized = true
        )

    private fun selfTransferEntry(amount: Double, timestamp: Long) =
        IncomeTransactionEntity(
            transactionId = txnId(),
            amount = amount,
            timestamp = timestamp,
            source = IncomeSource.TRANSFER_IN.name,
            sender = "M-Shwari",
            parserSource = "MPESA",
            note = "Self-transfer",
            isCategorized = true
        )

    private fun uncategorizedIncomeEntry(amount: Double, timestamp: Long) =
        IncomeTransactionEntity(
            transactionId = txnId(),
            amount = amount,
            timestamp = timestamp,
            source = IncomeSource.UNCATEGORIZED.name,
            sender = listOf("Unknown 07XX", "Paybill Ref 4479", "John Doe").random(rng),
            parserSource = "MPESA",
            isCategorized = false
        )

    private fun txnId(): String = "SAMP" + UUID.randomUUID().toString().take(10).uppercase()

    // ---------- Month math ----------

    private data class MonthAnchor(val year: Int, val month: Int, val startMs: Long) {
        val yearMonth: String get() = String.format(Locale.US, "%d-%02d", year, month)

        fun dayInMonth(day: Int): Long {
            val cal = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, day.coerceAtLeast(1), 9, 0, 0)
            }
            return cal.timeInMillis
        }

        fun daysInMonth(): Int {
            val cal = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1)
            }
            return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }

        /** Only meaningful for the current month — returns today's day-of-month clamped ≥ 1. */
        fun today(): Int {
            val cal = Calendar.getInstance()
            return if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) + 1 == month)
                cal.get(Calendar.DAY_OF_MONTH)
            else daysInMonth()
        }
    }

    private fun Long.jitterMinutes(random: Random, range: IntRange): Long =
        this + random.nextInt(range.first, range.last + 1) * 60_000L

    private fun buildMonthAnchors(count: Int): List<MonthAnchor> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(count - 1))
        }
        val list = mutableListOf<MonthAnchor>()
        repeat(count) {
            list += MonthAnchor(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.timeInMillis)
            cal.add(Calendar.MONTH, 1)
        }
        return list
    }

    // ==================== Cleanup ====================

    suspend fun clearAllData() {
        expenseDao.deleteAll()
        budgetDao.deleteAll()
        monthlyIncomeBudgetDao.deleteAll()
        incomeTransactionDao.deleteAll()
    }

    companion object {
        private const val MONTHS_OF_HISTORY = 14 // covers full Year-in-Review + last year comparison
        private const val SEED = 20260710L
    }
}
