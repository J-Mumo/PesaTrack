package com.pesatrack.data.export

import com.pesatrack.domain.models.CategoryMonthGrid
import com.pesatrack.domain.models.GridRow
import java.util.Locale

/**
 * Renders a [CategoryMonthGrid] as a CSV suitable for opening in Excel /
 * Google Sheets. Layout mirrors what the user described they used to keep
 * manually:
 *
 * ```
 * Category,Jan,Feb,Mar,...,Dec,Total
 * Groceries,4500,3800,...,52000
 *   Bread,300,250,...,3200
 *   ...
 * Total,...,...,...,GRAND
 * ```
 *
 * Notes:
 * - Sub-category rows are indented with two spaces so the hierarchy survives
 *   the round-trip into a spreadsheet.
 * - Empty cells render as a blank field (Excel treats blank as "no data",
 *   which is honest — a zero would suggest the user spent nothing). Zero
 *   totals are still emitted as `0`.
 * - The current in-progress period is marked with a `*` in the header (same
 *   marker the on-screen grid shows) so the reader knows that column is
 *   partial data.
 * - Values are written as plain integers (KES has no fractional day-to-day
 *   use in this app); commas inside labels are quoted per RFC 4180.
 */
object CategoryMonthGridCsvExporter {

    fun buildCsv(grid: CategoryMonthGrid): String {
        val sb = StringBuilder()

        // Header row
        sb.append("Category")
        grid.periodLabels.forEachIndexed { i, label ->
            val marked = if (grid.partialPeriodIndexes.contains(i)) "$label*" else label
            sb.append(',').append(csvEscape(marked))
        }
        sb.append(",Total\n")

        // Body rows — respect the order the repository produced (groups
        // followed by their sub-categories).
        grid.rows.forEach { row ->
            sb.append(csvEscape(rowLabel(row)))
            row.monthlyValues.forEach { v ->
                sb.append(',')
                if (v != null) sb.append(formatAmount(v))
            }
            sb.append(',').append(formatAmount(row.yearTotal)).append('\n')
        }

        // Period totals + grand total row
        sb.append("Total")
        grid.periodTotals.forEach { v ->
            sb.append(',').append(formatAmount(v))
        }
        sb.append(',').append(formatAmount(grid.grandTotal)).append('\n')

        if (grid.partialPeriodIndexes.isNotEmpty()) {
            sb.append("# * current period is still in progress\n")
        }
        if (grid.includesFees) {
            sb.append("# Includes transaction fees (category 606)\n")
        } else {
            sb.append("# Transaction fees excluded (see settings)\n")
        }

        return sb.toString()
    }

    private fun rowLabel(row: GridRow): String {
        // Two-space indent per depth level so the hierarchy shows up in the
        // spreadsheet without needing a separate depth column.
        val indent = "  ".repeat(row.depth.coerceAtLeast(0))
        return indent + row.label
    }

    private fun formatAmount(value: Double): String {
        // Whole-KES precision, no thousands separator (so the file re-parses
        // cleanly). Excel will format it however the user prefers on their end.
        return String.format(Locale.US, "%.0f", value)
    }

    private fun csvEscape(raw: String): String {
        val needsQuoting = raw.contains(',') || raw.contains('"') || raw.contains('\n')
        if (!needsQuoting) return raw
        val escaped = raw.replace("\"", "\"\"")
        return "\"" + escaped + "\""
    }
}
