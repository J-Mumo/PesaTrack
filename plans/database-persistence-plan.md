# Backend Database Persistence Implementation Plan

## Overview

This document outlines the plan to migrate the PesaTrack backend from in-memory storage to persistent database storage using SQLite for development and PostgreSQL for production.

---

## Current State Analysis

### Current In-Memory Implementation

The current [`backend/src/services/paymentService.js`](../backend/src/services/paymentService.js:1) uses JavaScript `Map` objects for storage:

```javascript
const pendingTransactions = new Map();
const completedTransactions = new Map();
```

**Problems with current approach:**
1. **Data Loss on Restart**: All transactions are lost when server restarts
2. **No Query Capabilities**: Cannot filter, sort, or aggregate historical data
3. **Memory Limitations**: Large transaction volumes could exhaust memory
4. **No Audit Trail**: Cannot track transaction history for debugging

---

## Database Choice

### Development: SQLite
- **Pros**: Zero configuration, file-based, perfect for local development
- **Cons**: Limited concurrent writes (fine for dev)

### Production: PostgreSQL
- **Pros**: Robust, scalable, excellent for concurrent operations
- **Cons**: Requires setup/hosting

### ORM: Prisma
**Recommended** for this project because:
- Works with both SQLite and PostgreSQL with same code
- Auto-generates TypeScript types (even for JS projects)
- Built-in migrations
- Simple query API
- Great documentation

---

## Database Schema Design

### Entity Relationship Diagram

```mermaid
erDiagram
    Transaction ||--o| TransactionMetadata : has
    
    Transaction {
        string id PK
        string checkoutRequestId UK
        string merchantRequestId
        string phoneNumber
        decimal amount
        string paymentType
        string recipient
        string accountReference
        string transactionDesc
        int categoryId
        string notes
        string status
        string transactionId
        datetime transactionDate
        string failureReason
        datetime createdAt
        datetime updatedAt
        datetime completedAt
    }
    
    TransactionMetadata {
        int id PK
        string transactionId FK
        string key
        string value
        datetime createdAt
    }
```

### SQL Schema

```sql
-- transactions table
CREATE TABLE transactions (
    id TEXT PRIMARY KEY DEFAULT (hex(randomblob(16))),
    checkout_request_id TEXT UNIQUE NOT NULL,
    merchant_request_id TEXT,
    phone_number TEXT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_type TEXT NOT NULL CHECK(payment_type IN ('SEND_MONEY', 'BUY_GOODS', 'PAY_BILL')),
    recipient TEXT,
    account_reference TEXT,
    transaction_desc TEXT,
    category_id INTEGER,
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    mpesa_receipt_number TEXT,
    transaction_date DATETIME,
    failure_reason TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME
);

-- Index for common queries
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_checkout_id ON transactions(checkout_request_id);
CREATE INDEX idx_transactions_phone ON transactions(phone_number);
CREATE INDEX idx_transactions_created ON transactions(created_at);

-- transaction_metadata table for extensible data
CREATE TABLE transaction_metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    key TEXT NOT NULL,
    value TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metadata_transaction ON transaction_metadata(transaction_id);
```

---

## Implementation Plan

### Step 1: Install Dependencies

Add to `package.json`:

```json
{
  "dependencies": {
    "prisma": "^5.x",
    "@prisma/client": "^5.x"
  }
}
```

### Step 2: Initialize Prisma

```bash
cd backend
npx prisma init --datasource-provider sqlite
```

### Step 3: Create Prisma Schema

Create [`backend/prisma/schema.prisma`](../backend/prisma/schema.prisma):

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "sqlite"
  url      = env("DATABASE_URL")
}

model Transaction {
  id                  String    @id @default(uuid())
  checkoutRequestId   String    @unique @map("checkout_request_id")
  merchantRequestId   String?   @map("merchant_request_id")
  phoneNumber         String    @map("phone_number")
  amount              Decimal   @db.Decimal(10, 2)
  paymentType         String    @map("payment_type")
  recipient           String?
  accountReference    String?   @map("account_reference")
  transactionDesc     String?   @map("transaction_desc")
  categoryId          Int?      @map("category_id")
  notes               String?
  status              String    @default("PENDING")
  mpesaReceiptNumber  String?   @map("mpesa_receipt_number")
  transactionDate     DateTime? @map("transaction_date")
  failureReason       String?   @map("failure_reason")
  createdAt           DateTime  @default(now()) @map("created_at")
  updatedAt           DateTime  @updatedAt @map("updated_at")
  completedAt         DateTime? @map("completed_at")

  metadata TransactionMetadata[]

  @@index([status])
  @@index([phoneNumber])
  @@index([createdAt])
  @@map("transactions")
}

