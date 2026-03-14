package com.pesatrack.utils.parsers

import com.pesatrack.utils.SmsParser

/**
 * Registry of all SMS parser strategies.
 *
 * Dispatches incoming SMS to the appropriate parser based on sender ID.
 * New bank parsers are added here as simple list entries.
 *
 * Usage:
 * ```
 * val parsed = SmsParserRegistry.parseTransaction(sender, body)
 * if (parsed != null) { ... }
 * ```
 */
object SmsParserRegistry {

    /**
     * All registered parsers, ordered by priority.
     * M-PESA is first (most common), then bank parsers.
     */
    private val parsers: List<SmsParserStrategy> = listOf(
        MpesaSmsParser(),
        NcbaBankParser(),
        // Future: EquityBankParser(), KcbBankParser(), etc.
    )

    /**
     * Find a parser that can handle this SMS.
     *
     * @param sender The SMS originating address
     * @param body The SMS message body
     * @return The matching parser, or null if no parser can handle it
     */
    fun findParser(sender: String, body: String): SmsParserStrategy? {
        return parsers.firstOrNull { it.canHandle(sender, body) }
    }

    /**
     * Parse a transaction from any supported source.
     *
     * @param sender The SMS originating address
     * @param body The SMS message body
     * @return ParsedTransaction if parsing successful, null otherwise
     */
    fun parseTransaction(sender: String, body: String): SmsParser.ParsedTransaction? {
        return findParser(sender, body)?.parse(body)
    }

    /**
     * Check if any registered parser can handle this SMS.
     */
    fun canHandleAny(sender: String, body: String): Boolean {
        return parsers.any { it.canHandle(sender, body) }
    }

    /**
     * Get all known sender IDs across all parsers.
     * Used by SmsImportService to query ContentResolver for all supported senders.
     */
    fun getAllSenderIds(): List<String> {
        return parsers.flatMap { it.senderIds }.distinct()
    }

    /**
     * Get sender IDs for specific parsers by display name.
     * Used when filtering by enabled banks in settings.
     *
     * @param enabledParsers Set of parser display names (e.g., "M-PESA", "NCBA Bank")
     * @return List of sender IDs for the enabled parsers
     */
    fun getEnabledSenderIds(enabledParsers: Set<String>): List<String> {
        return parsers
            .filter { it.displayName in enabledParsers }
            .flatMap { it.senderIds }
            .distinct()
    }

    /**
     * Get all registered parser display names.
     * Used by settings screen to show available banks.
     */
    fun getAllParserNames(): List<String> {
        return parsers.map { it.displayName }
    }
}
