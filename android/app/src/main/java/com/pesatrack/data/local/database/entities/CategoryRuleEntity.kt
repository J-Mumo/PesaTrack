package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-defined auto-categorization rule.
 *
 * These rules are checked BEFORE the built-in KeywordRulesEngine,
 * giving users full control over how recipients are categorized.
 *
 * Match types:
 * - EXACT: Full recipient name must match (case-insensitive)
 * - CONTAINS: Recipient name must contain the pattern (case-insensitive)
 * - STARTS_WITH: Recipient name must start with the pattern (case-insensitive)
 */
@Entity(
    tableName = "category_rules",
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
        Index(value = ["pattern"])
    ]
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The pattern to match against recipient names */
    val pattern: String,

    /** Match type: EXACT, CONTAINS, or STARTS_WITH */
    val matchType: String,

    /** Target category ID (FK → categories) */
    val categoryId: Long,

    /** Higher priority rules are checked first */
    val priority: Int = 0,

    /** Whether this rule is active */
    val isActive: Boolean = true,

    /** Timestamp when the rule was created */
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Supported match types for category rules
 */
enum class RuleMatchType {
    EXACT,
    CONTAINS,
    STARTS_WITH
}
