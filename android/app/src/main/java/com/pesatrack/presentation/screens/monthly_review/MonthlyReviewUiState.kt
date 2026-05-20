package com.pesatrack.presentation.screens.monthly_review

import com.pesatrack.domain.insights.MonthlyReviewSnapshot

/**
 * UI state for [MonthlyReviewScreen].
 */
data class MonthlyReviewUiState(
    val isLoading: Boolean = true,
    val snapshot: MonthlyReviewSnapshot? = null,
    val previousReports: List<MonthlyReviewSnapshot> = emptyList(),
    val error: String? = null
)
