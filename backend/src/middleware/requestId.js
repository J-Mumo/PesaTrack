'use strict';

const { randomUUID } = require('node:crypto');

const HEADER = 'x-request-id';

function requestId(req, res, next) {
  const incoming = req.headers[HEADER];
  const id = typeof incoming === 'string' && incoming.length > 0 && incoming.length <= 128
    ? incoming
    : randomUUID();
  req.id = id;
  res.setHeader(HEADER, id);
  next();
}

module.exports = { requestId, HEADER };
