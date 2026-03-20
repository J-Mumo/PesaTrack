package com.pesatrack.services

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a recipient to be categorized.
 *
 * @property recipientKey Normalized recipient key (uppercase, trimmed)
 * @property displayName Human-readable name (e.g., "NAIVAS SUPERMARKET")
 * @property paymentType PaymentType enum name (SEND_MONEY, BUY_GOODS, etc.)
 * @property totalAmount Sum of all transactions for this recipient
 * @property transactionCount Number of transactions from this recipient
 */
data class RecipientInfo(
    val recipientKey: String,
    val displayName: String,
    val paymentType: String,
    val totalAmount: Double,
    val transactionCount: Int
)

/**
 * Data class representing a suggested category for a recipient.
 *
 * @property categoryId The suggested category ID from the app's category tree
 * @property categoryName Human-readable category name
 * @property groupName Parent group name (e.g., "Food & Dining")
 * @property confidence Confidence level (0.0–1.0)
 */
data class CategorySuggestion(
    val categoryId: Long,
    val categoryName: String,
    val groupName: String,
    val confidence: Float
)

/**
 * Result wrapper for categorization requests.
 *
 * @property suggestions Map of recipientKey → CategorySuggestion
 * @property error Error message if the request failed (null on success)
 */
data class CategorizationResult(
    val suggestions: Map<String, CategorySuggestion> = emptyMap(),
    val error: String? = null
)

/**
 * Service for on-device expense categorization using keyword/rules engine.
 *
 * Replaces the previous Gemini AI implementation with a deterministic,
 * offline rules engine. No API key or network connection required.
 *
 * Takes a list of uncategorized recipient names with context (PaymentType, total amount)
 * and uses keyword matching to suggest the most appropriate category from the app's
 * 17-group, 89+ subcategory tree.
 */
@Singleton
class CategorizationService @Inject constructor(
    private val keywordRulesEngine: KeywordRulesEngine
) {

    companion object {
        private const val TAG = "CategorizationService"
    }

    /**
     * Suggest categories for a list of recipients using the keyword rules engine.
     *
     * This is instant and works offline — no API calls, no rate limits.
     *
     * @param recipients List of recipient info to categorize
     * @return CategorizationResult with suggestions or error
     */
    suspend fun suggestCategories(
        recipients: List<RecipientInfo>
    ): CategorizationResult {
        if (recipients.isEmpty()) {
            return CategorizationResult()
        }

        return try {
            Log.d(TAG, "Categorizing ${recipients.size} recipients via rules engine")
            val result = keywordRulesEngine.categorize(recipients)
            Log.d(TAG, "Rules engine returned ${result.suggestions.size} suggestions")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Rules engine categorization failed", e)
            CategorizationResult(error = "Categorization failed: ${e.message}")
        }
    }
}
