package com.pesatrack.services

import com.pesatrack.data.local.database.dao.*
import com.pesatrack.data.local.database.entities.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to populate the database with sample data for demo/screenshot purposes.
 */
@Singleton
class SampleDataService @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val monthlyIncomeBudgetDao: MonthlyIncomeBudgetDao
) {

    suspend fun populateSampleData() {
        // 1. Ensure default categories exist
        categoryDao.insertAll(DefaultCategories.categories)

        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        // Current month start
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val monthStart = calendar.timeInMillis
        
        // Last month start
        calendar.add(Calendar.MONTH, -1)
        val lastMonthStart = calendar.timeInMillis
        val lastMonthKey = String.format(Locale.US, "%d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
        
        // This month key
        calendar.add(Calendar.MONTH, 1)
        val thisMonthKey = String.format(Locale.US, "%d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)

        // 2. Insert Income
        monthlyIncomeBudgetDao.upsert(MonthlyIncomeBudgetEntity(amount = 150000.0, yearMonth = lastMonthKey, note = "Salary + Bonus"))
        monthlyIncomeBudgetDao.upsert(MonthlyIncomeBudgetEntity(amount = 135000.0, yearMonth = thisMonthKey, note = "Salary"))

        // 3. Insert Budgets
        budgetDao.insert(BudgetEntity(categoryId = 7, amount = 25000.0, period = "MONTHLY", isGroupBudget = true)) // Food
        budgetDao.insert(BudgetEntity(categoryId = 16, amount = 15000.0, period = "MONTHLY", isGroupBudget = true)) // Transport
        budgetDao.insert(BudgetEntity(categoryId = 10, amount = 40000.0, period = "MONTHLY", isGroupBudget = true)) // Home
        budgetDao.insert(BudgetEntity(categoryId = 4, amount = 10000.0, period = "MONTHLY", isGroupBudget = true)) // Entertainment

        // 4. Insert Expenses
        val sampleExpenses = mutableListOf<ExpenseEntity>()
        
        // Fixed Home Expenses (Rent, Electricity, Water)
        sampleExpenses.add(createExpense("Rent Payment", 35000.0, 1009, "PAY_BILL", monthStart + 86400000L))
        sampleExpenses.add(createExpense("KPLC PrepAID", 3500.0, 1002, "PAY_BILL", monthStart + 172800000L))
        sampleExpenses.add(createExpense("Nairobi Water", 1200.0, 1012, "PAY_BILL", monthStart + 259200000L))
        
        // Food & Dining
        sampleExpenses.add(createExpense("Zucchini Groceries", 4500.0, 703, "BUY_GOODS", now - 86400000L * 2))
        sampleExpenses.add(createExpense("Java House", 1850.0, 702, "BUY_GOODS", now - 86400000L * 1))
        sampleExpenses.add(createExpense("KFC Delivery", 1200.0, 705, "BUY_GOODS", now - 3600000L * 5))
        sampleExpenses.add(createExpense("Naivas Supermarket", 8500.0, 703, "BUY_GOODS", lastMonthStart + 86400000L * 5))
        
        // Transport
        sampleExpenses.add(createExpense("Uber Trip", 650.0, 1608, "PAY_BILL", now - 3600000L * 2))
        sampleExpenses.add(createExpense("Shell Fuel", 5000.0, 1712, "BUY_GOODS", now - 86400000L * 3))
        sampleExpenses.add(createExpense("Bolt Lite", 450.0, 1608, "PAY_BILL", lastMonthStart + 86400000L * 10))
        
        // Digital
        sampleExpenses.add(createExpense("Safaricom Airtime", 1000.0, 202, "SEND_MONEY", now - 3600000L * 10))
        sampleExpenses.add(createExpense("Netflix", 1100.0, 210, "PAY_BILL", monthStart + 86400000L * 7))
        
        // Savings/Investments
        sampleExpenses.add(createExpense("Sacco Deposit", 20000.0, 1809, "PAY_BILL", monthStart + 86400000L * 3))
        sampleExpenses.add(createExpense("MMF Topup", 15000.0, 1805, "PAY_BILL", monthStart + 86400000L * 15))

        expenseDao.insertAll(sampleExpenses)
    }

    private fun createExpense(
        name: String,
        amount: Double,
        catId: Long,
        type: String,
        time: Long
    ): ExpenseEntity {
        return ExpenseEntity(
            transactionId = "SAMP" + UUID.randomUUID().toString().take(6).uppercase(),
            amount = amount,
            recipient = name,
            recipientName = name,
            categoryId = catId,
            paymentType = type,
            source = "MANUAL",
            timestamp = time,
            isCategorized = true
        )
    }

    suspend fun clearAllData() {
        expenseDao.deleteAll()
        budgetDao.deleteAll()
        monthlyIncomeBudgetDao.deleteAll()
    }
}
