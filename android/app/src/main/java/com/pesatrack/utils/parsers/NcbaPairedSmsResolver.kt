package com.pesatrack.utils.parsers

import com.pesatrack.domain.models.Expense
import com.pesatrack.domain.models.ExpenseSource
import com.pesatrack.domain.models.PaymentType
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Pure, shared live/import policy. Proximity alone never proves a card pairing. */
object NcbaPairedSmsResolver {
    const val SENDER = "NCBA_BANK"
    const val WINDOW_MS = 10 * 60 * 1000L

    /** Use the carrier date (inbox date_sent), falling back to inbox date when unavailable. */
    data class Message(val sender: String, val body: String, val timestamp: Long)
    data class Approval(val currency: String, val amount: BigDecimal, val merchant: String, val card: String)
    data class Debit(val amount: BigDecimal, val reference: String, val bodyTimestamp: Long, val message: Message)
    data class Resolution(val expense: Expense, val debit: Debit?)

    private val approvalPattern = Regex(
        "approved a transaction of ([A-Z]{3})\\s+([\\d,]+(?:\\.\\d{1,2})?)\\s+at\\s+(.+?)\\s+on your card no\\.\\s*ending\\s*\\*(\\d+)",
        RegexOption.IGNORE_CASE
    )
    private val debitPattern = Regex(
        "^Your account\\s+\\S+\\s+has been debited with KES\\.?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+on\\s+(\\d{2}/\\d{2}/\\d{4})\\s+at\\s+(\\d{2}:\\d{2}(?::\\d{2})?)\\.?\\s+Ref[:.]\\s*([A-Z0-9]+)(?=[\\s.,;]|$)",
        RegexOption.IGNORE_CASE
    )
    private val whitespace = Regex("\\s+")
    private val kenyaZone = ZoneId.of("Africa/Nairobi")

    fun isNcba(sender: String): Boolean = sender.trim().equals(SENDER, ignoreCase = true)

    fun parseApproval(message: Message): Approval? {
        if (!isNcba(message.sender)) return null
        val m = approvalPattern.find(message.body) ?: return null
        val amount = amount(m.groupValues[2]) ?: return null
        return Approval(m.groupValues[1].uppercase(Locale.ROOT), amount,
            m.groupValues[3].trim(), "*${m.groupValues[4]}")
    }

    fun parseDebit(message: Message): Debit? {
        if (!isNcba(message.sender)) return null
        val m = debitPattern.find(message.body.trim()) ?: return null
        val amount = amount(m.groupValues[1]) ?: return null
        val time = m.groupValues[3]
        val bodyDate = try {
            val format = if (time.length == 8) "dd/MM/uuuu HH:mm:ss" else "dd/MM/uuuu HH:mm"
            LocalDateTime.parse("${m.groupValues[2]} $time",
                DateTimeFormatter.ofPattern(format).withResolverStyle(ResolverStyle.STRICT))
                .atZone(kenyaZone).toInstant().toEpochMilli()
        } catch (_: Exception) { return null }
        return Debit(amount, m.groupValues[4].uppercase(Locale.ROOT), bodyDate, message)
    }

    /** Always approval-derived: a later debit must never change the persisted identity. */
    fun stableId(message: Message): String {
        val canonical = "${message.timestamp}|${message.body.trim().replace(whitespace, " ")}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "NCBA_CARD_" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun resolve(message: Message, inbox: List<Message>): Resolution? {
        val approval = parseApproval(message) ?: return null
        val messages = (inbox + message)
            .filter { isNcba(it.sender) && kotlin.math.abs(it.timestamp - message.timestamp) <= 2 * WINDOW_MS }
            .distinctBy { stableId(it) }
        val approvals = messages.mapNotNull { sms -> parseApproval(sms)?.let { sms to it } }
        // FTC is the observed card family, not a universal bank-reference guarantee.
        // Unknown families remain unpaired; FTX service/M-PESA debits cannot be attached.
        val rawDebits = messages.mapNotNull(::parseDebit)
        val debits = rawDebits.filter { it.reference.matches(Regex("FTC[A-Z0-9]{6,}")) }
            .groupBy { it.reference }.values.map { copies ->
                // Same reference denotes the same debit; choose deterministically, not inbox order.
                copies.minWith(compareBy<Debit> { it.message.timestamp }.thenBy { it.message.body })
            }
        fun eligible(sms: Message, card: Approval, debit: Debit): Boolean =
            kotlin.math.abs(sms.timestamp - debit.message.timestamp) <= WINDOW_MS &&
                kotlin.math.abs(sms.timestamp - debit.bodyTimestamp) <= WINDOW_MS &&
                (card.currency != "KES" || card.amount.compareTo(debit.amount) == 0)

        val candidates = debits.filter { eligible(message, approval, it) }
        val paired = candidates.singleOrNull()?.takeIf { debit ->
            approvals.count { (sms, card) -> eligible(sms, card, debit) } == 1 &&
                // Conflicting SMS with the same bank ref are not trustworthy evidence.
                rawDebits.filter { it.reference == debit.reference }
                    .all { it.amount.compareTo(debit.amount) == 0 && it.bodyTimestamp == debit.bodyTimestamp }
        }
        if (approval.currency != "KES" && paired == null) return null
        val kes = if (approval.currency == "KES") approval.amount else paired!!.amount
        val notes = "${approval.currency} ${approval.amount.toPlainString()} at ${approval.merchant} (Card ${approval.card})" +
            (paired?.let { "; Bank ref: ${it.reference}" } ?: "")
        return Resolution(Expense(
            transactionId = stableId(message), amount = kes.toDouble(), recipient = approval.card,
            recipientName = approval.merchant, paymentType = PaymentType.CARD_PAYMENT,
            source = ExpenseSource.SMS_BANK, notes = notes, timestamp = message.timestamp,
            rawSms = message.body, createdAt = message.timestamp, isCategorized = false
        ), paired)
    }

    private fun amount(text: String): BigDecimal? =
        text.replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
}