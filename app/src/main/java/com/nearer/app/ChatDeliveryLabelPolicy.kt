package com.nearer.app

/** Turns internal delivery states into short, honest labels people can understand. */
internal object ChatDeliveryLabelPolicy {
    fun label(state: String): String = when (state) {
        "Sending…", "Sending" -> "Sending…"
        "Delivered" -> "Delivered to their phone"
        "Queued" -> "Waiting for them to reconnect"
        "Interrupted" -> "Not confirmed — check before resending"
        "Failed" -> "Not sent"
        else -> state
    }
}
