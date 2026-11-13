package com.nearer.app

/**
 * Reconciles persisted outgoing chat bubbles with the durable transport outbox
 * after process death. A message still in the outbox is retryable; a message
 * left in a transient sending state without an outbox record is not.
 */
internal object ChatRestartRecoveryPolicy {
    fun reconcile(
        messages: MutableList<ChatMessage>,
        queuedMessageIds: Set<String>,
        deliveredMessageIds: Set<String> = emptySet()
    ): Boolean {
        var changed = false
        for (index in messages.indices) {
            val message = messages[index]
            if (!message.outgoing || message.delivery == "Delivered") continue

            val recoveredDelivery = when {
                message.id in deliveredMessageIds -> "Delivered"
                message.id in queuedMessageIds -> "Queued"
                message.delivery == "Sending…" || message.delivery == "Sending" -> "Interrupted"
                else -> message.delivery
            }
            if (recoveredDelivery != message.delivery) {
                messages[index] = message.copy(delivery = recoveredDelivery)
                changed = true
            }
        }
        return changed
    }
}
