package com.pesatrack.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.data.repository.InsightsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that generates the Weekly Review snapshot and posts the
 * Thursday-evening notification.
 *
 * Scheduling: [scheduleWeekly] enqueues a 7-day-period worker with an initial
 * delay aligned to the next Thursday at 18:00 local time. WorkManager does not
 * support cron-like day-of-week triggers natively, so we align via the initial
 * delay and trust the 7-day cadence to keep the worker on Thursday.
 *
 * Safety nets inside [doWork]:
 * - Skip silently if the user disabled the toggle in Settings.
 * - Skip the notification when there is no spend data for the period
 *   (don't post an empty review; honest numbers per Principle 5).
 *
 * See plans/insights-and-reports-plan.md \u2192 *Notification anatomy*.
 */
@HiltWorker
class WeeklyReviewWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val insightsRepository: InsightsRepository,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (!appPreferences.getWeeklyReviewEnabled()) {
                Log.d(TAG, "Weekly review disabled — skipping")
                return Result.success()
            }

            val snapshot = insightsRepository.generateAndStoreWeeklyReview()

            // Skip the notification when there is literally no activity in the period —
            // there is nothing honest to say.
            if (snapshot.periodTotal <= 0.0) {
                Log.d(TAG, "Weekly review generated but no spend in window — suppressing notification")
                return Result.success()
            }

            NotificationHelper.showWeeklyReviewNotification(
                context = context,
                snapshotId = snapshot.id,
                periodTotal = snapshot.periodTotal,
                previousPeriodTotal = snapshot.previousPeriodTotal,
                biggestChangeCategoryName = snapshot.biggestChangeCategoryName,
                biggestChangeDelta = snapshot.biggestChangeDelta
            )

            Log.d(TAG, "Weekly review notification posted (snapshotId=${snapshot.id})")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weekly review generation failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WeeklyReviewWorker"
        const val WORK_NAME = "weekly_review_check"

        /** Thursday at 18:00 local time, per plans/insights-and-reports-plan.md. */
        private const val TRIGGER_DAY_OF_WEEK = Calendar.THURSDAY
        private const val TRIGGER_HOUR_OF_DAY = 18

        /**
         * Schedule (or re-schedule) the weekly worker. Uses [ExistingPeriodicWorkPolicy.KEEP]
         * so calling this on every cold start is safe — the existing schedule is preserved.
         * Call [cancel] first if you want to force a re-schedule with a new initial delay.
         */
        fun scheduleWeekly(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyReviewWorker>(
                7, TimeUnit.DAYS
            )
                .setInitialDelay(initialDelayMs(System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancel the weekly review worker (used when the user disables it). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Milliseconds from [now] until the next Thursday at 18:00 local time.
         * Exposed `internal` so unit tests can pin a "now" and assert against it.
         */
        internal fun initialDelayMs(now: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            // Move forward to the next Thursday 18:00. If we're already past
            // Thursday 18:00 this week, this naturally rolls over to next week.
            val daysUntilThursday = ((TRIGGER_DAY_OF_WEEK - cal.get(Calendar.DAY_OF_WEEK)) + 7) % 7
            cal.add(Calendar.DAY_OF_YEAR, daysUntilThursday)
            cal.set(Calendar.HOUR_OF_DAY, TRIGGER_HOUR_OF_DAY)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 7)
            }
            return cal.timeInMillis - now
        }
    }
}
