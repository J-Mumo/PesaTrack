package com.pesatrack.presentation.screens.weekly_review

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
 * ViewModel for the Weekly Review screen (Insights & Reports v1.0).
 *
 * Responsibilities:
 * - Hydrate a specific snapshot (deep-link from notification) when an id is passed,
 *   otherwise load the most recent snapshot.
 * - Generate a fresh snapshot on demand (pull-to-refresh / "Refresh" button).
 * - Mark snapshots as viewed so future Insights features can rank by recency-of-view.
 */
@HiltViewModel
class WeeklyReviewViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyReviewUiState())
    val uiState: StateFlow<WeeklyReviewUiState> = _uiState.asStateFlow()

    /**
     * Initial load. If [snapshotId] is non-null we deep-link to that specific snapshot
     * (so notifications always show the report they promised). Otherwise we load the
     * latest, generating one on the fly if there is no stored snapshot yet.
     */
    fun load(snapshotId: Long? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val snapshot = when {
                    snapshotId != null -> insightsRepository.getSnapshot(snapshotId)
                    else -> insightsRepository.getLatestWeekly()
                }

                val resolved = if (snapshot == null && snapshotId == null) {
                    // No stored snapshot at all — generate one so the screen has content
                    // when opened from Settings before the first Thursday trigger.
                    val entity = insightsRepository.generateAndStoreWeeklyReview()
                    insightsRepository.getSnapshot(entity.id)
                } else snapshot

                if (snapshotId != null && resolved != null) {
                    insightsRepository.markViewed(snapshotId)
                }

                val previous = insightsRepository.getPreviousWeeklySnapshots()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snapshot = resolved,
                        previousSnapshots = previous,
                        errorMessage = if (resolved == null) "No weekly review available yet." else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Could not load weekly review.")
                }
            }
        }
    }

    /** Generate a fresh weekly review for the current trailing 7 days. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val entity = insightsRepository.generateAndStoreWeeklyReview()
                val snapshot = insightsRepository.getSnapshot(entity.id)
                val previous = insightsRepository.getPreviousWeeklySnapshots()
                _uiState.update {
                    it.copy(isLoading = false, snapshot = snapshot, previousSnapshots = previous)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Could not refresh weekly review.")
                }
            }
        }
    }

    /** Load an older snapshot picked from the "Previous reports" list. */
    fun viewSnapshot(snapshotId: Long) {
        load(snapshotId)
    }
}
