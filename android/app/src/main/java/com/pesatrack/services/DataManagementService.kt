package com.pesatrack.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pesatrack.data.local.database.dao.CategoryDao
import com.pesatrack.data.local.database.dao.CategoryRuleDao
import com.pesatrack.data.local.database.dao.ExpenseDao
import com.pesatrack.data.local.database.entities.DefaultCategories
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
 */
@Singleton
class DataManagementService @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val categoryRuleDao: CategoryRuleDao
) {

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
