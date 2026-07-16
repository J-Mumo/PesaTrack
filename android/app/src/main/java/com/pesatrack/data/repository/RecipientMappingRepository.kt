package com.pesatrack.data.repository

import com.pesatrack.data.local.database.dao.RecipientCategoryMappingDao
import com.pesatrack.data.local.database.entities.RecipientCategoryMappingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for recipient→category mapping operations.
 *
 * Supports MULTI-CATEGORY mappings with confidence-based auto-categorization:
 * - One recipient can map to multiple categories with usage counts
 * - Confidence = timesUsed / totalUsesForRecipient
 * - Auto-categorization only applies when confidence ≥ 80%
 * - Otherwise, the mapping is used as a suggestion (pre-fill in UI)
 *
 * Examples:
 *   KPLC: Electricity (100%, 50 uses) → auto-categorize ✅
 *   Naivas: Food (70%, 35 uses), Shopping (24%, 12 uses) → suggest Food, but don't auto-assign
 */
@Singleton
class RecipientMappingRepository @Inject constructor(
    private val mappingDao: RecipientCategoryMappingDao
) {

    companion object {
        /**
         * Minimum confidence (0.0–1.0) required for auto-categorization.
         * If the most-used category for a recipient has ≥ this confidence,
         * new transactions are auto-categorized. Otherwise, left for manual review.
         */
        const val AUTO_CATEGORIZE_CONFIDENCE_THRESHOLD = 0.80f

        /**
         * Prefix on the composite key used for Paybill mappings.
         * A paybill business (e.g. "NCBA LOOP" for paybill 247247) can be an aggregator
         * shared by many unrelated merchants that are only distinguished by the account
         * number. Keying paybill mappings on `<paybillBusiness>::<account>` prevents one
         * merchant's category from cross-firing onto another under the same paybill.
         */
        private const val PAYBILL_KEY_PREFIX = "PAYBILL::"

        /**
         * Build the composite mapping key for a Paybill payment.
         * Returns null if either half is missing/blank — in that case the caller
         * should skip saving/looking up a paybill mapping.
         */
        fun composePaybillKey(paybillName: String?, account: String?): String? {
            val name = paybillName?.let { normalizeRecipientKey(it) }?.takeIf { it.isNotBlank() }
                ?: return null
            val acct = account?.let { normalizeRecipientKey(it) }?.takeIf { it.isNotBlank() }
                ?: return null
            return "$PAYBILL_KEY_PREFIX$name::$acct"
        }

        /**
         * Normalize a recipient key for consistent lookups.
         *
         * - Trims whitespace
         * - Converts to uppercase
         * - Removes trailing periods (Buy Goods SMS has "SHOP NAME.")
         *
         * Examples:
         *   "NAIVAS SUPERMARKET" → "NAIVAS SUPERMARKET"
         *   "naivas supermarket" → "NAIVAS SUPERMARKET"
         *   "sarah k ltd."       → "SARAH K LTD"
         *   "0712345678"         → "0712345678"
         *   " KPLC PREPAID "     → "KPLC PREPAID"
         */
        fun normalizeRecipientKey(key: String): String {
            return key.trim()
                .uppercase()
                .trimEnd('.')
                .trim()
        }
    }

    /**
     * Result of a confidence-based category lookup.
     *
     * @param categoryId The most-used category ID for this recipient
     * @param confidence Confidence level (0.0–1.0)
     * @param isHighConfidence Whether confidence ≥ threshold (safe to auto-assign)
     * @param allMappings All category mappings for this recipient, sorted by usage
     */
    data class CategorySuggestion(
        val categoryId: Long,
        val confidence: Float,
        val isHighConfidence: Boolean,
        val allMappings: List<RecipientCategoryMappingEntity>
    )

    /**
     * Save or update a recipient→category mapping.
     * If the (recipientKey, categoryId) pair exists, increment usage.
     * If it's a new pair, create it.
     */
    suspend fun saveMapping(
        recipientKey: String,
        categoryId: Long,
        displayName: String? = null
    ) {
        val normalized = normalizeRecipientKey(recipientKey)
        val existing = mappingDao.getMappingsForRecipient(normalized)
            .find { it.categoryId == categoryId }

        if (existing != null) {
            // Increment usage for existing (recipient, category) pair
            mappingDao.upsert(
                existing.copy(
                    recipientDisplayName = displayName ?: existing.recipientDisplayName,
                    timesUsed = existing.timesUsed + 1,
                    lastUsed = System.currentTimeMillis()
                )
            )
        } else {
            // New category for this recipient
            mappingDao.upsert(
                RecipientCategoryMappingEntity(
                    recipientKey = normalized,
                    categoryId = categoryId,
                    recipientDisplayName = displayName,
                    timesUsed = 1,
                    lastUsed = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Save multiple mappings at once (batch categorize).
     */
    suspend fun saveMappings(mappings: List<RecipientCategoryMappingEntity>) {
        mappingDao.upsertAll(mappings.map {
            it.copy(recipientKey = normalizeRecipientKey(it.recipientKey))
        })
    }

    /**
     * Get a category suggestion for a recipient with confidence level.
     *
     * Returns null if no mapping exists.
     * Returns CategorySuggestion with isHighConfidence=true if the most-used
     * category has ≥80% of total usage (safe for auto-categorization).
     */
    suspend fun getCategorySuggestion(recipientKey: String): CategorySuggestion? {
        val normalized = normalizeRecipientKey(recipientKey)
        val mappings = mappingDao.getMappingsForRecipient(normalized)
        if (mappings.isEmpty()) return null

        val totalUsage = mappings.sumOf { it.timesUsed }
        val primary = mappings.first() // Already sorted by timesUsed DESC
        val confidence = if (totalUsage > 0) primary.timesUsed.toFloat() / totalUsage else 0f

        return CategorySuggestion(
            categoryId = primary.categoryId,
            confidence = confidence,
            isHighConfidence = confidence >= AUTO_CATEGORIZE_CONFIDENCE_THRESHOLD,
            allMappings = mappings
        )
    }

    /**
     * Look up the category for a recipient ONLY if high confidence.
     * Returns the category ID if confidence ≥ threshold, null otherwise.
     *
     * Used for auto-categorization during import and live SMS processing.
     */
    suspend fun getCategoryForRecipient(recipientKey: String): Long? {
        val suggestion = getCategorySuggestion(recipientKey) ?: return null
        return if (suggestion.isHighConfidence) suggestion.categoryId else null
    }

    /**
     * Save a mapping for a Paybill payment under the composite `<paybill>::<account>` key.
     * Silently no-ops when either half is blank so we don't fall back to the (poisonable)
     * paybill-name-only mapping used before this fix.
     */
    suspend fun savePaybillMapping(
        paybillName: String?,
        account: String?,
        categoryId: Long,
        displayName: String? = null
    ) {
        val composite = composePaybillKey(paybillName, account) ?: return
        saveMapping(
            recipientKey = composite,
            categoryId = categoryId,
            displayName = displayName ?: paybillName
        )
    }

    /**
     * Look up an auto-categorization for a Paybill payment. Only matches on the
     * exact `<paybill>::<account>` pair, so aggregator paybills (e.g. NCBA Loop 247247)
     * never cross-fire between merchants that share the paybill number.
     */
    suspend fun getCategoryForPaybill(paybillName: String?, account: String?): Long? {
        val composite = composePaybillKey(paybillName, account) ?: return null
        val suggestion = getCategorySuggestion(composite) ?: return null
        if (!suggestion.isHighConfidence) return null
        mappingDao.incrementUsage(composite, suggestion.categoryId)
        return suggestion.categoryId
    }

    /**
     * Look up category, trying both recipient and recipientName.
     * Returns category ID only if high confidence.
     *
     * Also increments usage count when a mapping is found and used.
     */
    suspend fun getCategoryForRecipientOrName(
        recipient: String?,
        recipientName: String?
    ): Long? {
        // Try recipientName first (more specific for businesses)
        if (!recipientName.isNullOrBlank()) {
            val nameKey = normalizeRecipientKey(recipientName)
            val suggestion = getCategorySuggestion(nameKey)
            if (suggestion != null && suggestion.isHighConfidence) {
                mappingDao.incrementUsage(nameKey, suggestion.categoryId)
                return suggestion.categoryId
            }
        }
        // Then try recipient (phone number or till number)
        if (!recipient.isNullOrBlank()) {
            val recipientKey = normalizeRecipientKey(recipient)
            val suggestion = getCategorySuggestion(recipientKey)
            if (suggestion != null && suggestion.isHighConfidence) {
                mappingDao.incrementUsage(recipientKey, suggestion.categoryId)
                return suggestion.categoryId
            }
        }
        return null
    }

    /**
     * Get category suggestion trying both recipient and recipientName.
     * Returns the suggestion regardless of confidence level (for UI pre-fill).
     *
     * Unlike getCategoryForRecipientOrName, this returns even low-confidence suggestions.
     */
    suspend fun getSuggestionForRecipientOrName(
        recipient: String?,
        recipientName: String?
    ): CategorySuggestion? {
        // Try recipientName first
        if (!recipientName.isNullOrBlank()) {
            val nameKey = normalizeRecipientKey(recipientName)
            val suggestion = getCategorySuggestion(nameKey)
            if (suggestion != null) return suggestion
        }
        // Then try recipient
        if (!recipient.isNullOrBlank()) {
            val recipientKey = normalizeRecipientKey(recipient)
            val suggestion = getCategorySuggestion(recipientKey)
            if (suggestion != null) return suggestion
        }
        return null
    }

    /**
     * Get all mappings as a Flow (for UI display)
     */
    fun getAllMappings(): Flow<List<RecipientCategoryMappingEntity>> {
        return mappingDao.getAllMappings()
    }

    /**
     * Get all known recipient keys for quick batch lookup
     */
    suspend fun getAllRecipientKeys(): Set<String> {
        return mappingDao.getAllRecipientKeys().toSet()
    }

    /**
     * Get all mappings grouped by recipient for fast lookup during import.
     * Returns Map<recipientKey, List<Mapping>> sorted by timesUsed DESC.
     */
    suspend fun getMappingsGroupedByRecipient(): Map<String, List<RecipientCategoryMappingEntity>> {
        return mappingDao.getAllMappingsSync().groupBy { it.recipientKey }
    }

    /**
     * Get confident mappings as a simple map for fast import lookup.
     * Only includes recipients with ≥80% confidence.
     */
    suspend fun getConfidentMappingsAsMap(): Map<String, Long> {
        val grouped = getMappingsGroupedByRecipient()
        return grouped.mapNotNull { (key, mappings) ->
            val totalUsage = mappings.sumOf { it.timesUsed }
            val primary = mappings.maxByOrNull { it.timesUsed } ?: return@mapNotNull null
            val confidence = if (totalUsage > 0) primary.timesUsed.toFloat() / totalUsage else 0f
            if (confidence >= AUTO_CATEGORIZE_CONFIDENCE_THRESHOLD) {
                key to primary.categoryId
            } else null
        }.toMap()
    }

    /**
     * Delete all mappings for a recipient
     */
    suspend fun deleteMapping(recipientKey: String) {
        mappingDao.deleteAllForRecipient(normalizeRecipientKey(recipientKey))
    }

    /**
     * Get count of mapped recipients
     */
    suspend fun getMappingCount(): Int {
        return mappingDao.getMappedRecipientCount()
    }
}
