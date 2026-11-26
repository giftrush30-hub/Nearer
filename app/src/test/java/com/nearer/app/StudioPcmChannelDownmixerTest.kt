package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPcmChannelDownmixerTest {
    @Test fun duplicatesMonoToBothStereoChannels() {
        val result = StudioPcmChannelDownmixer.downmix(floatArrayOf(0.4f), 1)
        assertEquals(0.4f, result.left, 0.00001f)
        assertEquals(0.4f, result.right, 0.00001f)
    }

    @Test fun preservesStereoChannelsWithoutAttenuation() {
        val result = StudioPcmChannelDownmixer.downmix(floatArrayOf(-0.25f, 0.75f), 2)
        assertEquals(-0.25f, result.left, 0.00001f)
        assertEquals(0.75f, result.right, 0.00001f)
    }

    @Test fun foldsFivePointOneCenterAndSurroundIntoBothStereoSides() {
        val mask = 0x4 or 0x8 or 0x10 or 0x20 or 0x40 or 0x80
        val result = StudioPcmChannelDownmixer.downmix(
            floatArrayOf(0.2f, 0.3f, 0.4f, 0.6f, 0.8f, 1.0f),
            6,
            mask
        )
        assertEquals(0.2f + 0.4f * 0.70710677f + 0.6f * 0.5f + 0.8f * 0.70710677f, result.left, 0.0001f)
        assertEquals(0.3f + 0.4f * 0.70710677f + 0.6f * 0.5f + 1.0f * 0.70710677f, result.right, 0.0001f)
    }

    @Test fun usesCanonicalFivePointOneOrderWhenMaskIsMissing() {
        val result = StudioPcmChannelDownmixer.downmix(
            floatArrayOf(0.1f, 0.2f, 0.5f, 0f, 0.3f, 0.4f),
            6,
            null
        )
        assertEquals(0.1f + 0.5f * 0.70710677f + 0.3f * 0.70710677f, result.left, 0.0001f)
        assertEquals(0.2f + 0.5f * 0.70710677f + 0.4f * 0.70710677f, result.right, 0.0001f)
    }

    @Test fun doesNotClipDownmixBeforeTheTimelineLimiter() {
        val result = StudioPcmChannelDownmixer.downmix(
            floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f),
            6,
            0x4 or 0x8 or 0x10 or 0x20 or 0x40 or 0x80
        )
        assertTrue(result.left > 1f)
        assertTrue(result.right > 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteFrames() {
        StudioPcmChannelDownmixer.downmix(floatArrayOf(0.1f, 0.2f), 6)
    }
}
