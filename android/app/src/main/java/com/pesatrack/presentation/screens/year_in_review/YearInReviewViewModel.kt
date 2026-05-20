package com.pesatrack.presentation.screens.year_in_review

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.InsightsRepository
import com.pesatrack.presentation.components.ReportRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Year-in-Review screen (Insights & Reports v1.4).
 */
@HiltViewModel
class YearInReviewViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(YearInReviewUiState())
    val uiState: StateFlow<YearInReviewUiState> = _uiState.asStateFlow()

    /**
     * Load a specific year's snapshot or the latest yearly review.
     */
    fun load(year: Int? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val targetYear = year ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                // Always regenerate for fresh data
                val snapshot = insightsRepository.generateAndStoreYearlyReview(targetYear)

                val previous = insightsRepository.getPreviousYearlySnapshots()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snapshot = snapshot,
                        previousReports = previous,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not load year-in-review.")
                }
            }
        }
    }

    /** Generate a fresh yearly review. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val snapshot = insightsRepository.generateAndStoreYearlyReview(currentYear)
                val previous = insightsRepository.getPreviousYearlySnapshots()
                _uiState.update {
                    it.copy(isLoading = false, snapshot = snapshot, previousReports = previous)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not refresh year-in-review.")
                }
            }
        }
    }

    /** Load an older year's snapshot. */
    fun viewYear(year: Int) {
        load(year)
    }

    /** Share the report as an image. */
    fun shareReport(context: Context, captureComposable: () -> android.graphics.Bitmap?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSharing = true) }
            try {
                val bitmap = captureComposable()
                if (bitmap != null) {
                    val snapshot = _uiState.value.snapshot
                    val title = "PesaTrack ${snapshot?.year ?: ""} Year in Review"
                    ReportRenderer.shareReportAsImage(context, bitmap, title)
                }
            } finally {
                _uiState.update { it.copy(isSharing = false) }
            }
        }
    }
}
