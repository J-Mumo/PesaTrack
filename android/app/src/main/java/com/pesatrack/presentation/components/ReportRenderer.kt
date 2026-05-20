package com.pesatrack.presentation.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for sharing report content as images.
 *
 * Provides a function to save a Bitmap to cache and share via Android share intent.
 */
object ReportRenderer {

    /**
     * Save a bitmap to the app cache directory and launch an Android share intent.
     *
     * @param context Application or Activity context.
     * @param bitmap The rendered report bitmap.
     * @param title Title for the share sheet.
     */
    fun shareReportAsImage(context: Context, bitmap: Bitmap, title: String) {
        val cachePath = File(context.cacheDir, "shared_reports")
        cachePath.mkdirs()
        val file = File(cachePath, "report_${System.currentTimeMillis()}.png")

        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, title))
    }
}
