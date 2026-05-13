package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted snapshot of a generated report (Weekly / Monthly / Quarterly / Yearly).
 *
 * Each row represents one generated report for a single period. Snapshots are
 * created by the periodic [com.pesatrack.services.WeeklyReviewWorker] (and
 * future per-cadence workers) and read back by the corresponding *ReviewScreen
 * for deep-link viewing and the "Previous reports" list.
 *
 * Storage strategy is intentionally simple for v1.0:
 * - Aggregate numbers live in their own columns (queryable, no JSON dependency).
 * - The Top 5 category list is encoded as a single delimited string in
 *   [topCategories], one entry per line in the form `name|amount|percentage`.
 *   `othersAmount`/`othersCount` capture the rolled-up tail per the
 *   "Where it went" rule in plans/insights-and-reports-plan.md.
 *
 * If/when more complex card data is added in v1.2+, this entity can be split
 * into a parent + child rows in a follow-up migration.
 */
@Entity(
    tableName = "report_snapshots",
    indices = [
        Index(value = ["cadence", "periodStart"], unique = true),
        Index(value = ["cadence", "generatedAt"])
    ]
)
data class ReportSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Report cadence — one of "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY". */
    val cadence: String,

    /** Inclusive start of the reporting window (epoch millis). */
    val periodStart: Long,

    /** Exclusive end of the reporting window (epoch millis). */
    val periodEnd: Long,

    /** When the snapshot was generated (epoch millis). */
    val generatedAt: Long,

    /** Total spend in the period (excludes pass-through expenses). */
    val periodTotal: Double,

    /** Total spend in the comparable previous period (e.g. last week). */
    val previousPeriodTotal: Double,

    /** Average spend per day in the period. */
    val averagePerDay: Double,

    /** Number of distinct days the period covers (typically 7 for weekly). */
    val periodDays: Int,

    /** Category with the largest absolute change vs. previous period (nullable when no data). */
    val biggestChangeCategoryName: String? = null,

    /** Signed delta in KES for the biggest-change category (positive = up, negative = down). */
    val biggestChangeDelta: Double = 0.0,

    /** Total transaction fees in the period (category 606). */
    val feesTotal: Double = 0.0,

    /** Headroom for the period: income − spend. `null` when the user has no income set. */
    val headroomAmount: Double? = null,

    /** Label describing the headroom window shown to the user (e.g. "May 2026"). */
    val headroomLabel: String? = null,

    /**
     * Top 5 categories encoded as `name|amount|percentage` lines, joined by `\n`.
     * Percentage is 0–100, two decimals.
     */
    val topCategories: String = "",

    /** Spend rolled into "(N others: KES X)" — i.e. everything outside Top 5. */
    val othersAmount: Double = 0.0,

    /** Count of categories rolled into `othersAmount`. */
    val othersCount: Int = 0,

    /** Whether the user has marked too little data for honest comparisons. */
    val limitedData: Boolean = false,

    /** When the user opened this snapshot (null until first view). */
    val viewedAt: Long? = null
)
