package com.pesatrack.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pesatrack.data.local.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * WorkManager worker that runs once daily to check for upcoming and overdue
 * recurring expenses and send reminder notifications.
 *
 * Scheduled via [scheduleDaily] from the Application. WorkManager is best-effort,
 * with an initial delay until 09:00 local time, not an exact alarm.
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
            // Do not consume a reminder cooldown while permission/channel delivery is blocked.
            if (!NotificationHelper.canShowRecurringReminders(context)) return Result.success()

            val summary = recurringExpenseService.getRecurringExpenses(forceRefresh = true)

            val now = System.currentTimeMillis()

            for (recurring in summary.recurringExpenses) {
                // Only notify for high-confidence recurring expenses
                if (recurring.confidence < RecurringExpenseService.MIN_CONFIDENCE_FOR_FORECAST) continue

                val daysUntil = RecurringReminderTiming.daysUntil(recurring.nextExpected, now)
                // Today remains eligible after noon; tomorrow is a calendar day, not 24 hours.
                if (daysUntil in 0L..1L) {
                    val throttleKey = "recurring_remind_${recurring.recipientKey}"
                    if (appPreferences.canSendRecurringNotification(throttleKey, recurring.cycle.expectedDays)) {
                        val dueDesc = if (daysUntil == 0L) "expected today" else "expected tomorrow"
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
                if (RecurringReminderTiming.isOverdue(recurring.nextExpected, now)) {
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
        } catch (e: CancellationException) {
            throw e
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
        val daysAgo = -RecurringReminderTiming.daysUntil(expectedTime, now)
        return when {
            daysAgo <= 1 -> "yesterday"
            else -> "$daysAgo days ago"
        }
    }

    companion object {
        private const val TAG = "RecurringReminderWorker"
        const val WORK_NAME = "recurring_reminder_check"

        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(RecurringReminderTiming.initialDelayMs(System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
