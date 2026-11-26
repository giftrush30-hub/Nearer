package com.nearer.app

import kotlin.math.abs

/**
 * Deterministic offline downmix from Android's canonical channel-position order to stereo.
 *
 * Android defines channel samples in ascending channel-mask bit order. When a decoder omits
 * the mask, the canonical layout for the reported channel count is used. The downstream
 * timeline mixer owns final peak limiting, so this stage deliberately does not clip sums.
 */
internal object StudioPcmChannelDownmixer {
    private const val FRONT_LEFT = 0x4
    private const val FRONT_RIGHT = 0x8
    private const val FRONT_CENTER = 0x10
    private const val LOW_FREQUENCY = 0x20
    private const val BACK_LEFT = 0x40
    private const val BACK_RIGHT = 0x80
    private const val BACK_CENTER = 0x400
    private const val SIDE_LEFT = 0x800
    private const val SIDE_RIGHT = 0x1000

    private enum class Position { LEFT, RIGHT, CENTER, LFE, BACK_LEFT, BACK_RIGHT, BACK_CENTER, SIDE_LEFT, SIDE_RIGHT }

    data class StereoFrame(val left: Float, val right: Float)

    fun downmix(
        samples: FloatArray,
        channelCount: Int,
        channelMask: Int? = null
    ): StereoFrame {
        require(channelCount in 1..8) { "Only one to eight decoded audio channels are supported." }
        require(samples.size >= channelCount) { "The decoded PCM frame is incomplete." }
        if (channelCount == 1) {
            val mono = finiteSample(samples[0])
            return StereoFrame(mono, mono)
        }

        val positions = positionsFor(channelCount, channelMask)
        var left = 0f
        var right = 0f
        positions.forEachIndexed { index, position ->
            val sample = finiteSample(samples[index])
            when (position) {
                Position.LEFT, Position.BACK_LEFT, Position.SIDE_LEFT -> left += sample * if (position == Position.LEFT) 1f else ROOT_HALF
                Position.RIGHT, Position.BACK_RIGHT, Position.SIDE_RIGHT -> right += sample * if (position == Position.RIGHT) 1f else ROOT_HALF
                Position.CENTER -> {
                    left += sample * ROOT_HALF
                    right += sample * ROOT_HALF
                }
                Position.BACK_CENTER -> {
                    left += sample * HALF
                    right += sample * HALF
                }
                Position.LFE -> {
                    left += sample * HALF
                    right += sample * HALF
                }
            }
        }
        return StereoFrame(left, right)
    }

    private fun positionsFor(channelCount: Int, mask: Int?): List<Position> {
        val bits = mask?.takeIf { it != 0 }?.let { value ->
            buildList {
                for (bit in 0..30) {
                    val flag = 1 shl bit
                    if (value and flag != 0) add(flag)
                }
            }
        }
        val mapped = bits?.mapNotNull(::positionForBit)
        if (mapped != null && mapped.size == channelCount && bits.size == channelCount) return mapped

        return when (channelCount) {
            2 -> listOf(Position.LEFT, Position.RIGHT)
            3 -> listOf(Position.LEFT, Position.RIGHT, Position.CENTER)
            4 -> listOf(Position.LEFT, Position.RIGHT, Position.BACK_LEFT, Position.BACK_RIGHT)
            5 -> listOf(Position.LEFT, Position.RIGHT, Position.CENTER, Position.BACK_LEFT, Position.BACK_RIGHT)
            6 -> listOf(Position.LEFT, Position.RIGHT, Position.CENTER, Position.LFE, Position.BACK_LEFT, Position.BACK_RIGHT)
            7 -> listOf(Position.LEFT, Position.RIGHT, Position.CENTER, Position.LFE, Position.BACK_LEFT, Position.BACK_RIGHT, Position.BACK_CENTER)
            8 -> listOf(Position.LEFT, Position.RIGHT, Position.CENTER, Position.LFE, Position.BACK_LEFT, Position.BACK_RIGHT, Position.SIDE_LEFT, Position.SIDE_RIGHT)
            else -> error("Unsupported decoded channel layout.")
        }
    }

    private fun positionForBit(bit: Int): Position? = when (bit) {
        FRONT_LEFT -> Position.LEFT
        FRONT_RIGHT -> Position.RIGHT
        FRONT_CENTER -> Position.CENTER
        LOW_FREQUENCY -> Position.LFE
        BACK_LEFT -> Position.BACK_LEFT
        BACK_RIGHT -> Position.BACK_RIGHT
        BACK_CENTER -> Position.BACK_CENTER
        SIDE_LEFT -> Position.SIDE_LEFT
        SIDE_RIGHT -> Position.SIDE_RIGHT
        else -> null
    }

    private fun finiteSample(value: Float): Float = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f

    private const val ROOT_HALF = 0.70710677f
    private const val HALF = 0.5f
}
