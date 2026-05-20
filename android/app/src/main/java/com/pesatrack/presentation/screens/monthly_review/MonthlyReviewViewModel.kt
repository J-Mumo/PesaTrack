package com.pesatrack.presentation.screens.monthly_review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.InsightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Monthly Review screen (Insights & Reports v1.1).
 */
@HiltViewModel
class MonthlyReviewViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyReviewUiState())
    val uiState: StateFlow<MonthlyReviewUiState> = _uiState.asStateFlow()

    /**
     * Load a specific snapshot by id or the latest monthly review.
     */
    fun load(snapshotId: Long? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = when {
                    snapshotId != null -> insightsRepository.getMonthlySnapshot(snapshotId)
                    else -> insightsRepository.getLatestMonthly()
                }

                val resolved = if (snapshot == null && snapshotId == null) {
                    // Generate one on the fly
                    insightsRepository.generateAndStoreMonthlyReview()
                } else snapshot

                val previous = insightsRepository.getPreviousMonthlySnapshots()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snapshot = resolved,
                        previousReports = previous,
                        error = if (resolved == null) "No monthly review available yet." else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not load monthly review.")
                }
            }
        }
    }

    /** Generate a fresh monthly review. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = insightsRepository.generateAndStoreMonthlyReview()
                val previous = insightsRepository.getPreviousMonthlySnapshots()
                _uiState.update {
                    it.copy(isLoading = false, snapshot = snapshot, previousReports = previous)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not refresh monthly review.")
                }
            }
        }
    }

    /** Load an older snapshot from "Previous reports" list. */
    fun viewSnapshot(snapshotId: Long) {
        load(snapshotId)
    }
}
