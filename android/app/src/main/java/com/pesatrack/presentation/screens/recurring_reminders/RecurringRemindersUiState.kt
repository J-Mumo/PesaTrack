package com.pesatrack.presentation.screens.recurring_reminders

import com.pesatrack.domain.models.RecurringExpense
import com.pesatrack.services.RecurringReminderSelections

data class RecurringRemindersUiState(
    val payments: List<RecurringExpense> = emptyList(),
    val selections: RecurringReminderSelections = RecurringReminderSelections(),
    val masterEnabled: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val search: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)