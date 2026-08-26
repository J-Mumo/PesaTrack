package com.pesatrack.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesatrack.domain.models.GroupTrendPreview
import com.pesatrack.domain.models.GroupTrendRow
import com.pesatrack.domain.models.TrendDirection
import com.pesatrack.utils.formatAsCurrency

/**
 * Compact 3-column preview of the top-N groups over the last few periods,
 * shown on Home directly under the "By Category" section. Mirrors the
 * Category × Month Grid in Analytics → Yearly. Investment & Savings
 * direction is semantically inverted (up = good) per the AGENTS.md
 * "save/invest by default" principle.
 */
@Composable
fun GroupTrendPreviewCard(preview: GroupTrendPreview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Group",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.4f)
                )
                preview.periodLabels.forEachIndexed { i, label ->
                    val shown = if (i == preview.periodLabels.lastIndex && preview.currentPeriodIsPartial) "$label*" else label
                    Text(
                        text = shown,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "Δ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.5f)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            preview.rows.forEach { row ->
                GroupTrendRowView(row = row)
            }

            if (preview.currentPeriodIsPartial) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "* current period is still in progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GroupTrendRowView(row: GroupTrendRow) {
    val swatchColor = row.color?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (swatchColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        row.amounts.forEach { v ->
            Text(
                text = if (v == null) "—" else v.formatAsCurrency(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                color = if (v == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        val (glyph, tint) = trendGlyphAndTint(row.direction, row.isInvestment)
        Text(
            text = glyph,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = tint,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.5f)
        )
    }
}

@Composable
private fun trendGlyphAndTint(
    direction: TrendDirection,
    isInvestment: Boolean
): Pair<String, Color> {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    // Investment: UP is good. Spending: UP is bad.
    return when (direction) {
        TrendDirection.UP2 -> "▲▲" to if (isInvestment) positive else negative
        TrendDirection.UP -> "▲" to if (isInvestment) positive else negative
        TrendDirection.FLAT -> "•" to neutral
        TrendDirection.DOWN -> "▼" to if (isInvestment) negative else positive
        TrendDirection.DOWN2 -> "▼▼" to if (isInvestment) negative else positive
        TrendDirection.INSUFFICIENT -> "—" to neutral
    }
}
