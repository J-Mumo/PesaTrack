package com.pesatrack.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pesatrack.data.repository.InsightsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that generates the Monthly Review snapshot and posts a
 * notification on the 1st of each month at ~09:00 local time.
 */
@HiltWorker
class MonthlyReviewWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val insightsRepository: InsightsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val snapshot = insightsRepository.generateAndStoreMonthlyReview()

            if (snapshot.totalSpent <= 0.0) {
                Log.d(TAG, "Monthly review generated but no spend — suppressing notification")
                return Result.success()
            }

            NotificationHelper.showMonthlyReviewNotification(
                context = context,
                snapshotId = snapshot.id.toLongOrNull() ?: 0L,
                monthName = snapshot.monthName,
                totalSpent = snapshot.totalSpent,
                deltaPercent = snapshot.deltaPercent,
                headroom = snapshot.headroom
            )

            Log.d(TAG, "Monthly review notification posted (month=${snapshot.monthName})")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Monthly review generation failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MonthlyReviewWorker"
        const val WORK_NAME = "monthly_review_check"

        private const val TRIGGER_DAY_OF_MONTH = 1
        private const val TRIGGER_HOUR_OF_DAY = 9

        /**
         * Schedule (or re-schedule) the monthly worker. Uses [ExistingPeriodicWorkPolicy.KEEP]
         * so calling this on every cold start is safe.
         */
        fun scheduleMonthly(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlyReviewWorker>(
                30, TimeUnit.DAYS
            )
                .setInitialDelay(initialDelayMs(System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Milliseconds from [now] until the next 1st of month at 09:00 local time.
         */
        internal fun initialDelayMs(now: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            // Move to next 1st at 09:00
            cal.set(Calendar.DAY_OF_MONTH, TRIGGER_DAY_OF_MONTH)
            cal.set(Calendar.HOUR_OF_DAY, TRIGGER_HOUR_OF_DAY)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.MONTH, 1)
            }
            return cal.timeInMillis - now
        }
    }
}
