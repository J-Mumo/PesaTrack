package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Category entity for expense categorization
 * Supports hierarchical parent-child relationships
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["name"])
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Category name */
    val name: String,
    
    /** Material icon name */
    val icon: String,
    
    /** Hex color code */
    val color: String,
    
    /** Parent category ID (null for top-level groups) */
    val parentId: Long? = null,
    
    /** Whether this is a group (parent) category */
    val isGroup: Boolean = false,
    
    /** Whether this is a default category */
    val isDefault: Boolean = false,
    
    /** Display order within parent */
    val sortOrder: Int = 0
)

/**
 * Default categories organized in alphabetical groups.
 *
 * ID scheme: GroupID * 100 + sortOrder
 * e.g. Group 1 (Beekeeping) → 101, 102, ...
 *      Group 7 (Food & Dining) → 701, 702, ...
 *
 * Version history:
 * - v1: Initial 17 groups, 89 subcategories (included Beekeeping)
 * - v2 (migration 5→6): Alphabetical groups, merges, renames, additions
 *
 * Note: Beekeeping was removed from defaults in v11. It remains for existing users
 *       as a custom (non-default) category that can be edited/deleted.
 */
object DefaultCategories {
    
    // ==================== Category Groups (Alphabetical) ====================

    private val groups = listOf(
        CategoryEntity(id = 2, name = "Digital & Tech", icon = "devices", color = "#607D8B", isGroup = true, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 3, name = "Education", icon = "school", color = "#3F51B5", isGroup = true, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 4, name = "Entertainment", icon = "movie", color = "#E91E63", isGroup = true, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 5, name = "Faith & Giving", icon = "volunteer_activism", color = "#673AB7", isGroup = true, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 6, name = "Financial", icon = "account_balance", color = "#795548", isGroup = true, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 7, name = "Food & Dining", icon = "restaurant", color = "#FF5722", isGroup = true, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 8, name = "Government & Legal", icon = "gavel", color = "#455A64", isGroup = true, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 9, name = "Health", icon = "local_hospital", color = "#F44336", isGroup = true, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 10, name = "Home & Utilities", icon = "home", color = "#4CAF50", isGroup = true, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 18, name = "Investment & Savings", icon = "trending_up", color = "#006064", isGroup = true, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 11, name = "Life Events", icon = "cake", color = "#FF9800", isGroup = true, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 12, name = "Miscellaneous", icon = "more_horiz", color = "#9E9E9E", isGroup = true, isDefault = true, sortOrder = 12),
        CategoryEntity(id = 13, name = "Personal Care", icon = "face", color = "#00BCD4", isGroup = true, isDefault = true, sortOrder = 13),
        CategoryEntity(id = 14, name = "Pets", icon = "pets", color = "#8BC34A", isGroup = true, isDefault = true, sortOrder = 14),
        CategoryEntity(id = 15, name = "Shopping", icon = "shopping_bag", color = "#E91E63", isGroup = true, isDefault = true, sortOrder = 15),
        CategoryEntity(id = 16, name = "Transport & Travel", icon = "commute", color = "#9C27B0", isGroup = true, isDefault = true, sortOrder = 16),
        CategoryEntity(id = 17, name = "Vehicle", icon = "directions_car", color = "#2196F3", isGroup = true, isDefault = true, sortOrder = 17),
    )

    // ==================== Group 2: Digital & Tech ====================

    private val digitalCategories = listOf(
        CategoryEntity(id = 201, name = "AI Subscriptions", icon = "smart_toy", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 202, name = "Airtime", icon = "sim_card", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 203, name = "App Subscriptions", icon = "apps", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 204, name = "Cloud Storage", icon = "cloud_upload", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 205, name = "Data Bundles", icon = "signal_cellular_alt", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 206, name = "Device Repairs", icon = "laptop", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 207, name = "Domain", icon = "language", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 208, name = "Hosting", icon = "cloud", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 209, name = "Software License", icon = "verified", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 210, name = "Streaming", icon = "subscriptions", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 211, name = "VPN", icon = "vpn_key", color = "#607D8B", parentId = 2, isDefault = true, sortOrder = 11),
    )

    // ==================== Group 3: Education ====================

