package com.pesatrack.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pesatrack.data.local.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

/**
 * WorkManager worker that runs once daily to check for upcoming and overdue
 * recurring expenses and send reminder notifications.
 *
 * Scheduled via [androidx.work.PeriodicWorkRequest] in [MainActivity] on app start.
 * Uses [RecurringExpenseService] to detect patterns and check dates.
 *
 * Throttling:
 * - Max 1 notification per recurring expense per cycle (tracked in AppPreferences)
 * - User can disable via Settings toggle
 */
@HiltWorker
class RecurringReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recurringExpenseService: RecurringExpenseService,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Check if recurring reminders are enabled
            if (!appPreferences.getRecurringRemindersEnabled()) {
                Log.d(TAG, "Recurring reminders disabled — skipping")
                return Result.success()
            }

            val summary = recurringExpenseService.getRecurringExpenses(forceRefresh = true)

            // Check upcoming expenses (within 1 day)
            val now = System.currentTimeMillis()
            val tomorrowEnd = now + RecurringExpenseService.MS_PER_DAY

            for (recurring in summary.recurringExpenses) {
                // Only notify for high-confidence recurring expenses
                if (recurring.confidence < RecurringExpenseService.MIN_CONFIDENCE_FOR_FORECAST) continue

                // Check if upcoming (within next 24 hours)
                if (recurring.nextExpected in now..tomorrowEnd) {
                    val throttleKey = "recurring_remind_${recurring.recipientKey}"
                    if (appPreferences.canSendRecurringNotification(throttleKey, recurring.cycle.expectedDays)) {
                        val daysUntil = ((recurring.nextExpected - now) / RecurringExpenseService.MS_PER_DAY).toInt()
                        val dueDesc = when {
                            daysUntil <= 0 -> "due today"
                            daysUntil == 1 -> "due tomorrow"
                            else -> "due in $daysUntil days"
                        }
                        NotificationHelper.showRecurringReminderNotification(
                            context = context,
                            recipientKey = recurring.recipientKey,
                            recipientName = recurring.recipientDisplayName,
                            amount = recurring.averageAmount,
                            dueDescription = dueDesc
                        )
                        appPreferences.setLastRecurringNotifTime(throttleKey)
                    }
                }

                // Check if overdue (expected date passed + grace period)
                if (recurring.isOverdue) {
                    val throttleKey = "recurring_overdue_${recurring.recipientKey}"
                    if (appPreferences.canSendRecurringNotification(throttleKey, recurring.cycle.expectedDays)) {
                        val expectedDesc = if (recurring.expectedDayOfMonth != null) {
                            "Usually by the ${ordinalSuffix(recurring.expectedDayOfMonth)}"
                        } else {
                            "Expected ${daysAgoText(recurring.nextExpected, now)}"
                        }
                        NotificationHelper.showOverdueNotification(
                            context = context,
                            recipientKey = recurring.recipientKey,
                            recipientName = recurring.recipientDisplayName,
                            expectedByDescription = expectedDesc
                        )
                        appPreferences.setLastRecurringNotifTime(throttleKey)
                    }
                }
            }

            Log.d(TAG, "Recurring reminder check complete: ${summary.recurringExpenses.size} patterns, " +
                    "${summary.upcomingThisWeek.size} upcoming, ${summary.overdueExpenses.size} overdue")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking recurring expenses", e)
            Result.retry()
        }
    }

    private fun ordinalSuffix(day: Int): String {
        val suffix = when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    private fun daysAgoText(expectedTime: Long, now: Long): String {
        val daysAgo = ((now - expectedTime) / RecurringExpenseService.MS_PER_DAY).toInt()
        return when {
            daysAgo <= 1 -> "yesterday"
            else -> "$daysAgo days ago"
        }
    }

    companion object {
        private const val TAG = "RecurringReminderWorker"
        const val WORK_NAME = "recurring_reminder_check"
    }
}
