package com.pesatrack.presentation.theme

import androidx.compose.ui.graphics.Color

// Primary colors - M-PESA inspired green palette
val PrimaryGreen = Color(0xFF1B5E20)
val PrimaryGreenVariant = Color(0xFF2E7D32)
val SecondaryGreen = Color(0xFF00C853)
val MpesaGreen = Color(0xFF4CAF50)

// Background colors
val BackgroundLight = Color(0xFFFAFAFA)
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)

// Text colors
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnSecondaryLight = Color(0xFF000000)
val OnBackgroundLight = Color(0xFF1C1B1F)
val OnSurfaceLight = Color(0xFF1C1B1F)
val OnBackgroundDark = Color(0xFFE6E1E5)
val OnSurfaceDark = Color(0xFFE6E1E5)

// Error
val ErrorColor = Color(0xFFB00020)
val OnErrorColor = Color(0xFFFFFFFF)

// Category colors
val CategoryFood = Color(0xFFFF5722)
val CategoryTransport = Color(0xFF2196F3)
val CategoryShopping = Color(0xFF9C27B0)
val CategoryBills = Color(0xFF4CAF50)
val CategoryEntertainment = Color(0xFFE91E63)
val CategoryHealth = Color(0xFF00BCD4)
val CategoryRent = Color(0xFF795548)
val CategoryOther = Color(0xFF607D8B)

/**
 * Get category color from hex string
 */
fun getCategoryColor(hexColor: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexColor))
    } catch (e: Exception) {
        CategoryOther
    }
}
