/**
 * Payment Routes
 * 
 * Endpoints for initiating and querying M-PESA payments
 */

const express = require('express');
const router = express.Router();
const darajaService = require('../services/darajaService');
const paymentService = require('../services/paymentService');
const { validate } = require('../middleware/validation');

/**
 * POST /api/payment/initiate
 * Initiate STK Push payment
 */
router.post('/initiate', validate('initiatePayment'), async (req, res, next) => {
  try {
    const {
      phoneNumber,
      amount,
      paymentType,
      recipient,
      accountReference,
      transactionDesc,
      categoryId,
      notes
    } = req.body;

    console.log(`📱 Initiating ${paymentType} payment: KES ${amount} to ${recipient}`);

    // Initiate STK Push
    const result = await darajaService.initiateSTKPush({
      phoneNumber,
      amount,
      accountReference: accountReference || 'PesaTrack',
      transactionDesc: transactionDesc || 'Payment'
    });

    if (result.success) {
      // Store pending transaction with app-specific data (now async with DB)
      await paymentService.storePendingTransaction(result.checkoutRequestId, {
        phoneNumber,
        amount,
        paymentType,
        recipient,
        accountReference: accountReference || 'PesaTrack',
        transactionDesc: transactionDesc || 'Payment',
        categoryId,
        notes,
        merchantRequestId: result.merchantRequestId
      });

      console.log(`✅ STK Push sent: ${result.checkoutRequestId}`);
    }

    res.json({
      success: result.success,
      checkoutRequestId: result.checkoutRequestId,
      merchantRequestId: result.merchantRequestId,
      responseDescription: result.responseDescription,
      customerMessage: result.customerMessage
    });

  } catch (error) {
    next(error);
  }
});

/**
 * GET /api/payment/status/:checkoutRequestId
 * Query payment status
 *
 * Implements rate limiting to comply with Daraja API limits (5 req/60s)
 * Returns 429 with Retry-After header when rate limited
 */
router.get('/status/:checkoutRequestId', async (req, res, next) => {
  try {
    const { checkoutRequestId } = req.params;

    // First check local status (from database) - no rate limit consumed
    const localStatus = await paymentService.getTransactionStatus(checkoutRequestId);
    
    if (localStatus?.status === 'COMPLETED') {
      return res.json({
        success: true,
        status: 'COMPLETED',
        transaction: localStatus
      });
    }

    if (localStatus?.status === 'FAILED') {
      return res.json({
        success: false,
        status: 'FAILED',
        reason: localStatus.failureReason
      });
    }

    // Query M-PESA if still pending or unknown
    // This includes internal rate limiting and caching
    const mpesaStatus = await darajaService.querySTKStatus(checkoutRequestId);

    // Handle rate limiting - return 429 with Retry-After header
    if (mpesaStatus.rateLimited) {
      res.set('Retry-After', mpesaStatus.retryAfter.toString());
      return res.status(429).json({
        success: false,
        status: 'RATE_LIMITED',
        retryAfter: mpesaStatus.retryAfter,
        message: `Too many requests. Please retry after ${mpesaStatus.retryAfter} seconds.`
      });
    }

    // Log cache usage for debugging
    if (mpesaStatus.fromCache) {
      console.log(`📋 Returning cached status for ${checkoutRequestId}`);
    }

    res.json({
      success: mpesaStatus.success,
      status: mpesaStatus.success ? 'COMPLETED' : 'PENDING',
      resultCode: mpesaStatus.resultCode,
      resultDesc: mpesaStatus.resultDesc,
      fromCache: mpesaStatus.fromCache || false
    });

  } catch (error) {
    next(error);
  }
});

/**
 * GET /api/payment/transactions
 * Get all transactions with pagination
 * 
 * Query params:
 * - page: Page number (default: 1)
 * - limit: Items per page (default: 50)
 * - status: Filter by status (PENDING, COMPLETED, FAILED)
 */
router.get('/transactions', async (req, res, next) => {
  try {
    const { page, limit, status } = req.query;
    
    const result = await paymentService.getAllTransactions({
      page: page ? parseInt(page) : 1,
      limit: limit ? parseInt(limit) : 50,
      status
    });
    
    res.json(result);
  } catch (error) {
    next(error);
  }
});

/**
 * GET /api/payment/statistics
 * Get transaction statistics
 */
router.get('/statistics', async (req, res, next) => {
  try {
    const stats = await paymentService.getStatistics();
    res.json(stats);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
