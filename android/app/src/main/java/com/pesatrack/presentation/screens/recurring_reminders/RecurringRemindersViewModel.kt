package com.pesatrack.presentation.screens.recurring_reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.services.NotificationHelper
import com.pesatrack.services.RecurringExpenseService
import com.pesatrack.services.RecurringReminderOverride
import com.pesatrack.services.RecurringReminderPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RecurringRemindersViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val service: RecurringExpenseService,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecurringRemindersUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(preferences.recurringRemindersEnabled, preferences.recurringReminderSelections) { master, selections ->
                    master to selections
                }.collect { (master, selections) ->
                    _uiState.update { it.copy(masterEnabled = master, selections = selections) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(error = "Reminder choices could not be loaded. Try again.") } }
        }
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(notificationsAllowed = NotificationHelper.canShowRecurringReminders(context)) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val payments = withContext(Dispatchers.IO) { service.getRecurringExpenses(forceRefresh = true).recurringExpenses }
                _uiState.update { it.copy(payments = payments, isLoading = false, error = null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(isLoading = false, error = "Payment patterns could not be loaded. Your choices are unchanged.") } }
        }
    }

    fun search(value: String) { _uiState.update { it.copy(search = value) } }

    fun choose(identity: String, enabled: Boolean?) = save {
        preferences.setRecurringReminderOverride(identity, enabled?.let {
            if (it) RecurringReminderOverride.ENABLED else RecurringReminderOverride.DISABLED
        })
        if (enabled == false) NotificationHelper.dismissRecurringPayment(context, identity)
        dismissUnselected()
    }

    fun useDefaults() = save {
        preferences.resetRecurringReminderOverrides()
        NotificationHelper.dismissAllRecurringReminders(context)
    }

    private suspend fun dismissUnselected() {
        val selections = preferences.getRecurringReminderSelections()
        val master = preferences.getRecurringRemindersEnabled()
        _uiState.value.payments.filter { !RecurringReminderPolicy.resolve(master, it, selections).selected }.forEach {
            NotificationHelper.dismissRecurringPayment(context, it.recipientKey)
            it.legacyRecipientKeys.forEach { legacy -> NotificationHelper.dismissRecurringPayment(context, legacy) }
        }
    }

    private fun save(action: suspend () -> Unit) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try { action(); _uiState.update { it.copy(isSaving = false, error = null) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.update { it.copy(isSaving = false, error = "Choices could not be saved. If unreadable, use defaults to reset them.") } }
        }
    }
}