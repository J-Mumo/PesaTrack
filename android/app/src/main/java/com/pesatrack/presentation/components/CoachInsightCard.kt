package com.pesatrack.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pesatrack.services.ai.CoachInsight

/**
 * Home-screen card that renders one AI-generated Coach Insight for the
 * current day. Composable is fully driven by an already-rehydrated
 * [CoachInsight] — no ViewModel or repository access here; the
 * rehydration + fetch-orchestration happen in
 * [com.pesatrack.services.ai.CoachInsightRepository] before this card is
 * ever wired into state.
 *
 * ---
 *
 * ## Layout (top → bottom)
 *
 *  1. Header row — `⚡ Coach insight` label in a small caption style.
 *     Signals "AI-generated" without leaning on the word "AI" (per
 *     product guidelines: describe capability, not tech).
 *  2. Title — 1-line bold headline. The model produces 8..60 chars;
 *     Compose wraps overflow with `TextOverflow.Visible` so we never
 *     truncate the point.
 *  3. Body — 2..3 sentence paragraph, 40..400 chars.
 *  4. Saveable pill (optional) — small tonal chip reading
 *     "Could save ~ KES X". Only rendered when
 *     [CoachInsight.saveableAmountKes] is non-null; the plan §2 says
 *     saveable numbers **must** ship with assumptions, so this pill
 *     never appears alone — the assumptions expander below is guaranteed
 *     to have content.
 *  5. Action button (optional) — verb-first CTA. Only when both
 *     [CoachInsight.actionLabel] and [CoachInsight.actionDeeplink] are
 *     non-null (server-side soft-fix nulls both when the deep-link
 *     target isn't in the digest).
 *  6. Assumptions expander (optional) — starts collapsed as
 *     "Show assumptions" TextButton. Tap to reveal the model's list of
 *     what it took as given. This surfaces the AGENTS.md "honest
 *     numbers" principle: no projection without visible assumptions.
 *
 * ## Copy discipline (enforced upstream in the LLM prompt + deny-list)
 *
 *  - Neutral framing. "You spent KES 12,400 on Food" — not "You
 *    overspent".
 *  - KES with thousands separator.
 *  - No shame, no fear framing, no guaranteed-returns claims.
 *
 * See plans/ai-pro-phase2-spec.md §2 (UX) and §4 (schema).
 */
@Composable
fun CoachInsightCard(
    insight: CoachInsight,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAssumptionsExpanded: () -> Unit = {},
) {
    var assumptionsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Header ─────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Coach insight",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }

            // ── Title ──────────────────────────────────────────────
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            // ── Body ───────────────────────────────────────────────
            Text(
                text = insight.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            // ── Saveable pill (optional) ───────────────────────────
            insight.saveableAmountKes?.let { amount ->
                SaveablePill(amount = amount)
            }

            // ── Action button (optional) ───────────────────────────
            val actionLabel = insight.actionLabel
            val actionDeeplink = insight.actionDeeplink
            if (!actionLabel.isNullOrBlank() && !actionDeeplink.isNullOrBlank()) {
                TextButton(
                    onClick = onActionClick,
                    modifier = Modifier.padding(top = 0.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = actionLabel)
                }
            }

            // ── Assumptions expander (optional) ────────────────────
            if (insight.assumptions.isNotEmpty()) {
                AssumptionsExpander(
                    assumptions = insight.assumptions,
                    expanded = assumptionsExpanded,
                    onToggle = {
                        val next = !assumptionsExpanded
                        assumptionsExpanded = next
                        // Only log the expand direction — the collapse is a
                        // trivial UX undo and doesn't answer any product
                        // question worth a Firebase event.
                        if (next) onAssumptionsExpanded()
                    },
                )
            }
        }
    }
}

/**
 * Small tonal chip that surfaces the model's estimated saveable amount
 * as a positive-framed opportunity. The word "could" is deliberate —
 * this is illustration, not guarantee, per AGENTS.md "honest numbers".
 */
@Composable
private fun SaveablePill(amount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "Could save ~ KES ${String.format("%,d", amount)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Tap-to-expand block listing the model's stated assumptions. Collapsed
 * state is a compact TextButton; expanded state is a bulleted column.
 * Surfacing this is the whole point of the "honest numbers" principle —
 * no projection without visible assumptions.
 */
@Composable
private fun AssumptionsExpander(
    assumptions: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = if (expanded) "Hide assumptions" else "Show assumptions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                assumptions.forEach { assumption ->
                    Text(
                        text = "• $assumption",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
