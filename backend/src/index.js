require('dotenv').config();

const express = require('express');
const cors = require('cors');
const morgan = require('morgan');

const paymentRoutes = require('./routes/payment');
const callbackRoutes = require('./routes/callback');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(morgan('dev'));

// Routes
app.use('/api/payment', paymentRoutes);
app.use('/api/callback', callbackRoutes);

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ 
    status: 'ok', 
    timestamp: new Date().toISOString(),
    environment: process.env.MPESA_ENV || 'sandbox'
  });
});

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Error:', err.message);
  res.status(err.status || 500).json({
    success: false,
    error: err.message || 'Internal Server Error'
  });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({
    success: false,
    error: 'Endpoint not found'
  });
});

app.listen(PORT, () => {
  console.log(`🚀 PesaTrack Backend running on port ${PORT}`);
  console.log(`📱 Environment: ${process.env.MPESA_ENV || 'sandbox'}`);
});

module.exports = app;
