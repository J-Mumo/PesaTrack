package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Budget entity representing a spending limit for a category group or sub-category.
 *
 * Budget levels:
 * - categoryId = group ID (1-18) + isGroupBudget = true → Group-level budget (tracks all sub-categories)
 * - categoryId = sub-category ID + isGroupBudget = false → Sub-category-level budget (tracks one sub-category)
 *
 * Supported periods: WEEKLY, MONTHLY, YEARLY, CUSTOM
 * - WEEKLY/MONTHLY/YEARLY: standard calendar-aligned periods.
 * - CUSTOM: user-defined date range via customStartDate/customEndDate.
 *
 * No rollover — each period starts fresh.
 * Alert thresholds are hardcoded at 80% (warning) and 100% (exceeded).
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["isActive"])
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Category ID (FK → categories). Can be a group or sub-category. */
    val categoryId: Long? = null,

    /**
     * Whether this budget tracks a whole group (true) or a single sub-category (false).
     * - true: categoryId is a group ID; spending = sum of all sub-categories in that group.
     * - false: categoryId is a sub-category ID; spending = only that sub-category.
     */
    val isGroupBudget: Boolean = true,

    /** Budget limit in KES */
    val amount: Double,

    /** Budget period: WEEKLY, MONTHLY, YEARLY, CUSTOM */
    val period: String,

    /** Start date millis for CUSTOM period. Null for standard periods. */
    val customStartDate: Long? = null,

    /** End date millis for CUSTOM period. Null for standard periods. */
    val customEndDate: Long? = null,

    /** Whether this budget is currently active */
    val isActive: Boolean = true,

    /** Record creation timestamp */
    val createdAt: Long = System.currentTimeMillis(),

    /** Record last update timestamp */
    val updatedAt: Long = System.currentTimeMillis()
)
