/**
 * Request Validation Middleware
 * 
 * Uses Joi for request validation
 */

const Joi = require('joi');

// Kenyan phone number validation
const phoneRegex = /^(?:254|\+254|0)?([71]\d{8})$/;

// Validation schemas
const schemas = {
  initiatePayment: Joi.object({
    phoneNumber: Joi.string()
      .pattern(phoneRegex)
      .required()
      .messages({
        'string.pattern.base': 'Invalid Kenyan phone number format',
        'any.required': 'Phone number is required'
      }),
    amount: Joi.number()
      .min(1)
      .max(150000)
      .required()
      .messages({
        'number.min': 'Amount must be at least KES 1',
        'number.max': 'Amount cannot exceed KES 150,000',
        'any.required': 'Amount is required'
      }),
    paymentType: Joi.string()
      .valid('SEND_MONEY', 'BUY_GOODS', 'PAY_BILL')
      .required()
      .messages({
        'any.only': 'Invalid payment type',
        'any.required': 'Payment type is required'
      }),
    recipient: Joi.string()
      .required()
      .messages({
        'any.required': 'Recipient is required'
      }),
    accountReference: Joi.string()
      .max(12)
      .default('PesaTrack'),
    transactionDesc: Joi.string()
      .max(13)
      .default('Payment'),
    // App-specific fields (for tracking)
    categoryId: Joi.number().optional(),
    notes: Joi.string().max(500).optional()
  }),

  queryStatus: Joi.object({
    checkoutRequestId: Joi.string()
      .required()
      .messages({
        'any.required': 'Checkout Request ID is required'
      })
  })
};

/**
 * Validation middleware factory
 */
const validate = (schemaName) => {
  return (req, res, next) => {
    const schema = schemas[schemaName];
    
    if (!schema) {
      return next(new Error(`Unknown validation schema: ${schemaName}`));
    }

    const { error, value } = schema.validate(req.body, {
      abortEarly: false,
      stripUnknown: true
    });

    if (error) {
      const errors = error.details.map(detail => ({
        field: detail.path.join('.'),
        message: detail.message
      }));

      return res.status(400).json({
        success: false,
        error: 'Validation failed',
        details: errors
      });
    }

    // Replace body with validated value
    req.body = value;
    next();
  };
};

module.exports = { validate, schemas };
