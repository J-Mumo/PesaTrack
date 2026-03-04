-- CreateTable
CREATE TABLE "transactions" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "checkout_request_id" TEXT NOT NULL,
    "merchant_request_id" TEXT,
    "phone_number" TEXT NOT NULL,
    "amount" REAL NOT NULL,
    "payment_type" TEXT NOT NULL,
    "recipient" TEXT,
    "account_reference" TEXT,
    "transaction_desc" TEXT,
    "category_id" INTEGER,
    "notes" TEXT,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "mpesa_receipt_number" TEXT,
    "transaction_date" DATETIME,
    "failure_reason" TEXT,
    "created_at" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" DATETIME NOT NULL,
    "completed_at" DATETIME
);

-- CreateTable
CREATE TABLE "transaction_metadata" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "transaction_id" TEXT NOT NULL,
    "key" TEXT NOT NULL,
    "value" TEXT,
    "created_at" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "transaction_metadata_transaction_id_fkey" FOREIGN KEY ("transaction_id") REFERENCES "transactions" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateIndex
CREATE UNIQUE INDEX "transactions_checkout_request_id_key" ON "transactions"("checkout_request_id");

-- CreateIndex
CREATE INDEX "transactions_status_idx" ON "transactions"("status");

-- CreateIndex
CREATE INDEX "transactions_phone_number_idx" ON "transactions"("phone_number");

-- CreateIndex
CREATE INDEX "transactions_created_at_idx" ON "transactions"("created_at");

-- CreateIndex
CREATE INDEX "transaction_metadata_transaction_id_idx" ON "transaction_metadata"("transaction_id");
