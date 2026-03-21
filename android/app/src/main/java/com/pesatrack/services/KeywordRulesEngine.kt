package com.pesatrack.services

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device keyword/rules engine for expense categorization.
 *
 * Replaces the Gemini AI service with deterministic, offline categorization
 * based on recipient name keywords + payment type context.
 *
 * Rules are evaluated in priority order:
 * 1. PaymentType-based rules (AIRTIME → 202, TRANSACTION_COST → 606)
 * 2. Exact recipient name matches (known Kenyan businesses)
 * 3. Keyword/substring matches against recipient display name
 * 4. Fallback to Miscellaneous (1201) for SEND_MONEY to unknown persons
 *
 * Category IDs reference [DefaultCategories] in CategoryEntity.kt:
 *   GroupID * 100 + sortOrder (e.g., Groceries = 703, Electricity = 1002)
 */
@Singleton
class KeywordRulesEngine @Inject constructor() {

    companion object {
        private const val TAG = "KeywordRulesEngine"

        // Confidence levels for different match types
        private const val CONFIDENCE_EXACT = 0.98f
        private const val CONFIDENCE_KEYWORD = 0.85f
        private const val CONFIDENCE_PAYMENT_TYPE = 0.95f
        private const val CONFIDENCE_FALLBACK = 0.30f
    }

