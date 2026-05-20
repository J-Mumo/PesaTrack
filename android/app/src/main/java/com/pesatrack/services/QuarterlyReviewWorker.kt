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
import kotlin.math.abs

/**
 * Periodic worker that generates the Quarterly Review snapshot and posts a
 * notification on the 1st of Apr/Jul/Oct/Jan at ~09:00 local time.
 */
@HiltWorker
class QuarterlyReviewWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val insightsRepository: InsightsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val snapshot = insightsRepository.generateAndStoreQuarterlyReview()

            if (snapshot.periodTotal <= 0.0) {
                Log.d(TAG, "Quarterly review generated but no spend — suppressing notification")
                return Result.success()
            }

            NotificationHelper.showQuarterlyReviewNotification(
                context = context,
                snapshotId = snapshot.id.toLongOrNull() ?: 0L,
                periodLabel = snapshot.periodLabel,
                totalSpent = snapshot.periodTotal,
                deltaPercent = snapshot.deltaPercent
            )

            Log.d(TAG, "Quarterly review notification posted (period=${snapshot.periodLabel})")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Quarterly review generation failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "QuarterlyReviewWorker"
        const val WORK_NAME = "quarterly_review_check"

        private const val TRIGGER_DAY_OF_MONTH = 1
        private const val TRIGGER_HOUR_OF_DAY = 9

        // Quarter start months (1-based): Jan, Apr, Jul, Oct
        private val QUARTER_START_MONTHS = listOf(1, 4, 7, 10)

        /**
         * Schedule (or re-schedule) the quarterly worker. Uses [ExistingPeriodicWorkPolicy.KEEP]
         * so calling this on every cold start is safe.
         */
        fun scheduleQuarterly(context: Context) {
            val request = PeriodicWorkRequestBuilder<QuarterlyReviewWorker>(
                90, TimeUnit.DAYS
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
         * Milliseconds from [now] until the next quarter-start 1st at 09:00 local time.
         * Quarter starts: Jan 1, Apr 1, Jul 1, Oct 1.
         */
        internal fun initialDelayMs(now: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now

            val currentMonth = cal.get(Calendar.MONTH) + 1 // 1-based
            val currentYear = cal.get(Calendar.YEAR)

            // Find the next quarter start month
            val nextQMonth = QUARTER_START_MONTHS.firstOrNull { it > currentMonth }
            val targetMonth: Int
            val targetYear: Int
            if (nextQMonth != null) {
                targetMonth = nextQMonth
                targetYear = currentYear
            } else {
                targetMonth = 1 // January next year
                targetYear = currentYear + 1
            }

            cal.set(Calendar.YEAR, targetYear)
            cal.set(Calendar.MONTH, targetMonth - 1) // 0-based
            cal.set(Calendar.DAY_OF_MONTH, TRIGGER_DAY_OF_MONTH)
            cal.set(Calendar.HOUR_OF_DAY, TRIGGER_HOUR_OF_DAY)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            // If we're already past this time (e.g. it's Jan 1 10:00), go to next quarter
            if (cal.timeInMillis <= now) {
                val idx = QUARTER_START_MONTHS.indexOf(targetMonth)
                if (idx < QUARTER_START_MONTHS.size - 1) {
                    cal.set(Calendar.MONTH, QUARTER_START_MONTHS[idx + 1] - 1)
                } else {
                    cal.set(Calendar.YEAR, targetYear + 1)
                    cal.set(Calendar.MONTH, 0) // January
                }
            }

            return cal.timeInMillis - now
        }
    }
}
