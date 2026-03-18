package com.pesatrack.presentation.screens.excel_import

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.services.ExcelImportService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelImportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBatchCategorize: () -> Unit,
    viewModel: ExcelImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Multi-file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = mutableListOf<android.net.Uri>()

            // Check for multiple selections
            val clipData = data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else {
                // Single selection
                data?.data?.let { uris.add(it) }
            }

            viewModel.onFilesSelected(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from Excel") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState.phase) {
                ExcelImportPhase.READY -> {
                    item { ExcelInfoCard() }
                    item {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel"
                                    ))
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                filePickerLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Icon(Icons.Filled.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select Excel File(s)")
                        }
                    }
                }

                ExcelImportPhase.FILES_SELECTED -> {
                    item { ExcelInfoCard() }

                    // Show selected files
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Selected Files",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                uiState.selectedFileNames.forEach { name ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Description,
                                            null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons
                    item {
                        Button(
                            onClick = viewModel::startImport,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Import")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel"
                                    ))
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                filePickerLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Icon(Icons.Filled.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Change Files")
                        }
                    }
                }

                ExcelImportPhase.IMPORTING -> {
                    item {
                        ImportProgressCard(
                            current = uiState.progressCurrent,
                            total = uiState.progressTotal,
                            phase = uiState.progressPhase
                        )
                    }
                }

                ExcelImportPhase.COMPLETED -> {
                    val result = uiState.result
                    if (result != null) {
                        item { ExcelResultCard(result = result) }

                        // Navigate to batch categorize if there are uncategorized imports
                        if (result.rowsWithUnknownCategory > 0 || result.rowsImportedAsStandalone > 0) {
                            item {
                                Button(
                                    onClick = onNavigateToBatchCategorize,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Icon(Icons.Filled.Category, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Categorize Expenses")
                                }
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = viewModel::reset,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import More Files")
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

                ExcelImportPhase.ERROR -> {
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
private fun ExcelInfoCard() {
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
                imageVector = Icons.Filled.TableChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Import Excel Expense History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Upload your expense tracking spreadsheet (.xlsx). " +
                            "PesaTrack will match entries to your SMS transactions and auto-categorize them. " +
                            "Unmatched entries within your SMS date range will be imported as new expenses. " +
                            "You can select multiple files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ImportProgressCard(
    current: Int,
    total: Int,
    phase: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Processing Excel...",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = phase,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (total > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$current / $total rows processed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { current.toFloat() / total.toFloat().coerceAtLeast(1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ExcelResultCard(result: ExcelImportService.ExcelImportResult) {
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
                    text = "Excel Import Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Summary stats
            ResultRow("Files processed", result.filesProcessed.toString(),
                icon = Icons.Filled.Description)
            ResultRow("Sheets processed", result.sheetsProcessed.toString(),
                icon = Icons.Filled.TableChart)
            ResultRow("Total Excel rows", result.totalExcelRows.toString(),
                icon = Icons.Filled.List)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Matching Results",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            ResultRow("Matched to SMS expenses", result.rowsMatchedToSms.toString(),
                icon = Icons.Filled.Link)
            ResultRow("Imported as new expenses", result.rowsImportedAsStandalone.toString(),
                icon = Icons.Filled.AddCircle)
            ResultRow("Skipped (already exists)", result.rowsSkippedAlreadyExists.toString(),
                icon = Icons.Filled.SkipNext)
            ResultRow("Skipped (out of date range)", result.rowsSkippedOutOfRange.toString(),
                icon = Icons.Filled.DateRange)
            ResultRow("Unknown categories", result.rowsWithUnknownCategory.toString(),
                icon = Icons.Filled.HelpOutline)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            ResultRow("Recipient mappings learned", result.recipientMappingsLearned.toString(),
                icon = Icons.Filled.Psychology)

            if (result.parseErrors > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("Parse errors", result.parseErrors.toString(),
                    icon = Icons.Filled.Warning)
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
