package com.pesatrack.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pesatrack.R
import com.pesatrack.presentation.MainActivity

/**
 * Helper class for creating and managing notifications.
 * 
 * Handles notification channel creation (Android 8+) and
 * shows notifications for newly detected M-PESA transactions.
 */
object NotificationHelper {
    
    private const val CHANNEL_ID = "pesatrack_expenses"
    private const val CHANNEL_NAME = "Expense Notifications"
    private const val CHANNEL_DESCRIPTION = "Notifications for new M-PESA transactions detected via SMS"

    private const val BUDGET_CHANNEL_ID = "pesatrack_budget_alerts"
    private const val BUDGET_CHANNEL_NAME = "Budget Alerts"
    private const val BUDGET_CHANNEL_DESCRIPTION = "Alerts when spending approaches or exceeds budget limits"

    private const val RECURRING_CHANNEL_ID = "pesatrack_recurring_reminders"
    private const val RECURRING_CHANNEL_NAME = "Recurring Reminders"
    private const val RECURRING_CHANNEL_DESCRIPTION = "Reminders for upcoming and overdue recurring expenses"

    private const val WEEKLY_REVIEW_CHANNEL_ID = "pesatrack_weekly_review"
    private const val WEEKLY_REVIEW_CHANNEL_NAME = "Weekly Review"
    private const val WEEKLY_REVIEW_CHANNEL_DESCRIPTION =
        "Your weekly spending review (Thursdays) \u2014 see Insights & Reports plan."
    /** Fixed notification id so a fresh weekly review replaces any previous one. */
    private const val WEEKLY_REVIEW_NOTIFICATION_ID = 410_001
    
    /**
     * Create the notification channel (required for Android 8.0+).
     * Safe to call multiple times — only creates the channel once.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            
            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show a notification prompting the user to categorize a new expense.
     *
     * @param context Application context
     * @param expenseId The ID of the saved expense
     * @param amount The transaction amount in KES
     * @param recipient The recipient name/number from the transaction
     */
    fun showExpenseNotification(
        context: Context,
        expenseId: Long,
        amount: Double,
        recipient: String
    ) {
        // Ensure channel exists
        createNotificationChannel(context)
        
        // Intent to open the categorize screen for this expense
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "categorize")
            putExtra("expense_id", expenseId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            expenseId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val formattedAmount = String.format("KES %,.2f", amount)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Expense: $formattedAmount")
            .setContentText("To $recipient — Tap to categorize")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        
        // Use expense ID as notification ID for uniqueness
        notificationManager.notify(expenseId.toInt(), notification)
    }

