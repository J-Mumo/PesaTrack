package com.pesatrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pesatrack.domain.models.Category
import com.pesatrack.presentation.theme.getCategoryColor

/**
 * Category chip component for selection
 */
@Composable
fun CategoryChip(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(category.color)
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = categoryColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else Modifier
            ),
        color = if (isSelected) {
            categoryColor.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(categoryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(category.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(14.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Get Material icon for category icon name
 * Maps icon names from categories to Material Icons
 */
fun getCategoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        // Vehicle
        "directions_car" -> Icons.Filled.DirectionsCar
        "payments" -> Icons.Filled.Payments
        "security" -> Icons.Filled.Security
        "badge" -> Icons.Filled.Badge
        "build" -> Icons.Filled.Build
        "car_repair" -> Icons.Filled.CarRepair
        "tire_repair" -> Icons.Filled.TireRepair
        "battery_charging_full" -> Icons.Filled.BatteryChargingFull
        "straighten" -> Icons.Filled.Straighten
        "settings" -> Icons.Filled.Settings
        "local_car_wash" -> Icons.Filled.LocalCarWash
        "car_rental" -> Icons.Filled.CarRental
        "local_gas_station" -> Icons.Filled.LocalGasStation
        "local_parking" -> Icons.Filled.LocalParking
        "toll" -> Icons.Filled.Toll
        
        // Home
        "home" -> Icons.Filled.Home
        "house" -> Icons.Filled.House
        "bolt" -> Icons.Filled.Bolt
        "water_drop" -> Icons.Filled.WaterDrop
        "propane_tank" -> Icons.Filled.PropaneTank
        "wifi" -> Icons.Filled.Wifi
        "cleaning_services" -> Icons.Filled.CleaningServices
        "chair" -> Icons.Filled.Chair
        "microwave" -> Icons.Filled.Microwave
        "blinds" -> Icons.Filled.Blinds
        "handyman" -> Icons.Filled.Handyman
        "pest_control" -> Icons.Filled.PestControl
        "shield" -> Icons.Filled.Shield
        "plumbing" -> Icons.Filled.Plumbing
        "electrical_services" -> Icons.Filled.ElectricalServices
        "yard" -> Icons.Filled.Yard
        
        // Food
        "restaurant" -> Icons.Filled.Restaurant
        "dinner_dining" -> Icons.Filled.DinnerDining
        "shopping_cart" -> Icons.Filled.ShoppingCart
        
        // Transport
        "commute" -> Icons.Filled.Commute
        "directions_bus" -> Icons.Filled.DirectionsBus
        "local_taxi" -> Icons.Filled.LocalTaxi
        "two_wheeler" -> Icons.Filled.TwoWheeler
        "flight" -> Icons.Filled.Flight
        "train" -> Icons.Filled.Train
        "park" -> Icons.Filled.Park
        "hotel" -> Icons.Filled.Hotel
        
        // Shopping
        "shopping_bag" -> Icons.Filled.ShoppingBag
        "checkroom" -> Icons.Filled.Checkroom
        "smartphone" -> Icons.Filled.Smartphone
        "menu_book" -> Icons.Filled.MenuBook
        "palette" -> Icons.Filled.Palette
        "grass" -> Icons.Filled.Grass
        "local_shipping" -> Icons.Filled.LocalShipping
        
        // Personal Care
        "face" -> Icons.Filled.Face
        "content_cut" -> Icons.Filled.ContentCut
        "face_retouching_natural" -> Icons.Filled.FaceRetouchingNatural
        "spa" -> Icons.Filled.Spa
        
        // Health
        "local_hospital" -> Icons.Filled.LocalHospital
        "medical_services" -> Icons.Filled.MedicalServices
        "medication" -> Icons.Filled.Medication
        "dentistry" -> Icons.Filled.HealthAndSafety // No direct dentistry icon
        "visibility" -> Icons.Filled.Visibility
        "fitness_center" -> Icons.Filled.FitnessCenter
        "health_and_safety" -> Icons.Filled.HealthAndSafety
        
        // Financial
        "account_balance" -> Icons.Filled.AccountBalance
        "savings" -> Icons.Filled.Savings
        "trending_up" -> Icons.Filled.TrendingUp
        "percent" -> Icons.Filled.Percent
        "receipt_long" -> Icons.Filled.ReceiptLong
        "price_check" -> Icons.Filled.PriceCheck
        "groups" -> Icons.Filled.Groups
        "elderly" -> Icons.Filled.Elderly
        "show_chart" -> Icons.Filled.ShowChart
        "account_balance_wallet" -> Icons.Filled.AccountBalanceWallet
        "phone_android" -> Icons.Filled.PhoneAndroid
        "contactless" -> Icons.Filled.Contactless
        "swap_horiz" -> Icons.Filled.SwapHoriz
        "sync_alt" -> Icons.Filled.SyncAlt
        
        // Faith
        "volunteer_activism" -> Icons.Filled.VolunteerActivism
        "church" -> Icons.Filled.Church
        "favorite" -> Icons.Filled.Favorite
        "auto_awesome" -> Icons.Filled.AutoAwesome
        
        // Digital
        "devices" -> Icons.Filled.Devices
        "sim_card" -> Icons.Filled.SimCard
        "signal_cellular_alt" -> Icons.Filled.SignalCellularAlt
        "language" -> Icons.Filled.Language
        "public" -> Icons.Filled.Public
        "cloud" -> Icons.Filled.Cloud
        "lock" -> Icons.Filled.Lock
        "smart_toy" -> Icons.Filled.SmartToy
        "verified" -> Icons.Filled.Verified
        "cloud_upload" -> Icons.Filled.CloudUpload
        "laptop" -> Icons.Filled.Laptop
        "vpn_key" -> Icons.Filled.VpnKey
        "subscriptions" -> Icons.Filled.Subscriptions
        
        // Beekeeping
        "hive" -> Icons.Filled.Hive
        "table_bar" -> Icons.Filled.TableBar
        "construction" -> Icons.Filled.Construction
        "emoji_nature" -> Icons.Filled.EmojiNature
        "scatter_plot" -> Icons.Filled.ScatterPlot
        "hexagon" -> Icons.Filled.Hexagon
        "agriculture" -> Icons.Filled.Agriculture
        "inventory_2" -> Icons.Filled.Inventory2
        
        // Pets
        "pets" -> Icons.Filled.Pets
        "healing" -> Icons.Filled.Healing
        "category" -> Icons.Filled.Category
        
        // Entertainment
        "movie" -> Icons.Filled.Movie
        "theaters" -> Icons.Filled.Theaters
        "sports_esports" -> Icons.Filled.SportsEsports
        "event" -> Icons.Filled.Event
        
        // Education
        "school" -> Icons.Filled.School
        "workspace_premium" -> Icons.Filled.WorkspacePremium
        
        // Government
        "gavel" -> Icons.Filled.Gavel
        "request_quote" -> Icons.Filled.RequestQuote
        "receipt" -> Icons.Filled.Receipt
        "money_off" -> Icons.Filled.MoneyOff
        "support_agent" -> Icons.Filled.SupportAgent
        "warning" -> Icons.Filled.Warning
        "apartment" -> Icons.Filled.Apartment
        "terrain" -> Icons.Filled.Terrain
        "verified_user" -> Icons.Filled.VerifiedUser
        "approval" -> Icons.Filled.Approval
        
        // Life Events
        "cake" -> Icons.Filled.Cake
        "sentiment_very_dissatisfied" -> Icons.Filled.SentimentVeryDissatisfied
        "child_friendly" -> Icons.Filled.ChildFriendly
        "handshake" -> Icons.Filled.Handshake
        
        // Misc
        "more_horiz" -> Icons.Filled.MoreHoriz
        "water" -> Icons.Filled.Water
        
        // Default
        else -> Icons.Filled.Category
    }
}
