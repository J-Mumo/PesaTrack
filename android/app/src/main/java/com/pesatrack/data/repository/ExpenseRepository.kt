package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.ExpenseDao
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
            timestamp = timestamp,
            createdAt = createdAt,
            isCategorized = isCategorized
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
            timestamp = timestamp,
            createdAt = createdAt,
            isCategorized = isCategorized
        )
    }
}
