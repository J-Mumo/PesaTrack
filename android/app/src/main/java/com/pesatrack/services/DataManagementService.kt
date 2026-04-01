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
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for data management operations:
 * - Reset categories to defaults (removes custom categories + all rules)
 * - Export expenses to CSV (write to cache dir + share via Android share sheet)
 * - Backup database + settings to .zip archive via SAF
 * - Restore database + settings from .zip archive via SAF
 */
@Singleton
class DataManagementService @Inject constructor(
    private val database: PesaTrackDatabase,
    private val appPreferences: AppPreferences,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val categoryRuleDao: CategoryRuleDao
) {

    companion object {
        private const val TAG = "DataManagementService"
        private const val DB_NAME = "pesatrack_database"
        private const val BACKUP_DB_ENTRY = "pesatrack_database.db"
        private const val BACKUP_SETTINGS_ENTRY = "settings.json"
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
     * Backup the Room database + selected preferences to a .zip archive.
     *
     * The .zip contains:
     * - pesatrack_database.db — the complete Room database
     * - settings.json — month start day, bank tracking preferences
     *
     * @param context Android context for accessing database path
     * @param destinationUri SAF URI chosen by user (e.g. Downloads, Google Drive)
     * @return true if backup succeeded
     */
    suspend fun backupDatabase(context: Context, destinationUri: Uri): Boolean {
        return try {
            // 1. Checkpoint WAL to merge all pending writes into the main .db file
            database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")

            // 2. Read settings to include in backup
            val monthStartDay = appPreferences.getMonthStartDay()
            val bankTrackingEnabled = appPreferences.bankTrackingEnabled.first()
            val enabledBanks = appPreferences.enabledBanks.first()

            val settingsJson = JSONObject().apply {
                put("monthStartDay", monthStartDay)
                put("bankTrackingEnabled", bankTrackingEnabled)
                put("enabledBanks", JSONArray(enabledBanks.toList()))
            }

            // 3. Create .zip in cache dir
            val dbFile = context.getDatabasePath(DB_NAME)
            val zipFile = File(context.cacheDir, "backup_temp.zip")

            ZipOutputStream(zipFile.outputStream()).use { zip ->
                // Add database file
                zip.putNextEntry(ZipEntry(BACKUP_DB_ENTRY))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()

                // Add settings JSON
                zip.putNextEntry(ZipEntry(BACKUP_SETTINGS_ENTRY))
                zip.write(settingsJson.toString(2).toByteArray())
                zip.closeEntry()
            }

            // 4. Copy .zip to SAF destination
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                zipFile.inputStream().use { input -> input.copyTo(output) }
            } ?: run {
                zipFile.delete()
                return false
            }

            zipFile.delete()
            Log.i(TAG, "Backup completed successfully to $destinationUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            false
        }
    }

    // ==================== Database Restore ====================

    /**
     * Restore the Room database + settings from a .zip backup archive.
     *
     * Flow:
     * 1. Extract .zip to temp directory
     * 2. Validate the extracted .db file (SQLite header check)
     * 3. Close the current database connection
     * 4. Replace the database files (delete WAL/SHM, copy backup .db)
     * 5. Restore settings from settings.json to DataStore
     * 6. Return true — caller is responsible for restarting the app process
     *
     * @param context Android context for accessing database path
     * @param sourceUri SAF URI of the backup .zip file
     * @return true if restore succeeded (caller must restart app)
     */
    suspend fun restoreDatabase(context: Context, sourceUri: Uri): Boolean {
        val tempDir = File(context.cacheDir, "restore_temp")
        return try {
            tempDir.mkdirs()

            // 1. Extract .zip contents
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = File(tempDir, entry.name)
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { output -> zip.copyTo(output) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return false

            val dbBackup = File(tempDir, BACKUP_DB_ENTRY)
            val settingsFile = File(tempDir, BACKUP_SETTINGS_ENTRY)

            // 2. Validate — database file must exist and be valid SQLite
            if (!dbBackup.exists() || !isValidSqliteFile(dbBackup)) {
                Log.e(TAG, "Restore failed: invalid or missing database in backup")
                tempDir.deleteRecursively()
                return false
            }

            // 3. Close the current database connection
            database.close()

            // 4. Replace the database files
            val dbPath = context.getDatabasePath(DB_NAME)
            val walFile = File(dbPath.path + "-wal")
            val shmFile = File(dbPath.path + "-shm")

            // Delete WAL/SHM files to prevent conflicts with restored database
            walFile.delete()
            shmFile.delete()

            // Copy backup over the main database file
            dbBackup.copyTo(dbPath, overwrite = true)

            // 5. Restore settings if settings.json is present
            if (settingsFile.exists()) {
                try {
                    val json = JSONObject(settingsFile.readText())
                    if (json.has("monthStartDay")) {
                        appPreferences.setMonthStartDay(json.getInt("monthStartDay"))
                    }
                    if (json.has("bankTrackingEnabled")) {
                        appPreferences.setBankTrackingEnabled(json.getBoolean("bankTrackingEnabled"))
                    }
                    if (json.has("enabledBanks")) {
                        val banksArray = json.getJSONArray("enabledBanks")
                        for (i in 0 until banksArray.length()) {
                            appPreferences.setBankEnabled(banksArray.getString(i), true)
                        }
                    }
                    Log.i(TAG, "Settings restored from backup")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore settings (non-fatal): ${e.message}")
                }
            }

            // 6. Clean up temp files
            tempDir.deleteRecursively()

            Log.i(TAG, "Database restore completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            tempDir.deleteRecursively()
            false
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
            String(header, 0, 15) == "SQLite format 3"
        } catch (e: Exception) {
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
