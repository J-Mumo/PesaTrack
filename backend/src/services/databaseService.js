/**
 * Database Service
 * 
 * Provides Prisma client instance for database operations.
 * Uses libSQL adapter for SQLite database in Prisma 7.
 * Handles connection management and graceful shutdown.
 */

// Load environment variables first
require('dotenv').config();

const { PrismaClient } = require('../generated/prisma');
const { PrismaLibSql } = require('@prisma/adapter-libsql');
const path = require('path');

// Get absolute path to database file (same as DATABASE_URL in .env: file:./dev.db)
const dbPath = path.resolve(__dirname, '../../dev.db');
// On Windows, file:/// needs to be used with the path starting at the drive letter
const normalizedPath = dbPath.replace(/\\/g, '/');
const libsqlUrl = `file:///${normalizedPath}`;

console.log(`📦 Using database: ${libsqlUrl}`);

// Create Prisma adapter directly with URL (Prisma 7 pattern)
// PrismaLibSql now accepts url/authToken directly instead of a libsql client
const adapter = new PrismaLibSql({
  url: libsqlUrl,
});

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
