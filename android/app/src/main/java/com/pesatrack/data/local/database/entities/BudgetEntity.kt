package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Budget entity representing a spending limit for a category group or total spending.
 *
 * Budget levels:
 * - categoryGroupId = null → "Total Spending" budget
 * - categoryGroupId = group ID (1-18) → Group-level budget
 *
 * Supported periods: WEEKLY, MONTHLY, YEARLY
 * No rollover — each period starts fresh.
 * Alert thresholds are hardcoded at 80% (warning) and 100% (exceeded).
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryGroupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoryGroupId"]),
        Index(value = ["isActive"])
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Category group ID (FK → categories). Null = "Total Spending" budget. */
    val categoryGroupId: Long? = null,

    /** Budget limit in KES */
    val amount: Double,

    /** Budget period: WEEKLY, MONTHLY, YEARLY */
    val period: String,

    /** Whether this budget is currently active */
    val isActive: Boolean = true,

    /** Record creation timestamp */
    val createdAt: Long = System.currentTimeMillis(),

    /** Record last update timestamp */
    val updatedAt: Long = System.currentTimeMillis()
)
