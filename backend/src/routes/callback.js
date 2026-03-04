/**
 * Callback Routes
 * 
 * Handles M-PESA callback notifications
 */

const express = require('express');
const router = express.Router();
const paymentService = require('../services/paymentService');

// Store for real-time listeners (SSE)
const listeners = new Map();

/**
 * POST /api/callback/mpesa
 * Receive M-PESA STK Push callback
 * 
 * This endpoint is called by Safaricom after payment completion
 */
router.post('/mpesa', async (req, res) => {
  console.log('📩 M-PESA Callback received');
  console.log(JSON.stringify(req.body, null, 2));

  try {
    // Parse callback data
    const callbackData = paymentService.parseCallback(req.body);
    
    console.log(`📋 Parsed callback:`, callbackData);

    if (callbackData.success) {
      // Mark transaction as completed (now async with DB)
      const completed = await paymentService.completeTransaction(
        callbackData.checkoutRequestId,
        callbackData
      );
      
      console.log(`✅ Transaction completed: ${callbackData.transactionId}`);
      
      // Notify any waiting listeners
      notifyListeners(callbackData.checkoutRequestId, {
        status: 'COMPLETED',
        transaction: completed
      });
    } else {
      // Mark transaction as failed (now async with DB)
      const failed = await paymentService.failTransaction(
        callbackData.checkoutRequestId,
        callbackData.resultDesc
      );
      
      console.log(`❌ Transaction failed: ${callbackData.resultDesc}`);
      
      // Notify any waiting listeners
      notifyListeners(callbackData.checkoutRequestId, {
        status: 'FAILED',
        reason: callbackData.resultDesc
      });
    }

    // Always respond with success to M-PESA
    res.json({ ResultCode: 0, ResultDesc: 'Success' });

  } catch (error) {
    console.error('Callback processing error:', error);
    // Still respond with success to avoid M-PESA retries
    res.json({ ResultCode: 0, ResultDesc: 'Accepted' });
  }
});

/**
 * GET /api/callback/listen/:checkoutRequestId
 * Server-Sent Events endpoint for real-time payment updates
 * 
 * Android app can connect to this to receive instant payment notifications
 */
router.get('/listen/:checkoutRequestId', async (req, res) => {
  const { checkoutRequestId } = req.params;
  
  // Set up SSE
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');

  // Send initial connection message
  res.write(`data: ${JSON.stringify({ status: 'CONNECTED' })}\n\n`);

  // Check if already completed (now async with DB)
  try {
    const existingStatus = await paymentService.getTransactionStatus(checkoutRequestId);
    if (existingStatus?.status === 'COMPLETED' || existingStatus?.status === 'FAILED') {
      res.write(`data: ${JSON.stringify(existingStatus)}\n\n`);
      res.end();
      return;
    }
  } catch (error) {
    console.error('Error checking transaction status:', error);
  }

  // Store listener
  listeners.set(checkoutRequestId, res);

  // Clean up on close
  req.on('close', () => {
    listeners.delete(checkoutRequestId);
  });

  // Timeout after 2 minutes
  setTimeout(() => {
    if (listeners.has(checkoutRequestId)) {
      res.write(`data: ${JSON.stringify({ status: 'TIMEOUT' })}\n\n`);
      res.end();
      listeners.delete(checkoutRequestId);
    }
  }, 120000);
});

/**
 * Notify listening clients of payment result
 */
function notifyListeners(checkoutRequestId, data) {
  const listener = listeners.get(checkoutRequestId);
  if (listener) {
    listener.write(`data: ${JSON.stringify(data)}\n\n`);
    listener.end();
    listeners.delete(checkoutRequestId);
  }
}

module.exports = router;
