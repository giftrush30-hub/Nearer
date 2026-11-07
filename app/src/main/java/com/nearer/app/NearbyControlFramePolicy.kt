package com.nearer.app

/** Bounds untrusted Nearby BYTES control envelopes before JSON parsing. */
internal object NearbyControlFramePolicy {
    const val MAX_BYTES = 16 * 1024

    fun accepts(frame: ByteArray?): Boolean =
        frame != null && frame.isNotEmpty() && frame.size <= MAX_BYTES
}