    private val educationCategories = listOf(
        CategoryEntity(id = 301, name = "Certifications", icon = "workspace_premium", color = "#3F51B5", parentId = 3, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 302, name = "Conferences", icon = "groups", color = "#3F51B5", parentId = 3, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 303, name = "Courses/Training", icon = "school", color = "#3F51B5", parentId = 3, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 304, name = "School Fees", icon = "account_balance", color = "#3F51B5", parentId = 3, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 305, name = "Stationery", icon = "edit", color = "#3F51B5", parentId = 3, isDefault = true, sortOrder = 5),
    )

    // ==================== Group 4: Entertainment ====================

    private val entertainmentCategories = listOf(
        CategoryEntity(id = 401, name = "Events", icon = "event", color = "#E91E63", parentId = 4, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 402, name = "Games", icon = "sports_esports", color = "#E91E63", parentId = 4, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 403, name = "Hobbies", icon = "palette", color = "#E91E63", parentId = 4, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 404, name = "Movies", icon = "theaters", color = "#E91E63", parentId = 4, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 405, name = "Other Entertainment", icon = "movie", color = "#E91E63", parentId = 4, isDefault = true, sortOrder = 5),
    )

    // ==================== Group 5: Faith & Giving ====================

    private val faithCategories = listOf(
        CategoryEntity(id = 501, name = "Church Program", icon = "auto_awesome", color = "#673AB7", parentId = 5, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 502, name = "Community Program", icon = "diversity_3", color = "#673AB7", parentId = 5, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 503, name = "Give", icon = "volunteer_activism", color = "#673AB7", parentId = 5, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 504, name = "Offering", icon = "favorite", color = "#673AB7", parentId = 5, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 505, name = "Seed", icon = "grass", color = "#673AB7", parentId = 5, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 506, name = "Tithe", icon = "church", color = "#673AB7", parentId = 5, isDefault = true, sortOrder = 6),
    )

    // ==================== Group 6: Financial (expense-only; investments moved to Group 18) ====================

    private val financialCategories = listOf(
        CategoryEntity(id = 601, name = "Bank Charges", icon = "account_balance_wallet", color = "#795548", parentId = 6, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 603, name = "Loan Interest", icon = "percent", color = "#795548", parentId = 6, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 604, name = "Loan Repayment", icon = "account_balance", color = "#795548", parentId = 6, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 606, name = "Mpesa Transaction Cost", icon = "phone_android", color = "#795548", parentId = 6, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 608, name = "Pesalink Charges", icon = "swap_horiz", color = "#795548", parentId = 6, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 609, name = "RTGS Charges", icon = "sync_alt", color = "#795548", parentId = 6, isDefault = true, sortOrder = 6),
    )

    // ==================== Group 7: Food & Dining ====================

    private val foodCategories = listOf(
        CategoryEntity(id = 701, name = "Drinking Water", icon = "water", color = "#FF5722", parentId = 7, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 702, name = "Eating Out", icon = "restaurant", color = "#FF5722", parentId = 7, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 703, name = "Groceries", icon = "shopping_cart", color = "#FF5722", parentId = 7, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 704, name = "Snacks/Drinks", icon = "local_cafe", color = "#FF5722", parentId = 7, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 705, name = "Takeaway/Delivery", icon = "delivery_dining", color = "#FF5722", parentId = 7, isDefault = true, sortOrder = 5),
    )

    // ==================== Group 8: Government & Legal ====================

    private val governmentCategories = listOf(
        CategoryEntity(id = 801, name = "County Rates", icon = "apartment", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 802, name = "Driving License Renewal", icon = "badge", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 803, name = "Excise Duty", icon = "request_quote", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 804, name = "Good Conduct Certificate", icon = "verified_user", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 805, name = "Income Tax/KRA Filing", icon = "description", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 806, name = "KRA Penalties", icon = "warning", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 807, name = "Land Rent", icon = "terrain", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 808, name = "NTSA", icon = "directions_car", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 809, name = "Passport Fees", icon = "flight", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 810, name = "SHA", icon = "health_and_safety", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 811, name = "Tax Consultancy", icon = "support_agent", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 812, name = "Visa Fees", icon = "approval", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 12),
        CategoryEntity(id = 813, name = "Withholding Tax", icon = "money_off", color = "#455A64", parentId = 8, isDefault = true, sortOrder = 13),
    )

