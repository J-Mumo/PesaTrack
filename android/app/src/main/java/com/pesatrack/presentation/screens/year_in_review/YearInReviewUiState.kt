package com.pesatrack.presentation.screens.year_in_review

import com.pesatrack.domain.insights.YearInReviewSnapshot

/**
 * UI state for [YearInReviewScreen].
 */
data class YearInReviewUiState(
    val isLoading: Boolean = true,
    val snapshot: YearInReviewSnapshot? = null,
    val previousReports: List<YearInReviewSnapshot> = emptyList(),
    val error: String? = null,
    val isSharing: Boolean = false
)
