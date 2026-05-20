package com.pesatrack.presentation.screens.quarterly_review

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
 * ViewModel for the Quarterly Review screen (Insights & Reports v1.3).
 */
@HiltViewModel
class QuarterlyReviewViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuarterlyReviewUiState())
    val uiState: StateFlow<QuarterlyReviewUiState> = _uiState.asStateFlow()

    /**
     * Load a specific snapshot by id or the latest quarterly review.
     */
    fun load(snapshotId: Long? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = when {
                    snapshotId != null -> insightsRepository.getQuarterlySnapshot(snapshotId)
                    else -> {
                        // Always regenerate to ensure fresh data
                        insightsRepository.generateAndStoreQuarterlyReview()
                    }
                }

                val previous = insightsRepository.getPreviousQuarterlySnapshots()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snapshot = snapshot,
                        previousReports = previous,
                        error = if (snapshot == null) "No quarterly review available yet." else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not load quarterly review.")
                }
            }
        }
    }

    /** Generate a fresh quarterly review. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = insightsRepository.generateAndStoreQuarterlyReview()
                val previous = insightsRepository.getPreviousQuarterlySnapshots()
                _uiState.update {
                    it.copy(isLoading = false, snapshot = snapshot, previousReports = previous)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not refresh quarterly review.")
                }
            }
        }
    }

    /** Load an older snapshot from "Previous reports" list. */
    fun viewSnapshot(snapshotId: Long) {
        load(snapshotId)
    }
}
