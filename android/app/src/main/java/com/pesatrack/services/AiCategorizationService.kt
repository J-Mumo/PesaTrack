package com.pesatrack.services

import android.util.Log
import com.pesatrack.data.local.database.entities.RuleMatchType
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.data.repository.CategoryRule
import com.pesatrack.data.repository.CategoryRuleRepository
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
 * Service for on-device expense categorization.
 *
 * Evaluation order:
 * 0. **User-defined rules** (from category_rules table — highest priority)
 * 1. Built-in KeywordRulesEngine (PaymentType → exact name → keyword → fallback)
 *
 * User rules take precedence over built-in rules, giving users full control
 * over how their recipients are categorized.
 */
@Singleton
class CategorizationService @Inject constructor(
    private val keywordRulesEngine: KeywordRulesEngine,
    private val categoryRuleRepository: CategoryRuleRepository,
    private val categoryRepository: CategoryRepository
) {

    companion object {
        private const val TAG = "CategorizationService"
        private const val CONFIDENCE_USER_RULE = 0.99f
    }

    /**
     * Suggest categories for a list of recipients.
     *
     * Checks user-defined rules first, then falls back to the built-in engine.
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
            // Load user-defined rules
            val userRules = categoryRuleRepository.getActiveRules()
            Log.d(TAG, "Loaded ${userRules.size} user rules, categorizing ${recipients.size} recipients")

            val suggestions = mutableMapOf<String, CategorySuggestion>()

            // First pass: try user rules
            val remaining = mutableListOf<RecipientInfo>()
            for (recipient in recipients) {
                val userMatch = matchUserRule(recipient, userRules)
                if (userMatch != null) {
                    suggestions[recipient.recipientKey] = userMatch
                } else {
                    remaining.add(recipient)
                }
            }

            Log.d(TAG, "User rules matched ${suggestions.size} recipients, ${remaining.size} remaining")

            // Second pass: built-in engine for unmatched recipients
            if (remaining.isNotEmpty()) {
                val builtInResult = keywordRulesEngine.categorize(remaining)
                suggestions.putAll(builtInResult.suggestions)
            }

            Log.d(TAG, "Total: ${suggestions.size}/${recipients.size} categorized")
            CategorizationResult(suggestions = suggestions)
        } catch (e: Exception) {
            Log.e(TAG, "Categorization failed", e)
            CategorizationResult(error = "Categorization failed: ${e.message}")
        }
    }

    /**
     * Match a recipient against user-defined rules.
     * Rules are already sorted by priority descending.
     */
    private suspend fun matchUserRule(
        recipient: RecipientInfo,
        rules: List<CategoryRule>
    ): CategorySuggestion? {
        val name = recipient.displayName.uppercase().trim()

        for (rule in rules) {
            val pattern = rule.pattern.uppercase().trim()
            val matches = when (rule.matchType) {
                RuleMatchType.EXACT -> name == pattern
                RuleMatchType.CONTAINS -> name.contains(pattern)
                RuleMatchType.STARTS_WITH -> name.startsWith(pattern)
            }

            if (matches) {
                // Resolve category name and group name
                val category = categoryRepository.getCategoryById(rule.categoryId)
                val groupName = category?.parentId?.let { parentId ->
                    categoryRepository.getCategoryById(parentId)?.name
                } ?: "Unknown"

                return CategorySuggestion(
                    categoryId = rule.categoryId,
                    categoryName = category?.name ?: "Unknown",
                    groupName = groupName,
                    confidence = CONFIDENCE_USER_RULE
                )
            }
        }

        return null
    }
}
