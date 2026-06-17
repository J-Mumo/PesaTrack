package com.pesatrack.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Transaction-level income record.
 *
 * One row per detected income event (SMS, statement import, manual entry).
 * Mirrors the [ExpenseEntity] contract: [transactionId] is unique so dedupe
 * across re-parses and statement re-imports is free.
 *
 * The `source` column stores an `IncomeSource` enum name (e.g. "SALARY",
 * "BUSINESS", "TRANSFER_IN"). Some sources have `isInflow = false`
 * (e.g. TRANSFER_IN for self-transfers) — those are counted in raw totals
 * but excluded from savings-rate denominators by the repository layer.
 */
@Entity(
    tableName = "income_transactions",
    indices = [
        Index(value = ["transactionId"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["source"])
    ]
)
data class IncomeTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** M-PESA confirmation code, bank reference, or generated id for manual entries. */
    val transactionId: String,

    /** Amount in KES. */
    val amount: Double,

    /** Epoch milliseconds. */
    val timestamp: Long,

    /** `IncomeSource` enum name. */
    val source: String,

    /** Raw counterparty name from SMS, when available. */
    val sender: String? = null,

    /** Full SMS body for audit / re-parsing. */
    val rawSms: String? = null,

    /** "MPESA" | "NCBA" | "KCB" | "EQUITY" | "MANUAL" | "STATEMENT_IMPORT" | "EXCEL_IMPORT". */
    val parserSource: String,

    /** Optional user note. */
    val note: String? = null,

    /**
     * Pass-through income the user explicitly wants ignored
     * (mirrors `expenses.isExcluded`).
     */
    val isExcluded: Boolean = false,

    /** Whether the user has confirmed / set the source. */
    val isCategorized: Boolean = false
)
