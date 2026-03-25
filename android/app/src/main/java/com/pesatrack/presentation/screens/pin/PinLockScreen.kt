package com.pesatrack.presentation.screens.pin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PIN lock screen — shown as an overlay when the app is locked.
 *
 * Features:
 * - 4-dot PIN indicator with shake animation on error
 * - Number pad (1-9, biometric/0/backspace)
 * - Cooldown timer display
 * - Biometric button (optional, bottom-left of keypad)
 *
 * @param uiState Current PIN UI state
 * @param onDigitEntered Called when a digit button is tapped
 * @param onBackspace Called when backspace is tapped
 * @param onBiometricRequest Called when the biometric button is tapped
 */
@Composable
fun PinLockScreen(
    uiState: PinUiState,
    onDigitEntered: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometricRequest: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Shake animation for error
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(uiState.isError) {
        if (uiState.isError) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // Shake sequence: left-right-left-right-center
            for (i in 0 until 4) {
                val target = if (i % 2 == 0) 20f else -20f
                shakeOffset.animateTo(target, animationSpec = spring(stiffness = 2000f))
            }
            shakeOffset.animateTo(0f, animationSpec = spring(stiffness = 2000f))
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Lock icon
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Error / cooldown message
            val messageText = when {
                uiState.cooldownSeconds > 0 -> "Try again in ${uiState.cooldownSeconds}s"
                uiState.errorMessage != null -> uiState.errorMessage
                else -> ""
            }
            Text(
                text = messageText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.isError || uiState.cooldownSeconds > 0)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN dots
            Row(
                modifier = Modifier.graphicsLayer {
                    translationX = shakeOffset.value
                },
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                for (i in 0 until 4) {
                    PinDot(
                        filled = i < uiState.filledDots,
                        isError = uiState.isError
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Number pad
            NumberPad(
                onDigitEntered = onDigitEntered,
                onBackspace = onBackspace,
                onBiometricRequest = onBiometricRequest,
                showBiometric = uiState.showBiometricButton,
                enabled = !uiState.isInputBlocked
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * A single PIN dot indicator.
 */
@Composable
private fun PinDot(filled: Boolean, isError: Boolean) {
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        filled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(color, CircleShape)
                } else {
                    Modifier.border(2.dp, color, CircleShape)
                }
            )
    )
}

/**
 * Number pad with digits 1-9, biometric/0/backspace bottom row.
 */
@Composable
private fun NumberPad(
    onDigitEntered: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometricRequest: () -> Unit,
    showBiometric: Boolean,
    enabled: Boolean
) {
    val alpha = if (enabled) 1f else 0.4f

    Column(
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Rows 1-2-3, 4-5-6, 7-8-9
        for (row in 0 until 3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                for (col in 0 until 3) {
                    val digit = row * 3 + col + 1
                    NumberButton(
                        text = digit.toString(),
                        onClick = { if (enabled) onDigitEntered(digit) }
                    )
                }
            }
        }

        // Bottom row: biometric / 0 / backspace
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Biometric button or empty spacer
            if (showBiometric) {
                IconKeyButton(
                    icon = Icons.Default.Fingerprint,
                    contentDescription = "Unlock with biometrics",
                    onClick = { if (enabled) onBiometricRequest() }
                )
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }

            // Zero
            NumberButton(
                text = "0",
                onClick = { if (enabled) onDigitEntered(0) }
            )

            // Backspace
            IconKeyButton(
                icon = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete",
                onClick = { if (enabled) onBackspace() }
            )
        }
    }
}

/**
 * A circular number button.
 */
@Composable
private fun NumberButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 28.sp
        )
    }
}

/**
 * An icon button in the keypad (biometric, backspace).
 */
@Composable
private fun IconKeyButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
    }
}
