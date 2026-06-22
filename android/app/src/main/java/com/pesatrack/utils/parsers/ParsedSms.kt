package com.pesatrack.utils.parsers

import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.IncomeTransaction

/**
 * Result of parsing a single SMS body through an [SmsParserStrategy].
 *
 * Income tracking Phase 2 — replaces the previous nullable `ParsedTransaction?`
 * return so a parser can distinguish "this SMS is income" from "this SMS is
 * spend" from "this SMS is irrelevant" without a side-channel flag.
 */
sealed class ParsedSms {

    /**
     * An expense (outgoing money) the receiver should insert into the
     * expenses table.
     *
     * Mirrors the legacy [com.pesatrack.utils.SmsParser.ParsedTransaction]
     * shape so the migration is mechanical.
     */
    data class ExpenseResult(
        val expense: Expense,
        val transactionCost: Expense?,
        /**
         * When true, this represents a card approval SMS that should UPDATE an
         * existing CARD_PAYMENT expense (adding merchant name) rather than
         * inserting a new record. See [com.pesatrack.services.SmsReceiver] for
         * the lookup-and-merge logic.
         */
        val isCardApprovalUpdate: Boolean = false
    ) : ParsedSms()

    /**
     * An incoming-money SMS the receiver should insert into
     * `income_transactions`.
     */
    data class IncomeResult(
        val income: IncomeTransaction
    ) : ParsedSms()

    /**
     * The SMS was recognised by the parser (e.g. it's from a supported sender)
     * but is not actionable — typically reversal notices, balance reports, or
     * advertising. The receiver should ignore it.
     */
    data object NotARelevantMessage : ParsedSms()
}
