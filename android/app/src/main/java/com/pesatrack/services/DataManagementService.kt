package com.pesatrack.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.pesatrack.data.local.database.PesaTrackDatabase
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.CategoryRuleDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.entities.DefaultCategories
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.utils.UsageSummaryGenerator
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for data management operations:
 * - Reset categories to defaults (removes custom categories + all rules)
 * - Export expenses to CSV (write to cache dir + share via Android share sheet)
 * - Backup database to raw .db file via SAF (settings embedded in metadata table)
 * - Restore database from raw .db file via SAF
 */
@Singleton
class DataManagementService @Inject constructor(
    private val database: PesaTrackDatabase,
    private val appPreferences: AppPreferences,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val categoryRuleDao: CategoryRuleDao,
    private val usageSummaryGenerator: UsageSummaryGenerator
) {

    companion object {
        private const val TAG = "DataManagementService"
        private const val DB_NAME = "pesatrack_database"
        private const val METADATA_TABLE = "_backup_metadata"
    }

    /**
     * Reset categories to default state:
     * 1. Delete all custom (non-default) categories — FK ON DELETE SET_NULL
     *    will set affected expenses' categoryId to null (uncategorized)
     * 2. Delete all user-defined auto-categorization rules
     * 3. Re-insert default categories (REPLACE strategy — no-op for existing)
     *
     * @return number of custom categories that were deleted
     */
    suspend fun resetCategoriesToDefault(): Int {
        // Count custom categories before deleting
        val allCount = categoryDao.getCategoryCount()
        val defaultCategories = DefaultCategories.categories

        // Step 1: Delete all custom categories
        categoryDao.deleteAllCustom()

        // Step 2: Delete all auto-categorization rules
        categoryRuleDao.deleteAll()

        // Step 3: Re-insert default categories (ensures they all exist)
        categoryDao.insertAll(defaultCategories)

        val afterCount = categoryDao.getCategoryCount()
        return (allCount - afterCount).coerceAtLeast(0)
    }

    /**
     * Export all expenses to a CSV file in the app's cache directory.
     *
     * @param context Android context for accessing cache directory
     * @return the File pointing to the generated CSV, or null on failure
     */
    suspend fun exportExpensesToCsv(context: Context): File? {
        return try {
            val expenses = expenseDao.getAllExpensesForExport()
            if (expenses.isEmpty()) return null

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val fileName = "PesaTrack_Export_${fileNameFormat.format(Date())}.csv"

            val exportDir = File(context.cacheDir, "exports")
            exportDir.mkdirs()
            val csvFile = File(exportDir, fileName)

            csvFile.bufferedWriter().use { writer ->
                // CSV header
                writer.write("Date,Amount (KES),Recipient,Category,Group,Payment Type,Transaction ID,Source,Notes,Excluded")
                writer.newLine()

                // CSV rows
                for (expense in expenses) {
                    val date = dateFormat.format(Date(expense.timestamp))
                    val amount = "%.2f".format(expense.amount)
                    val recipient = escapeCsv(expense.recipientName ?: expense.recipient)
                    val category = escapeCsv(expense.categoryName)
                    val group = escapeCsv(expense.groupName)
                    val paymentType = escapeCsv(expense.paymentType)
                    val txnId = escapeCsv(expense.transactionId ?: "")
                    val source = escapeCsv(expense.source)
                    val notes = escapeCsv(expense.notes ?: "")
                    val excluded = if (expense.isExcluded) "Yes" else "No"

                    writer.write("$date,$amount,$recipient,$category,$group,$paymentType,$txnId,$source,$notes,$excluded")
                    writer.newLine()
                }
            }

            // Track export counter (fire-and-forget — already in suspend context)
            appPreferences.incrementExportsCount()

            csvFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Create an Android share intent for the given CSV file.
     *
     * @param context Android context
     * @param csvFile the CSV file to share
     * @return a chooser intent ready to start
     */
    fun createShareIntent(context: Context, csvFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PesaTrack Expense Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, "Export PesaTrack Data")
    }

    // ==================== Database Backup ====================

    /**
     * Backup the Room database as a raw .db file via SAF.
     *
     * Before copying, embeds user settings (month start day, bank toggles) into a
     * temporary _backup_metadata table inside the database so everything is in one file.
     *
     * Flow:
     * 1. Write settings into _backup_metadata table
     * 2. WAL checkpoint to flush writes into the main .db file
     * 3. Copy the .db file to a temp location in cache
     * 4. Drop _backup_metadata from the live database (cleanup)
     * 5. Copy the temp file to the SAF destination URI
     *
     * @param context Android context for accessing database path
     * @param destinationUri SAF URI chosen by user (e.g. Downloads, Google Drive)
     * @return true if backup succeeded
     */
    suspend fun backupDatabase(context: Context, destinationUri: Uri): Boolean {
        return try {
            val db = database.openHelper.writableDatabase

            // 1. Create metadata table and write settings
            db.execSQL("CREATE TABLE IF NOT EXISTS $METADATA_TABLE (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("DELETE FROM $METADATA_TABLE")

            val monthStartDay = appPreferences.getMonthStartDay()
            val bankTrackingEnabled = appPreferences.bankTrackingEnabled.first()
            val enabledBanks = appPreferences.enabledBanks.first()

            db.execSQL("INSERT INTO $METADATA_TABLE (key, value) VALUES ('monthStartDay', '$monthStartDay')")
            db.execSQL("INSERT INTO $METADATA_TABLE (key, value) VALUES ('bankTrackingEnabled', '$bankTrackingEnabled')")
            db.execSQL("INSERT INTO $METADATA_TABLE (key, value) VALUES ('enabledBanks', '${JSONArray(enabledBanks.toList())}')")
            val usageMetricsJson = usageSummaryGenerator.asJson().toString().replace("'", "''")
            db.execSQL("INSERT INTO $METADATA_TABLE (key, value) VALUES ('usageMetrics', '$usageMetricsJson')")
            Log.d(TAG, "Settings written to metadata table: monthStartDay=$monthStartDay, bankTrackingEnabled=$bankTrackingEnabled, enabledBanks=$enabledBanks")

            // 2. WAL checkpoint to merge all writes into the main .db file
            // Note: PRAGMA statements that return results (like wal_checkpoint) must use query() instead of execSQL()
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                cursor.moveToFirst()
            }
            Log.d(TAG, "WAL checkpoint completed")

            // 3. Get the database file and verify it exists
            val dbFile = context.getDatabasePath(DB_NAME)
            Log.d(TAG, "Database path: ${dbFile.absolutePath}, exists: ${dbFile.exists()}, size: ${dbFile.length()} bytes")

            if (!dbFile.exists() || dbFile.length() == 0L) {
                Log.e(TAG, "Backup failed: database file does not exist or is empty")
                // Cleanup metadata table
                db.execSQL("DROP TABLE IF EXISTS $METADATA_TABLE")
                return false
            }

            // 4. Copy db to temp file in cache (so we have a stable snapshot)
            val tempFile = File(context.cacheDir, "backup_temp.db")
            tempFile.delete()
            dbFile.copyTo(tempFile, overwrite = true)
            Log.d(TAG, "Temp backup file: ${tempFile.length()} bytes")

            // 5. Drop metadata table from live database (cleanup — we don't want it lingering)
            db.execSQL("DROP TABLE IF EXISTS $METADATA_TABLE")

            // 6. Copy temp file to SAF destination
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                tempFile.inputStream().use { input ->
                    val bytesCopied = input.copyTo(output)
                    Log.d(TAG, "Copied $bytesCopied bytes to SAF destination")
                }
                output.flush()
            } ?: run {
                Log.e(TAG, "Backup failed: could not open output stream for $destinationUri")
                tempFile.delete()
                return false
            }

            tempFile.delete()
            Log.i(TAG, "Backup completed successfully to $destinationUri (raw .db)")

            // Track backup counter (fire-and-forget — already in suspend context)
            appPreferences.incrementBackupsCount()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            // Cleanup metadata table on error
            try { database.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS $METADATA_TABLE") } catch (_: Exception) {}
            false
        }
    }

    // ==================== Database Restore ====================

    /**
     * Restore the Room database from a raw .db backup file.
     *
     * Flow:
     * 1. Copy the backup file from SAF URI to temp location
     * 2. Validate it's a real SQLite database (header check)
     * 3. Extract settings from _backup_metadata table (if present)
     * 4. Close the current database connection
     * 5. Replace the database files (delete WAL/SHM, copy backup .db)
     * 6. Restore settings to DataStore
     * 7. Return true — caller is responsible for restarting the app process
     *
     * @param context Android context for accessing database path
     * @param sourceUri SAF URI of the backup .db file
     * @return true if restore succeeded (caller must restart app)
     */
    suspend fun restoreDatabase(context: Context, sourceUri: Uri): Boolean {
        val tempFile = File(context.cacheDir, "restore_temp.db")
        return try {
            tempFile.delete()

            // 1. Copy the backup file to a temp location
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                Log.e(TAG, "Restore failed: could not open input stream for URI $sourceUri")
                return false
            }

            Log.d(TAG, "Backup file copied to temp: ${tempFile.length()} bytes")

            // 2. Validate it's a real SQLite database
            if (!isValidSqliteFile(tempFile)) {
                Log.e(TAG, "Restore failed: file is not a valid SQLite database")
                tempFile.delete()
                return false
            }

            // 3. Extract settings from _backup_metadata table (if present)
            val settings = extractMetadataSettings(tempFile)
            Log.d(TAG, "Extracted settings: $settings")

            // 4. Close the current database connection
            database.close()

            // 5. Replace the database files
            val dbPath = context.getDatabasePath(DB_NAME)
            val walFile = File(dbPath.path + "-wal")
            val shmFile = File(dbPath.path + "-shm")

            walFile.delete()
            shmFile.delete()

            tempFile.copyTo(dbPath, overwrite = true)
            Log.d(TAG, "Database file replaced at: ${dbPath.absolutePath} (${dbPath.length()} bytes)")

            // 6. Restore settings to DataStore
            if (settings != null) {
                restoreSettingsFromMap(settings)
            }

            // 7. Cleanup
            tempFile.delete()

            Log.i(TAG, "Database restore completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            tempFile.delete()
            false
        }
    }

    /**
     * Open a SQLite database file and read the _backup_metadata table.
     * Returns a map of key→value, or null if the table doesn't exist.
     */
    private fun extractMetadataSettings(dbFile: File): Map<String, String>? {
        return try {
            val sqliteDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            )
            val settings = mutableMapOf<String, String>()
            try {
                val cursor = sqliteDb.rawQuery("SELECT key, value FROM $METADATA_TABLE", null)
                cursor.use {
                    while (it.moveToNext()) {
                        settings[it.getString(0)] = it.getString(1)
                    }
                }
                // Drop metadata table so it doesn't linger in the restored database
                sqliteDb.execSQL("DROP TABLE IF EXISTS $METADATA_TABLE")
                Log.d(TAG, "Metadata extracted and table dropped: $settings")
            } finally {
                sqliteDb.close()
            }
            if (settings.isNotEmpty()) settings else null
        } catch (e: Exception) {
            Log.w(TAG, "No metadata table found (non-fatal): ${e.message}")
            null
        }
    }

    /**
     * Restore settings from the metadata map to DataStore.
     */
    private suspend fun restoreSettingsFromMap(settings: Map<String, String>) {
        try {
            settings["monthStartDay"]?.toIntOrNull()?.let {
                appPreferences.setMonthStartDay(it)
                Log.d(TAG, "Restored monthStartDay=$it")
            }
            settings["bankTrackingEnabled"]?.toBooleanStrictOrNull()?.let {
                appPreferences.setBankTrackingEnabled(it)
                Log.d(TAG, "Restored bankTrackingEnabled=$it")
            }
            settings["enabledBanks"]?.let { banksStr ->
                try {
                    val banksArray = JSONArray(banksStr)
                    for (i in 0 until banksArray.length()) {
                        appPreferences.setBankEnabled(banksArray.getString(i), true)
                    }
                    Log.d(TAG, "Restored enabledBanks: $banksStr")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse enabledBanks: ${e.message}")
                }
            }
            Log.i(TAG, "Settings restored from backup metadata")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore settings (non-fatal): ${e.message}")
        }
    }

    /**
     * Validate that a file is a valid SQLite database by checking the magic header bytes.
     * SQLite files start with the 16-byte string "SQLite format 3\000".
     */
    private fun isValidSqliteFile(file: File): Boolean {
        if (file.length() < 100) return false
        return try {
            val header = ByteArray(16)
            file.inputStream().use { it.read(header) }
            val headerStr = String(header, 0, 15)
            val valid = headerStr == "SQLite format 3"
            Log.d(TAG, "SQLite validation for ${file.name}: header='$headerStr', valid=$valid, size=${file.length()}")
            valid
        } catch (e: Exception) {
            Log.w(TAG, "SQLite validation failed for ${file.name}: ${e.message}")
            false
        }
    }

    /**
     * Escape a CSV field value (wrap in quotes if it contains comma, quote, or newline).
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
