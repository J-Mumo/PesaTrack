package com.pesatrack.services

import com.pesatrack.data.repository.BudgetRepository
import com.pesatrack.domain.models.BudgetAlert
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for checking budget thresholds after expenses are saved.
 *
 * Called from [SmsReceiver] and ViewModels after a successful expense insert.
 * Returns a list of [BudgetAlert]s if any budget crossed the 80% or 100% threshold.
 */
@Singleton
class BudgetService @Inject constructor(
    private val budgetRepository: BudgetRepository
) {

    /**
     * Check if any budgets have crossed a threshold after an expense was saved.
     *
     * @param expenseCategoryId The sub-category ID of the expense (not the group ID).
     *                          This method resolves the group ID internally.
     *                          Pass null if the expense is uncategorized — no alerts will fire.
     * @return List of [BudgetAlert]s for budgets at or above a threshold.
     */
    suspend fun checkBudgetsAfterExpense(expenseCategoryId: Long?): List<BudgetAlert> {
        if (expenseCategoryId == null) return emptyList()

        // Resolve the group ID from the sub-category
        val groupId = budgetRepository.getGroupIdForCategory(expenseCategoryId) ?: return emptyList()

        return budgetRepository.checkBudgetAlerts(groupId)
    }
}