    /**
     * Categorize a list of recipients using keyword rules.
     * Returns the same result format as the old AI service for drop-in compatibility.
     */
    fun categorize(recipients: List<RecipientInfo>): CategorizationResult {
        if (recipients.isEmpty()) {
            return CategorizationResult()
        }

        val suggestions = mutableMapOf<String, CategorySuggestion>()

        for (recipient in recipients) {
            try {
                val suggestion = categorizeRecipient(recipient)
                if (suggestion != null) {
                    suggestions[recipient.recipientKey] = suggestion
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to categorize '${recipient.displayName}': ${e.message}")
            }
        }

        Log.d(TAG, "Categorized ${suggestions.size}/${recipients.size} recipients via rules engine")
        return CategorizationResult(suggestions = suggestions)
    }

    /**
     * Categorize a single recipient. Returns null if no rule matches.
     */
    private fun categorizeRecipient(recipient: RecipientInfo): CategorySuggestion? {
        val name = recipient.displayName.uppercase().trim()
        val paymentType = recipient.paymentType

        // 1. PaymentType-based rules (highest priority for specific types)
        paymentTypeRule(paymentType)?.let { return it }

        // 2. Exact name matches (known Kenyan businesses/services)
        exactNameMatch(name)?.let { return it }

        // 3. Keyword/substring rules (name contains keyword)
        keywordMatch(name, paymentType)?.let { return it }

        // 4. Fallback for SEND_MONEY → Miscellaneous (likely personal transfer)
        if (paymentType == "SEND_MONEY") {
            return CategorySuggestion(
                categoryId = 1201L,
                categoryName = "Miscellaneous",
                groupName = "Miscellaneous",
                confidence = CONFIDENCE_FALLBACK
            )
        }

        // No match — return null (will remain uncategorized)
        return null
    }

    // ==================== Rule 1: PaymentType-Based ====================

    private fun paymentTypeRule(paymentType: String): CategorySuggestion? {
        return when (paymentType) {
            "AIRTIME" -> CategorySuggestion(
                categoryId = 202L,
                categoryName = "Airtime",
                groupName = "Digital & Tech",
                confidence = CONFIDENCE_PAYMENT_TYPE
            )
            "TRANSACTION_COST" -> CategorySuggestion(
                categoryId = 606L,
                categoryName = "Mpesa Transaction Cost",
                groupName = "Financial",
                confidence = CONFIDENCE_PAYMENT_TYPE
            )
            else -> null
        }
    }

    // ==================== Rule 2: Exact Name Matches ====================

    /**
     * Known Kenyan businesses and services mapped to exact recipient names.
     * Names are UPPERCASED for case-insensitive matching.
     */
    private fun exactNameMatch(name: String): CategorySuggestion? {
        return exactNameMap[name]
    }

    private val exactNameMap: Map<String, CategorySuggestion> = buildMap {
        // ── Food & Dining: Groceries (703) ──
        val groceries = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_EXACT)
        put("NAIVAS SUPERMARKET", groceries)
        put("NAIVAS", groceries)
        put("NAIVAS LIMITED", groceries)
        put("QUICKMART", groceries)
        put("QUICKMART SUPERMARKET", groceries)
        put("QUICKMART LIMITED", groceries)
        put("CARREFOUR", groceries)
        put("CARREFOUR SUPERMARKET", groceries)
        put("MAJID AL FUTTAIM", groceries) // Carrefour parent
        put("CLEANSHELF SUPERMARKET", groceries)
        put("CLEANSHELF", groceries)
        put("CHANDARANA", groceries)
        put("CHANDARANA SUPERMARKET", groceries)
        put("FOODPLUS", groceries)
        put("FOOD PLUS", groceries)
        put("TUSKYS", groceries)
        put("TUSKYS SUPERMARKET", groceries)
        put("KHETIAS", groceries)
        put("KHETIA SUPERMARKET", groceries)
        put("MATHAI SUPERMARKET", groceries)
        put("MULLEYS SUPERMARKET", groceries)
        put("EASTMATT SUPERMARKET", groceries)
        put("EASTMATT", groceries)
        put("GREENMART", groceries)
        put("GREEN MART", groceries)

        // ── Food & Dining: Eating Out / Takeaway (702, 705) ──
        val eatingOut = CategorySuggestion(702L, "Eating Out", "Food & Dining", CONFIDENCE_EXACT)
        val takeaway = CategorySuggestion(705L, "Takeaway/Delivery", "Food & Dining", CONFIDENCE_EXACT)
        put("KFC", eatingOut)
        put("KFC KENYA", eatingOut)
        put("JAVA HOUSE", eatingOut)
        put("JAVA", eatingOut)
        put("ARTCAFFE", eatingOut)
        put("BIG SQUARE", eatingOut)
        put("BURGER KING", eatingOut)
        put("CHICKEN INN", eatingOut)
        put("GALITOS", eatingOut)
        put("MCDONALDS", eatingOut)
        put("PIZZA INN", eatingOut)
        put("PIZZA HUT", eatingOut)
        put("SUBWAY", eatingOut)
        put("DOMINOS", eatingOut)
        put("DOMINOS PIZZA", eatingOut)
        put("SUBWAY KENYA", eatingOut)
        put("SIMBISA BRANDS", eatingOut)  // Owns Chicken Inn, Pizza Inn, etc.
        put("GLOVO", takeaway)
        put("BOLT FOOD", takeaway)
        put("UBER EATS", takeaway)
        put("JUMIA FOOD", takeaway)

        // ── Food & Dining: Snacks/Drinks (704) ──
        val snacks = CategorySuggestion(704L, "Snacks/Drinks", "Food & Dining", CONFIDENCE_EXACT)
        put("STARBUCKS", snacks)
        put("COLD STONE CREAMERY", snacks)

        // ── Home & Utilities: Electricity (1002) ──
        val electricity = CategorySuggestion(1002L, "Electricity", "Home & Utilities", CONFIDENCE_EXACT)
        put("KPLC", electricity)
        put("KPLC PREPAID", electricity)
        put("KPLC POSTPAID", electricity)
        put("KENYA POWER", electricity)
        put("KENYA POWER AND LIGHTING", electricity)
        put("KPLC PRE-PAID", electricity)

        // ── Home & Utilities: Water Bill (1012) ──
        val water = CategorySuggestion(1012L, "Water Bill", "Home & Utilities", CONFIDENCE_EXACT)
        put("NAIROBI WATER", water)
        put("NAIROBI CITY WATER", water)
        put("NCWSC", water)
        put("ELDOWAS", water)

        // ── Home & Utilities: Gas (1004) ──
        val gas = CategorySuggestion(1004L, "Gas", "Home & Utilities", CONFIDENCE_EXACT)
        put("K-GAS", gas)
        put("TOTAL GAS", gas)
        put("PRO GAS", gas)

        // ── Home & Utilities: Rent (1009) ──
        val rent = CategorySuggestion(1009L, "Rent", "Home & Utilities", CONFIDENCE_EXACT)
        put("RENT", rent)
        put("HOUSE RENT", rent)

        // ── Home & Utilities: Home WiFi (1007) ──
        val wifi = CategorySuggestion(1007L, "Home WiFi", "Home & Utilities", CONFIDENCE_EXACT)
        put("ZUKU", wifi)
        put("SAFARICOM HOME", wifi)
        put("FAIBA", wifi)
        put("JAMII TELECOM", wifi)
        put("LIQUID HOME", wifi)
        put("TELKOM KENYA", wifi)

        // ── Digital & Tech: Streaming (210) ──
        val streaming = CategorySuggestion(210L, "Streaming", "Digital & Tech", CONFIDENCE_EXACT)
        put("NETFLIX", streaming)
        put("SPOTIFY", streaming)
        put("SHOWMAX", streaming)
        put("DSTV", streaming)
        put("GOTV", streaming)
        put("MULTICHOICE", streaming) // DStv / GoTV parent
        put("YOUTUBE PREMIUM", streaming)
        put("STARTIMES", streaming)

        // ── Digital & Tech: Data Bundles (205) ──
        val dataBundles = CategorySuggestion(205L, "Data Bundles", "Digital & Tech", CONFIDENCE_EXACT)
        put("SAFARICOM", dataBundles)
        put("AIRTEL", dataBundles)
        put("TELKOM", dataBundles)

        // ── Transport: Uber/Bolt (1608) ──
        val ridehail = CategorySuggestion(1608L, "Uber/Bolt", "Transport & Travel", CONFIDENCE_EXACT)
        put("UBER", ridehail)
        put("UBER BV", ridehail)
        put("BOLT", ridehail)
        put("BOLT TECHNOLOGY", ridehail)
        put("LITTLE RIDE", ridehail)
        put("LITTLE CAB", ridehail)

        // ── Transport: Boda Boda (1602) ──
        val boda = CategorySuggestion(1602L, "Boda Boda", "Transport & Travel", CONFIDENCE_EXACT)
        put("BODA BODA", boda)
        put("BODA", boda)

        // ── Transport: SGR Train (1607) ──
        val sgr = CategorySuggestion(1607L, "SGR Train", "Transport & Travel", CONFIDENCE_EXACT)
        put("SGR", sgr)
        put("MADARAKA EXPRESS", sgr)
        put("KENYA RAILWAYS", sgr)

        // ── Transport: Flight (1605) ──
        val flight = CategorySuggestion(1605L, "Flight", "Transport & Travel", CONFIDENCE_EXACT)
        put("KENYA AIRWAYS", flight)
        put("KQ", flight)
        put("JAMBOJET", flight)
        put("FLY540", flight)
        put("SAFARILINK", flight)

        // ── Vehicle: Fuel (1712) ──
        val fuel = CategorySuggestion(1712L, "Fuel", "Vehicle", CONFIDENCE_EXACT)
        put("SHELL", fuel)
        put("TOTAL ENERGIES", fuel)
        put("TOTAL", fuel)
        put("RUBIS", fuel)
        put("RUBIS ENERGY", fuel)
        put("OIL LIBYA", fuel)
        put("ORYX ENERGIES", fuel)
        put("GULF ENERGY", fuel)
        put("ENGEN", fuel)
        put("HASHI ENERGY", fuel)
        put("GALANA OIL", fuel)

        // ── Vehicle: Expressway (1711) ──
        val expressway = CategorySuggestion(1711L, "Expressway", "Vehicle", CONFIDENCE_EXACT)
        put("NAIROBI EXPRESSWAY", expressway)
        put("MOJA EXPRESSWAY", expressway)

        // ── Vehicle: Parking (1713) ──
        val parking = CategorySuggestion(1713L, "Parking", "Vehicle", CONFIDENCE_EXACT)
        put("NAIROBI COUNTY PARKING", parking)
        put("COUNTY PARKING", parking)
        put("JAMBOPAY PARKING", parking)

        // ── Government: NTSA (808) ──
        val ntsa = CategorySuggestion(808L, "NTSA", "Government & Legal", CONFIDENCE_EXACT)
        put("NTSA", ntsa)
        put("TIMS", ntsa)

        // ── Government: KRA (805) ──
        val kra = CategorySuggestion(805L, "Income Tax/KRA Filing", "Government & Legal", CONFIDENCE_EXACT)
        put("KRA", kra)
        put("KENYA REVENUE AUTHORITY", kra)
        put("ITAX", kra)

        // ── Government: SHA (810) ──
        val sha = CategorySuggestion(810L, "SHA", "Government & Legal", CONFIDENCE_EXACT)
        put("SHA", sha)
        put("NHIF", sha) // Legacy name
        put("SOCIAL HEALTH AUTHORITY", sha)

        // ── Investment & Savings: NSSF (1806) ──
        val nssf = CategorySuggestion(1806L, "NSSF", "Investment & Savings", CONFIDENCE_EXACT)
        put("NSSF", nssf)

        // ── Investment & Savings: SACCO (1809) ──
        val sacco = CategorySuggestion(1809L, "SACCO", "Investment & Savings", CONFIDENCE_EXACT)
        put("STIMA SACCO", sacco)
        put("MWALIMU NATIONAL SACCO", sacco)
        put("MWALIMU SACCO", sacco)
        put("KENYA RE SACCO", sacco)
        put("SAFARICOM SACCO", sacco)
        put("HARAMBEE SACCO", sacco)

        // ── Investment & Savings: Money Market Fund (1805) ──
        val mmf = CategorySuggestion(1805L, "Money Market Fund", "Investment & Savings", CONFIDENCE_EXACT)
        put("CIC ASSET MANAGEMENT", mmf)
        put("CIC MONEY MARKET", mmf)
        put("SANLAM INVESTMENTS", mmf)
        put("SANLAM", mmf)
        put("CYTONN INVESTMENTS", mmf)
        put("CYTONN", mmf)
        put("ZIMELE ASSET MANAGEMENT", mmf)
        put("OLD MUTUAL MONEY MARKET", mmf)

        // ── Investment & Savings: Unit Trusts (1813) ──
        val unitTrusts = CategorySuggestion(1813L, "Unit Trusts/Mutual Funds", "Investment & Savings", CONFIDENCE_EXACT)
        put("BRITAM ASSET MANAGERS", unitTrusts)
        put("BRITAM", unitTrusts)
        put("ICEA LION", unitTrusts)
        put("NABO CAPITAL", unitTrusts)

        // ── Investment & Savings: Stocks/Shares (1811) ──
        val stocks = CategorySuggestion(1811L, "Stocks/Shares", "Investment & Savings", CONFIDENCE_EXACT)
        put("GENGHIS CAPITAL", stocks)
        put("AIB-AXYS AFRICA", stocks)
        put("SBG SECURITIES", stocks)

        // ── Investment & Savings: Treasury Bill/Bond (1812) ──
        val tbill = CategorySuggestion(1812L, "Treasury Bill/Bond", "Investment & Savings", CONFIDENCE_EXACT)
        put("M-AKIBA", tbill)
        put("CBK", tbill)
        put("CENTRAL BANK OF KENYA", tbill)

        // ── Investment & Savings: Pension/Retirement (1807) ──
        val pension = CategorySuggestion(1807L, "Pension/Retirement", "Investment & Savings", CONFIDENCE_EXACT)
        put("OLD MUTUAL", pension)
        put("BRITAM PENSION", pension)
        put("JUBILEE INSURANCE PENSION", pension)

        // ── Investment & Savings: Crypto (1802) ──
        val crypto = CategorySuggestion(1802L, "Crypto", "Investment & Savings", CONFIDENCE_EXACT)
        put("BINANCE", crypto)
        put("YELLOW CARD", crypto)
        put("PAXFUL", crypto)

        // ── Health: Pharmacy (906) ──
        val pharmacy = CategorySuggestion(906L, "Pharmacy", "Health", CONFIDENCE_EXACT)
        put("GOODLIFE PHARMACY", pharmacy)
        put("HALTONS PHARMACY", pharmacy)

        // ── Health: Gym/Fitness (902) ──
        val gym = CategorySuggestion(902L, "Gym/Fitness", "Health", CONFIDENCE_EXACT)
        put("SMART GYMS", gym)

        // ── Shopping: General Shopping (1505) ──
        val shopping = CategorySuggestion(1505L, "General Shopping", "Shopping", CONFIDENCE_EXACT)
        put("JUMIA", shopping)
        put("JUMIA KENYA", shopping)
        put("KILIMALL", shopping)
        put("MASOKO", shopping)

        // ── Shopping: Electronics (1504) ──
        val electronics = CategorySuggestion(1504L, "Electronics", "Shopping", CONFIDENCE_EXACT)
        put("SAMSUNG", electronics)
        put("HOTPOINT", electronics)

        // ── Shopping: Clothing (1503) ──
        val clothing = CategorySuggestion(1503L, "Clothing", "Shopping", CONFIDENCE_EXACT)
        put("MR PRICE", clothing)
        put("LC WAIKIKI", clothing)
        put("WOOLWORTHS", clothing)

        // ── Financial: Bank Charges (601) ──
        val bankCharges = CategorySuggestion(601L, "Bank Charges", "Financial", CONFIDENCE_EXACT)
        put("CBA COMMISSION", bankCharges)
        put("NCBA", bankCharges)
        put("NCBA BANK", bankCharges)
    }

