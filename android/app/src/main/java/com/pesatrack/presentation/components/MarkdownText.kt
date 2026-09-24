package com.pesatrack.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Minimal markdown renderer used by the Ask Your Money chat bubble.
 *
 * Supports the subset our backend system prompt is instructed to emit:
 *
 *  - `## Heading` — a bolded section header (never `#`; the prompt
 *    reserves that for the client).
 *  - `- item` and `1. item` — bullet / numbered list rows.
 *  - `**bold**` — inline bold spans inside any paragraph, bullet, cell,
 *    or heading. `*italic*` is intentionally NOT supported — the prompt
 *    reserves italic for our fallback template line rendered elsewhere.
 *  - Standard pipe tables:
 *      | col a | col b |
 *      | ----- | ----- |
 *      | 1     | 2     |
 *    Rendered as a bordered Compose row-per-row layout, horizontally
 *    scrollable on narrow screens.
 *  - Blank lines become paragraph gaps.
 *
 * Everything unrecognised is rendered as plain text. This keeps the
 * renderer dependency-free and small enough to R8-shrink cleanly.
 * Anything richer (nested lists, code blocks, links) lands with a real
 * markdown library in a follow-up if user testing wants it.
 *
 * Contract: pure — no side effects, no state, no coroutines. Same input
 * always produces the same composition tree.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block -> RenderBlock(block, color) }
    }
}

@Composable
private fun RenderBlock(block: MdBlock, color: Color) {
    when (block) {
        is MdBlock.Paragraph -> {
            Text(
                text = renderInline(block.text),
                style = MaterialTheme.typography.bodyLarge,
                color = color,
            )
        }
        is MdBlock.Heading -> {
            Text(
                text = renderInline(block.text),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        is MdBlock.BulletList -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                block.items.forEach { item ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge, color = color)
                        Text(
                            text = renderInline(item),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                        )
                    }
                }
            }
        }
        is MdBlock.NumberedList -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                block.items.forEachIndexed { idx, item ->
                    Row {
                        Text(
                            "${idx + 1}. ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                        )
                        Text(
                            text = renderInline(item),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                        )
                    }
                }
            }
        }
        is MdBlock.Table -> {
            // Horizontal scroll so long tables don't force the chat
            // bubble to expand past the screen edge.
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    // Header row (bold).
                    TableRow(cells = block.header, color = color, bold = true)
                    HorizontalDivider(color = color.copy(alpha = 0.4f), thickness = 1.dp)
                    block.rows.forEach { row ->
                        TableRow(cells = row, color = color, bold = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, color: Color, bold: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cells.forEach { cell ->
            Text(
                text = renderInline(cell),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.width(100.dp),
            )
        }
    }
}

// ── Inline (bold) rendering ─────────────────────────────────────────────

private fun renderInline(text: String): AnnotatedString {
    // Split on ** boundaries; alternate spans are bold, others normal.
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val parts = text.split("**")
    parts.forEachIndexed { idx, part ->
        if (idx % 2 == 1) {
            builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                builder.append(part)
            }
        } else {
            builder.append(part)
        }
    }
    return builder.toAnnotatedString()
}

// ── Block parser ────────────────────────────────────────────────────────

internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val text: String) : MdBlock
    data class BulletList(val items: List<String>) : MdBlock
    data class NumberedList(val items: List<String>) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

/**
 * Split a markdown string into a list of blocks that the composables
 * above know how to render. Kept internal so the tests can exercise it
 * directly.
 *
 * Rules (mirror the system-prompt contract):
 *  - Lines starting with `## ` (or `### ` etc.) → [MdBlock.Heading].
 *  - Consecutive lines starting with `- ` → [MdBlock.BulletList].
 *  - Consecutive lines starting with `N. ` (digit + dot) → [MdBlock.NumberedList].
 *  - A run of lines starting with `|`, sandwiched by `| --- |` → [MdBlock.Table].
 *  - Any other run of non-blank lines → a single [MdBlock.Paragraph]
 *    (joined with spaces so hard-wraps in the model output don't render
 *    as separate paragraphs).
 *  - Blank lines separate blocks.
 */
internal fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = input.trim().split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Skip blank lines.
        if (trimmed.isEmpty()) { i++; continue }

        // Heading.
        val headingMatch = Regex("""^#{1,6}\s+(.+)$""").matchEntire(trimmed)
        if (headingMatch != null) {
            out += MdBlock.Heading(headingMatch.groupValues[1])
            i++
            continue
        }

        // Bullet list.
        if (trimmed.startsWith("- ")) {
            val items = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("- ")) {
                items += lines[i].trim().removePrefix("- ")
                i++
            }
            out += MdBlock.BulletList(items)
            continue
        }

        // Numbered list.
        if (Regex("""^\d+\.\s""").containsMatchIn(trimmed)) {
            val items = mutableListOf<String>()
            while (i < lines.size &&
                Regex("""^\d+\.\s""").containsMatchIn(lines[i].trim())
            ) {
                items += lines[i].trim().replaceFirst(Regex("""^\d+\.\s+"""), "")
                i++
            }
            out += MdBlock.NumberedList(items)
            continue
        }

        // Table — must have at least header + separator + one data row.
        if (trimmed.startsWith("|") && i + 1 < lines.size &&
            lines[i + 1].trim().matches(Regex("""^\|?\s*(:?-{2,}:?\s*\|\s*)+:?-{2,}:?\s*\|?\s*$"""))
        ) {
            val header = splitTableRow(lines[i])
            i += 2 // skip header + separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows += splitTableRow(lines[i])
                i++
            }
            out += MdBlock.Table(header = header, rows = rows)
            continue
        }

        // Paragraph — collect until blank or a recognised block.
        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val ln = lines[i]
            val lnTrim = ln.trim()
            if (lnTrim.isEmpty()) break
            if (lnTrim.startsWith("#") ||
                lnTrim.startsWith("- ") ||
                Regex("""^\d+\.\s""").containsMatchIn(lnTrim) ||
                lnTrim.startsWith("|")
            ) break
            paraLines += lnTrim
            i++
        }
        if (paraLines.isNotEmpty()) out += MdBlock.Paragraph(paraLines.joinToString(" "))
    }
    return out
}

private fun splitTableRow(line: String): List<String> {
    return line.trim()
        .trim('|')
        .split('|')
        .map { it.trim() }
}
