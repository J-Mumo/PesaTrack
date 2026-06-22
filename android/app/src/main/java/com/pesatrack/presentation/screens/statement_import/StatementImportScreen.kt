package com.pesatrack.presentation.screens.statement_import

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.services.StatementImportService

/**
 * M-PESA Statement Import screen.
 *
 * Allows users to import transactions from a password-protected M-PESA PDF statement.
 * Flow: Select PDF → Enter password (sent by Safaricom via SMS) → Import → View results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBatchCategorize: () -> Unit,
    viewModel: StatementImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Get the display name
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex >= 0) cursor.getString(nameIndex) else "statement.pdf"
            } ?: "statement.pdf"

            // Take persistable URI permission
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            viewModel.onFileSelected(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import M-PESA Statement") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState.phase) {
                StatementImportPhase.READY -> {
                    ReadyContent(
                        onSelectFile = {
                            filePickerLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                }

                StatementImportPhase.PASSWORD_ENTRY,
                StatementImportPhase.FILE_SELECTED -> {
                    // Show file selected + password dialog
                    FileSelectedContent(
                        fileName = uiState.selectedFileName ?: "statement.pdf",
                        onChangeFile = {
                            filePickerLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                }

                StatementImportPhase.IMPORTING -> {
                    ImportingContent(
                        current = uiState.progressCurrent,
                        total = uiState.progressTotal,
                        phase = uiState.progressPhase
                    )
                }

                StatementImportPhase.COMPLETED -> {
                    CompletedContent(
                        result = uiState.result!!,
                        onImportAnother = { viewModel.onReset() },
                        onBatchCategorize = onNavigateToBatchCategorize
                    )
                }

                StatementImportPhase.ERROR -> {
                    ErrorContent(
                        error = uiState.error ?: "An unknown error occurred.",
                        onRetry = { viewModel.onReset() }
                    )
                }
            }
        }
    }

    // Password dialog
    if (uiState.showPasswordDialog) {
        PasswordDialog(
            password = uiState.passwordInput,
            onPasswordChanged = viewModel::onPasswordChanged,
            onDismiss = viewModel::onPasswordDialogDismissed,
            onConfirm = viewModel::onStartImport
        )
    }
}

@Composable
private fun ReadyContent(onSelectFile: () -> Unit) {
    Spacer(modifier = Modifier.height(48.dp))

    Icon(
        imageVector = Icons.Default.Description,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Import M-PESA Statement",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Import transactions from your M-PESA PDF statement. " +
                "Download the statement from the M-PESA app:\n\n" +
                "M-PESA App → M-PESA Statement → Select dates → Download",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onSelectFile,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.FileOpen, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Select PDF Statement")
    }

    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ℹ️ Note",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "M-PESA statements are password-protected. Safaricom sends the password " +
                        "via SMS when you request the statement. You'll be asked to enter it after selecting the file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FileSelectedContent(
    fileName: String,
    onChangeFile: () -> Unit
) {
    Spacer(modifier = Modifier.height(48.dp))

    Icon(
        imageVector = Icons.Default.PictureAsPdf,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = fileName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    TextButton(onClick = onChangeFile) {
        Text("Change file")
    }
}

@Composable
private fun PasswordDialog(
    password: String,
    onPasswordChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Statement Password") },
        text = {
            Column {
                Text(
                    text = "Enter the password sent by Safaricom via SMS when you requested the statement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    label = { Text("Statement Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = password.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ImportingContent(
    current: Int,
    total: Int,
    phase: String
) {
    Spacer(modifier = Modifier.height(80.dp))

    CircularProgressIndicator(modifier = Modifier.size(64.dp))

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = phase,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    if (total > 0) {
        Spacer(modifier = Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { current.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$current / $total transactions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompletedContent(
    result: StatementImportService.StatementImportResult,
    onImportAnother: () -> Unit,
    onBatchCategorize: () -> Unit
) {
    Spacer(modifier = Modifier.height(32.dp))

    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Import Complete!",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    if (result.statementPeriod != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = result.statementPeriod,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Results card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ResultRow("✅ Expenses imported", result.imported.toString())
            ResultRow("🏷️ Auto-categorized", result.autoCategorized.toString())
            ResultRow("💰 Transaction charges", result.chargesImported.toString())

            if (result.incomeImported > 0) {
                ResultRow("📥 Income imported", result.incomeImported.toString())
            }
            if (result.incomeDuplicates > 0) {
                ResultRow("⏭️ Income already in database", result.incomeDuplicates.toString())
            }
            if (result.skippedDuplicate > 0) {
                ResultRow("⏭️ Already in database", result.skippedDuplicate.toString())
            }
            if (result.skippedIncome > 0 && result.incomeImported == 0 && result.incomeDuplicates == 0) {
                ResultRow("📥 Income (skipped)", result.skippedIncome.toString())
            }
            if (result.skippedReversal > 0) {
                ResultRow("↩️ Reversals (skipped)", result.skippedReversal.toString())
            }
            if (result.unparseable > 0) {
                ResultRow("❓ Unrecognized", result.unparseable.toString())
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ResultRow("📊 Total rows in statement", result.totalRows.toString(), bold = true)
        }
    }

    // Uncategorized count
    val uncategorized = result.imported - result.autoCategorized - result.chargesImported
    if (uncategorized > 0) {
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "$uncategorized expenses need categorization",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onBatchCategorize,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Batch Categorize")
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(
        onClick = onImportAnother,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Import Another Statement")
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Spacer(modifier = Modifier.height(80.dp))

    Icon(
        imageVector = Icons.Default.Error,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Import Failed",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(onClick = onRetry) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Try Again")
    }
}