    // ==================== Rule 3: Keyword/Substring Matches ====================

    /**
     * List of keyword rules. Checked in order — first match wins.
     * Each rule: (keywords to check, categoryId, categoryName, groupName, confidence)
     */
    private fun keywordMatch(name: String, paymentType: String): CategorySuggestion? {
        for (rule in keywordRules) {
            if (rule.matches(name, paymentType)) {
                return rule.suggestion
            }
        }
        return null
    }

    private data class KeywordRule(
        /** Keywords — ALL must be present in the name (AND logic) */
        val keywords: List<String>,
        /** Optional: only match if paymentType matches */
        val paymentTypes: Set<String>? = null,
        /** The suggestion to return on match */
        val suggestion: CategorySuggestion
    ) {
        fun matches(name: String, paymentType: String): Boolean {
            // Check payment type constraint
            if (paymentTypes != null && paymentType !in paymentTypes) return false
            // Check all keywords present
            return keywords.all { keyword -> name.contains(keyword) }
        }
    }

    private val keywordRules: List<KeywordRule> = listOf(
        // ── Food & Dining ──
        KeywordRule(listOf("SUPERMARKET"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MINI MART"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MINIMART"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("GROCERY"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("GROCERIES"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BUTCHER"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BUTCHERY"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MEAT"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BAKERY"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("GREENGROCER"), suggestion = CategorySuggestion(703L, "Groceries", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("RESTAURANT"), suggestion = CategorySuggestion(702L, "Eating Out", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CAFE"), suggestion = CategorySuggestion(702L, "Eating Out", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("COFFEE"), suggestion = CategorySuggestion(704L, "Snacks/Drinks", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("HOTEL"), suggestion = CategorySuggestion(702L, "Eating Out", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("EATERY"), suggestion = CategorySuggestion(702L, "Eating Out", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FOOD"), paymentTypes = setOf("BUY_GOODS"), suggestion = CategorySuggestion(702L, "Eating Out", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("PIZZA"), suggestion = CategorySuggestion(705L, "Takeaway/Delivery", "Food & Dining", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CHICKEN"), suggestion = CategorySuggestion(702L, "Eating Out", "Food & Dining", CONFIDENCE_KEYWORD)),

        // ── Home & Utilities ──
        KeywordRule(listOf("KPLC"), suggestion = CategorySuggestion(1002L, "Electricity", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("KENYA POWER"), suggestion = CategorySuggestion(1002L, "Electricity", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("ELECTRIC"), suggestion = CategorySuggestion(1002L, "Electricity", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("WATER"), paymentTypes = setOf("PAY_BILL"), suggestion = CategorySuggestion(1012L, "Water Bill", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("GAS"), paymentTypes = setOf("BUY_GOODS"), suggestion = CategorySuggestion(1004L, "Gas", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CLEANING"), suggestion = CategorySuggestion(1001L, "Cleaning", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("RENT"), paymentTypes = setOf("PAY_BILL", "SEND_MONEY"), suggestion = CategorySuggestion(1009L, "Rent", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("WIFI"), suggestion = CategorySuggestion(1007L, "Home WiFi", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("INTERNET"), paymentTypes = setOf("PAY_BILL"), suggestion = CategorySuggestion(1007L, "Home WiFi", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FIBRE"), suggestion = CategorySuggestion(1007L, "Home WiFi", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FURNITURE"), suggestion = CategorySuggestion(1006L, "Home Furnishing", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FURNISHING"), suggestion = CategorySuggestion(1006L, "Home Furnishing", "Home & Utilities", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("SECURITY"), paymentTypes = setOf("PAY_BILL"), suggestion = CategorySuggestion(1011L, "Security", "Home & Utilities", CONFIDENCE_KEYWORD)),

        // ── Vehicle ──
        KeywordRule(listOf("FUEL"), suggestion = CategorySuggestion(1712L, "Fuel", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("PETROL"), suggestion = CategorySuggestion(1712L, "Fuel", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("DIESEL"), suggestion = CategorySuggestion(1712L, "Fuel", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("PETROLEUM"), suggestion = CategorySuggestion(1712L, "Fuel", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FILLING STATION"), suggestion = CategorySuggestion(1712L, "Fuel", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("ENERGY", "OIL"), suggestion = CategorySuggestion(1712L, "Fuel", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CAR WASH"), suggestion = CategorySuggestion(1710L, "Car Wash", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CARWASH"), suggestion = CategorySuggestion(1710L, "Car Wash", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("GARAGE"), suggestion = CategorySuggestion(1707L, "Car Repairs", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("AUTO REPAIR"), suggestion = CategorySuggestion(1707L, "Car Repairs", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MECHANIC"), suggestion = CategorySuggestion(1707L, "Car Repairs", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TYRE"), suggestion = CategorySuggestion(1709L, "Car Tyres", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TIRE"), suggestion = CategorySuggestion(1709L, "Car Tyres", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("PARKING"), suggestion = CategorySuggestion(1713L, "Parking", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("EXPRESSWAY"), suggestion = CategorySuggestion(1711L, "Expressway", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TOLL"), suggestion = CategorySuggestion(1711L, "Expressway", "Vehicle", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("INSURANCE"), suggestion = CategorySuggestion(1704L, "Car Insurance", "Vehicle", CONFIDENCE_KEYWORD)),

        // ── Transport & Travel ──
        KeywordRule(listOf("UBER"), suggestion = CategorySuggestion(1608L, "Uber/Bolt", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BOLT"), suggestion = CategorySuggestion(1608L, "Uber/Bolt", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TAXI"), suggestion = CategorySuggestion(1608L, "Uber/Bolt", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CAB"), suggestion = CategorySuggestion(1608L, "Uber/Bolt", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BODA"), suggestion = CategorySuggestion(1602L, "Boda Boda", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("AIRLINE"), suggestion = CategorySuggestion(1605L, "Flight", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("AIRWAYS"), suggestion = CategorySuggestion(1605L, "Flight", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FLIGHT"), suggestion = CategorySuggestion(1605L, "Flight", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("SGR"), suggestion = CategorySuggestion(1607L, "SGR Train", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TRAIN"), suggestion = CategorySuggestion(1607L, "SGR Train", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MATATU"), suggestion = CategorySuggestion(1604L, "Fare", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BUS"), suggestion = CategorySuggestion(1604L, "Fare", "Transport & Travel", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("SHUTTLE"), suggestion = CategorySuggestion(1604L, "Fare", "Transport & Travel", CONFIDENCE_KEYWORD)),

        // ── Digital & Tech ──
        KeywordRule(listOf("NETFLIX"), suggestion = CategorySuggestion(210L, "Streaming", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("SPOTIFY"), suggestion = CategorySuggestion(210L, "Streaming", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("SHOWMAX"), suggestion = CategorySuggestion(210L, "Streaming", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("DSTV"), suggestion = CategorySuggestion(210L, "Streaming", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("GOTV"), suggestion = CategorySuggestion(210L, "Streaming", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("STARTIMES"), suggestion = CategorySuggestion(210L, "Streaming", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("DOMAIN"), suggestion = CategorySuggestion(207L, "Domain", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("HOSTING"), suggestion = CategorySuggestion(208L, "Hosting", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("VPN"), suggestion = CategorySuggestion(211L, "VPN", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("OPENAI"), suggestion = CategorySuggestion(201L, "AI Subscriptions", "Digital & Tech", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CHATGPT"), suggestion = CategorySuggestion(201L, "AI Subscriptions", "Digital & Tech", CONFIDENCE_KEYWORD)),

        // ── Health ──
        KeywordRule(listOf("PHARMACY"), suggestion = CategorySuggestion(906L, "Pharmacy", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CHEMIST"), suggestion = CategorySuggestion(906L, "Pharmacy", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("HOSPITAL"), suggestion = CategorySuggestion(904L, "Medical Checkup", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CLINIC"), suggestion = CategorySuggestion(904L, "Medical Checkup", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MEDICAL"), suggestion = CategorySuggestion(904L, "Medical Checkup", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("DOCTOR"), suggestion = CategorySuggestion(904L, "Medical Checkup", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("DENTAL"), suggestion = CategorySuggestion(901L, "Dental", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("DENTIST"), suggestion = CategorySuggestion(901L, "Dental", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("OPTICAL"), suggestion = CategorySuggestion(905L, "Optical", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("GYM"), suggestion = CategorySuggestion(902L, "Gym/Fitness", "Health", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FITNESS"), suggestion = CategorySuggestion(902L, "Gym/Fitness", "Health", CONFIDENCE_KEYWORD)),

        // ── Education ──
        KeywordRule(listOf("SCHOOL"), suggestion = CategorySuggestion(304L, "School Fees", "Education", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("UNIVERSITY"), suggestion = CategorySuggestion(304L, "School Fees", "Education", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("COLLEGE"), suggestion = CategorySuggestion(304L, "School Fees", "Education", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("ACADEMY"), suggestion = CategorySuggestion(304L, "School Fees", "Education", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TUITION"), suggestion = CategorySuggestion(304L, "School Fees", "Education", CONFIDENCE_KEYWORD)),

        // ── Faith & Giving ──
        KeywordRule(listOf("CHURCH"), suggestion = CategorySuggestion(501L, "Church Program", "Faith & Giving", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TITHE"), suggestion = CategorySuggestion(506L, "Tithe", "Faith & Giving", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("OFFERING"), suggestion = CategorySuggestion(504L, "Offering", "Faith & Giving", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MINISTRIES"), suggestion = CategorySuggestion(501L, "Church Program", "Faith & Giving", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MOSQUE"), suggestion = CategorySuggestion(501L, "Church Program", "Faith & Giving", CONFIDENCE_KEYWORD)),

        // ── Financial (expense-only) ──
        KeywordRule(listOf("LOAN"), paymentTypes = setOf("PAY_BILL"), suggestion = CategorySuggestion(604L, "Loan Repayment", "Financial", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TALA"), suggestion = CategorySuggestion(604L, "Loan Repayment", "Financial", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FULIZA"), suggestion = CategorySuggestion(604L, "Loan Repayment", "Financial", CONFIDENCE_KEYWORD)),

        // ── Investment & Savings ──
        KeywordRule(listOf("SACCO"), suggestion = CategorySuggestion(1809L, "SACCO", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MSHWARI"), suggestion = CategorySuggestion(1810L, "Savings", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("KCB MPESA"), suggestion = CategorySuggestion(1810L, "Savings", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MALI"), suggestion = CategorySuggestion(1811L, "Stocks/Shares", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("MONEY MARKET"), suggestion = CategorySuggestion(1805L, "Money Market Fund", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("INVEST"), suggestion = CategorySuggestion(1813L, "Unit Trusts/Mutual Funds", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CHAMA"), suggestion = CategorySuggestion(1801L, "Chama Contributions", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CRYPTO"), suggestion = CategorySuggestion(1802L, "Crypto", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BITCOIN"), suggestion = CategorySuggestion(1802L, "Crypto", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("TREASURY"), suggestion = CategorySuggestion(1812L, "Treasury Bill/Bond", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("PENSION"), suggestion = CategorySuggestion(1807L, "Pension/Retirement", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("RETIREMENT"), suggestion = CategorySuggestion(1807L, "Pension/Retirement", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("UNIT TRUST"), suggestion = CategorySuggestion(1813L, "Unit Trusts/Mutual Funds", "Investment & Savings", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FIXED DEPOSIT"), suggestion = CategorySuggestion(1803L, "Fixed Deposit", "Investment & Savings", CONFIDENCE_KEYWORD)),

        // ── Government ──
        KeywordRule(listOf("KRA"), suggestion = CategorySuggestion(805L, "Income Tax/KRA Filing", "Government & Legal", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("NTSA"), suggestion = CategorySuggestion(808L, "NTSA", "Government & Legal", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("ECITIZEN"), suggestion = CategorySuggestion(808L, "NTSA", "Government & Legal", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("NHIF"), suggestion = CategorySuggestion(810L, "SHA", "Government & Legal", CONFIDENCE_KEYWORD)),

        // ── Personal Care ──
        KeywordRule(listOf("BARBER"), suggestion = CategorySuggestion(1301L, "Haircut", "Personal Care", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("SALON"), suggestion = CategorySuggestion(1303L, "Salon", "Personal Care", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("KINYOZI"), suggestion = CategorySuggestion(1301L, "Haircut", "Personal Care", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("LAUNDRY"), suggestion = CategorySuggestion(1302L, "Laundry/Dry Cleaning", "Personal Care", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("DRY CLEAN"), suggestion = CategorySuggestion(1302L, "Laundry/Dry Cleaning", "Personal Care", CONFIDENCE_KEYWORD)),

        // ── Pets ──
        KeywordRule(listOf("VET"), suggestion = CategorySuggestion(1404L, "Vet", "Pets", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("VETERINARY"), suggestion = CategorySuggestion(1404L, "Vet", "Pets", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("PET"), suggestion = CategorySuggestion(1401L, "Pet Food", "Pets", CONFIDENCE_KEYWORD)),

        // ── Shopping ──
        KeywordRule(listOf("CLOTHES"), suggestion = CategorySuggestion(1503L, "Clothing", "Shopping", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("CLOTHING"), suggestion = CategorySuggestion(1503L, "Clothing", "Shopping", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("FASHION"), suggestion = CategorySuggestion(1503L, "Clothing", "Shopping", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BOUTIQUE"), suggestion = CategorySuggestion(1503L, "Clothing", "Shopping", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("BOOK"), suggestion = CategorySuggestion(1502L, "Books", "Shopping", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("ELECTRONICS"), suggestion = CategorySuggestion(1504L, "Electronics", "Shopping", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("PHONE"), paymentTypes = setOf("BUY_GOODS"), suggestion = CategorySuggestion(1508L, "Phone / Accessories", "Shopping", CONFIDENCE_KEYWORD)),

        // ── Beekeeping ──
        KeywordRule(listOf("BEE"), suggestion = CategorySuggestion(108L, "Bees", "Beekeeping", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("HIVE"), suggestion = CategorySuggestion(104L, "Bee Hives", "Beekeeping", CONFIDENCE_KEYWORD)),
        KeywordRule(listOf("HONEY"), suggestion = CategorySuggestion(109L, "Honey Harvesting", "Beekeeping", CONFIDENCE_KEYWORD)),

        // ── Withdrawal (from agent) mapped to Financial: Bank Charges ──
        KeywordRule(listOf("AGENT"), paymentTypes = setOf("WITHDRAW"), suggestion = CategorySuggestion(601L, "Bank Charges", "Financial", CONFIDENCE_KEYWORD)),
    )
}
