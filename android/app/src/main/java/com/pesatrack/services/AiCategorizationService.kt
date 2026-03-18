package com.pesatrack.services

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.pesatrack.BuildConfig
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.CategoryRepository
import com.pesatrack.domain.models.CategoryGroup
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a recipient to be categorized by AI.
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
 * Data class representing an AI-suggested category for a recipient.
 *
 * @property categoryId The suggested category ID from the app's category tree
 * @property categoryName Human-readable category name
 * @property groupName Parent group name (e.g., "Food & Dining")
 * @property confidence AI confidence level (0.0–1.0)
 */
data class AiCategorySuggestion(
    val categoryId: Long,
    val categoryName: String,
    val groupName: String,
    val confidence: Float
)

/**
 * Result wrapper for AI categorization requests.
 *
 * @property suggestions Map of recipientKey → AiCategorySuggestion
 * @property error Error message if the request failed (null on success)
 */
data class AiCategorizationResult(
    val suggestions: Map<String, AiCategorySuggestion> = emptyMap(),
    val error: String? = null
)

/**
 * Service for AI-powered expense categorization using Google Gemini API.
 *
 * Takes a list of uncategorized recipient names with context (PaymentType, total amount)
 * and uses Gemini to suggest the most appropriate category from the app's 17-group,
 * 80+ subcategory tree.
 *
 * API key resolution order:
 * 1. User-provided key from Settings (stored in DataStore)
 * 2. Build-time key from BuildConfig.GEMINI_API_KEY (from local.properties)
 * 3. If both empty → returns error
 */
