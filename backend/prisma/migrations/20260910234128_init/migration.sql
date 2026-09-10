-- CreateTable
CREATE TABLE "entitlements" (
    "id" TEXT NOT NULL,
    "purchase_token_hash" TEXT NOT NULL,
    "product_id" TEXT NOT NULL,
    "package_name" TEXT NOT NULL,
    "expiry_time_millis" BIGINT NOT NULL,
    "auto_renewing" BOOLEAN NOT NULL,
    "payment_state" INTEGER NOT NULL,
    "linked_purchase_token_hash" TEXT,
    "last_verified_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "entitlements_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "audit_events" (
    "id" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "purchase_token_hash" TEXT NOT NULL,
    "metadata" JSONB NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "audit_events_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "entitlements_purchase_token_hash_key" ON "entitlements"("purchase_token_hash");

-- CreateIndex
CREATE INDEX "entitlements_expiry_time_millis_idx" ON "entitlements"("expiry_time_millis");

-- CreateIndex
CREATE INDEX "audit_events_type_created_at_idx" ON "audit_events"("type", "created_at");

-- CreateIndex
CREATE INDEX "audit_events_purchase_token_hash_created_at_idx" ON "audit_events"("purchase_token_hash", "created_at");
