package com.pesatrack.presentation.screens.recurring_reminders

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pesatrack.services.RecurringReminderPolicy
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRemindersScreen(onNavigateBack: () -> Unit, viewModel: RecurringRemindersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmReset by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Choose payments") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Text("Rent and utilities start on. Other detected payments start off. You choose which reminders to receive.")
                Text("Estimated from payment history. A missing payment is not proof that a bill is unpaid. Selected payments still need high confidence. Checks are roughly daily, not exact alarms.",
                    style = MaterialTheme.typography.bodySmall)
                if (!state.masterEnabled) Text("All reminders paused", color = MaterialTheme.colorScheme.primary)
                if (!state.notificationsAllowed) {
                    Text("Android notifications are blocked. Your selections are saved, but reminders cannot be delivered.")
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open notification settings") }
                }
                if (!state.selections.valid) Text("Saved choices could not be read. Only category defaults apply. Use defaults to reset.")
                OutlinedTextField(value = state.search, onValueChange = viewModel::search,
                    label = { Text("Search payments or categories") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton(onClick = { confirmReset = true }, enabled = !state.isSaving) { Text("Use defaults for all payments") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = viewModel::refresh) { Text("Refresh patterns") }
            }
            val visible = state.payments.filter { payment ->
                state.search.isBlank() || listOfNotNull(payment.recipientDisplayName, payment.categoryName, payment.maskedAccountHint)
                    .any { it.contains(state.search, ignoreCase = true) }
            }
            if (!state.isLoading && state.error == null && state.payments.isEmpty()) item {
                Text("No recurring payments detected yet. Patterns need at least three payments.")
            }
            if (visible.isEmpty() && state.payments.isNotEmpty()) item { Text("No payments match your search.") }
            items(visible, key = { it.recipientKey }) { payment ->
                val decision = RecurringReminderPolicy.resolve(state.masterEnabled, payment, state.selections)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(payment.recipientDisplayName, style = MaterialTheme.typography.titleMedium)
                                payment.maskedAccountHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            Switch(checked = decision.selected, enabled = !state.isSaving && state.selections.valid,
                                onCheckedChange = { viewModel.choose(payment.recipientKey, it) })
                        }
                        Text("${payment.categoryName ?: "Uncategorized"} · ${payment.cycle.displayName()} · ${String.format(Locale.US, "KES %,.0f", payment.averageAmount)} estimated")
                        Text("Expected ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(payment.nextExpected))}", style = MaterialTheme.typography.bodySmall)
                        Text("Detection confidence: ${String.format(Locale.US, "%.0f%%", payment.confidence * 100)}", style = MaterialTheme.typography.bodySmall)
                        Text(when {
                            decision.overridden -> "Your choice"
                            decision.selected -> "On by default: ${payment.categoryName ?: "Utility"}"
                            else -> "Off by default"
                        }, style = MaterialTheme.typography.bodySmall)
                        if (payment.confidence < 0.7) Text("Not enough confidence to notify yet", style = MaterialTheme.typography.bodySmall)
                        if (decision.overridden) TextButton(onClick = { viewModel.choose(payment.recipientKey, null) }, enabled = !state.isSaving) { Text("Use default") }
                    }
                }
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Use defaults?") },
        text = { Text("Remove all individual choices, including payments no longer detected. Your master switch and notification cooldowns stay unchanged.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; viewModel.useDefaults() }) { Text("Use defaults") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
}