@Singleton
class AiCategorizationService @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val appPreferences: AppPreferences
) {

    companion object {
        private const val TAG = "AiCategorizationService"

        /** Maximum recipients per API call to avoid prompt bloat */
        private const val MAX_RECIPIENTS_PER_BATCH = 20

        /** Gemini model to use */
        private const val GEMINI_MODEL = "gemini-2.0-flash"
    }

    /**
     * Suggest categories for a list of recipients using Gemini AI.
     *
     * Batches requests if there are more than [MAX_RECIPIENTS_PER_BATCH] recipients.
     * Returns a map of recipientKey → AiCategorySuggestion.
     *
     * @param recipients List of recipient info to categorize
     * @return AiCategorizationResult with suggestions or error
     */
    suspend fun suggestCategories(
        recipients: List<RecipientInfo>
    ): AiCategorizationResult {
        if (recipients.isEmpty()) {
            return AiCategorizationResult()
        }

        // 1. Resolve API key
        val apiKey = resolveApiKey()
        if (apiKey.isNullOrBlank()) {
            return AiCategorizationResult(
                error = "No Gemini API key configured. Add one in Settings or local.properties."
            )
        }

        // 2. Load category tree
        val categoryGroups = try {
            categoryRepository.getCategoryGroups().first()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load categories", e)
            return AiCategorizationResult(error = "Failed to load categories: ${e.message}")
        }

        // 3. Build valid category ID set for validation
        val validCategoryIds = buildValidCategoryIdMap(categoryGroups)

        // 4. Process in batches
        val allSuggestions = mutableMapOf<String, AiCategorySuggestion>()
        val batches = recipients.chunked(MAX_RECIPIENTS_PER_BATCH)

        for (batch in batches) {
            try {
                val batchResult = processBatch(apiKey, batch, categoryGroups, validCategoryIds)
                allSuggestions.putAll(batchResult)
            } catch (e: Exception) {
                Log.e(TAG, "AI categorization batch failed", e)
                return AiCategorizationResult(
                    suggestions = allSuggestions,
                    error = "AI request failed: ${e.message}"
                )
            }
        }

        return AiCategorizationResult(suggestions = allSuggestions)
    }

    /**
     * Resolve the Gemini API key.
     * Priority: User-provided (DataStore) → BuildConfig → null
     */
    private suspend fun resolveApiKey(): String? {
        // Try user-provided key first
        val userKey = appPreferences.getGeminiApiKeySnapshot()
        if (!userKey.isNullOrBlank()) return userKey

        // Fall back to BuildConfig key
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank()) return buildKey

        return null
    }

    /**
     * Process a single batch of recipients through Gemini API.
     */
    private suspend fun processBatch(
        apiKey: String,
        recipients: List<RecipientInfo>,
        categoryGroups: List<CategoryGroup>,
        validCategoryIds: Map<Long, Pair<String, String>> // id → (categoryName, groupName)
    ): Map<String, AiCategorySuggestion> {
        val model = GenerativeModel(
            modelName = GEMINI_MODEL,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                topP = 0.95f
                maxOutputTokens = 2048
            }
        )

        val prompt = buildPrompt(recipients, categoryGroups)
        Log.d(TAG, "Sending prompt to Gemini (${recipients.size} recipients)")

        val response = model.generateContent(prompt)
        val responseText = response.text ?: throw Exception("Empty response from Gemini")
        Log.d(TAG, "Gemini response: $responseText")

        return parseResponse(responseText, validCategoryIds)
    }

    /**
     * Build the Gemini prompt with category tree and recipient list.
     */
    private fun buildPrompt(
        recipients: List<RecipientInfo>,
        categoryGroups: List<CategoryGroup>
    ): String {
        val categoryTree = buildCategoryTreeString(categoryGroups)
        val recipientList = buildRecipientListString(recipients)

        return """
You are a Kenyan expense categorizer for a personal finance app called PesaTrack. 
Given the following expense categories and recipient names from M-PESA and bank transactions, 
suggest the most appropriate subcategory for each recipient.

IMPORTANT RULES:
- You MUST return a valid subcategory ID from the list below (not a group ID)
- Consider the payment type and amount as context clues
- Use your knowledge of Kenyan businesses, services, and payment patterns
- "Send Money" to a person is often for personal reasons — use Miscellaneous (1201) unless the name clearly indicates a business
- "Buy Goods" (Till) payments are typically to businesses — match by business name
- "Pay Bill" payments are typically to utility companies or services
- Confidence should be 0.0–1.0 where 1.0 means absolutely certain

AVAILABLE CATEGORIES:
$categoryTree

RECIPIENTS TO CATEGORIZE:
$recipientList

Return ONLY a valid JSON array with no additional text, markdown, or explanation.
Each element must have exactly these fields:
[
  {"recipientKey": "EXACT_KEY_FROM_INPUT", "categoryId": NUMBER, "confidence": NUMBER}
]

Example:
[
  {"recipientKey": "NAIVAS SUPERMARKET", "categoryId": 703, "confidence": 0.95},
  {"recipientKey": "KPLC PREPAID", "categoryId": 1002, "confidence": 0.98}
]
""".trimIndent()
    }

    /**
     * Build a formatted string of the category tree for the prompt.
     */
    private fun buildCategoryTreeString(categoryGroups: List<CategoryGroup>): String {
        return categoryGroups.joinToString("\n") { group ->
            val children = group.children.joinToString(", ") { child ->
                "${child.name} (ID: ${child.id})"
            }
            "  ${group.parent.name} (Group ${group.parent.id}): $children"
        }
    }

    /**
     * Build a formatted string of recipients for the prompt.
     */
    private fun buildRecipientListString(recipients: List<RecipientInfo>): String {
        return recipients.mapIndexed { index, recipient ->
            val paymentTypeLabel = when (recipient.paymentType) {
                "SEND_MONEY" -> "Send Money"
                "BUY_GOODS" -> "Buy Goods (Till)"
                "PAY_BILL" -> "Pay Bill"
                "WITHDRAW" -> "Withdraw from Agent"
                "AIRTIME" -> "Airtime Purchase"
                "MPESA_CARD" -> "M-PESA Card"
                "BANK_DEBIT" -> "Bank Debit"
                "TRANSACTION_COST" -> "Transaction Cost"
                else -> recipient.paymentType
            }
            val amountStr = "KES %.0f".format(recipient.totalAmount)
            "${index + 1}. \"${recipient.displayName}\" (recipientKey: \"${recipient.recipientKey}\", " +
                    "type: $paymentTypeLabel, total: $amountStr, ${recipient.transactionCount} transactions)"
        }.joinToString("\n")
    }

    /**
     * Parse the Gemini JSON response into a map of suggestions.
     * Validates category IDs against the actual database.
     */
    private fun parseResponse(
        responseText: String,
        validCategoryIds: Map<Long, Pair<String, String>>
    ): Map<String, AiCategorySuggestion> {
        val suggestions = mutableMapOf<String, AiCategorySuggestion>()

        try {
            // Extract JSON array from response (may be wrapped in markdown code blocks)
            val jsonStr = extractJsonArray(responseText)
            val jsonArray = JSONArray(jsonStr)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val recipientKey = item.getString("recipientKey")
                val categoryId = item.getLong("categoryId")
                val confidence = item.getDouble("confidence").toFloat().coerceIn(0f, 1f)

                // Validate category ID exists in the database
                val categoryInfo = validCategoryIds[categoryId]
                if (categoryInfo != null) {
                    suggestions[recipientKey] = AiCategorySuggestion(
                        categoryId = categoryId,
                        categoryName = categoryInfo.first,
                        groupName = categoryInfo.second,
                        confidence = confidence
                    )
                } else {
                    Log.w(TAG, "AI suggested invalid categoryId $categoryId for '$recipientKey', skipping")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI response: $responseText", e)
            throw Exception("Failed to parse AI response: ${e.message}")
        }

        return suggestions
    }

    /**
     * Extract a JSON array string from text that may be wrapped in markdown code blocks.
     * Handles responses like:
     * ```json
     * [...]
     * ```
     */
    private fun extractJsonArray(text: String): String {
        // Try to find JSON array directly
        val trimmed = text.trim()

        // If it starts with '[', it's already clean JSON
        if (trimmed.startsWith("[")) {
            return trimmed
        }

        // Try to extract from markdown code block
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(\\[.*?])\\s*\\n?```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockPattern.find(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }

        // Try to find the first [ ... ] in the text
        val arrayStart = trimmed.indexOf('[')
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1)
        }

        throw Exception("Could not find JSON array in response")
    }

    /**
     * Build a map of valid category IDs → (name, groupName) for validation.
     */
    private fun buildValidCategoryIdMap(
        categoryGroups: List<CategoryGroup>
    ): Map<Long, Pair<String, String>> {
        val map = mutableMapOf<Long, Pair<String, String>>()
        for (group in categoryGroups) {
            for (child in group.children) {
                map[child.id] = Pair(child.name, group.parent.name)
            }
        }
        return map
    }
}
