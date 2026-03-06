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
 * Default categories organized in groups
 * Based on user's actual expense categories
 */
object DefaultCategories {
    
    // Category Groups (Parents)
    private val groups = listOf(
        CategoryEntity(id = 1, name = "Vehicle", icon = "directions_car", color = "#2196F3", isGroup = true, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 2, name = "Home & Utilities", icon = "home", color = "#4CAF50", isGroup = true, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 3, name = "Food & Dining", icon = "restaurant", color = "#FF5722", isGroup = true, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 4, name = "Transport", icon = "commute", color = "#9C27B0", isGroup = true, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 5, name = "Shopping", icon = "shopping_bag", color = "#E91E63", isGroup = true, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 6, name = "Personal Care", icon = "face", color = "#00BCD4", isGroup = true, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 7, name = "Health", icon = "local_hospital", color = "#F44336", isGroup = true, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 8, name = "Financial", icon = "account_balance", color = "#795548", isGroup = true, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 9, name = "Faith & Giving", icon = "volunteer_activism", color = "#673AB7", isGroup = true, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 10, name = "Digital & Tech", icon = "devices", color = "#607D8B", isGroup = true, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 11, name = "Beekeeping", icon = "hive", color = "#FFC107", isGroup = true, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 12, name = "Pets", icon = "pets", color = "#8BC34A", isGroup = true, isDefault = true, sortOrder = 12),
        CategoryEntity(id = 13, name = "Entertainment", icon = "movie", color = "#E91E63", isGroup = true, isDefault = true, sortOrder = 13),
        CategoryEntity(id = 14, name = "Education", icon = "school", color = "#3F51B5", isGroup = true, isDefault = true, sortOrder = 14),
        CategoryEntity(id = 15, name = "Government & Legal", icon = "gavel", color = "#455A64", isGroup = true, isDefault = true, sortOrder = 15),
        CategoryEntity(id = 16, name = "Life Events", icon = "cake", color = "#FF9800", isGroup = true, isDefault = true, sortOrder = 16),
        CategoryEntity(id = 17, name = "Miscellaneous", icon = "more_horiz", color = "#9E9E9E", isGroup = true, isDefault = true, sortOrder = 17),
    )
    
    // Vehicle (Group 1)
    private val vehicleCategories = listOf(
        CategoryEntity(id = 101, name = "Car Payment", icon = "payments", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 102, name = "Car Insurance", icon = "security", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 103, name = "Car Registration", icon = "badge", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 104, name = "Car Repairs", icon = "build", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 105, name = "Car Service", icon = "car_repair", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 106, name = "Car Tyres", icon = "tire_repair", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 107, name = "Car Battery", icon = "battery_charging_full", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 108, name = "Car Alignment", icon = "straighten", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 109, name = "Car Accessories", icon = "settings", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 110, name = "Car Wash", icon = "local_car_wash", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 111, name = "Car Hire", icon = "car_rental", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 112, name = "Fuel", icon = "local_gas_station", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 12),
        CategoryEntity(id = 113, name = "Parking", icon = "local_parking", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 13),
        CategoryEntity(id = 114, name = "Expressway", icon = "toll", color = "#2196F3", parentId = 1, isDefault = true, sortOrder = 14),
    )
    
    // Home & Utilities (Group 2)
    private val homeCategories = listOf(
        CategoryEntity(id = 201, name = "Rent", icon = "house", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 202, name = "Electricity", icon = "bolt", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 203, name = "Water Bill", icon = "water_drop", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 204, name = "Gas", icon = "propane_tank", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 205, name = "Internet", icon = "wifi", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 206, name = "Cleaning", icon = "cleaning_services", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 207, name = "Home Furnishing", icon = "chair", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 208, name = "Cooker", icon = "microwave", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 209, name = "Curtains", icon = "blinds", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 210, name = "Repairs", icon = "handyman", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 211, name = "Pest Control", icon = "pest_control", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 212, name = "Security", icon = "shield", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 12),
        CategoryEntity(id = 213, name = "Plumber", icon = "plumbing", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 13),
        CategoryEntity(id = 214, name = "Electrician", icon = "electrical_services", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 14),
        CategoryEntity(id = 215, name = "Gardening", icon = "yard", color = "#4CAF50", parentId = 2, isDefault = true, sortOrder = 15),
    )
    