    // ==================== Group 9: Health ====================

    private val healthCategories = listOf(
        CategoryEntity(id = 901, name = "Dental", icon = "dentistry", color = "#F44336", parentId = 9, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 902, name = "Gym/Fitness", icon = "fitness_center", color = "#F44336", parentId = 9, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 903, name = "Health Insurance", icon = "health_and_safety", color = "#F44336", parentId = 9, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 904, name = "Medical Checkup", icon = "medical_services", color = "#F44336", parentId = 9, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 905, name = "Optical", icon = "visibility", color = "#F44336", parentId = 9, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 906, name = "Pharmacy", icon = "medication", color = "#F44336", parentId = 9, isDefault = true, sortOrder = 6),
    )

    // ==================== Group 10: Home & Utilities ====================

    private val homeCategories = listOf(
        CategoryEntity(id = 1001, name = "Cleaning", icon = "cleaning_services", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1002, name = "Electricity", icon = "bolt", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1003, name = "Gardening", icon = "yard", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1004, name = "Gas", icon = "propane_tank", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1005, name = "Home Appliances", icon = "microwave", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1006, name = "Home Furnishing", icon = "chair", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1007, name = "Home WiFi", icon = "wifi", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1008, name = "Pest Control", icon = "pest_control", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 1009, name = "Rent", icon = "house", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 1010, name = "Repairs", icon = "handyman", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 1011, name = "Security", icon = "shield", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 1012, name = "Water Bill", icon = "water_drop", color = "#4CAF50", parentId = 10, isDefault = true, sortOrder = 12),
    )

    // ==================== Group 18: Investment & Savings ====================

    private val investmentCategories = listOf(
        CategoryEntity(id = 1801, name = "Chama Contributions", icon = "groups", color = "#006064", parentId = 18, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1802, name = "Crypto", icon = "currency_bitcoin", color = "#006064", parentId = 18, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1803, name = "Fixed Deposit", icon = "lock", color = "#006064", parentId = 18, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1804, name = "Insurance (Investment)", icon = "shield", color = "#006064", parentId = 18, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1805, name = "Money Market Fund", icon = "show_chart", color = "#006064", parentId = 18, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1806, name = "NSSF", icon = "elderly", color = "#006064", parentId = 18, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1807, name = "Pension/Retirement", icon = "account_balance", color = "#006064", parentId = 18, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1808, name = "Real Estate", icon = "apartment", color = "#006064", parentId = 18, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 1809, name = "SACCO", icon = "handshake", color = "#006064", parentId = 18, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 1810, name = "Savings", icon = "savings", color = "#006064", parentId = 18, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 1811, name = "Stocks/Shares", icon = "candlestick_chart", color = "#006064", parentId = 18, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 1812, name = "Treasury Bill/Bond", icon = "receipt_long", color = "#006064", parentId = 18, isDefault = true, sortOrder = 12),
        CategoryEntity(id = 1813, name = "Unit Trusts/Mutual Funds", icon = "pie_chart", color = "#006064", parentId = 18, isDefault = true, sortOrder = 13),
    )

    // ==================== Group 11: Life Events ====================

    private val lifeEventsCategories = listOf(
        CategoryEntity(id = 1101, name = "Baby Shower", icon = "child_friendly", color = "#FF9800", parentId = 11, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1102, name = "Birthday Gift", icon = "cake", color = "#FF9800", parentId = 11, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1103, name = "Funeral Contribution", icon = "sentiment_very_dissatisfied", color = "#FF9800", parentId = 11, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1104, name = "Graduation Gift", icon = "school", color = "#FF9800", parentId = 11, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1105, name = "Harambee/Fundraiser", icon = "handshake", color = "#FF9800", parentId = 11, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1106, name = "Holiday Gifts", icon = "card_giftcard", color = "#FF9800", parentId = 11, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1107, name = "House Warming", icon = "home", color = "#FF9800", parentId = 11, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1108, name = "Wedding Contribution", icon = "favorite", color = "#FF9800", parentId = 11, isDefault = true, sortOrder = 8),
    )

    // ==================== Group 12: Miscellaneous ====================

