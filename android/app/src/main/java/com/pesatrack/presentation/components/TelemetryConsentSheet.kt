package com.pesatrack.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One-time consent sheet for anonymous usage analytics (Phase 1 telemetry).
 *
 * UX principles applied here (see AGENTS.md — "Nudge, don't nag" and
 * plans/product-principles.md):
 *  - Framed as an **invitation to help**, not a scary permission request.
 *    The primary CTA is a benefit ("Help make PesaTrack better"), not a
 *    consent verb ("Allow").
 *  - Concrete, honest bullets on what is / isn't collected. The
 *    "never sent" list is longer than the "sent" list — because that's true.
 *  - Both buttons close the sheet permanently. Users are not nagged again;
 *    the toggle in Settings is the escape hatch either way.
 *  - Secondary "Not now" is a plain text button, not a warning-styled
 *    negative action. Declining should feel low-friction, not shameful.
 *
 * Emits the appropriate telemetry events (prompt shown / accepted /
 * dismissed) via the callbacks, which the caller wires into
 * [com.pesatrack.services.telemetry.TelemetryClient].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryConsentSheet(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            // Icon medallion
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Help make PesaTrack better",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Share anonymous usage data so we know which " +
                    "features actually help — and which to fix.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(20.dp))

            // What IS collected
            InfoCard(
                icon = Icons.Filled.Check,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "What we collect",
                lines = listOf(
                    "Which screens you open",
                    "Which features you tap",
                    "App version and crash signals",
                ),
            )

            Spacer(Modifier.height(12.dp))

            // What is NEVER collected
            InfoCard(
                icon = Icons.Filled.Shield,
                iconTint = MaterialTheme.colorScheme.tertiary,
                title = "What never leaves your phone",
                lines = listOf(
                    "Your SMS or transaction messages",
                    "Amounts, merchants, or account numbers",
                    "Your name, phone number, or email",
                ),
            )

            Spacer(Modifier.height(20.dp))

            // Primary CTA — designed to be the obvious choice.
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Yes, help improve PesaTrack",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = "Not now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Reassurance footer + policy link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "You can change this any time in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(PRIVACY_POLICY_URL),
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = "Read the privacy policy",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    lines: List<String>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            lines.forEach { line ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "•  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val PRIVACY_POLICY_URL =
    "https://j-mumo.github.io/PesaTrack/privacy-policy.html"