    /**
     * Create the budget alert notification channel (required for Android 8.0+).
     * Safe to call multiple times — only creates the channel once.
     */
    fun createBudgetAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BUDGET_CHANNEL_ID,
                BUDGET_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = BUDGET_CHANNEL_DESCRIPTION
            }

            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show a budget alert notification when spending crosses a threshold.
     *
     * @param context Application context
     * @param budgetId The budget ID
     * @param categoryName Category group name (e.g. "Food & Dining") or "Total Spending"
     * @param spent Actual spending in the current period (KES)
     * @param budgetAmount Budget limit (KES)
     * @param percentage Percentage of budget used
     * @param threshold The threshold crossed: 80 or 100
     */
    fun showBudgetAlertNotification(
        context: Context,
        budgetId: Long,
        categoryName: String,
        spent: Double,
        budgetAmount: Double,
        percentage: Int,
        threshold: Int
    ) {
        // Ensure channel exists
        createBudgetAlertChannel(context)

        // Intent to open the app (budget screen via main activity)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "budget")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (budgetId * 10 + threshold).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedSpent = String.format("KES %,.0f", spent)
        val formattedBudget = String.format("KES %,.0f", budgetAmount)

        val (icon, title) = when {
            percentage > 100 -> "🚨" to "$categoryName: Budget exceeded!"
            threshold >= 100 -> "🚨" to "$categoryName: Budget fully used!"
            else -> "⚠\uFE0F" to "$categoryName: ${threshold}% of budget used"
        }

        val notification = NotificationCompat.Builder(context, BUDGET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("$formattedSpent / $formattedBudget ($percentage%)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        // Unique notification ID per budget per threshold level
        val notificationId = (budgetId * 10 + threshold).toInt()
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Show a proactive forecast notification when spending is projected to exceed budget.
     *
     * Two message templates:
     * - **Projected overspend**: "📊 Food & Dining: On track for KES 18,600 (124%). KES 240/day to stay on budget."
     * - **Exhaustion imminent**: "⏰ Food & Dining budget runs out in ~4 days. KES 2,400 remaining."
     *
     * Uses PRIORITY_DEFAULT (lower than budget exceeded alerts which use PRIORITY_HIGH).
     * Notification ID: budgetId * 10 + 5 (distinct from threshold 80/100 IDs).
     *
     * @param context Application context
     * @param budgetId The budget ID
     * @param categoryName Category name or "Total Spending"
     * @param projectedTotal Projected end-of-period total (KES)
     * @param budgetAmount Budget limit (KES)
     * @param projectedPercentage Projected % of budget at end of period
     * @param safeDailyBudget KES per day to stay on track
     * @param daysRemaining Days left in the period
     * @param exhaustionImminent Whether budget runs out within 5 days
     * @param remaining Budget amount minus current spending (KES)
     */
    fun showForecastNotification(
        context: Context,
        budgetId: Long,
        categoryName: String,
        projectedTotal: Double,
        budgetAmount: Double,
        projectedPercentage: Int,
        safeDailyBudget: Double,
        daysRemaining: Int,
        exhaustionImminent: Boolean,
        remaining: Double
    ) {
        // Ensure channel exists
        createBudgetAlertChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "budget")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (budgetId * 10 + 5).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedProjected = String.format("KES %,.0f", projectedTotal)
        val formattedSafe = String.format("KES %,.0f", safeDailyBudget)

        val (title, text) = when {
            exhaustionImminent -> {
                val formattedRemaining = String.format("KES %,.0f", remaining)
                "⏰ $categoryName budget runs out in ~${daysRemaining} days" to
                    "$formattedRemaining remaining"
            }
            projectedPercentage > 100 -> {
                "🚨 $categoryName: Projected $formattedProjected ($projectedPercentage%)" to
                    "$formattedSafe/day to get back on track"
            }
            else -> {
                "📊 $categoryName: On track at $formattedProjected ($projectedPercentage%)" to
                    "$formattedSafe/day to stay on budget"
            }
        }

        val notification = NotificationCompat.Builder(context, BUDGET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        // Unique notification ID: budgetId * 10 + 5 (distinct from 80/100 threshold IDs)
        val notificationId = (budgetId * 10 + 5).toInt()
        notificationManager.notify(notificationId, notification)
    }

    // ==================== Recurring Reminders ====================

    /**
     * Create the recurring reminders notification channel (required for Android 8.0+).
     * Safe to call multiple times — only creates the channel once.
     */
    fun createRecurringReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RECURRING_CHANNEL_ID,
                RECURRING_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = RECURRING_CHANNEL_DESCRIPTION
            }

            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show a reminder notification for an upcoming recurring expense.
     *
     * @param context Application context
     * @param recipientKey Unique key for the recurring expense (used for notification ID)
     * @param recipientName Display name of the recipient
     * @param amount Expected amount (KES)
     * @param dueDescription When it's due (e.g. "due tomorrow", "due in 3 days")
     */
    fun showRecurringReminderNotification(
        context: Context,
        recipientKey: String,
        recipientName: String,
        amount: Double,
        dueDescription: String
    ) {
        createRecurringReminderChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val notificationId = recipientKey.hashCode() + 100_000
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = String.format("KES %,.0f", amount)

        val notification = NotificationCompat.Builder(context, RECURRING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📅 $recipientName ($formattedAmount)")
            .setContentText("Recurring payment $dueDescription")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Show a notification for an overdue recurring expense.
     *
     * @param context Application context
     * @param recipientKey Unique key for the recurring expense (used for notification ID)
     * @param recipientName Display name of the recipient
     * @param expectedByDescription When it was expected (e.g. "usually by the 15th")
     */
    fun showOverdueNotification(
        context: Context,
        recipientKey: String,
        recipientName: String,
        expectedByDescription: String
    ) {
        createRecurringReminderChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val notificationId = recipientKey.hashCode() + 200_000
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RECURRING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ $recipientName payment overdue")
            .setContentText("$expectedByDescription — no payment detected yet")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    // ==================== Weekly Review (Insights & Reports v1.0) ====================

    /**
     * Create the Weekly Review notification channel.
     * Safe to call multiple times — only creates the channel once.
     */
    fun createWeeklyReviewChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WEEKLY_REVIEW_CHANNEL_ID,
                WEEKLY_REVIEW_CHANNEL_NAME,
                // DEFAULT (not HIGH) per the plan: it's a review, not an alert.
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = WEEKLY_REVIEW_CHANNEL_DESCRIPTION
            }

            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show the Weekly Review notification.
     *
     * Body copy follows plans/insights-and-reports-plan.md → *Notification anatomy*:
     * `KES {total} spent this week {arrow} {pct}% vs last week. Biggest change: {category} {arrow} KES {delta}.`
     *
     * @param snapshotId DB id of the persisted snapshot; passed through the deep link
     *                   so the screen can hydrate the exact report the user was notified about.
     */
    fun showWeeklyReviewNotification(
        context: Context,
        snapshotId: Long,
        periodTotal: Double,
        previousPeriodTotal: Double,
        biggestChangeCategoryName: String?,
        biggestChangeDelta: Double
    ) {
        createWeeklyReviewChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "weekly_review")
            putExtra("report_snapshot_id", snapshotId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            WEEKLY_REVIEW_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTotal = String.format("KES %,.0f", periodTotal)
        val body = buildString {
            append("$formattedTotal spent this week")
            if (previousPeriodTotal > 0.0) {
                val pct = ((periodTotal - previousPeriodTotal) / previousPeriodTotal) * 100.0
                val arrow = if (pct >= 0.0) "\u2191" else "\u2193"
                append(" $arrow ${String.format("%.0f", kotlin.math.abs(pct))}% vs last week.")
            } else {
                append(".")
            }
            if (biggestChangeCategoryName != null && kotlin.math.abs(biggestChangeDelta) > 0.0) {
                val arrow = if (biggestChangeDelta >= 0.0) "\u2191" else "\u2193"
                val deltaStr = String.format("KES %,.0f", kotlin.math.abs(biggestChangeDelta))
                append(" Biggest change: $biggestChangeCategoryName $arrow $deltaStr.")
            }
        }

        val notification = NotificationCompat.Builder(context, WEEKLY_REVIEW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Your week in review")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.notify(WEEKLY_REVIEW_NOTIFICATION_ID, notification)
    }
}
