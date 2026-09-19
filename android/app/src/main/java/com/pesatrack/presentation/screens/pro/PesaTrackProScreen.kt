package com.pesatrack.presentation.screens.pro

import android.app.Activity
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pesatrack.services.pro.ProProduct
import java.util.Date

/**
 * PesaTrack Pro subscription screen (Slice A5b).
 *
 * The screen self-adapts through four surfaces (see [PesaTrackProUiState]):
 *  - **Loading** — spinner while product details load.
 *  - **Entitled** — status card + expiry + "Manage subscription in Play"
 *    link. Subscribe buttons hidden.
 *  - **Available** — tier cards (Monthly + Annual) with prices from Play.
 *  - **Coming soon** — Play returned no products (SKUs not yet
 *    published). Placeholder text; no purchase attempt possible.
 *
 * This design is what lets v1.6.0 ship safely before the Play Console
 * SKUs are published — users just see the placeholder, and when the
 * SKUs go live the same APK auto-lights up without a version bump.
 *
 * See plans/ai-pro-phase1-spec.md §3.1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PesaTrackProScreen(
    onNavigateBack: () -> Unit,
    viewModel: PesaTrackProViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity

    // Surface every OutcomeMessage as a snackbar, then clear it.
    LaunchedEffect(uiState.outcomeMessage) {
        val msg = uiState.outcomeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg.text, duration = SnackbarDuration.Short)
        viewModel.dismissMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PesaTrack Pro") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard()

            when {
                uiState.loading -> LoadingPanel()

                uiState.isCurrentlyEntitled -> EntitledPanel(uiState = uiState)

                uiState.showComingSoonPlaceholder -> ComingSoonPanel()

                else -> {
                    Text(
                        text = "Choose a plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    uiState.tiers.forEach { tier ->
                        TierCard(
                            tier = tier,
                            purchaseInFlight = uiState.purchaseInFlight,
                            onSubscribe = {
                                activity?.let { viewModel.subscribe(it, tier.product) }
                            },
                        )
                    }
                }
            }

            // Always show restore, even in "coming soon" — a user may have
            // subscribed on another device before we published the SKU here.
            if (!uiState.isCurrentlyEntitled) {
                RestoreRow(
                    inFlight = uiState.restoreInFlight,
                    onRestore = { viewModel.restore() },
                )
            }

            Spacer(Modifier.height(8.dp))
            FootnoteText()
        }
    }
}

// ─── Section composables ────────────────────────────────────────────────

@Composable
private fun HeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "PesaTrack Pro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = "AI-powered coaching that turns your spending patterns into small, honest nudges toward saving more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun LoadingPanel() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Fetching subscription pricing…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComingSoonPanel() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Coming soon",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "We're finalising the subscription tiers. This screen will light up once the plans are live on Google Play — no update needed. In the meantime, PesaTrack's free features remain unchanged.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EntitledPanel(uiState: PesaTrackProUiState) {
    val expiryMs = uiState.currentState.expiresAtEpochMs ?: 0L
    val expiryText = if (expiryMs > 0) {
        val formatted = DateFormat.getMediumDateFormat(LocalContext.current).format(Date(expiryMs))
        if (uiState.currentState.autoRenewing) {
            "Renews on $formatted"
        } else {
            "Access continues until $formatted"
        }
    } else null

    val tierLabel = when (uiState.currentState.tier) {
        ProProduct.MONTHLY -> "Monthly"
        ProProduct.ANNUAL -> "Annual"
        null -> "Active"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "PesaTrack Pro — $tierLabel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            expiryText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = "Manage your subscription (change plan, cancel) directly in the Google Play Store.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun TierCard(
    tier: TierViewData,
    purchaseInFlight: Boolean,
    onSubscribe: () -> Unit,
) {
    val label = when (tier.product) {
        ProProduct.MONTHLY -> "Monthly"
        ProProduct.ANNUAL -> "Annual"
    }
    val cadence = billingPeriodLabel(tier.billingPeriod)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${tier.formattedPrice}$cadence",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            tier.savingsCaption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSubscribe,
                enabled = !purchaseInFlight,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (purchaseInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(20.dp)
                            .height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Subscribe")
                }
            }
        }
    }
}

@Composable
private fun RestoreRow(inFlight: Boolean, onRestore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Already subscribed?",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onRestore, enabled = !inFlight) {
            if (inFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.width(18.dp).height(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Restore purchase")
        }
    }
}

@Composable
private fun FootnoteText() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "About PesaTrack Pro",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Subscriptions renew automatically until cancelled. You can cancel any time in Google Play; you keep access until the end of the paid period. Your transaction data stays on this device — Pro adds AI coaching that summarises trends without ever sending raw SMS or PII off-device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────

/**
 * Map an ISO 8601 billing period (`P1M` / `P1Y`) to a short human suffix
 * appended to the price. Deliberately terse — the tier heading already
 * says "Monthly" / "Annual"; this is the price-line qualifier.
 */
private fun billingPeriodLabel(period: String): String = when (period) {
    "P1M" -> " / month"
    "P1Y" -> " / year"
    "P3M" -> " / 3 months"
    "P6M" -> " / 6 months"
    else -> if (period.isBlank()) "" else " · $period"
}
