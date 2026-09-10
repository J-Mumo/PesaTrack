/**
 * Payment Service
 * 
 * Business logic for payment processing
 * Handles payment initiation, tracking, and persistence using Prisma
 */

const { prisma } = require('./databaseService');

class PaymentService {
  /**
   * Store pending transaction in database
   */
  async storePendingTransaction(checkoutRequestId, transactionData) {
    try {
      const transaction = await prisma.transaction.create({
        data: {
          checkoutRequestId,
          merchantRequestId: transactionData.merchantRequestId,
          phoneNumber: transactionData.phoneNumber,
          amount: transactionData.amount,
          paymentType: transactionData.paymentType || 'UNKNOWN',
          recipient: transactionData.recipient,
          accountReference: transactionData.accountReference,
          transactionDesc: transactionData.transactionDesc,
          categoryId: transactionData.categoryId,
          notes: transactionData.notes,
          status: 'PENDING',
        },
      });
      
      console.log(`💾 Transaction stored: ${checkoutRequestId}`);
      return transaction;
    } catch (error) {
      console.error('Error storing transaction:', error.message);
      throw error;
    }
  }

  /**
   * Get pending transaction from database
   */
  async getPendingTransaction(checkoutRequestId) {
    try {
      return await prisma.transaction.findUnique({
        where: { checkoutRequestId },
      });
    } catch (error) {
      console.error('Error getting transaction:', error.message);
      return null;
    }
  }

  /**
   * Mark transaction as completed
   */
  async completeTransaction(checkoutRequestId, callbackData) {
    try {
      const completed = await prisma.transaction.update({
        where: { checkoutRequestId },
        data: {
          status: 'COMPLETED',
          mpesaReceiptNumber: callbackData.transactionId,
          transactionDate: callbackData.transactionDate 
            ? new Date(callbackData.transactionDate) 
            : new Date(),
          completedAt: new Date(),
        },
      });

      console.log(`✅ Transaction completed in DB: ${callbackData.transactionId}`);
      return completed;
    } catch (error) {
      console.error('Error completing transaction:', error.message);
      throw error;
    }
  }

  /**
   * Mark transaction as failed
   */
  async failTransaction(checkoutRequestId, reason) {
    try {
      const failed = await prisma.transaction.update({
        where: { checkoutRequestId },
        data: {
          status: 'FAILED',
          failureReason: reason,
          completedAt: new Date(),
        },
      });

      console.log(`❌ Transaction failed in DB: ${checkoutRequestId}`);
      return failed;
    } catch (error) {
      console.error('Error failing transaction:', error.message);
      throw error;
    }
  }

  /**
   * Get transaction status from database
   */
  async getTransactionStatus(checkoutRequestId) {
    try {
      const transaction = await prisma.transaction.findUnique({
        where: { checkoutRequestId },
        select: {
          id: true,
          status: true,
          mpesaReceiptNumber: true,
          amount: true,
          transactionDate: true,
          failureReason: true,
          completedAt: true,
          phoneNumber: true,
          paymentType: true,
          recipient: true,
          categoryId: true,
        },
      });

      if (!transaction) {
        return null;
      }

      return {
        ...transaction,
        transactionId: transaction.mpesaReceiptNumber,
      };
    } catch (error) {
      console.error('Error getting transaction status:', error.message);
      return null;
    }
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
   * Get all transactions with pagination
   */
  async getAllTransactions(options = {}) {
    const { page = 1, limit = 50, status } = options;
    
    try {
      const where = status ? { status } : {};
      
      const [transactions, total] = await Promise.all([
        prisma.transaction.findMany({
          where,
          orderBy: { createdAt: 'desc' },
          skip: (page - 1) * limit,
          take: limit,
        }),
        prisma.transaction.count({ where }),
      ]);

      return {
        transactions,
        pagination: {
          page,
          limit,
          total,
          pages: Math.ceil(total / limit),
        },
      };
    } catch (error) {
      console.error('Error getting all transactions:', error.message);
      return {
        transactions: [],
        pagination: { page: 1, limit, total: 0, pages: 0 },
      };
    }
  }

  /**
   * Get transactions by phone number
   */
  async getTransactionsByPhone(phoneNumber, options = {}) {
    const { limit = 20 } = options;
    
    try {
      return await prisma.transaction.findMany({
        where: { phoneNumber },
        orderBy: { createdAt: 'desc' },
        take: limit,
      });
    } catch (error) {
      console.error('Error getting transactions by phone:', error.message);
      return [];
    }
  }

  /**
   * Get transaction statistics
   */
  async getStatistics() {
    try {
      const [total, completed, failed, pending, totalAmount] = await Promise.all([
        prisma.transaction.count(),
        prisma.transaction.count({ where: { status: 'COMPLETED' } }),
        prisma.transaction.count({ where: { status: 'FAILED' } }),
        prisma.transaction.count({ where: { status: 'PENDING' } }),
        prisma.transaction.aggregate({
          where: { status: 'COMPLETED' },
          _sum: { amount: true },
        }),
      ]);

      return {
        total,
        completed,
        failed,
        pending,
        totalAmount: totalAmount._sum.amount || 0,
      };
    } catch (error) {
      console.error('Error getting statistics:', error.message);
      return {
        total: 0,
        completed: 0,
        failed: 0,
        pending: 0,
        totalAmount: 0,
      };
    }
  }
}

module.exports = new PaymentService();