model TransactionMetadata {
  id            Int         @id @default(autoincrement())
  transactionId String      @map("transaction_id")
  key           String
  value         String?
  createdAt     DateTime    @default(now()) @map("created_at")

  transaction   Transaction @relation(fields: [transactionId], references: [id], onDelete: Cascade)

  @@index([transactionId])
  @@map("transaction_metadata")
}
```

### Step 4: Update Environment Configuration

Update [`backend/.env.example`](../backend/.env.example):

```env
# Database
DATABASE_URL="file:./dev.db"  # SQLite for development
# DATABASE_URL="postgresql://user:password@localhost:5432/pesatrack"  # PostgreSQL for production
```

### Step 5: Create Database Service Layer

#### New File: `backend/src/services/databaseService.js`

```javascript
const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient({
  log: process.env.NODE_ENV === 'development' ? ['query', 'error', 'warn'] : ['error'],
});

// Graceful shutdown
process.on('beforeExit', async () => {
  await prisma.$disconnect();
});

module.exports = prisma;
```

### Step 6: Refactor Payment Service

Replace in-memory Maps with Prisma queries in [`backend/src/services/paymentService.js`](../backend/src/services/paymentService.js):

```javascript
const prisma = require('./databaseService');

class PaymentService {
  /**
   * Store pending transaction
   */
  async storePendingTransaction(checkoutRequestId, transactionData) {
    return await prisma.transaction.create({
      data: {
        checkoutRequestId,
        merchantRequestId: transactionData.merchantRequestId,
        phoneNumber: transactionData.phoneNumber,
        amount: transactionData.amount,
        paymentType: transactionData.paymentType,
        recipient: transactionData.recipient,
        categoryId: transactionData.categoryId,
        notes: transactionData.notes,
        status: 'PENDING',
      },
    });
  }

  /**
   * Get pending transaction
   */
  async getPendingTransaction(checkoutRequestId) {
    return await prisma.transaction.findUnique({
      where: { checkoutRequestId },
    });
  }

  /**
   * Mark transaction as completed
   */
  async completeTransaction(checkoutRequestId, callbackData) {
    return await prisma.transaction.update({
      where: { checkoutRequestId },
      data: {
        status: 'COMPLETED',
        mpesaReceiptNumber: callbackData.transactionId,
        transactionDate: callbackData.transactionDate 
          ? new Date(callbackData.transactionDate) 
          : new Date(),
        completedAt: new Date(),
      },
    });
  }

  /**
   * Mark transaction as failed
   */
  async failTransaction(checkoutRequestId, reason) {
    return await prisma.transaction.update({
      where: { checkoutRequestId },
      data: {
        status: 'FAILED',
        failureReason: reason,
        completedAt: new Date(),
      },
    });
  }

  /**
   * Get transaction status
   */
  async getTransactionStatus(checkoutRequestId) {
    return await prisma.transaction.findUnique({
      where: { checkoutRequestId },
      select: {
        status: true,
        mpesaReceiptNumber: true,
        amount: true,
        transactionDate: true,
        failureReason: true,
        completedAt: true,
      },
    });
  }

  /**
   * Get all transactions (with pagination)
   */
  async getAllTransactions(options = {}) {
    const { page = 1, limit = 50, status } = options;
    
    const where = status ? { status } : {};
    
    const [transactions, total] = await Promise.all([
      prisma.transaction.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma.transaction.count({ where }),
    ]);

    return {
      transactions,
      pagination: {
        page,
        limit,
        total,
        pages: Math.ceil(total / limit),
      },
    };
  }

  /**
   * Get transactions by phone number
   */
  async getTransactionsByPhone(phoneNumber, options = {}) {
    const { limit = 20 } = options;
    
    return await prisma.transaction.findMany({
      where: { phoneNumber },
      orderBy: { createdAt: 'desc' },
      take: limit,
    });
  }

  // ... parseCallback remains unchanged (no database interaction)
}

