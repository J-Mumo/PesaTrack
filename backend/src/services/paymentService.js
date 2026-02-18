/**
 * Payment Service
 * 
 * Business logic for payment processing
 * Handles payment initiation and tracking
 */

// In-memory store for pending transactions
// In production, use a proper database
const pendingTransactions = new Map();
const completedTransactions = new Map();

class PaymentService {
  /**
   * Store pending transaction
   */
  storePendingTransaction(checkoutRequestId, transactionData) {
    pendingTransactions.set(checkoutRequestId, {
      ...transactionData,
      status: 'PENDING',
      createdAt: new Date().toISOString()
    });
  }

  /**
   * Get pending transaction
   */
  getPendingTransaction(checkoutRequestId) {
    return pendingTransactions.get(checkoutRequestId);
  }

  /**
   * Mark transaction as completed
   */
  completeTransaction(checkoutRequestId, callbackData) {
    const pending = pendingTransactions.get(checkoutRequestId);
    
    const completed = {
      ...(pending || {}),
      ...callbackData,
      status: 'COMPLETED',
      completedAt: new Date().toISOString()
    };

    completedTransactions.set(checkoutRequestId, completed);
    pendingTransactions.delete(checkoutRequestId);

    return completed;
  }

  /**
   * Mark transaction as failed
   */
  failTransaction(checkoutRequestId, reason) {
    const pending = pendingTransactions.get(checkoutRequestId);
    
    const failed = {
      ...(pending || {}),
      status: 'FAILED',
      failureReason: reason,
      failedAt: new Date().toISOString()
    };

    completedTransactions.set(checkoutRequestId, failed);
    pendingTransactions.delete(checkoutRequestId);

    return failed;
  }

  /**
   * Get transaction status
   */
  getTransactionStatus(checkoutRequestId) {
    // Check completed first
    if (completedTransactions.has(checkoutRequestId)) {
      return completedTransactions.get(checkoutRequestId);
    }
    
    // Check pending
    if (pendingTransactions.has(checkoutRequestId)) {
      return pendingTransactions.get(checkoutRequestId);
    }

    return null;
  }

  /**
   * Parse M-PESA callback data
   */
  parseCallback(callbackBody) {
    const stkCallback = callbackBody?.Body?.stkCallback;
    
    if (!stkCallback) {
      throw new Error('Invalid callback format');
    }

    const result = {
      merchantRequestId: stkCallback.MerchantRequestID,
      checkoutRequestId: stkCallback.CheckoutRequestID,
      resultCode: stkCallback.ResultCode,
      resultDesc: stkCallback.ResultDesc,
      success: stkCallback.ResultCode === 0
    };

    // Parse callback metadata if successful
    if (result.success && stkCallback.CallbackMetadata?.Item) {
      const items = stkCallback.CallbackMetadata.Item;
      
      items.forEach(item => {
        switch (item.Name) {
          case 'Amount':
            result.amount = item.Value;
            break;
          case 'MpesaReceiptNumber':
            result.transactionId = item.Value;
            break;
          case 'TransactionDate':
            result.transactionDate = this.parseTransactionDate(item.Value);
            break;
          case 'PhoneNumber':
            result.phoneNumber = item.Value.toString();
            break;
        }
      });
    }

    return result;
  }

  /**
   * Parse M-PESA transaction date
   * Format: 20240115123456 -> ISO date string
   */
  parseTransactionDate(dateNum) {
    const str = dateNum.toString();
    const year = str.substring(0, 4);
    const month = str.substring(4, 6);
    const day = str.substring(6, 8);
    const hour = str.substring(8, 10);
    const minute = str.substring(10, 12);
    const second = str.substring(12, 14);
    
    return new Date(
      `${year}-${month}-${day}T${hour}:${minute}:${second}+03:00`
    ).toISOString();
  }

  /**
   * Get all transactions (for debugging)
   */
  getAllTransactions() {
    return {
      pending: Array.from(pendingTransactions.entries()),
      completed: Array.from(completedTransactions.entries())
    };
  }
}

module.exports = new PaymentService();