    // Food & Dining (Group 3)
    private val foodCategories = listOf(
        CategoryEntity(id = 301, name = "Food", icon = "restaurant", color = "#FF5722", parentId = 3, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 302, name = "Food Entertainment", icon = "dinner_dining", color = "#FF5722", parentId = 3, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 303, name = "Groceries", icon = "shopping_cart", color = "#FF5722", parentId = 3, isDefault = true, sortOrder = 3),
    )
    
    // Transport (Group 4)
    private val transportCategories = listOf(
        CategoryEntity(id = 401, name = "Fare", icon = "directions_bus", color = "#9C27B0", parentId = 4, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 402, name = "Uber/Bolt", icon = "local_taxi", color = "#9C27B0", parentId = 4, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 403, name = "Boda Boda", icon = "two_wheeler", color = "#9C27B0", parentId = 4, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 404, name = "Flight", icon = "flight", color = "#9C27B0", parentId = 4, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 405, name = "SGR Train", icon = "train", color = "#9C27B0", parentId = 4, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 406, name = "Park Fees", icon = "park", color = "#9C27B0", parentId = 4, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 407, name = "Accommodation", icon = "hotel", color = "#9C27B0", parentId = 4, isDefault = true, sortOrder = 7),
    )
    
    // Shopping (Group 5)
    private val shoppingCategories = listOf(
        CategoryEntity(id = 501, name = "Shopping", icon = "shopping_bag", color = "#E91E63", parentId = 5, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 502, name = "Clothing", icon = "checkroom", color = "#E91E63", parentId = 5, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 503, name = "Phone / Accessories", icon = "smartphone", color = "#E91E63", parentId = 5, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 504, name = "Books", icon = "menu_book", color = "#E91E63", parentId = 5, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 505, name = "Art", icon = "palette", color = "#E91E63", parentId = 5, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 507, name = "Shipping", icon = "local_shipping", color = "#E91E63", parentId = 5, isDefault = true, sortOrder = 6),
    )
    
    // Personal Care (Group 6)
    private val personalCareCategories = listOf(
        CategoryEntity(id = 601, name = "Barber", icon = "content_cut", color = "#00BCD4", parentId = 6, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 602, name = "Shave", icon = "face_retouching_natural", color = "#00BCD4", parentId = 6, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 603, name = "Salon", icon = "spa", color = "#00BCD4", parentId = 6, isDefault = true, sortOrder = 3),
    )
    
    // Health (Group 7)
    private val healthCategories = listOf(
        CategoryEntity(id = 701, name = "Medical Checkup", icon = "medical_services", color = "#F44336", parentId = 7, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 702, name = "Pharmacy", icon = "medication", color = "#F44336", parentId = 7, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 703, name = "Dental", icon = "dentistry", color = "#F44336", parentId = 7, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 704, name = "Optical", icon = "visibility", color = "#F44336", parentId = 7, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 705, name = "Gym/Fitness", icon = "fitness_center", color = "#F44336", parentId = 7, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 706, name = "Health Insurance", icon = "health_and_safety", color = "#F44336", parentId = 7, isDefault = true, sortOrder = 6),
    )
    
