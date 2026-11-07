package com.nearer.app

/**
 * Encodes/decodes a short sequence of intentional call-start events.
 *
 * This is only the deterministic signal format. Android does not expose every
 * carrier call/hang-up event to an ordinary app, so this codec does not claim
 * that Flash is live until device and carrier tests validate the event source.
 */
internal object FlashSignalPattern {
    data class Preset(val code: String, val gapsMillis: List<Long>)

    val presets = listOf(
        Preset("PING", listOf(1_000L)),
        Preset("COME", listOf(1_000L, 1_000L)),
        Preset("URGENT", listOf(500L, 500L, 500L)),
        Preset("CALL_ME", listOf(1_500L, 500L))
    )

    fun decode(eventTimesMillis: List<Long>, toleranceMillis: Long = 650L): String? {
        if (eventTimesMillis.size < 2 || toleranceMillis < 0L) return null
        val ordered = eventTimesMillis.zipWithNext().map { (a, b) -> b - a }
        if (ordered.any { it <= 0L }) return null
        return presets.firstOrNull { preset ->
            preset.gapsMillis.size == ordered.size &&
                preset.gapsMillis.zip(ordered).all { (expected, actual) ->
                    kotlin.math.abs(expected - actual) <= toleranceMillis
                }
        }?.code
    }
}
