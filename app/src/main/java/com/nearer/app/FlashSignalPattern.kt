package com.nearer.app

/**
 * NeaRer Flash is an experimental incoming-call timing signalling codec.
 * Presets are separated far enough that the timing tolerance cannot make
 * one preset decode as another.
 */
object FlashSignalPattern {
    private val patterns = listOf(
        Signal("PING", listOf(5_000L, 12_000L)),
        Signal("COME", listOf(11_000L, 8_000L)),
        Signal("URGENT", listOf(14_000L, 14_000L, 14_000L)),
        Signal("CALL_ME", listOf(20_000L, 17_000L))
    )

    data class Signal(val name: String, val gapsMillis: List<Long>)

    /**
     * Turns a decoded preset into the words shown in a NeaRer chat bubble.
     * These are short preset messages, not arbitrary user-authored text.
     */
    fun toChatMessage(signalName: String): String = when (signalName) {
        "FLASH_CALL" -> "Flash call received"
        "PING" -> "Ping"
        "COME" -> "Please come"
        "URGENT" -> "Urgent — please check this"
        "CALL_ME" -> "Please call me"
        else -> "NeaRer Flash signal"
    }

    fun presets(): List<Signal> = patterns.toList()

    fun decode(eventTimesMillis: List<Long>, toleranceMillis: Long = 1_000L): String? {
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
