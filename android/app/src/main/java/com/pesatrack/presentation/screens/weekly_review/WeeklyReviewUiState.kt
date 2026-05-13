package com.pesatrack.presentation.screens.weekly_review

import com.pesatrack.data.local.database.entities.ReportSnapshotEntity
import com.pesatrack.domain.insights.WeeklyReviewSnapshot

/**
 * UI state for [WeeklyReviewScreen].
 *
 * @property isLoading Whether the snapshot is still being hydrated.
 * @property snapshot The current weekly review snapshot, or null if none exists yet
 *                    (e.g. first install before any Thursday has elapsed).
 * @property previousSnapshots Past weekly snapshots for the "Previous reports" list (newest first).
 * @property errorMessage Optional error to surface (e.g. a generation failure).
 */
data class WeeklyReviewUiState(
    val isLoading: Boolean = true,
    val snapshot: WeeklyReviewSnapshot? = null,
    val previousSnapshots: List<ReportSnapshotEntity> = emptyList(),
    val errorMessage: String? = null
)