    // Financial (Group 8)
    private val financialCategories = listOf(
        CategoryEntity(id = 801, name = "Savings", icon = "savings", color = "#795548", parentId = 8, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 802, name = "Invest", icon = "trending_up", color = "#795548", parentId = 8, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 803, name = "Loan", icon = "account_balance", color = "#795548", parentId = 8, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 804, name = "Loan Interest", icon = "percent", color = "#795548", parentId = 8, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 805, name = "Treasury Bill", icon = "receipt_long", color = "#795548", parentId = 8, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 806, name = "Treasury Bill Commission", icon = "price_check", color = "#795548", parentId = 8, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 807, name = "SACCO", icon = "groups", color = "#795548", parentId = 8, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 808, name = "NSSF", icon = "elderly", color = "#795548", parentId = 8, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 809, name = "Money Market Fund", icon = "show_chart", color = "#795548", parentId = 8, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 810, name = "Bank Charges", icon = "account_balance_wallet", color = "#795548", parentId = 8, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 811, name = "Mpesa Transaction Cost", icon = "phone_android", color = "#795548", parentId = 8, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 812, name = "Mobile Payments Charges", icon = "contactless", color = "#795548", parentId = 8, isDefault = true, sortOrder = 12),
        CategoryEntity(id = 813, name = "Pesalink Charges", icon = "swap_horiz", color = "#795548", parentId = 8, isDefault = true, sortOrder = 13),
        CategoryEntity(id = 814, name = "RTGS Charges", icon = "sync_alt", color = "#795548", parentId = 8, isDefault = true, sortOrder = 14),
    )
    
    // Faith & Giving (Group 9)
    private val faithCategories = listOf(
        CategoryEntity(id = 901, name = "Tithe", icon = "church", color = "#673AB7", parentId = 9, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 902, name = "Offering", icon = "favorite", color = "#673AB7", parentId = 9, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 903, name = "Give", icon = "volunteer_activism", color = "#673AB7", parentId = 9, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 904, name = "Heaven's Gate", icon = "auto_awesome", color = "#673AB7", parentId = 9, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 905, name = "Seed", icon = "grass", color = "#673AB7", parentId = 9, isDefault = true, sortOrder = 5),
    )
    
    // Digital & Tech (Group 10)
    private val digitalCategories = listOf(
        CategoryEntity(id = 1001, name = "Airtime", icon = "sim_card", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1002, name = "Internet Bundles", icon = "signal_cellular_alt", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1003, name = "Domain Renewal", icon = "language", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1004, name = "Website Domain", icon = "public", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1005, name = "Hosting", icon = "cloud", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1006, name = "SSL Certificate", icon = "lock", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1007, name = "Open AI", icon = "smart_toy", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1008, name = "Software License", icon = "verified", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 1009, name = "Cloud Storage", icon = "cloud_upload", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 1010, name = "Laptop Repairs", icon = "laptop", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 1011, name = "VPN", icon = "vpn_key", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 1012, name = "Streaming", icon = "subscriptions", color = "#607D8B", parentId = 10, isDefault = true, sortOrder = 12),
    )
    
    // Beekeeping (Group 11)
    private val beekeepingCategories = listOf(
        CategoryEntity(id = 1101, name = "Bee Hives", icon = "hive", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1102, name = "Bee Hive Tables", icon = "table_bar", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1103, name = "Bee Equipment", icon = "construction", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1104, name = "Bees", icon = "emoji_nature", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1105, name = "Bee Swarmer", icon = "scatter_plot", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1106, name = "Bee Wax", icon = "hexagon", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1107, name = "Bee Feed", icon = "restaurant", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1108, name = "Bee Medicine", icon = "medication", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 1109, name = "Honey Harvesting", icon = "agriculture", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 1110, name = "Honey Packaging", icon = "inventory_2", color = "#FFC107", parentId = 11, isDefault = true, sortOrder = 10),
    )
    
    // Pets (Group 12)
    private val petCategories = listOf(
        CategoryEntity(id = 1201, name = "Vet", icon = "pets", color = "#8BC34A", parentId = 12, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1202, name = "Cat Treatment", icon = "healing", color = "#8BC34A", parentId = 12, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1203, name = "Pet Food", icon = "restaurant", color = "#8BC34A", parentId = 12, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1204, name = "Pet Supplies", icon = "category", color = "#8BC34A", parentId = 12, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1205, name = "Pet Grooming", icon = "content_cut", color = "#8BC34A", parentId = 12, isDefault = true, sortOrder = 5),
    )
    
