package com.nearer.app

/**
 * Thread-safe active-transfer index. Every HTTP download request has its own
 * identifier, so simultaneous receivers and duplicate filenames stay separate.
 */
internal class FastShareTransferLedger {
    private val active = LinkedHashMap<String, FastShareActiveTransfer>()

    @Synchronized
    fun update(transfer: FastShareActiveTransfer) {
        active[transfer.id] = transfer
    }

    @Synchronized
    fun finish(transferId: String) {
        active.remove(transferId)
    }

    @Synchronized
    fun snapshot(): List<FastShareActiveTransfer> =
        active.values.sortedBy { it.startedAtMillis }

    @Synchronized
    fun clear() {
        active.clear()
    }
}
