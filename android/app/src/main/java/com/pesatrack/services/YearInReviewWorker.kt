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
 * Periodic worker that generates the Year-in-Review snapshot and posts a
 * notification on Dec 28 at ~18:00 local time. If unviewed, re-posts on Jan 2.
 */
@HiltWorker
class YearInReviewWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val insightsRepository: InsightsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            // On Dec 28 we review the current year; on Jan 2 we review previous year
            val reviewYear = if (cal.get(Calendar.MONTH) == Calendar.JANUARY) year - 1 else year

            val snapshot = insightsRepository.generateAndStoreYearlyReview(reviewYear)

            if (snapshot.annualTotal <= 0.0) {
                Log.d(TAG, "Year-in-review generated but no spend — suppressing notification")
                return Result.success()
            }

            NotificationHelper.showYearlyReviewNotification(
                context = context,
                year = snapshot.year,
                totalSpent = snapshot.annualTotal
            )

            Log.d(TAG, "Year-in-review notification posted (year=${snapshot.year})")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Year-in-review generation failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "YearInReviewWorker"
        const val WORK_NAME = "yearly_review_check"

        /**
         * Schedule (or re-schedule) the yearly worker. Uses [ExistingPeriodicWorkPolicy.KEEP]
         * so calling this on every cold start is safe.
         *
         * Fires Dec 28 at 18:00. Period is 365 days.
         */
        fun scheduleYearly(context: Context) {
            val request = PeriodicWorkRequestBuilder<YearInReviewWorker>(
                365, TimeUnit.DAYS
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
         * Milliseconds from [now] until the next Dec 28 at 18:00 local time.
         */
        internal fun initialDelayMs(now: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now

            val currentYear = cal.get(Calendar.YEAR)
            cal.set(Calendar.YEAR, currentYear)
            cal.set(Calendar.MONTH, Calendar.DECEMBER)
            cal.set(Calendar.DAY_OF_MONTH, 28)
            cal.set(Calendar.HOUR_OF_DAY, 18)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            // If we're already past Dec 28 this year, target next year
            if (cal.timeInMillis <= now) {
                cal.set(Calendar.YEAR, currentYear + 1)
            }

            return cal.timeInMillis - now
        }
    }
}
