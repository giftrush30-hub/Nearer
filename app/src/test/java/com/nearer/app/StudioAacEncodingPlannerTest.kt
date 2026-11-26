package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAacEncodingPlannerTest {
    @Test fun acceptsCommonOfflineAacLcProfilesAndRejectsUnsafeSettings() {
        assertTrue(StudioAacEncodingPlanner.validate(StudioAacEncodingConfig()).isValid)
        assertTrue(StudioAacEncodingPlanner.validate(StudioAacEncodingConfig(44_100, 1, 96_000)).isValid)
        assertFalse(StudioAacEncodingPlanner.validate(StudioAacEncodingConfig(12_345, 2, 192_000)).isValid)
        assertFalse(StudioAacEncodingPlanner.validate(StudioAacEncodingConfig(48_000, 6, 192_000)).isValid)
        assertFalse(StudioAacEncodingPlanner.validate(StudioAacEncodingConfig(48_000, 2, 900_000)).isValid)
    }

    @Test fun floatPcmConversionClipsAndUsesFullSignedPcm16Range() {
        assertEquals(Short.MIN_VALUE, StudioAacEncodingPlanner.floatToPcm16(-1f))
        assertEquals(Short.MAX_VALUE, StudioAacEncodingPlanner.floatToPcm16(1f))
        assertEquals(0.toShort(), StudioAacEncodingPlanner.floatToPcm16(0f))
        assertEquals(Short.MIN_VALUE, StudioAacEncodingPlanner.floatToPcm16(-2f))
        assertEquals(Short.MAX_VALUE, StudioAacEncodingPlanner.floatToPcm16(2f))
    }

    @Test fun inputCapacityIsRoundedDownToWholeInterleavedPcmFrames() {
        assertEquals(1_024, StudioAacEncodingPlanner.inputFramesForBuffer(4_096, 2))
        assertEquals(1_024, StudioAacEncodingPlanner.inputFramesForBuffer(2_048, 1))
        assertEquals(1, StudioAacEncodingPlanner.inputFramesForBuffer(5, 2))
        assertEquals(0, StudioAacEncodingPlanner.inputFramesForBuffer(3, 2))
    }

    @Test fun timestampsAreCalculatedFromAbsoluteFramePositionsWithoutDrift() {
        assertEquals(0L, StudioAacEncodingPlanner.presentationTimeUs(0L, 48_000))
        assertEquals(21_333L, StudioAacEncodingPlanner.presentationTimeUs(1_024L, 48_000))
        assertEquals(1_000_000L, StudioAacEncodingPlanner.presentationTimeUs(48_000L, 48_000))
        assertEquals(1_000_000L, StudioAacEncodingPlanner.presentationTimeUs(44_100L, 44_100))
    }

    @Test fun chunksRespectCodecBufferCapacityAndKeepAbsoluteClock() {
        val config = StudioAacEncodingConfig()
        val first = StudioAacEncodingPlanner.planInputChunk(0L, 5_000L, 8_192, config)!!
        assertEquals(2_048, first.frameCount)
        assertEquals(0L, first.presentationTimeUs)
        val second = StudioAacEncodingPlanner.planInputChunk(
            first.firstFrame + first.frameCount, 2_952L, 8_192, config
        )!!
        assertEquals(2_048, second.frameCount)
        assertEquals(42_666L, second.presentationTimeUs)
        val last = StudioAacEncodingPlanner.planInputChunk(4_096L, 904L, 8_192, config)!!
        assertEquals(904, last.frameCount)
        assertNull(StudioAacEncodingPlanner.planInputChunk(5_000L, 0L, 8_192, config))
    }

    @Test fun refusesIncompleteCodecInputFrameAndInvalidFramePositions() {
        assertFalse(runCatching {
            StudioAacEncodingPlanner.planInputChunk(0L, 1L, 3, StudioAacEncodingConfig())
        }.isSuccess)
        assertFalse(runCatching {
            StudioAacEncodingPlanner.presentationTimeUs(-1L, 48_000)
        }.isSuccess)
        assertFalse(runCatching {
            StudioAacEncodingPlanner.presentationTimeUs(Long.MAX_VALUE, 48_000)
        }.isSuccess)
    }

    @Test fun requiresActualAacOutputFormatAndCodecSpecificDataBeforeMuxTrackRegistration() {
        assertTrue(
            StudioAacEncodingPlanner.validateEncoderOutputFormat(
                "audio/mp4a-latm", 48_000, 2, 5
            ).isEmpty()
        )
        assertFalse(
            StudioAacEncodingPlanner.validateEncoderOutputFormat(
                "audio/raw", 48_000, 2, 0
            ).isEmpty()
        )
    }

    @Test fun muxTimestampGuardRejectsNegativeDuplicateAndOutOfOrderSamples() {
        val guard = StudioMuxTimestampGuard()
        assertTrue(guard.accept(0L))
        assertTrue(guard.accept(21_333L))
        assertFalse(guard.accept(21_333L))
        assertFalse(guard.accept(20_000L))
        assertFalse(guard.accept(-1L))
        assertEquals(21_333L, guard.lastAcceptedPresentationTimeUs())
    }
}
