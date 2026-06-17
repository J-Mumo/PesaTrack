package com.pesatrack.domain.models

/**
 * Domain model for a single income transaction.
 *
 * Mirrors [com.pesatrack.data.local.database.entities.IncomeTransactionEntity]
 * but exposes `source` as the typed [IncomeSource] enum.
 */
data class IncomeTransaction(
    val id: Long = 0,
    val transactionId: String,
    val amount: Double,
    val timestamp: Long,
    val source: IncomeSource,
    val sender: String? = null,
    val rawSms: String? = null,
    val parserSource: String,
    val note: String? = null,
    val isExcluded: Boolean = false,
    val isCategorized: Boolean = false
)

/** Result of reconciling detected (SMS) income with the user's manual override. */
data class EffectiveIncome(
    /** The value analytics should use, or null when nothing is known. */
    val value: Double?,
    val source: EffectiveIncomeSource,
    /** Sum of non-excluded inflow [IncomeTransaction]s for the period. */
    val detectedAmount: Double,
    /** Manual override amount for the period, if set. */
    val manualAmount: Double?
)

/** Why [EffectiveIncome.value] is what it is — surface this in UI for honesty. */
enum class EffectiveIncomeSource {
    /** No detected income and no manual override — analytics needing income should hide. */
    NONE,

    /** Only the manual override is available; no detected income for the period. */
    MANUAL_OVERRIDE,

    /** Detected income exists and is being used (matches or exceeds the override). */
    DETECTED,

    /** Detected income exists but is suspiciously below the override; using the override. */
    DETECTED_BELOW_OVERRIDE
}

/** Aggregate of one source's detected income for a period. */
data class IncomeSourceTotal(
    val source: IncomeSource,
    val total: Double,
    val transactionCount: Int
)
