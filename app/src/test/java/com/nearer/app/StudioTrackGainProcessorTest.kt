package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class StudioTrackGainProcessorTest {
    private fun render(
        samples: ShortArray,
        channels: Int,
        chunkFrames: Int,
        gain: Float = 1f,
        pan: Float = 0f,
        durationMs: Long,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L
    ): ShortArray {
        val processor = StudioTrackGainProcessor(gain, pan, durationMs, fadeInMs, fadeOutMs)
        val format = AudioProcessor.AudioFormat(1_000, channels, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(format)
        assertEquals(2, outputFormat.channelCount)
        processor.flush()
        val result = ArrayList<Short>()
        val inputFrames = samples.size / channels
        var frameOffset = 0
        while (frameOffset < inputFrames) {
            val frames = minOf(chunkFrames, inputFrames - frameOffset)
            val input = ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.nativeOrder())
            val start = frameOffset * channels
            for (index in start until start + frames * channels) input.putShort(samples[index])
            input.flip()
            processor.queueInput(input)
            val output = processor.output
            while (output.hasRemaining()) result += output.short
            frameOffset += frames
        }
        processor.queueEndOfStream()
        val tail = processor.output
        while (tail.hasRemaining()) result += tail.short
        processor.reset()
        return result.toShortArray()
    }

    @Test fun gainAndFadeEnvelopeAreIndependentOfMedia3InputChunkBoundaries() {
        val samples = ShortArray(4_000) { 12_000 }
        val whole = render(samples, channels = 2, chunkFrames = 2_000, gain = 0.8f,
            durationMs = 2_000L, fadeInMs = 400L, fadeOutMs = 600L)
        val fragmented = render(samples, channels = 2, chunkFrames = 37, gain = 0.8f,
            durationMs = 2_000L, fadeInMs = 400L, fadeOutMs = 600L)
        assertEquals(whole.size, fragmented.size)
        assertTrue(whole.indices.all { whole[it] == fragmented[it] })
        assertEquals(0, whole[0].toInt())
        assertTrue(kotlin.math.abs(whole[whole.lastIndex].toInt()) < 50)
        assertTrue(whole[400].toInt() in 6_700..6_900)
    }

    @Test fun monoInputIsDuplicatedToBothOutputChannels() {
        val mono = shortArrayOf(1_000, -2_000, 3_000, -4_000)
        val stereo = render(mono, channels = 1, chunkFrames = 2, durationMs = 4L)
        assertEquals(8, stereo.size)
        for (frame in mono.indices) {
            assertEquals(mono[frame], stereo[frame * 2])
            assertEquals(mono[frame], stereo[frame * 2 + 1])
        }
    }

    @Test fun hardPanKeepsTheSelectedSideAndMutesTheOppositeSide() {
        val stereo = shortArrayOf(10_000, 10_000, -8_000, -8_000)
        val left = render(stereo, channels = 2, chunkFrames = 1, pan = -1f, durationMs = 2L)
        val right = render(stereo, channels = 2, chunkFrames = 1, pan = 1f, durationMs = 2L)
        assertEquals(10_000, left[0].toInt())
        assertEquals(0, left[1].toInt())
        assertEquals(0, right[0].toInt())
        assertEquals(10_000, right[1].toInt())
        assertEquals(-8_000, left[2].toInt())
        assertEquals(-8_000, right[3].toInt())
    }

    @Test fun flushRestartsTheFrameClockForAReusedProcessor() {
        val processor = StudioTrackGainProcessor(
            gain = 1f,
            pan = 0f,
            durationMs = 1_000L,
            fadeInMs = 500L,
            fadeOutMs = 0L
        )
        processor.configure(AudioProcessor.AudioFormat(1_000, 2, C.ENCODING_PCM_16BIT))
        val inputSamples = ShortArray(400) { 10_000 }

        fun processOnePass(): ShortArray {
            val input = ByteBuffer.allocateDirect(inputSamples.size * 2).order(ByteOrder.nativeOrder())
            inputSamples.forEach { input.putShort(it) }
            input.flip()
            processor.queueInput(input)
            val output = processor.output
            val samples = ShortArray(output.remaining() / 2) { output.short }
            return samples
        }

        processor.flush()
        val firstPass = processOnePass()
        processor.flush()
        val secondPass = processOnePass()
        assertEquals(firstPass.size, secondPass.size)
        assertTrue(firstPass.indices.all { firstPass[it] == secondPass[it] })
        assertEquals(0, secondPass.first().toInt())
        assertTrue(secondPass[200] in 3_000..3_200)
        processor.reset()
    }

    @Test fun invalidChannelLayoutsAreRejectedInsteadOfMisreadingPcm() {
        val processor = StudioTrackGainProcessor(1f, 0f, 1_000L, 0L, 0L)
        val invalid = AudioProcessor.AudioFormat(48_000, 3, C.ENCODING_PCM_16BIT)
        var rejected = false
        try {
            processor.configure(invalid)
        } catch (_: AudioProcessor.UnhandledAudioFormatException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
