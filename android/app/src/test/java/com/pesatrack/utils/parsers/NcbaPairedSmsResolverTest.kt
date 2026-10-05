package com.pesatrack.utils.parsers

import com.pesatrack.domain.models.PaymentType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NcbaPairedSmsResolverTest {
    private val time = LocalDateTime.of(2026, 10, 5, 10, 10)
        .atZone(ZoneId.of("Africa/Nairobi")).toInstant().toEpochMilli()
    private fun approval(currency: String = "KES", amount: String = "1,298.00", merchant: String = "SIMBISA BRANDS SIGONA", date: Long = time) =
        NcbaPairedSmsResolver.Message("NCBA_BANK", "Joel, we have approved a transaction of $currency  $amount at $merchant on your card no. ending *6283. If its not yours, please call us", date)
    private fun debit(amount: String = "1,298.00", ref: String = "FTC261004PDTEQF", date: Long = time - 60_000) =
        NcbaPairedSmsResolver.Message("NCBA_BANK", "Your account 763****018 has been debited with KES $amount on 05/10/2026 at 10:09. Ref: $ref.", date)
    private fun resolve(a: NcbaPairedSmsResolver.Message, vararg messages: NcbaPairedSmsResolver.Message) =
        NcbaPairedSmsResolver.resolve(a, messages.toList())

    @Test fun `exact card pair does not borrow adjacent electricity amount or reference`() {
        val a = approval()
        val r = resolve(a, debit("1,500.00", "FTX26278PAFWI", time), debit())!!
        assertEquals(1298.0, r.expense.amount, 0.0)
        assertEquals("FTC261004PDTEQF", r.debit!!.reference)
        assertEquals(time - 60_000, r.debit.bodyTimestamp)
        assertEquals("SIMBISA BRANDS SIGONA", r.expense.recipientName)
        assertEquals("*6283", r.expense.recipient)
        assertEquals(PaymentType.CARD_PAYMENT, r.expense.paymentType)
        assertTrue(r.expense.notes!!.contains("FTC261004PDTEQF"))
    }

    @Test fun `KES approval alone is safe and replay identity never changes with later pairing`() {
        val a = approval()
        val alone = resolve(a)!!.expense
        assertEquals(1298.0, alone.amount, 0.0)
        assertEquals(alone.transactionId, resolve(a, debit())!!.expense.transactionId)
        assertEquals(alone.timestamp, resolve(a, debit())!!.expense.timestamp)
        assertEquals(alone.transactionId, resolve(a, debit().copy(timestamp = time + 60_000))!!.expense.transactionId)
    }

    @Test fun `distinct repeated purchases keep different approval identities`() {
        assertNotEquals(resolve(approval())!!.expense.transactionId,
            resolve(approval(date = time + 1000))!!.expense.transactionId)
    }

    @Test fun `KES mismatched amount remains authoritative`() {
        val r = resolve(approval(), debit("1,500.00"))!!
        assertNull(r.debit)
        assertEquals(1298.0, r.expense.amount, 0.0)
    }

    @Test fun `same amount FTX and unknown reference families never pair`() {
        for (ref in listOf("FTX26278PAFWI", "OTHER123456")) {
            assertNull(resolve(approval(), debit(ref = ref))!!.debit)
            assertNull(resolve(approval("USD", "11.60"), debit(ref = ref)))
        }
    }

    @Test fun `foreign approval requires KES debit rather than pretending USD is KES`() {
        val a = approval("USD", "11.60", "OPENAI")
        assertNull(resolve(a))
        val r = resolve(a, debit("1,574.87"))!!
        assertEquals(1574.87, r.expense.amount, 0.0)
        assertTrue(r.expense.notes!!.contains("USD 11.60"))
    }

    @Test fun `multiple eligible debits reject pairing regardless of order or distance`() {
        for (a in listOf(approval(), approval("USD", "11.60"))) {
            val first = debit()
            val second = debit(ref = "FTC261004SECOND", date = time + 90_000)
            val one = resolve(a, first, second)
            val two = resolve(a, second, first)
            if (a == approval()) {
                assertNull(one!!.debit)
                assertEquals(one.expense, two!!.expense)
            } else {
                assertNull(one)
                assertNull(two)
            }
        }
    }

    @Test fun `one debit competing with two approvals rejects both`() {
        val a = approval("USD", "11.60", "OPENAI")
        val b = approval("USD", "9.99", "OTHER", time + 30_000)
        assertNull(resolve(a, debit(), b))
        assertNull(resolve(b, a, debit()))
        assertNull(resolve(approval(), debit(), approval(merchant = "OTHER"))!!.debit)
    }

    @Test fun `foreign competitors prevent attaching an apparently matching KES debit`() {
        assertNull(resolve(approval(), debit(), approval("USD", "11.60"))!!.debit)
    }

    @Test fun `duplicate inbox rows are not ambiguity`() {
        assertNotNull(resolve(approval(), debit(), debit(), approval())!!.debit)
    }

    @Test fun `same reference redeliveries are resolved independently of inbox order`() {
        val a = approval()
        val original = debit()
        val duplicate = original.copy(timestamp = time + 1000)
        assertEquals(resolve(a, original, duplicate), resolve(a, duplicate, original))
    }

    @Test fun `conflicting amount or date with same ref rejects pairing`() {
        val a = approval("USD", "11.60")
        assertNull(resolve(a, debit(), debit("1,500.00")))
        assertNull(resolve(a, debit(), debit().copy(body = debit().body.replace("10:09", "10:08"))))
    }

    @Test fun `only exact recognized sender is usable`() {
        for (sender in listOf("OTHER_BANK", "FAKE_NCBA_BANK", "NCBA")) {
            assertNull(resolve(approval().copy(sender = sender), debit()))
            assertNull(resolve(approval("USD", "11.60"), debit().copy(sender = sender)))
        }
        assertNotNull(resolve(approval().copy(sender = "ncba_bank"), debit()))
    }

    @Test fun `old alerts and impossible body dates are rejected`() {
        val a = approval("USD", "11.60")
        assertNull(resolve(a, debit(date = time - NcbaPairedSmsResolver.WINDOW_MS - 1)))
        assertNull(resolve(a, debit().copy(body = debit().body.replace("05/10/2026", "31/02/2026"))))
        assertNull(resolve(a, debit().copy(body = debit().body.replace("05/10/2026", "04/10/2026"))))
    }

    @Test fun `approval whitespace is canonicalized in deterministic ID`() {
        val a = approval()
        assertEquals(NcbaPairedSmsResolver.stableId(a), NcbaPairedSmsResolver.stableId(a.copy(body = a.body.replace("KES  ", "KES "))))
    }

    @Test fun `historical replay and both arrival orders capture each sample once`() {
        val a = approval()
        val power = NcbaPairedSmsResolver.Message("NCBA_BANK", "Dear Customer, your Kenya Power Prepaid payment of KES 1500.00 to Meter Number: 92106709873 was successful. Token number: 19277682111301081334. Units: 59.7. Ref: FTX26278PAFWI.", time)
        val genericPower = debit("1,500.00", "FTX26278PAFWI")
        for (inbox in listOf(listOf(power, genericPower, debit(), a), listOf(a, debit(), genericPower, power))) {
            val rows = linkedMapOf<String, Double>() // models Room's unique-ID + IGNORE policy
            val parser = NcbaBankParser()
            for (sms in inbox + inbox) {
                val parsed = parser.parseSms(sms.body, sms.timestamp)
                if (parsed !is ParsedSms.ExpenseResult) continue
                val expense = if (parsed.isCardApprovalUpdate) resolve(sms, *inbox.toTypedArray())!!.expense else parsed.expense
                rows.putIfAbsent(expense.transactionId!!, expense.amount)
            }
            assertEquals(2, rows.size)
            assertEquals(2798.0, rows.values.sum(), 0.0)
        }
    }

    @Test fun `foreign parser emits provisional zero not a foreign KES fallback`() {
        val a = approval("USD", "11.60")
        val p = NcbaBankParser().parseSms(a.body, a.timestamp) as ParsedSms.ExpenseResult
        assertTrue(p.isCardApprovalUpdate)
        assertEquals(0.0, p.expense.amount, 0.0)
    }
}