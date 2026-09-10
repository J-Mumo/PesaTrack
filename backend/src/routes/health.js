'use strict';

const express = require('express');
const config = require('../config');

const router = express.Router();
const startedAtMs = Date.now();

// Public — no auth. Used by Caddy healthcheck and uptime monitoring.
router.get('/', (_req, res) => {
  res.json({
    status: 'ok',
    service: config.serviceName,
    version: config.serviceVersion,
    uptime_ms: Date.now() - startedAtMs,
    env: config.nodeEnv,
  });
});

module.exports = router;
