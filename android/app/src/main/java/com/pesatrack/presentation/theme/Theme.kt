package com.pesatrack.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryGreenVariant,
    onPrimaryContainer = OnPrimaryLight,
    secondary = SecondaryGreen,
    onSecondary = OnSecondaryLight,
    secondaryContainer = MpesaGreen,
    onSecondaryContainer = OnPrimaryLight,
    tertiary = MpesaGreen,
    onTertiary = OnPrimaryLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    error = ErrorColor,
    onError = OnErrorColor
)

private val DarkColorScheme = darkColorScheme(
    primary = SecondaryGreen,
    onPrimary = OnSecondaryLight,
    primaryContainer = PrimaryGreen,
    onPrimaryContainer = OnPrimaryLight,
    secondary = MpesaGreen,
    onSecondary = OnSecondaryLight,
    secondaryContainer = PrimaryGreenVariant,
    onSecondaryContainer = OnPrimaryLight,
    tertiary = SecondaryGreen,
    onTertiary = OnSecondaryLight,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    error = ErrorColor,
    onError = OnErrorColor
)

@Composable
fun PesaTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
