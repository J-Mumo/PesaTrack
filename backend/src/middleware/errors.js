// Global error + 404 handlers.

'use strict';

const { log } = require('./logger');

function notFoundHandler(req, res, _next) {
  res.status(404).json({
    error: 'not_found',
    request_id: req.id,
  });
}

// Express distinguishes error handlers by arity — keep 4 params.
// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, _next) {
  const status = Number.isInteger(err.status) ? err.status : 500;
  const code = typeof err.code === 'string' ? err.code : (status === 400 ? 'bad_request' : 'internal');

  log.error({
    msg: 'unhandled_error',
    request_id: req.id,
    status,
    code,
    error: err.message,
    // Only include stack in non-prod.
    stack: process.env.NODE_ENV === 'production' ? undefined : err.stack,
  });

  res.status(status).json({
    error: code,
    request_id: req.id,
  });
}

module.exports = { notFoundHandler, errorHandler };
