package com.nearer.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(androidx.media3.common.util.UnstableApi::class)
class StudioPitchCorrectionProcessorIntegrationTest {
    private fun render(frequencyHz: Double, settings: StudioPitchCorrectionSettings): FloatArray {
        val sampleRate = 48_000
        val frames = 24_576
        val processor = StudioPitchCorrectionProcessor(settings)
        processor.configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        repeat(frames) { frame ->
            val sample = (0.35 * sin(2.0 * PI * frequencyHz * frame / sampleRate)).toFloat()
            input.putShort((sample * 32767f).toInt().toShort())
        }
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        val samples = FloatArray(output.remaining() / 2) { output.short / 32768f }
        processor.reset()
        return samples
    }

    @Test fun disabledCorrectionPreservesAStableSingleTone() {
        val rendered = render(446.0, StudioPitchCorrectionSettings(enabled = false))
        val reading = StudioPitchEngine.detect(rendered.takeLast(4_096).toFloatArray(), 48_000, 400f, 500f)
        assertNotNull("A clear test tone should remain detectable", reading)
        assertTrue("Disabled correction must not retune the source", kotlin.math.abs(reading!!.frequencyHz - 446f) < 3f)
    }

    @Test fun hardTuneMovesSharpA4TowardTheSelectedCMajorA4() {
        val rendered = render(
            446.0,
            StudioPitchCorrectionSettings(
                keyRoot = 0,
                minor = false,
                style = StudioTuningStyle.HARD,
                retuneSpeedMs = 0,
                enabled = true,
                preserveFormants = false
            )
        )
        val reading = StudioPitchEngine.detect(rendered.takeLast(4_096).toFloatArray(), 48_000, 400f, 500f)
        assertNotNull("Corrected lead vocal should remain detectable", reading)
        assertTrue(
            "Hard tune should move the sharp A4 substantially closer to 440Hz; got ${reading!!.frequencyHz}",
            kotlin.math.abs(reading!!.frequencyHz - 440f) < 5f
        )
    }
}