    // Entertainment (Group 13)
    private val entertainmentCategories = listOf(
        CategoryEntity(id = 1301, name = "Entertainment", icon = "movie", color = "#E91E63", parentId = 13, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1302, name = "Movies", icon = "theaters", color = "#E91E63", parentId = 13, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1303, name = "Games", icon = "sports_esports", color = "#E91E63", parentId = 13, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1304, name = "Events", icon = "event", color = "#E91E63", parentId = 13, isDefault = true, sortOrder = 4),
    )
    
    // Education (Group 14)
    private val educationCategories = listOf(
        CategoryEntity(id = 1401, name = "Courses/Training", icon = "school", color = "#3F51B5", parentId = 14, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1402, name = "Certifications", icon = "workspace_premium", color = "#3F51B5", parentId = 14, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1403, name = "Conferences", icon = "groups", color = "#3F51B5", parentId = 14, isDefault = true, sortOrder = 3),
    )
    
    // Government & Legal (Group 15)
    private val governmentCategories = listOf(
        CategoryEntity(id = 1501, name = "Driving License Renewal", icon = "badge", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1502, name = "Excise Duty", icon = "request_quote", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1503, name = "KE Excise Duty", icon = "receipt", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1504, name = "Withholding Tax", icon = "money_off", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1505, name = "Tax Consultancy", icon = "support_agent", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1506, name = "KRA Penalties", icon = "warning", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 6),
        CategoryEntity(id = 1507, name = "County Rates", icon = "apartment", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 7),
        CategoryEntity(id = 1508, name = "Land Rent", icon = "terrain", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 8),
        CategoryEntity(id = 1509, name = "NHIF", icon = "health_and_safety", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 9),
        CategoryEntity(id = 1510, name = "Good Conduct Certificate", icon = "verified_user", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 10),
        CategoryEntity(id = 1511, name = "Passport Fees", icon = "flight", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 11),
        CategoryEntity(id = 1512, name = "Visa Fees", icon = "approval", color = "#455A64", parentId = 15, isDefault = true, sortOrder = 12),
    )
    
    // Life Events (Group 16)
    private val lifeEventsCategories = listOf(
        CategoryEntity(id = 1601, name = "Birthday Gift", icon = "cake", color = "#FF9800", parentId = 16, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1602, name = "Wedding Contribution", icon = "favorite", color = "#FF9800", parentId = 16, isDefault = true, sortOrder = 2),
        CategoryEntity(id = 1603, name = "Funeral Contribution", icon = "sentiment_very_dissatisfied", color = "#FF9800", parentId = 16, isDefault = true, sortOrder = 3),
        CategoryEntity(id = 1604, name = "Baby Shower", icon = "child_friendly", color = "#FF9800", parentId = 16, isDefault = true, sortOrder = 4),
        CategoryEntity(id = 1605, name = "Harambee/Fundraiser", icon = "handshake", color = "#FF9800", parentId = 16, isDefault = true, sortOrder = 5),
        CategoryEntity(id = 1606, name = "Graduation Gift", icon = "school", color = "#FF9800", parentId = 16, isDefault = true, sortOrder = 6),
    )
    
    // Miscellaneous (Group 17)
    private val miscCategories = listOf(
        CategoryEntity(id = 1701, name = "Miscellaneous", icon = "more_horiz", color = "#9E9E9E", parentId = 17, isDefault = true, sortOrder = 1),
        CategoryEntity(id = 1702, name = "Water", icon = "water", color = "#9E9E9E", parentId = 17, isDefault = true, sortOrder = 2),
    )
    
    /**
     * Get all categories (groups + children)
     */
    val categories: List<CategoryEntity> = groups + 
        vehicleCategories +
        homeCategories +
        foodCategories +
        transportCategories +
        shoppingCategories +
        personalCareCategories +
        healthCategories +
        financialCategories +
        faithCategories +
        digitalCategories +
        beekeepingCategories +
        petCategories +
        entertainmentCategories +
        educationCategories +
        governmentCategories +
        lifeEventsCategories +
        miscCategories
}
