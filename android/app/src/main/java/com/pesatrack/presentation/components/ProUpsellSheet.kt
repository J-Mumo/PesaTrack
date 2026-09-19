package com.pesatrack.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * PesaTrack Pro upsell bottom sheet. Slice A5b ships the composable; no
 * caller wires it yet because Phase 1 doesn't gate any features behind
 * Pro. Phase 2 (AI Coach) will show this from the coach entry-point
 * when the user isn't entitled.
 *
 * Copy discipline (AGENTS.md "nudge, don't nag"):
 *   - Neutral framing — the sheet describes what Pro adds, no
 *     fear-based hooks and no "you're missing out" language.
 *   - Dismissible primary — [onDismiss] is called from every path,
 *     including the "Learn more" TextButton, so the user is never
 *     trapped.
 *   - No claims about specific savings numbers — plans/product-principles
 *     forbid savings figures that aren't actually observable.
 *
 * See plans/ai-pro-phase1-spec.md §3.1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProUpsellSheet(
    onLearnMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "This one's part of PesaTrack Pro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "AI coaching turns your spending patterns into small, honest observations you can act on — or ignore. Your transactions never leave the device; only anonymised summaries do.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(4.dp))

            Button(onClick = onLearnMore, modifier = Modifier.fillMaxWidth()) {
                Text("See the plans")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Maybe later")
            }
        }
    }
}
