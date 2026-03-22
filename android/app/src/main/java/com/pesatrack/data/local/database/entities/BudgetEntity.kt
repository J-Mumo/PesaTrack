package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Budget entity representing a spending limit for a category group, sub-category, or total spending.
 *
 * Budget levels:
 * - categoryId = null → "Total Spending" budget
 * - categoryId = group ID (1-18) + isGroupBudget = true → Group-level budget (tracks all sub-categories)
 * - categoryId = sub-category ID + isGroupBudget = false → Sub-category-level budget (tracks one sub-category)
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

    /** Category ID (FK → categories). Null = "Total Spending" budget. Can be a group or sub-category. */
    val categoryId: Long? = null,

    /**
     * Whether this budget tracks a whole group (true) or a single sub-category (false).
     * - true: categoryId is a group ID; spending = sum of all sub-categories in that group.
     * - false: categoryId is a sub-category ID; spending = only that sub-category.
     * - Ignored when categoryId is null (Total Spending).
     */
    val isGroupBudget: Boolean = true,

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
