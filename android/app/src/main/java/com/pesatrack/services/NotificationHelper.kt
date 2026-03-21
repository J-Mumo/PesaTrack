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

        val (icon, title) = if (threshold >= 100) {
            "🚨" to "$categoryName: Budget exceeded!"
        } else {
            "⚠\uFE0F" to "$categoryName: ${threshold}% of budget used"
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
}
