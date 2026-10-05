package com.pesatrack.domain.insights

import com.pesatrack.data.local.database.dao.CategoryTotal

/**
 * "Aha" findings shown on Home the first time a user has data
 * (onboarding Paths A and B), or as clearly-labelled example data when they
 * have none (Path C).
 *
 * Principles: *Awareness before action* (facts first, no task required),
 * *Honest numbers* (fees from cat 606 are surfaced as their own line and the
 * period is always stated), *Nudge, don't nag* (card is dismissible).
 */
data class FirstFindings(
    /** Human-readable period label, e.g. "Last 30 days". */
    val periodLabel: String,
    val totalSpent: Double,
    val transactionCount: Int,
    /** Largest non-fee category, or null when nothing is categorised yet. */
    val topCategoryName: String?,
    val topCategoryTotal: Double,
    /** Share of [totalSpent] (0–100), rounded. */
    val topCategorySharePct: Int,
    /** Category 606 total. */
    val feesTotal: Double,
    /** True when this is illustrative sample data, not the user's own. */
    val isExample: Boolean = false
) {
    /** Headline kind for telemetry (E). */
    val headlineKind: String
        get() = when {
            feesTotal > 0.0 -> "fees_total"
            topCategoryName != null -> "top_category"
            else -> "monthly_total"
        }
}

object FirstFindingsGenerator {

    const val FEES_CATEGORY_ID = 606L
    /** Need at least this many transactions before the findings are meaningful. */
    const val MIN_TRANSACTIONS = 3

    /**
     * Build findings from category totals for a window. Returns null when
     * there isn't enough data to say anything honest.
     */
    fun generate(
        categoryTotals: List<CategoryTotal>,
        periodLabel: String
    ): FirstFindings? {
        val total = categoryTotals.sumOf { it.total }
        val count = categoryTotals.sumOf { it.transactionCount }
        if (count < MIN_TRANSACTIONS || total <= 0.0) return null

        val fees = categoryTotals
            .filter { it.categoryId == FEES_CATEGORY_ID }
            .sumOf { it.total }
        val top = categoryTotals
            .filter { it.categoryId != null && it.categoryId != FEES_CATEGORY_ID }
            .filter { it.total > 0.0 }
            .maxByOrNull { it.total }
        val share = if (top != null) Math.round(top.total / total * 100.0).toInt() else 0

        return FirstFindings(
            periodLabel = periodLabel,
            totalSpent = total,
            transactionCount = count,
            topCategoryName = top?.categoryName,
            topCategoryTotal = top?.total ?: 0.0,
            topCategorySharePct = share,
            feesTotal = fees
        )
    }

    /** Fixed illustrative findings for Path C (no SMS, no import). */
    fun example(): FirstFindings = FirstFindings(
        periodLabel = "Example month",
        totalSpent = 18_400.0,
        transactionCount = 46,
        topCategoryName = "Transport",
        topCategoryTotal = 5_900.0,
        topCategorySharePct = 32,
        feesTotal = 1_240.0,
        isExample = true
    )
}
