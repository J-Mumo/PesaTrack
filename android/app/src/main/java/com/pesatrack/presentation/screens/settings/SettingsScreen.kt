package com.pesatrack.presentation.screens.settings

import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Settings screen for configuring:
 * - Security (PIN lock, biometric unlock, lock timeout)
 * - Category management (custom categories + auto-categorization rules)
 * - Budget management
 * - Bank SMS tracking (master toggle + individual bank toggles)
 *
 * M-PESA tracking is always on and not shown here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBudget: () -> Unit = {},
    onNavigateToCategoryManagement: () -> Unit = {},
    onNavigateToPinSetup: (String) -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Check biometric availability
    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        val available = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
        viewModel.setBiometricAvailable(available)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Security
                SecuritySection(
                    uiState = uiState,
                    onPinToggle = { enabled ->
                        if (enabled) {
                            // Navigate to PIN setup screen
                            onNavigateToPinSetup("setup")
                        } else {
                            // Navigate to PIN disable screen (verify current PIN first)
                            onNavigateToPinSetup("disable")
                        }
                    },
                    onChangePin = {
                        onNavigateToPinSetup("change")
                    },
                    onBiometricToggle = viewModel::setBiometricEnabled,
                    onLockTimeoutChanged = viewModel::setLockTimeout
                )

                // Section: Categories
                CategoriesSection(onNavigateToCategoryManagement = onNavigateToCategoryManagement)

                // Section: Budgets
                BudgetsSection(onNavigateToBudget = onNavigateToBudget)

                // Section: SMS Sources
                SmsSourcesSection(
                    uiState = uiState,
                    onBankTrackingToggled = viewModel::setBankTrackingEnabled,
                    onBankToggled = viewModel::setBankEnabled
                )

                // Section: About
                AboutSection(onNavigateToAbout = onNavigateToAbout)
            }
        }
    }
}

/**
 * Security section — PIN lock, biometric unlock, lock timeout.
 */
@Composable
private fun SecuritySection(
    uiState: SettingsUiState,
    onPinToggle: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onLockTimeoutChanged: (Int) -> Unit
) {
    Text(
        text = "Security",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // PIN Lock toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "App Lock PIN",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (uiState.pinEnabled) "PIN is set" else "Tap to set up a 4-digit PIN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = uiState.pinEnabled,
                    onCheckedChange = onPinToggle
                )
            }

            // Remaining options shown only when PIN is enabled
            if (uiState.pinEnabled) {
                HorizontalDivider()

                // Change PIN
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChangePin)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change PIN",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 36.dp) // Align with text above
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Change PIN",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Biometric toggle (only if device supports it)
                if (uiState.biometricAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Unlock with Biometrics",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Use fingerprint or face unlock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.biometricEnabled,
                            onCheckedChange = onBiometricToggle
                        )
                    }
                }

                // Lock timeout picker
                LockTimeoutPicker(
                    currentTimeout = uiState.lockTimeoutSeconds,
                    onTimeoutChanged = onLockTimeoutChanged
                )
            }
        }
    }
}

/**
 * Lock timeout picker — dropdown with predefined timeout options.
 */
@Composable
private fun LockTimeoutPicker(
    currentTimeout: Int,
    onTimeoutChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val timeoutOptions = listOf(
        0 to "Immediately",
        30 to "After 30 seconds",
        60 to "After 1 minute",
        300 to "After 5 minutes"
    )

    val currentLabel = timeoutOptions.find { it.first == currentTimeout }?.second
        ?: "After ${currentTimeout}s"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = "Lock After",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Change timeout",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            timeoutOptions.forEach { (seconds, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (seconds == currentTimeout) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onTimeoutChanged(seconds)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Categories section — navigate to category management screen.
 */
@Composable
private fun CategoriesSection(
    onNavigateToCategoryManagement: () -> Unit
) {
    Text(
        text = "Categories",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        onClick = onNavigateToCategoryManagement,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Manage Categories",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Add custom categories & auto-categorization rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go to Categories",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Budgets section — navigate to budget management screen.
 */
@Composable
private fun BudgetsSection(
    onNavigateToBudget: () -> Unit
) {
    Text(
        text = "Budgets",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        onClick = onNavigateToBudget,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Manage Budgets",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Set spending limits per category or total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go to Budgets",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * SMS Sources configuration section.
 */
@Composable
private fun SmsSourcesSection(
    uiState: SettingsUiState,
    onBankTrackingToggled: (Boolean) -> Unit,
    onBankToggled: (String, Boolean) -> Unit
) {
    // Section header
    Text(
        text = "SMS Sources",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    // M-PESA card (always on, not toggleable)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "M-PESA",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Always enabled — tracks all M-PESA transaction SMS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = true,
                onCheckedChange = null, // Not toggleable
                enabled = false
            )
        }
    }

    // Bank SMS tracking section
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Bank SMS Tracking",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Track expenses from bank confirmation SMS. " +
                                "Duplicates with M-PESA are automatically filtered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.bankTrackingEnabled,
                    onCheckedChange = onBankTrackingToggled
                )
            }

            // Individual bank toggles (only shown when master toggle is on)
            if (uiState.bankTrackingEnabled && uiState.availableBanks.isNotEmpty()) {
                HorizontalDivider()

                Text(
                    text = "Select banks to track:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                uiState.availableBanks.forEach { bank ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bank.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = bank.enabled,
                            onCheckedChange = { enabled ->
                                onBankToggled(bank.displayName, enabled)
                            }
                        )
                    }
                }
            }

            // Hint when bank tracking is off
            if (!uiState.bankTrackingEnabled) {
                Text(
                    text = "Enable to import expenses from supported bank SMS (NCBA, etc.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp)
                )
            }
        }
    }
}

/**
 * About section — navigate to the About screen.
 */
@Composable
private fun AboutSection(
    onNavigateToAbout: () -> Unit
) {
    Text(
        text = "About",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        onClick = onNavigateToAbout,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "About PesaTrack",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Version info, privacy policy & contact",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go to About",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
