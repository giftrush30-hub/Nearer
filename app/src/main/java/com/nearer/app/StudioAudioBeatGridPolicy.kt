package com.nearer.app

/**
 * Converts a project tempo into an editable timeline grid.
 *
 * beatDivision is the number of snap positions per quarter-note beat:
 * 1 = quarter notes, 2 = eighth notes, 4 = sixteenth notes.
 */
internal object StudioAudioBeatGridPolicy {
    const val MIN_BPM = 40
    const val MAX_BPM = 240
    const val DEFAULT_BPM = 120
    const val MAX_TIMELINE_OFFSET_MS = 30_000L

    fun normalizeBpm(bpm: Int): Int = bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun normalizeBeatDivision(division: Int): Int =
        when (division) { 1, 2, 4 -> division; else -> 2 }

    fun gridIntervalMs(bpm: Int, beatDivision: Int): Double {
        val safeBpm = normalizeBpm(bpm)
        val safeDivision = normalizeBeatDivision(beatDivision)
        return 60_000.0 / safeBpm / safeDivision
    }

    fun snapOffsetMs(requestedMs: Long, bpm: Int, beatDivision: Int): Long {
        val safeRequested = requestedMs.coerceIn(0L, MAX_TIMELINE_OFFSET_MS)
        val interval = gridIntervalMs(bpm, beatDivision)
        return (kotlin.math.round(safeRequested / interval) * interval)
            .toLong().coerceIn(0L, MAX_TIMELINE_OFFSET_MS)
    }

    fun gridPositionMs(index: Int, bpm: Int, beatDivision: Int): Long {
        if (index <= 0) return 0L
        val interval = gridIntervalMs(bpm, beatDivision)
        return (index * interval).toLong().coerceAtMost(MAX_TIMELINE_OFFSET_MS)
    }
}
