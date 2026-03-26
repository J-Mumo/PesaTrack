package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Monthly income record.
 *
 * Stores the user's manually entered income for a specific month.
 * Used by the Budget screen to compare total budgeted amounts against
 * actual income, warning when budgets exceed income.
 *
 * One row per month — yearMonth is unique-indexed so upserting
 * naturally replaces the previous value for that month.
 */
@Entity(
    tableName = "income",
    indices = [
        Index(value = ["yearMonth"], unique = true)
    ]
)
data class IncomeEntity(
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
