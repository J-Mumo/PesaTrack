package com.pesatrack.presentation.screens.import_history

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.services.SmsImportService
import com.pesatrack.utils.formatAsCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBatchCategorize: () -> Unit,
    onNavigateToExcelImport: () -> Unit = {},
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        val context = LocalContext.current

        // Track SMS permission state — rechecked when returning from App Settings
        var smsPermissionGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                        PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                        PackageManager.PERMISSION_GRANTED
            )
        }

        // Permission launcher
        val smsPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            smsPermissionGranted = permissions.values.all { it }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState.phase) {
                ImportPhase.READY -> {
                    if (!smsPermissionGranted) {
                        // SMS permission gate — blocks SMS import but allows Excel
                        item {
                            SmsPermissionGateCard(
                                onGrantPermission = {
                                    smsPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_SMS,
                                            Manifest.permission.RECEIVE_SMS
                                        )
                                    )
                                },
                                onOpenSettings = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }

                        // Excel import still available without SMS permission
                        item {
                            OutlinedButton(
                                onClick = onNavigateToExcelImport,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Icon(Icons.Filled.TableChart, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import from Excel")
                            }
                        }
                    } else {
                        // Info card
                        item {
                            ImportInfoCard()
                        }

                        // Date range selection
                        item {
                            DateRangeSelector(
                                selectedRange = uiState.selectedRange,
                                onRangeSelected = viewModel::selectDateRange
                            )
                        }

                        // Start import button
                        item {
                            Button(
                                onClick = viewModel::startImport,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Icon(Icons.Filled.FileDownload, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import M-PESA History")
                            }
                        }

                        // Excel import button
                        item {
                            OutlinedButton(
                                onClick = onNavigateToExcelImport,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Icon(Icons.Filled.TableChart, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import from Excel")
                            }
                        }
                    }
                }

                ImportPhase.IMPORTING -> {
                    item {
                        ImportProgressCard(
                            current = uiState.progressCurrent,
                            total = uiState.progressTotal
                        )
                    }
                }

                ImportPhase.COMPLETED -> {
                    val result = uiState.result
                    if (result != null) {
                        item {
                            ImportResultCard(result = result)
                        }

                        // Navigate to batch categorize if there are uncategorized expenses
                        if (result.needsManualCategorization > 0) {
                            item {
                                Button(
                                    onClick = onNavigateToBatchCategorize,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Icon(Icons.Filled.Category, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Categorize ${result.needsManualCategorization} Expenses")
                                }
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }

                ImportPhase.ERROR -> {
                    item {
                        ErrorCard(
                            error = uiState.error ?: "Unknown error",
                            onRetry = viewModel::reset
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Sms,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Import M-PESA SMS History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PesaTrack will read your existing M-PESA SMS messages and import them as expenses. " +
                            "Duplicates are automatically skipped. Known recipients will be auto-categorized.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun DateRangeSelector(
    selectedRange: DateRange,
    onRangeSelected: (DateRange) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select Date Range",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            DateRange.entries.forEach { range ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedRange == range,
                        onClick = { onRangeSelected(range) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = range.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProgressCard(
    current: Int,
    total: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Importing SMS...",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (total > 0) {
                Text(
                    text = "$current / $total messages processed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { current.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "Reading SMS inbox...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ImportResultCard(result: SmsImportService.ImportResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Import Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            ResultRow("SMS found in inbox", result.totalSmsFound.toString())
            ResultRow("Parsed as expenses", result.totalParsed.toString())
            ResultRow("New expenses imported", result.newExpensesImported.toString())
            ResultRow("Duplicates skipped", result.duplicatesSkipped.toString())
            ResultRow("Transaction costs saved", result.transactionCostsSaved.toString())

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Categorization",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ResultRow(
                "Auto-categorized (rules)",
                result.autoCategorizedByRules.toString(),
                icon = Icons.Filled.AutoAwesome
            )
            ResultRow(
                "Auto-categorized (learned)",
                result.autoCategorizedByMapping.toString(),
                icon = Icons.Filled.Psychology
            )
            ResultRow(
                "Needs manual categorization",
                result.needsManualCategorization.toString(),
                icon = Icons.Filled.Edit
            )

            if (result.errors > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow(
                    "Errors",
                    result.errors.toString(),
                    icon = Icons.Filled.Warning
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ErrorCard(
    error: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Import Failed",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

/**
 * Full-screen gate card shown on the Import screen when SMS permission is not granted.
 * Explains why the permission is needed and provides buttons to grant it.
 * Excel import is still available separately (doesn't need SMS permission).
 */
@Composable
private fun SmsPermissionGateCard(
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Sms,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "SMS Permission Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PesaTrack needs SMS permission to read your M-PESA message history.\n\n" +
                        "This is used only to find and import M-PESA transactions — we never read personal messages.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Filled.Lock, null)
                Spacer(Modifier.width(8.dp))
                Text("Grant SMS Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App Settings")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You can still import expenses from Excel files without SMS permission.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}
