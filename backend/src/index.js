// PesaTrack API — Phase 1 bootstrap
// See plans/ai-pro-phase1-spec.md §4

'use strict';

require('dotenv/config');

const express = require('express');
const cors = require('cors');

const config = require('./config');
const { requestId } = require('./middleware/requestId');
const { httpLogger, log } = require('./middleware/logger');
const { errorHandler, notFoundHandler } = require('./middleware/errors');

const healthRoutes = require('./routes/health');
const billingRoutes = require('./routes/billing');
const aiRoutes = require('./routes/ai');

const app = express();

// --- Core middleware ---
app.disable('x-powered-by');
app.set('trust proxy', 1); // behind Caddy on Hetzner
app.use(requestId);
app.use(cors({
  origin: (origin, cb) => {
    // Native Android client sends no Origin — allow.
    if (!origin) return cb(null, true);
    if (origin === config.allowedOriginWeb) return cb(null, true);
    return cb(new Error('CORS: origin not allowed'));
  },
  credentials: false,
  maxAge: 600,
}));
app.use(express.json({ limit: '256kb' }));
app.use(httpLogger);

// --- Routes ---
app.use('/health', healthRoutes);
app.use('/billing', billingRoutes);
app.use('/ai', aiRoutes);

// --- Error handlers (last) ---
app.use(notFoundHandler);
app.use(errorHandler);

// --- Boot ---
if (require.main === module) {
  const server = app.listen(config.port, () => {
    log.info({
      msg: 'pesatrack-api listening',
      port: config.port,
      env: config.nodeEnv,
      service: config.serviceName,
      version: config.serviceVersion,
      devFakeBilling: config.devFakeBilling,
      enableAiEndpoints: config.enableAiEndpoints,
      enableAiEcho: config.enableAiEcho,
    });
  });

  const shutdown = (signal) => {
    log.info({ msg: 'shutdown signal', signal });
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 10_000).unref();
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

module.exports = app;
