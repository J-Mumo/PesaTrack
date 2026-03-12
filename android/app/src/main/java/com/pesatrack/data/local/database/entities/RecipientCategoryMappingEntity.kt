package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Stores learned recipient→category mappings.
 *
 * Supports MULTI-CATEGORY mappings: one recipient can map to multiple categories
 * with usage counts for confidence-based auto-categorization.
 *
 * Example:
 *   NAIVAS → Food (35 uses, 70%)
 *   NAIVAS → Shopping (12 uses, 24%)
 *   NAIVAS → Personal Care (3 uses, 6%)
 *
 * Used by:
 * - Historical SMS import (auto-categorize high-confidence recipients)
 * - Live SMS processing (auto-categorize if ≥80% confidence)
 * - Batch categorize screen (suggest most-used category)
 */
@Entity(
    tableName = "recipient_category_mapping",
    primaryKeys = ["recipientKey", "categoryId"],
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
        Index(value = ["recipientKey"])
    ]
)
data class RecipientCategoryMappingEntity(
    /**
     * Normalized recipient key.
     * - For phone numbers: normalized format "0712345678"
     * - For businesses: uppercase name "NAIVAS SUPERMARKET"
     * - For paybills: business name uppercase "KPLC PREPAID"
     */
    val recipientKey: String,

    /** The category ID assigned to this recipient */
    val categoryId: Long,

    /** Human-readable display name for the recipient */
    val recipientDisplayName: String? = null,

    /** Number of times this specific recipient+category combination has been used */
    val timesUsed: Int = 1,

    /** Timestamp of last use (for recency ranking) */
    val lastUsed: Long = System.currentTimeMillis()
)
