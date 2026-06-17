package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Manual monthly income budget / override.
 *
 * One row per month — yearMonth is unique-indexed so upserting
 * naturally replaces the previous value for that month.
 *
 * This is the user-set "expected income" used as a fallback when
 * SMS-detected income (see [IncomeTransactionEntity]) is missing or
 * suspiciously low. Reconciliation lives in `IncomeRepository`.
 *
 * The on-disk table is still named "income" — only the Kotlin type
 * has been renamed for clarity (Phase 1 of the income tracking plan).
 */
@Entity(
    tableName = "income",
    indices = [
        Index(value = ["yearMonth"], unique = true)
    ]
)
data class MonthlyIncomeBudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Budget amount in KES */
    val amount: Double,

    /** Year-month string, e.g. "2026-03" — unique per row */
    val yearMonth: String,

    /** Optional label, e.g. "Salary + freelance" */
    val note: String? = null,

    /** Last update timestamp */
    val updatedAt: Long = System.currentTimeMillis()
)
