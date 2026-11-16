package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioAudioBufferPolicyTest {
    @Test fun prefersNativeOutputRateWhenInputSupportsIt() {
        assertEquals(48_000, StudioAudioBufferPolicy.chooseSampleRate(48_000, listOf(44_100, 48_000)))
    }
    @Test fun selectsSupportedFallbackWhenNativeRateIsUnavailable() {
        assertEquals(44_100, StudioAudioBufferPolicy.chooseSampleRate(48_000, listOf(44_100, 32_000)))
    }
    @Test fun fallsBackTo48kWhenNativeRateIsInvalid() {
        assertEquals(48_000, StudioAudioBufferPolicy.chooseSampleRate(0, emptyList()))
    }
    @Test fun bufferCoversAndroidMinimumAndAtLeastThreeBursts() {
        assertEquals(1_536, StudioAudioBufferPolicy.bufferFrames(512, 512, 256))
    }
    @Test fun bufferIsBoundedForMisreportedDeviceValues() {
        assertEquals(32_768, StudioAudioBufferPolicy.bufferFrames(Int.MAX_VALUE, 512, 256))
    }
}
