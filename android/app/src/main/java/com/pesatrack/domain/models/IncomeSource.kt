package com.pesatrack.domain.models

/**
 * Source classification for an [IncomeTransaction].
 *
 * `isInflow = false` flags pass-through / self-transfer flows that should
 * count in raw totals but be excluded from savings-rate and "% of income"
 * denominators (e.g. an M-Shwari withdrawal back to M-PESA is not new income).
 */
enum class IncomeSource(val displayName: String, val isInflow: Boolean) {
    SALARY("Salary", true),
    BUSINESS("Business income", true),
    REFUND("Refund", true),
    INTEREST("Interest / dividends", true),
    FAMILY("Family / gift", true),
    TRANSFER_IN("Transfer in", false),
    OTHER("Other", true),
    UNCATEGORIZED("Uncategorized", true);

    companion object {
        /** Safe lookup that defaults to [UNCATEGORIZED] for unknown / null values. */
        fun fromName(name: String?): IncomeSource =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: UNCATEGORIZED

        /** Sources that contribute to the savings-rate denominator. */
        val INFLOW_SOURCES: List<IncomeSource> = entries.filter { it.isInflow }
    }
}
