package com.pesatrack.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.pesatrack.R
import com.pesatrack.data.repository.ExpenseRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification action buttons for expenses (Ignore / Undo).
 *
 * Flow:
 * 1. User taps "Ignore" → shows a 5-second "Ignored ✓ — Tap to undo" notification
 * 2. After 5s, persists the exclude to DB and dismisses notification
 * 3. If user taps "Undo" within the window, cancels the pending exclude
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var expenseRepository: ExpenseRepository

    private companion object {
        const val ACTION_IGNORE_EXPENSE = "com.pesatrack.ACTION_IGNORE_EXPENSE"
        const val ACTION_UNDO_IGNORE = "com.pesatrack.ACTION_UNDO_IGNORE"
        const val EXTRA_EXPENSE_ID = "expense_id"

        /** Delay before persisting the ignore (ms). User can undo within this window. */
        const val UNDO_WINDOW_MS = 5000L

        /**
         * In-memory set of expense IDs pending ignore.
         * If removed before the handler fires, the ignore is cancelled.
         */
        val pendingIgnores = mutableSetOf<Long>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val expenseId = intent.getLongExtra(EXTRA_EXPENSE_ID, -1L)
        if (expenseId == -1L) return

        when (intent.action) {
            ACTION_IGNORE_EXPENSE -> handleIgnore(context, expenseId)
            ACTION_UNDO_IGNORE -> handleUndo(context, expenseId)
        }
    }

    private fun handleIgnore(context: Context, expenseId: Long) {
        // Mark as pending
        pendingIgnores.add(expenseId)

        // Show "Ignored ✓ — Tap to undo" replacement notification
        showUndoNotification(context, expenseId)

        // Schedule actual persist after the undo window
        Handler(Looper.getMainLooper()).postDelayed({
            if (pendingIgnores.remove(expenseId)) {
                // Still pending → persist the exclude
                CoroutineScope(Dispatchers.IO).launch {
                    expenseRepository.setExcluded(expenseId, true)
                }
                // Dismiss the undo notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(expenseId.toInt())
            }
        }, UNDO_WINDOW_MS)
    }

    private fun handleUndo(context: Context, expenseId: Long) {
        // Remove from pending → the delayed handler will no-op
        pendingIgnores.remove(expenseId)

        // Dismiss the undo notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(expenseId.toInt())
    }

    private fun showUndoNotification(context: Context, expenseId: Long) {
        NotificationHelper.createNotificationChannel(context)

        val undoIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_UNDO_IGNORE
            putExtra(EXTRA_EXPENSE_ID, expenseId)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            (expenseId + 600_000).toInt(),
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "pesatrack_expenses")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Expense ignored ✓")
            .setContentText("Tap to undo")
            .setContentIntent(undoPendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(UNDO_WINDOW_MS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(expenseId.toInt(), notification)
    }
}
