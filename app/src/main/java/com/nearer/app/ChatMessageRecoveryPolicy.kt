package com.nearer.app

/**
 * Restores a truthful delivery label after the app process has stopped.
 *
 * A live in-memory send callback cannot survive process death. Leaving an
 * outgoing bubble marked "Sending…" forever falsely suggests active delivery.
 * Persisted terminal states and incoming messages remain unchanged.
 */
internal object ChatMessageRecoveryPolicy {
    fun deliveryAfterProcessRestart(outgoing: Boolean, delivery: String): String {
        if (!outgoing) return delivery
        return when (delivery) {
            "Sending…", "Sending..." -> "Interrupted"
            else -> delivery
        }
    }
}
