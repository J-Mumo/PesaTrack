package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent learned mapping from an income sender (M-PESA payer name,
 * bank originating account, etc.) to a default [com.pesatrack.domain.models.IncomeSource].
 *
 * Written by the income-categorization flow when the user confirms a
 * non-`UNCATEGORIZED` source for a sender we have not seen before.
 * Read by [com.pesatrack.data.repository.IncomeRepository.insertIfNew]
 * to auto-classify subsequent income from the same sender.
 *
 * Introduced in Migration v17 → v18 (see `plans/income-tracking-plan.md` §5.5).
 */
@Entity(tableName = "income_sender_rules")
data class IncomeSenderRuleEntity(
    @PrimaryKey
    val sender: String,
    /** [com.pesatrack.domain.models.IncomeSource] name. */
    val source: String,
    val learnedAt: Long
)