module.exports = new PaymentService();
```

### Step 7: Update Routes to Use Async

Update [`backend/src/routes/payment.js`](../backend/src/routes/payment.js) for async operations:

```javascript
// Change synchronous calls to async/await
router.post('/initiate', validate('initiatePayment'), async (req, res, next) => {
  try {
    // ... existing code ...
    
    if (result.success) {
      // Now async
      await paymentService.storePendingTransaction(result.checkoutRequestId, {
        // ... data
      });
    }
    
    // ... rest of handler
  } catch (error) {
    next(error);
  }
});
```

### Step 8: Add Migration Commands

Update `package.json` scripts:

```json
{
  "scripts": {
    "start": "node src/index.js",
    "dev": "nodemon src/index.js",
    "db:migrate": "prisma migrate dev",
    "db:migrate:prod": "prisma migrate deploy",
    "db:generate": "prisma generate",
    "db:studio": "prisma studio",
    "db:reset": "prisma migrate reset",
    "postinstall": "prisma generate"
  }
}
```

---

## File Structure After Implementation

```
backend/
├── prisma/
│   ├── schema.prisma              # Database schema
│   ├── migrations/                # Auto-generated migrations
│   │   └── 20240115_init/
│   │       └── migration.sql
│   └── dev.db                     # SQLite database file (dev only)
├── src/
│   ├── index.js                   # Entry point
│   ├── config/
│   │   └── daraja.js
│   ├── routes/
│   │   ├── payment.js             # Updated for async
│   │   └── callback.js            # Updated for async
│   ├── services/
│   │   ├── darajaService.js       # No changes needed
│   │   ├── paymentService.js      # Refactored to use Prisma
│   │   └── databaseService.js     # NEW: Prisma client instance
│   ├── middleware/
│   │   └── validation.js
│   └── utils/
│       └── helpers.js
├── package.json
├── .env
└── .gitignore                     # Add: prisma/dev.db
```

---

## Migration Steps (Summary)

```mermaid
flowchart TD
    A[Step 1: Install Prisma] --> B[Step 2: Create Schema]
    B --> C[Step 3: Run Initial Migration]
    C --> D[Step 4: Create Database Service]
    D --> E[Step 5: Refactor Payment Service]
    E --> F[Step 6: Update Routes to Async]
    F --> G[Step 7: Test All Endpoints]
    G --> H[Step 8: Deploy]
```

### Execution Commands

```bash
# 1. Install dependencies
cd backend
npm install prisma @prisma/client

# 2. Initialize Prisma (creates prisma folder)
npx prisma init --datasource-provider sqlite

# 3. Create the schema (copy from above)
# Edit prisma/schema.prisma

# 4. Run migration
npx prisma migrate dev --name init

# 5. Generate Prisma Client
npx prisma generate

# 6. Implement code changes
# Update paymentService.js, routes, etc.

# 7. Test
npm run dev

# 8. View data (optional)
npx prisma studio
```

---

## Production Considerations

### Switching to PostgreSQL

1. Update `.env`:
   ```env
   DATABASE_URL="postgresql://user:password@host:5432/pesatrack?schema=public"
   ```

2. Update `prisma/schema.prisma`:
   ```prisma
   datasource db {
     provider = "postgresql"
     url      = env("DATABASE_URL")
   }
   ```

3. Run migration:
   ```bash
   npx prisma migrate deploy
   ```

### Hosting Options

| Provider | PostgreSQL | Free Tier | Notes |
|----------|------------|-----------|-------|
| Railway | ✅ | 500 MB | Easy setup |
| Render | ✅ | 90 days | Good for MVPs |
| Supabase | ✅ | 500 MB | Free forever |
| Neon | ✅ | 3 GB | Serverless |
| PlanetScale | MySQL only | 5 GB | Alternative |

---

## Testing the Migration

### Test Cases

1. **Create Transaction**
   ```bash
   curl -X POST http://localhost:3000/api/payment/initiate \
     -H "Content-Type: application/json" \
     -d '{"phoneNumber": "254712345678", "amount": 100, "paymentType": "BUY_GOODS"}'
   ```

2. **Query Status**
   ```bash
   curl http://localhost:3000/api/payment/status/ws_CO_123456789
   ```

3. **List Transactions**
   ```bash
   curl http://localhost:3000/api/payment/transactions
   ```

4. **Verify Persistence**
   - Stop server
   - Restart server
   - Query transactions - should still exist

---

## Rollback Plan

If issues arise:

1. Keep in-memory implementation as fallback
2. Use environment flag to switch:
   ```javascript
   const USE_DATABASE = process.env.USE_DATABASE === 'true';
   
   if (USE_DATABASE) {
     module.exports = new DatabasePaymentService();
   } else {
     module.exports = new InMemoryPaymentService();
   }
   ```

---

## Next Steps After Database Implementation

1. **Add Transaction History Endpoint**
   - `GET /api/transactions?phone=254...&status=COMPLETED`
   
2. **Add Analytics Endpoints**
   - `GET /api/analytics/summary` - Total amounts by category
   - `GET /api/analytics/monthly` - Monthly spending trends

3. **Implement Data Retention Policy**
   - Archive or delete old transactions after X months

4. **Add Database Backups**
   - Automated PostgreSQL backups in production
