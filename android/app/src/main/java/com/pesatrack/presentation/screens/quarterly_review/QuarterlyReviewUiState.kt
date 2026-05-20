package com.pesatrack.presentation.screens.quarterly_review

import com.pesatrack.domain.insights.QuarterlyReviewSnapshot

/**
 * UI state for [QuarterlyReviewScreen].
 */
data class QuarterlyReviewUiState(
    val isLoading: Boolean = true,
    val snapshot: QuarterlyReviewSnapshot? = null,
    val previousReports: List<QuarterlyReviewSnapshot> = emptyList(),
    val error: String? = null
)
