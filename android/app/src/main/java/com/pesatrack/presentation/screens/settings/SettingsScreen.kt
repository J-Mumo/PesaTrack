package com.pesatrack.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Settings screen for configuring:
 * - Bank SMS tracking (master toggle + individual bank toggles)
 * - AI-powered categorization (enable/disable + Gemini API key)
 *
 * M-PESA tracking is always on and not shown here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                // Section: SMS Sources
                SmsSourcesSection(
                    uiState = uiState,
                    onBankTrackingToggled = viewModel::setBankTrackingEnabled,
                    onBankToggled = viewModel::setBankEnabled
                )

                // Section: AI Categorization
                AiCategorizationSection(
                    uiState = uiState,
                    onAiToggled = viewModel::setAiCategorizationEnabled,
                    onApiKeySaved = viewModel::saveGeminiApiKey
                )
            }
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
 * AI Categorization configuration section.
 */
@Composable
private fun AiCategorizationSection(
    uiState: SettingsUiState,
    onAiToggled: (Boolean) -> Unit,
    onApiKeySaved: (String) -> Unit
) {
    val context = LocalContext.current

    // Section header
    Text(
        text = "AI Categorization",
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
            // Master AI toggle
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "AI-Powered Suggestions",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Use Google Gemini to suggest categories for unknown recipients in Batch Categorize.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.aiCategorizationEnabled,
                    onCheckedChange = onAiToggled
                )
            }

            // API key section (only shown when AI is enabled)
            if (uiState.aiCategorizationEnabled) {
                HorizontalDivider()

                // API key input
                var apiKeyText by remember(uiState.geminiApiKey) {
                    mutableStateOf(uiState.geminiApiKey)
                }
                var showApiKey by remember { mutableStateOf(false) }
                var hasEdited by remember { mutableStateOf(false) }

                Text(
                    text = "Gemini API Key",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = {
                        apiKeyText = it
                        hasEdited = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (uiState.hasBuiltInApiKey) "Using built-in key (optional override)"
                            else "Enter your Gemini API key"
                        )
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Filled.VisibilityOff
                                        else Icons.Filled.Visibility,
                                    contentDescription = if (showApiKey) "Hide" else "Show"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )

                // Save button (only shown when key has been edited)
                if (hasEdited) {
                    Button(
                        onClick = {
                            onApiKeySaved(apiKeyText)
                            hasEdited = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (apiKeyText.isBlank()) "Clear API Key" else "Save API Key")
                    }
                }

                // "Get API Key" link
                Text(
                    text = "→ Get a free API key from Google AI Studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/apikey")
                            )
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp)
                )

                // Security note
                Text(
                    text = "Your API key is stored locally on this device and never shared with anyone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // Status indicator
                if (uiState.hasBuiltInApiKey && uiState.geminiApiKey.isBlank()) {
                    Text(
                        text = "✓ Using built-in API key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (uiState.geminiApiKey.isNotBlank()) {
                    Text(
                        text = "✓ Using custom API key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "⚠ No API key configured — AI suggestions won't work",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Hint when AI is off
            if (!uiState.aiCategorizationEnabled) {
                Text(
                    text = "Enable to get AI-powered category suggestions in Batch Categorize.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp)
                )
            }
        }
    }
}
