package com.nearer.app

/**
 * A bounded-lifetime, in-memory queue for one sequential file-transfer job.
 *
 * The active item stays at the head logically until the receiver confirms it;
 * calling currentOrNext() again after a disconnect returns the same item.
 */
internal class SequentialTransferQueue<T> {
    private val waiting = java.util.ArrayDeque<T>()
    private var current: T? = null

    var total: Int = 0
        private set
    var completed: Int = 0
        private set

    val hasPending: Boolean
        get() = current != null || waiting.isNotEmpty()

    fun enqueue(items: List<T>) {
        require(items.isNotEmpty()) { "A transfer queue needs at least one item." }
        check(!hasPending) { "Finish or clear the current transfer queue first." }
        waiting.addAll(items)
        total = items.size
        completed = 0
    }

    /** Returns the active item, or advances to the next item if none is active. */
    fun currentOrNext(): T? {
        if (current == null && waiting.isNotEmpty()) current = waiting.removeFirst()
        return current
    }

    /** Marks the active item complete only after the receiver's verification receipt. */
    fun completeCurrent(): Boolean {
        if (current == null) return false
        current = null
        completed = (completed + 1).coerceAtMost(total)
        return true
    }

    fun clear() {
        waiting.clear()
        current = null
        total = 0
        completed = 0
    }
}
