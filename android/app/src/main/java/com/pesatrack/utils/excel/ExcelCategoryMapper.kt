package com.pesatrack.utils.excel

/**
 * Maps Excel expense category labels to PesaTrack category IDs.
 *
 * Uses a hardcoded mapping table covering 55+ known labels from the user's
 * historical Excel spreadsheets. Unknown labels return null (imported uncategorized).
 *
 * Category IDs reference [DefaultCategories] in CategoryEntity.kt:
 *   GroupID * 100 + sortOrder (e.g., Faith & Giving = group 5, Give = 503)
 */
object ExcelCategoryMapper {

    /**
     * Map of normalized Excel label → PesaTrack category ID.
     * Labels are lowercased and trimmed for matching.
     */
    private val categoryMap: Map<String, Long> = mapOf(
        // ── Beekeeping (Group 1) ──
        "bee equipment" to 101L,
        "bee hives" to 104L,
        "bee hive tables" to 103L,
        "hive tables" to 103L,
        "bee swarmer" to 106L,
        "bee wax" to 107L,
        "bees" to 108L,
        "honey harvesting" to 109L,
        "honey packaging" to 110L,
        "hive maintenance" to 109L,  // closest match — maintenance/harvesting

        // ── Digital & Tech (Group 2) ──
        "airtime" to 202L,
        "airtime " to 202L,  // trailing space variant in Excel
        "data bundles" to 205L,
        "internet bundles" to 205L,
        "internet bundles " to 205L,
        "internet" to 1007L,  // Home WiFi (ISP subscription)
        "internet " to 1007L,
        "website domain" to 207L,
        "open ai" to 201L,

        // ── Education (Group 3) ──
        "school fees" to 304L,

        // ── Entertainment (Group 4) ──
        "entertainment" to 405L,
        "entertainment " to 405L,

        // ── Faith & Giving (Group 5) ──
        "give" to 503L,
        "give " to 503L,
        "heaven's gate" to 501L,  // Church program
        "heaven's gate " to 501L,
        "offering" to 504L,
        "offering " to 504L,
        "seed" to 505L,
        "tithe" to 506L,
        "tithe " to 506L,

        // ── Financial (Group 6) ──
        "bank charges" to 601L,
        "cba commission" to 601L,
        "cba commission " to 601L,
        "invest" to 602L,
        "invest " to 602L,
        "loan interest" to 603L,
        "loan interest " to 603L,
        "loan" to 604L,
        "loan repayment" to 604L,
        "loan repayment " to 604L,
        "money market fund" to 605L,
        "mpesa transaction cost" to 606L,
        "mpesa transaction cost " to 606L,
        "pesalink" to 608L,
        "pesalink charges" to 608L,
        "rtgs" to 609L,
        "rtgs charges" to 609L,
        "savings" to 611L,
        "treasury bill commission" to 612L,
        "treasury bill commission " to 612L,
        "mobile payment charges" to 601L,
        "mobile payment charged" to 601L,
        "mobile payments charges" to 601L,
        "mobile payment charges " to 601L,
        "mobile payment charges" to 601L,
        "mobile payment charges " to 601L,

        // ── Food & Dining (Group 7) ──
        "food" to 702L,
        "food " to 702L,
        "food entertainment" to 702L,
        "food entertainment " to 702L,
        "meat" to 703L,

        // ── Government & Legal (Group 8) ──
        "excise duty" to 803L,
        "excise duty " to 803L,
        "ke excise duty" to 803L,
        "withholding tax" to 813L,

        // ── Health (Group 9) ──
        "medical" to 904L,
        "medical " to 904L,

        // ── Home & Utilities (Group 10) ──
        "cleaning" to 1001L,
        "cleaning " to 1001L,
        "electricity" to 1002L,
        "electricity " to 1002L,
        "gas" to 1004L,
        "home furnishing" to 1006L,
        "home furnishing " to 1006L,
        "rent" to 1009L,
        "water" to 1012L,
        "water bill" to 1012L,

        // ── Miscellaneous (Group 12) ──
        "miscellaneous" to 1201L,
        "miscellaneous " to 1201L,

        // ── Personal Care (Group 13) ──
        "barber" to 1301L,
        "barber " to 1301L,

        // ── Pets (Group 14) ──
        "cat treatment" to 1404L,
        "cat treatment " to 1404L,

        // ── Shopping (Group 15) ──
        "clothing" to 1503L,
        "clothing " to 1503L,
        "shopping" to 1505L,
        "shopping " to 1505L,
        "shipping" to 1509L,
        "shipping " to 1509L,

        // ── Transport & Travel (Group 16) ──
        "accommodation" to 1601L,
        "fare" to 1604L,
        "fare " to 1604L,

        // ── Vehicle (Group 17) ──
        "car accessories" to 1701L,
        "car accessories " to 1701L,
        "car alignment" to 1708L,  // Car Service (alignment is a service job)
        "car alignment " to 1708L,
        "car battery" to 1702L,
        "car battery " to 1702L,
        "car hire" to 1703L,
        "car insurance" to 1704L,
        "car insurance " to 1704L,
        "car payment" to 1705L,
        "car registration" to 1706L,
        "car repairs" to 1707L,
        "car repairs " to 1707L,
        "car service" to 1708L,
        "car service " to 1708L,
        "car wash" to 1710L,
        "car wash " to 1710L,
        "expressway" to 1711L,
        "expressway " to 1711L,
        "fuel" to 1712L,
        "fuel " to 1712L,
        "parking" to 1713L,
        "parking " to 1713L,
    )

    /**
     * Look up the PesaTrack category ID for an Excel label.
     *
     * @param excelLabel Raw label from the Excel "Expense" column
     * @return Category ID, or null if unknown label (will be imported uncategorized)
     */
    fun getCategoryId(excelLabel: String): Long? {
        val normalized = excelLabel.trim().lowercase()
        return categoryMap[normalized]
    }

    /**
     * Check if a label is a known category.
     */
    fun isKnownLabel(excelLabel: String): Boolean {
        return getCategoryId(excelLabel) != null
    }

    /**
     * Get all known Excel labels (for diagnostics/logging).
     */
    fun getAllKnownLabels(): Set<String> {
        return categoryMap.keys
    }
}
