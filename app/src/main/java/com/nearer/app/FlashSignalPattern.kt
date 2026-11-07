package com.nearer.app

/**
 * NeaRer Flash is an experimental missed-call signalling codec.
 * Presets are separated far enough that the timing tolerance cannot make
 * one preset decode as another.
 */
object FlashSignalPattern {
    private val patterns = listOf(
        Signal("PING", listOf(900L)),
        Signal("COME", listOf(1_400L, 300L)),
        Signal("URGENT", listOf(450L, 450L, 450L)),
        Signal("CALL_ME", listOf(2_200L, 900L))
    )

    data class Signal(val name: String, val gapsMillis: List<Long>)

    fun presets(): List<Signal> = patterns.toList()

    fun decode(eventTimesMillis: List<Long>, toleranceMillis: Long = 200L): String? {
        if (toleranceMillis < 0L || eventTimesMillis.size < 2) return null
        val times = eventTimesMillis.takeLast(4)
        val gaps = times.zipWithNext { first, second -> second - first }
        if (gaps.any { it <= 0L }) return null

        return patterns.firstOrNull { signal ->
            if (gaps.size != signal.gapsMillis.size) return@firstOrNull false
            gaps.zip(signal.gapsMillis).all { (actual, expected) ->
                kotlin.math.abs(actual - expected) <= toleranceMillis
            }
        }?.name
    }
}
