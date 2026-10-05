package com.pesatrack.services

import com.pesatrack.domain.models.RecurringExpense
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.Locale

/** Local sensitive identifiers, not anonymized and never suitable for telemetry. */
object RecurringPaymentIdentity {
    private const val PREFIX = "recurring_identity_v1:"
    fun normalize(value: String): String = value.trim().uppercase(Locale.ROOT)
        .trimEnd('.').trim().replace(Regex("\\s+"), " ")

    // Existing parsers store PAY_BILL's account in recipient and business in recipientName.
    // Other payment types prefer the stored number/till; name-only data necessarily follows name edits.
    fun create(paymentType: String, recipient: String, recipientName: String?): String {
        val type = normalize(paymentType)
        val stored = normalize(recipient)
        val name = normalize(recipientName.orEmpty())
        val parts = if (type == "PAY_BILL") listOf(type, name.ifBlank { stored }, stored)
            else listOf(type, stored.ifBlank { name }, "")
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out -> parts.forEach { out.writeUTF(it) } }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())
    }

    fun isValid(identity: String): Boolean = runCatching {
        require(identity.startsWith(PREFIX) && identity.length <= 8192)
        DataInputStream(ByteArrayInputStream(Base64.getUrlDecoder().decode(identity.removePrefix(PREFIX)))).use {
            val type = it.readUTF()
            val recipient = it.readUTF()
            val account = it.readUTF()
            require(type.isNotBlank() && recipient.isNotBlank() && it.available() == 0)
            require(create(type, if (type == "PAY_BILL") account else recipient, recipient) == identity)
        }
    }.isSuccess

    fun maskedAccount(paymentType: String, recipient: String): String? =
        if (normalize(paymentType) == "PAY_BILL" && recipient.isNotBlank())
            "Account ••••${normalize(recipient).takeIf { it.length > 4 }?.takeLast(4).orEmpty()}" else null
}

enum class RecurringReminderOverride { ENABLED, DISABLED }

data class RecurringReminderSelections(
    val overrides: Map<String, RecurringReminderOverride> = emptyMap(),
    val present: Boolean = false,
    val valid: Boolean = true
)

/** Binary length-prefixed fields avoid delimiter collisions and JSON Android stubs in JVM tests. */
object RecurringReminderOverridesCodec {
    const val MAX_SERIALIZED_LENGTH = 1_000_000
    private const val MAX_ENTRIES = 2000
    fun encode(overrides: Map<String, RecurringReminderOverride>): String {
        require(overrides.size <= MAX_ENTRIES)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(overrides.size)
            overrides.toSortedMap().forEach { (key, value) ->
                require(RecurringPaymentIdentity.isValid(key))
                out.writeUTF(key)
                out.writeUTF(value.name)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray()).also {
            require(it.length <= MAX_SERIALIZED_LENGTH)
        }
    }

    fun decode(raw: String?): RecurringReminderSelections {
        if (raw == null) return RecurringReminderSelections()
        return runCatching {
            require(raw.length <= MAX_SERIALIZED_LENGTH)
            val result = linkedMapOf<String, RecurringReminderOverride>()
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(raw))).use { input ->
                require(input.readInt() == 1)
                val count = input.readInt()
                require(count in 0..MAX_ENTRIES)
                repeat(count) {
                    val key = input.readUTF()
                    require(RecurringPaymentIdentity.isValid(key) && key !in result)
                    result[key] = RecurringReminderOverride.valueOf(input.readUTF())
                }
                require(input.available() == 0)
            }
            RecurringReminderSelections(result, present = true)
        }.getOrElse { RecurringReminderSelections(present = true, valid = false) }
    }
}

data class RecurringReminderDecision(val selected: Boolean, val canDeliver: Boolean, val overridden: Boolean)

object RecurringReminderPolicy {
    val defaultCategories = setOf(1009L, 1002L, 1012L, 1007L, 1004L)
    fun resolve(master: Boolean, categoryId: Long?, categorySupport: Double, confidence: Double,
                override: RecurringReminderOverride?): RecurringReminderDecision {
        val eligible = categoryId != 606L
        val selected = eligible && when (override) {
            RecurringReminderOverride.ENABLED -> true
            RecurringReminderOverride.DISABLED -> false
            null -> categoryId in defaultCategories && categorySupport >= 0.7
        }
        return RecurringReminderDecision(selected, master && selected && confidence >= 0.7, override != null)
    }

    fun resolve(master: Boolean, payment: RecurringExpense, selections: RecurringReminderSelections) =
        resolve(master, payment.categoryId, payment.categorySupport, payment.confidence,
            selections.overrides[payment.recipientKey])

    fun cooldownElapsed(lastSent: Long, cycleDays: Int, now: Long): Boolean =
        lastSent == 0L || now - lastSent >= (cycleDays - 2).coerceAtLeast(1) * 86_400_000L

    fun migratedTimestamp(legacyTimes: List<Long>, ambiguous: Boolean, now: Long): Long =
        if (ambiguous && legacyTimes.any { it > 0L }) now
        else legacyTimes.maxOrNull() ?: 0L
}

data class RecurringReminderRestore(val master: Boolean?, val selections: RecurringReminderSelections,
                                    val warning: String?)

object RecurringReminderBackup {
    fun validate(master: String?, overrides: String?): RecurringReminderRestore {
        if (master == null && overrides == null) return RecurringReminderRestore(null,
            RecurringReminderSelections(present = true),
            "This older backup has no individual reminder choices. Category defaults are restored; your master switch is unchanged.")
        val decoded = RecurringReminderOverridesCodec.decode(overrides)
        val enabled = master?.toBooleanStrictOrNull()
        if (enabled == null || !decoded.valid || overrides == null) return RecurringReminderRestore(false,
            RecurringReminderSelections(present = true),
            "Reminder settings could not be read. Reminders are paused and individual choices reset to category defaults.")
        return RecurringReminderRestore(enabled, decoded, null)
    }
}

/** Shared send-time gate used by the worker; rejected/blocked sends never consume cooldown. */
object RecurringReminderDelivery {
    suspend fun attempt(eligible: suspend () -> Boolean, cooldown: suspend () -> Boolean,
                        deliver: () -> Boolean, record: suspend () -> Unit): Boolean {
        if (!eligible() || !cooldown() || !eligible() || !deliver()) return false
        record()
        return true
    }
}