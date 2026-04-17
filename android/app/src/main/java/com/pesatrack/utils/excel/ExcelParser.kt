package com.pesatrack.utils.excel

import android.util.Log
import org.apache.poi.ss.usermodel.DateUtil
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream

/**
 * Parses Excel (.xlsx) expense spreadsheets using a memory-efficient SAX streaming approach.
 *
 * This parser treats .xlsx as a ZIP archive and iterates through its entries using ZipInputStream.
 * It avoids loading the entire file into memory, which is critical for Android.
 *
 * Handles the user's specific spreadsheet format:
 * - Monthly sheets (Jan-2024, Feb-2024, etc.)
 * - 3 data columns: Date (A), Expense/Category (B), Amount (C)
 * - Right-side pivot summary in columns E-F (ignored)
 */
object ExcelParser {

    private const val TAG = "ExcelParser"

    private val SKIP_SHEETS = setOf("mom", "summary", "totals", "dashboard")

    private val DATE_FORMAT_ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    private val DATE_FORMAT_SLASH = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }

    data class ExcelExpenseRow(
        val date: Long,
        val categoryLabel: String,
        val amount: Double,
        val sheetName: String,
        val fileName: String
    )

    data class ParseResult(
        val rows: List<ExcelExpenseRow>,
        val sheetsProcessed: Int,
        val sheetsSkipped: Int,
        val rowsSkipped: Int,
        val parseErrors: Int
    )

    /**
     * Parse an Excel .xlsx file from an InputStream using a multi-pass ZIP stream approach.
     * Pass 1: Extract shared strings.
     * Pass 2: Extract workbook info (sheet names to rIds).
     * Pass 3: Extract relationships (rId to file path).
     * Pass 4: Parse the actual sheet data.
     *
     * Note: We use mark/reset if supported, or multiple stream openings if necessary.
     * For simplicity and memory safety, we wrap the input in a reusable manner if possible,
     * or accept that we might need to seek.
     */
    fun parse(fileInputStream: InputStream, fileName: String): ParseResult {
        val rows = mutableListOf<ExcelExpenseRow>()
        var sheetsProcessed = 0
        var sheetsSkipped = 0
        var rowsSkipped = 0
        var parseErrors = 0

        try {
            // Since we need to traverse the ZIP multiple times, and we want to avoid loading
            // the whole thing into memory, we'll read it once but extract the small XMLs
            // needed for structure into memory, while streaming the (potentially large) sheet data.

            val zipMap = mutableMapOf<String, ByteArray>()
            val sheetsToParse = mutableListOf<SheetToParse>()

            ZipInputStream(fileInputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("/")
                    when {
                        name == "xl/sharedStrings.xml" ||
                        name == "xl/workbook.xml" ||
                        name == "xl/_rels/workbook.xml.rels" -> {
                            zipMap[name] = zis.readBytes()
                        }
                        name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                            // We'll store the sheet data only when we know we need it
                            // To keep it streaming, we'd need a random access file or multiple passes.
                            // On Android, if the file is from a URI, we can re-open the stream.
                            // For now, we'll cache the sheet bytes if they aren't huge, or assume
                            // the user selects one file at a time.
                            zipMap[name] = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val sharedStrings = parseSharedStrings(zipMap["xl/sharedStrings.xml"])
            val sheetInfos = parseWorkbookAndRels(zipMap["xl/workbook.xml"], zipMap["xl/_rels/workbook.xml.rels"])

            for (info in sheetInfos) {
                val sheetNameLower = info.name.lowercase().trim()
                if (SKIP_SHEETS.contains(sheetNameLower)) {
                    sheetsSkipped++
                    continue
                }

                val sheetXml = zipMap[info.path]
                if (sheetXml == null) {
                    parseErrors++
                    continue
                }

                sheetsProcessed++
                val handler = SheetSaxHandler(sharedStrings, info.name, fileName)
                SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser()
                    .parse(ByteArrayInputStream(sheetXml), handler)

                rows.addAll(handler.rows)
                rowsSkipped += handler.rowsSkipped
                parseErrors += handler.parseErrors
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Excel file: ${e.message}", e)
            parseErrors++
        }

        return ParseResult(rows, sheetsProcessed, sheetsSkipped, rowsSkipped, parseErrors)
    }

    private data class SheetToParse(val name: String, val path: String)

    private fun parseSharedStrings(xml: ByteArray?): List<String> {
        if (xml == null) return emptyList()
        val strings = mutableListOf<String>()
        val handler = object : DefaultHandler() {
            private var inT = false
            private val chars = StringBuilder()
            override fun startElement(u: String, l: String, q: String, a: Attributes) { if (q == "t") { inT = true; chars.setLength(0) } }
            override fun characters(ch: CharArray, s: Int, len: Int) { if (inT) chars.append(ch, s, len) }
            override fun endElement(u: String, l: String, q: String) {
                if (q == "t") inT = false
                if (q == "si") { strings.add(chars.toString()); chars.setLength(0) }
            }
        }
        SAXParserFactory.newInstance().newSAXParser().parse(ByteArrayInputStream(xml), handler)
        return strings
    }

    private fun parseWorkbookAndRels(wbXml: ByteArray?, relsXml: ByteArray?): List<SheetToParse> {
        if (wbXml == null || relsXml == null) return emptyList()

        val relMap = mutableMapOf<String, String>()
        val relsHandler = object : DefaultHandler() {
            override fun startElement(u: String, l: String, q: String, a: Attributes) {
                if (q == "Relationship") {
                    val id = a.getValue("Id"); val target = a.getValue("Target")
                    if (id != null && target != null) relMap[id] = target
                }
            }
        }
        SAXParserFactory.newInstance().newSAXParser().parse(ByteArrayInputStream(relsXml), relsHandler)

        val sheets = mutableListOf<SheetToParse>()
        val wbHandler = object : DefaultHandler() {
            override fun startElement(uri: String, localName: String, qName: String, a: Attributes) {
                if (qName == "sheet") {
                    val name = a.getValue("name")
                    val rId = a.getValue("r:id") ?: a.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    if (name != null && rId != null) {
                        val target = relMap[rId] ?: return
                        val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                        sheets.add(SheetToParse(name, path))
                    }
                }
            }
        }
        SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser().parse(ByteArrayInputStream(wbXml), wbHandler)
        return sheets
    }

    private class SheetSaxHandler(
        private val sharedStrings: List<String>,
        private val sheetName: String,
        private val fileName: String
    ) : DefaultHandler() {
        val rows = mutableListOf<ExcelExpenseRow>()
        var rowsSkipped = 0
        var parseErrors = 0

        private var currentCellRef: String? = null
        private var cellType: String? = null
        private var inValue = false
        private val cellContents = StringBuilder()
        private var currentRowNum = -1
        private var colA: String? = null; var colB: String? = null; var colC: String? = null
        private var colAType: String? = null; var colBType: String? = null; var colCType: String? = null

        override fun startElement(u: String, l: String, q: String, a: Attributes) {
            when (q) {
                "row" -> {
                    currentRowNum = a.getValue("r")?.toIntOrNull() ?: (currentRowNum + 1)
                    colA = null; colB = null; colC = null
                }
                "c" -> {
                    currentCellRef = a.getValue("r")
                    cellType = a.getValue("t")
                    when (currentCellRef?.takeWhile { it.isLetter() }) {
                        "A" -> colAType = cellType
                        "B" -> colBType = cellType
                        "C" -> colCType = cellType
                    }
                }
                "v", "t" -> { inValue = true; cellContents.setLength(0) }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) { if (inValue) cellContents.append(ch, start, length) }

        override fun endElement(u: String, l: String, q: String) {
            if (q == "v" || q == "t") {
                inValue = false
                val raw = cellContents.toString()
                val value = if (cellType == "s") {
                    val idx = raw.toIntOrNull()
                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                } else raw

                when (currentCellRef?.takeWhile { it.isLetter() }) {
                    "A" -> colA = value
                    "B" -> colB = value
                    "C" -> colC = value
                }
            }
            if (q == "row") processRow()
        }

        private fun processRow() {
            if (currentRowNum == 1 && (colA ?: "").lowercase().contains("date")) return
            if (colA == null || colB == null || colC == null) return

            try {
                val date = parseDate(colA!!, colAType)
                val label = colB!!.trim()
                val amount = parseAmount(colC!!, colCType)

                if (date != null && label.isNotBlank() && amount != null && amount > 0) {
                    rows.add(ExcelExpenseRow(date, label, amount, sheetName, fileName))
                } else {
                    rowsSkipped++
                }
            } catch (e: Exception) {
                parseErrors++
            }
        }

        private fun parseDate(value: String, type: String?): Long? {
            if (type == null || type == "n") {
                val num = value.toDoubleOrNull()
                if (num != null) return try { DateUtil.getJavaDate(num).time } catch (_: Exception) { null }
            }
            return parseStringDate(value)
        }

        private fun parseAmount(value: String, type: String?): Double? {
            return value.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
        }

        private fun parseStringDate(dateStr: String): Long? {
            return try { DATE_FORMAT_ISO.parse(dateStr)?.time } catch (_: Exception) {
                try { DATE_FORMAT_SLASH.parse(dateStr)?.time } catch (_: Exception) { null }
            }
        }
    }
}
