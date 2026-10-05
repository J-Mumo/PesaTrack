package com.pesatrack.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.pesatrack.R
import com.pesatrack.data.repository.ExpenseRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        const val EXTRA_EXPENSE_ID = "expense_id"

        /** Delay before persisting the ignore (ms). User can undo within this window. */
        const val UNDO_WINDOW_MS = 5000L

        /**
         * In-memory set of expense IDs pending ignore.
         * If removed before the handler fires, the ignore is cancelled.
         */
        val pendingIgnores = PendingExpenseIgnores()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val expenseId = intent.getLongExtra(EXTRA_EXPENSE_ID, -1L)
        if (expenseId <= 0) return

        // Legacy Ignore/Undo PendingIntents (posted before this fix) have no kind/data.
        // Never accept an arbitrary kind or route a categorize broadcast into an activity.
        val kind = intent.getStringExtra(ExpenseNotificationContract.EXTRA_KIND)
        val legacy = kind == null && intent.data == null
        val role = when (intent.action) {
            ExpenseNotificationContract.ACTION_IGNORE -> "ignore"
            ExpenseNotificationContract.ACTION_UNDO -> "undo"
            else -> return
        }
        if (!legacy && (kind != ExpenseNotificationContract.NEW || intent.dataString !=
                ExpenseNotificationContract.identity(expenseId, kind, role))) return

        when (intent.action) {
            ExpenseNotificationContract.ACTION_IGNORE -> handleIgnore(context, expenseId, legacy)
            ExpenseNotificationContract.ACTION_UNDO -> handleUndo(context, expenseId, legacy)
        }
    }

    private fun handleIgnore(context: Context, expenseId: Long, legacy: Boolean) {
        val token = pendingIgnores.begin(expenseId)
        // Keep the receiver/process alive through the five-second undo + repository write.
        val result = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                showUndoNotification(appContext, expenseId, legacy)
                delay(UNDO_WINDOW_MS)
                if (pendingIgnores.commit(expenseId, token) { id, excluded ->
                        withContext(Dispatchers.IO) { expenseRepository.setExcluded(id, excluded) }
                    } && !pendingIgnores.hasPending(expenseId)) {
                    cancelNotification(appContext, expenseId, legacy)
                }
            } catch (error: Exception) {
                pendingIgnores.take(expenseId, token)
                android.util.Log.e("NotificationAction", "Could not ignore expense", error)
            } finally {
                result.finish()
            }
        }
    }

    private fun handleUndo(context: Context, expenseId: Long, legacy: Boolean) {
        // Remove from pending → the delayed handler will no-op
        pendingIgnores.undo(expenseId)
        cancelNotification(context, expenseId, legacy)
    }

    private fun cancelNotification(context: Context, expenseId: Long, legacy: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (legacy) {
            nm.cancel(expenseId.toInt())
        } else {
            nm.cancel(ExpenseNotificationContract.tag(expenseId, ExpenseNotificationContract.NEW),
                ExpenseNotificationContract.NOTIFICATION_ID)
        }
    }

    private fun showUndoNotification(context: Context, expenseId: Long, legacy: Boolean) {
        NotificationHelper.createNotificationChannel(context)

        val undoIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ExpenseNotificationContract.ACTION_UNDO
            if (!legacy) {
                data = Uri.parse(ExpenseNotificationContract.identity(expenseId, ExpenseNotificationContract.NEW, "undo"))
                putExtra(ExpenseNotificationContract.EXTRA_KIND, ExpenseNotificationContract.NEW)
            }
            putExtra(EXTRA_EXPENSE_ID, expenseId)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            if (legacy) (expenseId + 600_000).toInt() else 0,
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
        if (legacy) {
            nm.notify(expenseId.toInt(), notification)
        } else {
            nm.notify(ExpenseNotificationContract.tag(expenseId, ExpenseNotificationContract.NEW),
                ExpenseNotificationContract.NOTIFICATION_ID, notification)
        }
    }
}