    private val miscCategories = listOf(
        CategoryEntity(id = 1201, name = "Miscellaneous", icon = "more_horiz", color = "#9E9E9E", parentId = 12, isDefault = true, sortOrder = 1),
    )

    // ==================== Group 13: Personal Care ====================

    private val personalCareCategories = listOf(
        CategoryEntity(id = 1301, name = "Haircut", icon = "content_cut", color = "#00BCD4", parentId = 13, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1302, name = "Laundry/Dry Cleaning", icon = "local_laundry_service", color = "#00BCD4", parentId = 13, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1303, name = "Salon", icon = "spa", color = "#00BCD4", parentId = 13, isDefault = true, sortOrder = 3),
    )

    // ==================== Group 14: Pets ====================

    private val petCategories = listOf(
        CategoryEntity(id = 1401, name = "Pet Food", icon = "restaurant", color = "#8BC34A", parentId = 14, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1402, name = "Pet Grooming", icon = "content_cut", color = "#8BC34A", parentId = 14, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1403, name = "Pet Supplies", icon = "category", color = "#8BC34A", parentId = 14, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1404, name = "Vet", icon = "pets", color = "#8BC34A", parentId = 14, isDefault = true, sortOrder = 4),
    )

    // ==================== Group 15: Shopping ====================

    private val shoppingCategories = listOf(
        CategoryEntity(id = 1501, name = "Art", icon = "palette", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1502, name = "Books", icon = "menu_book", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1503, name = "Clothing", icon = "checkroom", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1504, name = "Electronics", icon = "devices_other", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1505, name = "General Shopping", icon = "shopping_bag", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1506, name = "Gifts", icon = "card_giftcard", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1507, name = "Household Items", icon = "house", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1508, name = "Phone / Accessories", icon = "smartphone", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 1509, name = "Shipping", icon = "local_shipping", color = "#E91E63", parentId = 15, isDefault = true, sortOrder = 9),
    )

    // ==================== Group 16: Transport & Travel ====================

    private val transportCategories = listOf(
        CategoryEntity(id = 1601, name = "Accommodation", icon = "hotel", color = "#9C27B0", parentId = 16, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1602, name = "Boda Boda", icon = "two_wheeler", color = "#9C27B0", parentId = 16, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1603, name = "Delivery Charges", icon = "local_shipping", color = "#9C27B0", parentId = 16, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1604, name = "Fare", icon = "directions_bus", color = "#9C27B0", parentId = 16, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1605, name = "Flight", icon = "flight", color = "#9C27B0", parentId = 16, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1606, name = "National Park Fees", icon = "park", color = "#9C27B0", parentId = 16, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1607, name = "SGR Train", icon = "train", color = "#9C27B0", parentId = 16, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1608, name = "Uber/Bolt", icon = "local_taxi", color = "#9C27B0", parentId = 16, isDefault = true, sortOrder = 8),
    )

    // ==================== Group 17: Vehicle ====================

    private val vehicleCategories = listOf(
        CategoryEntity(id = 1701, name = "Car Accessories", icon = "settings", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1702, name = "Car Battery", icon = "battery_charging_full", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1703, name = "Car Hire", icon = "car_rental", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1704, name = "Car Insurance", icon = "security", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1705, name = "Car Payment", icon = "payments", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1706, name = "Car Registration", icon = "badge", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1707, name = "Car Repairs", icon = "build", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1708, name = "Car Service", icon = "car_repair", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 1709, name = "Car Tyres", icon = "tire_repair", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 1710, name = "Car Wash", icon = "local_car_wash", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 1711, name = "Expressway", icon = "toll", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 1712, name = "Fuel", icon = "local_gas_station", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 12),
        CategoryEntity(id = 1713, name = "Parking", icon = "local_parking", color = "#2196F3", parentId = 17, isDefault = true, sortOrder = 13),
    )
    
    /**
     * Get all categories (groups + children)
     */
    val categories: List<CategoryEntity> = groups +
        digitalCategories +
        educationCategories +
        entertainmentCategories +
        faithCategories +
        financialCategories +
        foodCategories +
        governmentCategories +
        healthCategories +
        homeCategories +
        investmentCategories +
        lifeEventsCategories +
        miscCategories +
        personalCareCategories +
        petCategories +
        shoppingCategories +
        transportCategories +
        vehicleCategories
}
