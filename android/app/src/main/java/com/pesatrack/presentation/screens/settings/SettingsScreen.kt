package com.pesatrack.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import com.pesatrack.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings screen for configuring:
 * - Security (PIN lock, biometric unlock, lock timeout)
 * - Category management (custom categories + auto-categorization rules)
 * - Budget management
 * - Bank SMS tracking (master toggle + individual bank toggles)
 * - Data management (backup/restore, export CSV, reset, clear)
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
                            onNavigateToPinSetup("setup")
                        } else {
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

                // Section: Budget Month Start Day
                MonthStartDaySection(
                    monthStartDay = uiState.monthStartDay,
                    onMonthStartDayChanged = viewModel::setMonthStartDay
                )

                // Section: SMS Sources
                SmsSourcesSection(
                    uiState = uiState,
                    onBankTrackingToggled = viewModel::setBankTrackingEnabled,
                    onBankToggled = viewModel::setBankEnabled
                )

                // Section: Data Management
                DataManagementSection(
                    uiState = uiState,
                    onResetCategories = viewModel::resetCategoriesToDefault,
                    onExportData = { viewModel.exportData(context) },
                    onBackupData = { uri -> viewModel.backupDatabase(context, uri) },
                    onRestoreData = { uri -> viewModel.restoreDatabase(context, uri) },
                    onPopulateSampleData = viewModel::populateSampleData,
                    onClearData = viewModel::clearAllData,
                    viewModel = viewModel
                )

                // Section: About
                AboutSection(
                    onNavigateToAbout = onNavigateToAbout,
                    onSharePesaTrack = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, SETTINGS_SHARE_MESSAGE)
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "Share PesaTrack")
                        )
                    }
                )
            }
        }
    }
}

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

            if (uiState.pinEnabled) {
                HorizontalDivider()
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
                        modifier = Modifier.padding(start = 36.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Change PIN",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                LockTimeoutPicker(
                    currentTimeout = uiState.lockTimeoutSeconds,
                    onTimeoutChanged = onLockTimeoutChanged
                )
            }
        }
    }
}

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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            timeoutOptions.forEach { (seconds, label) ->
                DropdownMenuItem(
                    text = {
                        Text(text = label, fontWeight = if (seconds == currentTimeout) FontWeight.Bold else FontWeight.Normal)
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

@Composable
private fun CategoriesSection(onNavigateToCategoryManagement: () -> Unit) {
    Text(text = "Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Card(onClick = onNavigateToCategoryManagement, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(imageVector = Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(text = "Manage Categories", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(text = "Add custom categories & auto-categorization rules", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Go to Categories", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BudgetsSection(onNavigateToBudget: () -> Unit) {
    Text(text = "Budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Card(onClick = onNavigateToBudget, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(text = "Manage Budgets", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(text = "Set spending limits per category or total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Go to Budgets", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmsSourcesSection(uiState: SettingsUiState, onBankTrackingToggled: (Boolean) -> Unit, onBankToggled: (String, Boolean) -> Unit) {
    Text(text = "SMS Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "M-PESA", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = "Always enabled — tracks all M-PESA transaction SMS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = true, onCheckedChange = null, enabled = false)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Bank SMS Tracking", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Track expenses from bank confirmation SMS. Duplicates with M-PESA are automatically filtered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = uiState.bankTrackingEnabled, onCheckedChange = onBankTrackingToggled)
            }
            if (uiState.bankTrackingEnabled && uiState.availableBanks.isNotEmpty()) {
                HorizontalDivider()
                Text(text = "Select banks to track:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                uiState.availableBanks.forEach { bank ->
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = bank.displayName, style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = bank.enabled, onCheckedChange = { enabled -> onBankToggled(bank.displayName, enabled) })
                    }
                }
            }
            if (!uiState.bankTrackingEnabled) {
                Text(text = "Enable to import expenses from supported bank SMS (NCBA, etc.)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp))
            }
        }
    }
}

@Composable
private fun MonthStartDaySection(
    monthStartDay: Int,
    onMonthStartDayChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dayOptions = (1..28).toList()

    // Ordinal suffix helper
    fun ordinal(day: Int): String = when {
        day in 11..13 -> "${day}th"
        day % 10 == 1 -> "${day}st"
        day % 10 == 2 -> "${day}nd"
        day % 10 == 3 -> "${day}rd"
        else -> "${day}th"
    }

    val currentLabel = if (monthStartDay == 1) {
        "1st of each month (default)"
    } else {
        "${ordinal(monthStartDay)} of each month"
    }

    Text(
        text = "Budget Period",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Month Starts On",
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
                    contentDescription = "Change",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    dayOptions.forEach { day ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (day == 1) "1st (default)" else ordinal(day),
                                    fontWeight = if (day == monthStartDay) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onMonthStartDayChanged(day)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = "Set this to your salary date. A monthly budget from the ${ordinal(monthStartDay)} " +
                    "runs to the ${ordinal(if (monthStartDay == 1) 1 else monthStartDay - 1)} of the next month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutSection(
    onNavigateToAbout: () -> Unit,
    onSharePesaTrack: () -> Unit
) {
    Text(text = "About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToAbout)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(text = "About PesaTrack", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(text = "Version info, privacy policy & contact", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Go to About", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSharePesaTrack)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(text = "Share PesaTrack", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(text = "Tell friends about PesaTrack", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Share PesaTrack", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val SETTINGS_SHARE_MESSAGE =
    "I use PesaTrack to automatically track my M-PESA expenses - it reads SMS and categorizes " +
    "everything offline. Free on Play Store: https://play.google.com/store/apps/details?id=com.pesatrack"


@Composable
private fun DataManagementSection(
    uiState: SettingsUiState,
    onResetCategories: () -> Unit,
    onExportData: () -> Unit,
    onBackupData: (Uri) -> Unit,
    onRestoreData: (Uri) -> Unit,
    onPopulateSampleData: () -> Unit,
    onClearData: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    // SAF launcher for backup — creates a new .db file
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { onBackupData(it) }
    }

    // SAF launcher for restore — opens an existing .db file
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onRestoreData(it) }
    }

    Text(text = "Data Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Backup Data
            Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isBackingUp) {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                val fileName = "PesaTrack_Backup_${dateFormat.format(Date())}.db"
                backupLauncher.launch(fileName)
            }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(imageVector = Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(text = "Backup Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(text = "Save a backup of all your data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (uiState.isBackingUp) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Backup", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // Restore Data
            Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isRestoring) {
                showRestoreDialog = true
            }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(imageVector = Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(text = "Restore Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(text = "Restore from a previous backup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (uiState.isRestoring) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Restore", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // Export Data
            Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isExporting) { onExportData() }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(text = "Export Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(text = "Export all expenses and income as CSV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (uiState.isExporting) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Export", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // Reset Categories
            Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isResettingCategories) { showResetDialog = true }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Column {
                        Text(text = "Reset Categories", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(text = "Remove custom categories & rules, restore defaults", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (uiState.isResettingCategories) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            HorizontalDivider()

            // Developer-only: Sample Data & Clear All Data (hidden in release builds)
            if (BuildConfig.DEBUG) {
                // Populate Sample Data
                Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isPopulatingSampleData) { onPopulateSampleData() }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(text = "Populate Sample Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(text = "Add demo expenses for screenshots", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (uiState.isPopulatingSampleData) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                HorizontalDivider()

                // Clear All Data
                Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isPopulatingSampleData) { showClearDialog = true }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Column {
                            Text(text = "Clear All Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(text = "Delete all expenses, budgets, and income", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider()
            }

            uiState.dataManagementMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 36.dp))
            }
        }
    }

    LaunchedEffect(uiState.dataManagementMessage) {
        if (uiState.dataManagementMessage == "Export ready — opening share…") {
            val intent = viewModel.createShareIntent(context)
            if (intent != null) context.startActivity(intent)
            viewModel.clearDataManagementMessage()
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset Categories?") },
            text = { Text("This will:\n• Delete all custom categories and sub-categories\n• Delete all auto-categorization rules\n• Restore default categories\n\nExpenses with custom categories will become uncategorized.\nYour expense data will NOT be deleted.") },
            confirmButton = { TextButton(onClick = { showResetDialog = false; onResetCategories() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Data?") },
            text = { Text("This will permanently delete all your expenses, budgets, and income records. This action cannot be undone.") },
            confirmButton = { TextButton(onClick = { showClearDialog = false; onClearData() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Clear All") } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Restore Backup?") },
            text = { Text("This will REPLACE all current data:\n• All expenses and categories\n• Budgets and income records\n• Auto-categorization rules\n• Recipient mappings\n\nThis cannot be undone.\nThe app will restart after restore.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        restoreLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") } }
        )
    }
}
