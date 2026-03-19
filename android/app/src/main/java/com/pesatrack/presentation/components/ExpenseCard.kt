package com.pesatrack.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.PaymentType
import com.pesatrack.presentation.theme.getCategoryColor
import com.pesatrack.utils.formatAsCurrency
import java.text.SimpleDateFormat
import java.util.*

/**
 * Card component displaying a single expense item
 *
 * Title priority: categoryName > recipientName > recipient
 * Subtitle: paymentType • recipient info (when title is category)
 *
 * Long-press to toggle excluded status (pass-through money).
 * Excluded expenses are dimmed with strikethrough amount.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseCard(
    expense: Expense,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
    categoryColor: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    // Title: show category name (what the expense was for) as primary text
    val title = categoryName
        ?: expense.recipientName
        ?: expense.recipient
    
    val cardAlpha = if (expense.isExcluded) 0.5f else 1f
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable { onClick() }
                }
            )
            .alpha(cardAlpha)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category indicator / Payment type icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (expense.isExcluded) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else if (categoryColor != null) {
                            getCategoryColor(categoryColor).copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expense.isExcluded) Icons.Filled.VisibilityOff
                                  else getPaymentTypeIcon(expense.paymentType),
                    contentDescription = if (expense.isExcluded) "Excluded from totals" else null,
                    tint = if (expense.isExcluded) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (categoryColor != null) {
                        getCategoryColor(categoryColor)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Details
            Column(modifier = Modifier.weight(1f)) {
                // Primary: category name or recipient name
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Secondary: payment type + recipient (when title is category)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = expense.paymentType.displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    if (expense.isExcluded) {
                        Text(
                            text = " • Excluded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else if (categoryName != null) {
                        // When title is category, show recipient as secondary info
                        val recipientInfo = expense.recipientName ?: expense.recipient
                        Text(
                            text = " • $recipientInfo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (!expense.isCategorized) {
                        Text(
                            text = " • Uncategorized",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                Text(
                    text = formatDate(expense.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            
            // Amount — strikethrough if excluded
            Text(
                text = expense.amount.formatAsCurrency(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (expense.isExcluded) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                textDecoration = if (expense.isExcluded) TextDecoration.LineThrough else TextDecoration.None
            )
        }
    }
}

/**
 * Get icon for payment type
 */
@Suppress("DEPRECATION")
fun getPaymentTypeIcon(paymentType: PaymentType): ImageVector {
    return when (paymentType) {
        PaymentType.CASH -> Icons.Filled.Payments
        PaymentType.SEND_MONEY -> Icons.Filled.Send
        PaymentType.BUY_GOODS -> Icons.Filled.ShoppingCart
        PaymentType.PAY_BILL -> Icons.Filled.Receipt
        PaymentType.WITHDRAW -> Icons.Filled.AccountBalance
        PaymentType.AIRTIME -> Icons.Filled.PhoneAndroid
        PaymentType.MPESA_CARD -> Icons.Filled.CreditCard
        PaymentType.TRANSACTION_COST -> Icons.Filled.MonetizationOn
        PaymentType.BANK_DEBIT -> Icons.Filled.AccountBalance
    }
}

/**
 * Format timestamp to readable date
 */
fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} min ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
