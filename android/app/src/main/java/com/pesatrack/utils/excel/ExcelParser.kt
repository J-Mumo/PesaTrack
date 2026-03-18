package com.pesatrack.utils.excel

import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parses Excel (.xlsx) expense spreadsheets into structured data.
 *
 * Handles the user's specific spreadsheet format:
 * - Monthly sheets (Jan-2024, Feb-2024, etc.)
 * - 3 data columns: Date (A), Expense/Category (B), Amount (C)
 * - Right-side pivot summary in columns E-F (ignored)
 * - Mixed date formats: yyyy-MM-dd and dd/MM/yyyy
 * - MoM summary sheet (skipped)
 */
object ExcelParser {

    private const val TAG = "ExcelParser"

    /** Sheets to skip (summary/non-data sheets) */
    private val SKIP_SHEETS = setOf("mom", "summary", "totals", "dashboard")

    /** Date formatters for the two known formats */
    private val DATE_FORMAT_ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }
    private val DATE_FORMAT_SLASH = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
        isLenient = false
    }

    /**
     * Parsed row from Excel spreadsheet.
     */
    data class ExcelExpenseRow(
        /** Transaction date as epoch millis */
        val date: Long,
        /** Raw category label from Excel (e.g. "Food", "Seed") */
        val categoryLabel: String,
        /** Amount in KES */
        val amount: Double,
        /** Source sheet name (e.g. "Jan-2024") */
        val sheetName: String,
        /** Source filename (for multi-file support) */
        val fileName: String
    )

    /**
     * Result of parsing one or more Excel files.
     */
    data class ParseResult(
        val rows: List<ExcelExpenseRow>,
        val sheetsProcessed: Int,
        val sheetsSkipped: Int,
        val rowsSkipped: Int,
        val parseErrors: Int
    )

    /**
     * Parse an Excel .xlsx file from an InputStream.
     *
     * @param inputStream InputStream from Android SAF file picker
     * @param fileName Display name of the file (for logging and transaction IDs)
     * @return ParseResult with all valid expense rows
     */
    fun parse(inputStream: InputStream, fileName: String): ParseResult {
        val rows = mutableListOf<ExcelExpenseRow>()
        var sheetsProcessed = 0
        var sheetsSkipped = 0
        var rowsSkipped = 0
        var parseErrors = 0

        try {
            val workbook: Workbook = XSSFWorkbook(inputStream)

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val sheetName = sheet.sheetName.trim()

                // Skip summary sheets
                if (SKIP_SHEETS.contains(sheetName.lowercase())) {
                    Log.d(TAG, "Skipping summary sheet: $sheetName")
                    sheetsSkipped++
                    continue
                }

                Log.d(TAG, "Processing sheet: $sheetName (${sheet.lastRowNum + 1} rows)")
                sheetsProcessed++

                for (rowIndex in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    // Skip header row (first row of each sheet)
                    if (rowIndex == 0) {
                        val firstCell = getCellStringValue(row.getCell(0))
                        if (firstCell.lowercase().contains("date")) {
                            continue
                        }
                    }

                    try {
                        val parsed = parseRow(row, sheetName, fileName)
                        if (parsed != null) {
                            rows.add(parsed)
                        } else {
                            rowsSkipped++
                        }
                    } catch (e: Exception) {
                        parseErrors++
                        Log.w(TAG, "Error parsing row $rowIndex in $sheetName: ${e.message}")
                    }
                }
            }

            workbook.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening workbook: ${e.message}", e)
            parseErrors++
        }

        Log.d(TAG, "Parse complete: ${rows.size} rows from $sheetsProcessed sheets " +
                "($rowsSkipped skipped, $parseErrors errors)")

        return ParseResult(
            rows = rows,
            sheetsProcessed = sheetsProcessed,
            sheetsSkipped = sheetsSkipped,
            rowsSkipped = rowsSkipped,
            parseErrors = parseErrors
        )
    }

    /**
     * Parse a single row from the spreadsheet.
     *
     * Expected columns: A=Date, B=Expense/Category, C=Amount
     * Columns D+ are ignored (pivot summary area).
     *
     * @return ExcelExpenseRow or null if row is empty/invalid
     */
    private fun parseRow(row: Row, sheetName: String, fileName: String): ExcelExpenseRow? {
        // Column A: Date
        val dateCell = row.getCell(0) ?: return null
        val date = parseDateCell(dateCell) ?: return null

        // Column B: Category label
        val labelCell = row.getCell(1) ?: return null
        val label = getCellStringValue(labelCell).trim()
        if (label.isBlank()) return null

        // Column C: Amount
        val amountCell = row.getCell(2) ?: return null
        val amount = parseAmountCell(amountCell) ?: return null
        if (amount <= 0) return null

        return ExcelExpenseRow(
            date = date,
            categoryLabel = label,
            amount = amount,
            sheetName = sheetName,
            fileName = fileName
        )
    }

    /**
     * Parse a date cell that may contain:
     * - An Excel date value (numeric)
     * - A string in yyyy-MM-dd format
     * - A string in dd/MM/yyyy format
     *
     * @return Epoch millis or null if unparseable
     */
    private fun parseDateCell(cell: Cell): Long? {
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue?.time
                } else {
                    // Might be a date stored as number — try Excel serial date
                    try {
                        val date = DateUtil.getJavaDate(cell.numericCellValue)
                        date.time
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            CellType.STRING -> {
                parseStringDate(cell.stringCellValue.trim())
            }
            else -> null
        }
    }

    /**
     * Try to parse a date string in multiple formats.
     */
    private fun parseStringDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null

        // Try yyyy-MM-dd first
        try {
            val date = DATE_FORMAT_ISO.parse(dateStr)
            if (date != null) return date.time
        } catch (_: Exception) {}

        // Try dd/MM/yyyy
        try {
            val date = DATE_FORMAT_SLASH.parse(dateStr)
            if (date != null) return date.time
        } catch (_: Exception) {}

        Log.w(TAG, "Unparseable date: '$dateStr'")
        return null
    }

    /**
     * Parse an amount cell that may be numeric or string.
     */
    private fun parseAmountCell(cell: Cell): Double? {
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> {
                cell.stringCellValue.trim()
                    .replace(",", "")
                    .replace("KES", "")
                    .replace("Ksh", "")
                    .trim()
                    .toDoubleOrNull()
            }
            CellType.FORMULA -> {
                try {
                    cell.numericCellValue
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * Get string value from a cell regardless of type.
     */
    private fun getCellStringValue(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    DATE_FORMAT_ISO.format(cell.dateCellValue)
                } else {
                    cell.numericCellValue.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (_: Exception) {
                    try {
                        cell.numericCellValue.toString()
                    } catch (_: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }
}
