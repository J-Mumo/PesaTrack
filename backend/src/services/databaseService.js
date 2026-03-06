/**
 * Database Service
 * 
 * Provides Prisma client instance for database operations.
 * Uses libSQL adapter for SQLite database in Prisma 7.
 * Handles connection management and graceful shutdown.
 * 
 * Supports both local development (file-based SQLite) and
 * cloud deployment (Railway volume or libSQL remote URL).
 */

// Load environment variables first
require('dotenv').config();

const { PrismaClient } = require('../generated/prisma');
const { PrismaLibSql } = require('@prisma/adapter-libsql');
const path = require('path');

/**
 * Determine the database URL based on environment.
 * 
 * Priority:
 * 1. LIBSQL_URL env var (for remote libSQL/Turso databases)
 * 2. DATABASE_URL env var with file: prefix (for custom SQLite path)
 * 3. Default: file-based SQLite at backend/data/pesatrack.db (production)
 *    or backend/dev.db (development)
 */
function getDatabaseUrl() {
  // Remote libSQL database (Turso or self-hosted)
  if (process.env.LIBSQL_URL) {
    console.log(`📦 Using remote database: ${process.env.LIBSQL_URL}`);
    return process.env.LIBSQL_URL;
  }

  // Custom DATABASE_URL (e.g., file:./dev.db)
  if (process.env.DATABASE_URL && process.env.DATABASE_URL.startsWith('file:')) {
    const relativePath = process.env.DATABASE_URL.replace('file:', '');
    const absolutePath = path.resolve(__dirname, '../..', relativePath);
    const normalizedPath = absolutePath.replace(/\\/g, '/');
    // libSQL file protocol: file: on Linux, file:/// on Windows
    const prefix = process.platform === 'win32' ? 'file:///' : 'file:';
    const url = `${prefix}${normalizedPath}`;
    console.log(`📦 Using database: ${url}`);
    return url;
  }

  // Default: use data/ directory for persistence on Railway volumes
  const dbDir = process.env.NODE_ENV === 'production'
    ? path.resolve(__dirname, '../../data')
    : path.resolve(__dirname, '../..');
  
  const dbFile = process.env.NODE_ENV === 'production'
    ? 'pesatrack.db'
    : 'dev.db';
  
  const dbPath = path.join(dbDir, dbFile);
  const normalizedPath = dbPath.replace(/\\/g, '/');
  // libSQL file protocol: file: on Linux, file:/// on Windows
  const prefix = process.platform === 'win32' ? 'file:///' : 'file:';
  const url = `${prefix}${normalizedPath}`;
  console.log(`📦 Using database: ${url}`);
  return url;
}

const dbUrl = getDatabaseUrl();

// Create Prisma adapter with libSQL
const adapterOptions = { url: dbUrl };

// Add auth token for remote databases (Turso)
if (process.env.LIBSQL_AUTH_TOKEN) {
  adapterOptions.authToken = process.env.LIBSQL_AUTH_TOKEN;
}

const adapter = new PrismaLibSql(adapterOptions);

// Create Prisma client with adapter
const prisma = new PrismaClient({
  adapter,
  log: process.env.NODE_ENV === 'development' 
    ? ['error', 'warn'] 
    : ['error'],
});

// Test database connection on startup
async function connectDatabase() {
  try {
    // Ensure data directory exists in production
    if (process.env.NODE_ENV === 'production') {
      const fs = require('fs');
      const dataDir = path.resolve(__dirname, '../../data');
      if (!fs.existsSync(dataDir)) {
        fs.mkdirSync(dataDir, { recursive: true });
        console.log('📁 Created data directory:', dataDir);
      }
    }

    // Test connection by running a simple query
    await prisma.$queryRaw`SELECT 1`;
    console.log('📦 Database connected successfully');
    return true;
  } catch (error) {
    console.error('❌ Database connection failed:', error.message);
    return false;
  }
}

// Graceful shutdown
async function disconnectDatabase() {
  await prisma.$disconnect();
  console.log('📦 Database disconnected');
}

// Handle process termination
process.on('beforeExit', async () => {
  await disconnectDatabase();
});

process.on('SIGINT', async () => {
  await disconnectDatabase();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  await disconnectDatabase();
  process.exit(0);
});

module.exports = {
  prisma,
  connectDatabase,
  disconnectDatabase,
};
