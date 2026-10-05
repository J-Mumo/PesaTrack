package com.pesatrack.services

/** Android-free identity contract: extras alone do not distinguish PendingIntents. */
object ExpenseNotificationContract {
    const val ACTION_OPEN = "com.pesatrack.OPEN_EXPENSE_NOTIFICATION"
    const val ACTION_IGNORE = "com.pesatrack.ACTION_IGNORE_EXPENSE"
    const val ACTION_UNDO = "com.pesatrack.ACTION_UNDO_IGNORE"
    const val EXTRA_KIND = "expense_notification_kind"
    const val NEW = "new"
    const val MISC = "misc"
    const val NOTIFICATION_ID = 0

    fun tag(expenseId: Long, kind: String): String {
        require(expenseId > 0 && kind in listOf(NEW, MISC))
        return "pesatrack.expense.$kind:$expenseId"
    }

    fun identity(expenseId: Long, kind: String, role: String): String {
        require(role in listOf("body", "categorize", "ignore", "undo"))
        return "pesatrack://expense-notification/$kind/$expenseId/$role".also {
            tag(expenseId, kind)
        }
    }

    fun isOpen(action: String?, data: String?, expenseId: Long, kind: String?): Boolean =
        action == ACTION_OPEN && expenseId > 0 && kind in listOf(NEW, MISC) &&
            listOf("body", "categorize").any { data == identity(expenseId, kind!!, it) }
}

/** Accessed on the main dispatcher by the receiver; tokens prevent stale timers committing. */
class PendingExpenseIgnores {
    private var nextToken = 0L
    private val pending = mutableMapOf<Long, Long>()

    fun begin(expenseId: Long): Long {
        require(expenseId > 0)
        return (++nextToken).also { pending[expenseId] = it }
    }

    fun undo(expenseId: Long) { pending.remove(expenseId) }

    fun hasPending(expenseId: Long): Boolean = pending.containsKey(expenseId)

    suspend fun commit(expenseId: Long, token: Long, exclude: suspend (Long, Boolean) -> Unit): Boolean {
        if (!take(expenseId, token)) return false
        exclude(expenseId, true)
        return true
    }

    fun take(expenseId: Long, token: Long): Boolean {
        if (pending[expenseId] != token) return false
        pending.remove(expenseId)
        return true
    }
}