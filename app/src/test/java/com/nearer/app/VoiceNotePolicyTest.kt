package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceNotePolicyTest {
    @Test fun acceptsSupportedAudioWithValidMetadata() {
        val descriptor = VoiceNotePolicy.create(
            fileName = "NeaRer_Voice_123.m4a",
            mimeType = "audio/mp4",
            durationMillis = 12_000L,
            byteCount = 48_000L
        )
        assertEquals("NeaRer_Voice_123.m4a", descriptor?.fileName)
        assertEquals("audio/mp4", descriptor?.mimeType)
        assertEquals(12_000L, descriptor?.durationMillis)
        assertEquals(48_000L, descriptor?.byteCount)
    }

    @Test fun rejectsUnsupportedMimeEmptyAndOutOfRangeDuration() {
        assertNull(VoiceNotePolicy.create("voice.m4a", "video/mp4", 1_000L, 100L))
        assertNull(VoiceNotePolicy.create("voice.m4a", "audio/mp4", 0L, 100L))
        assertNull(VoiceNotePolicy.create("voice.m4a", "audio/mp4", VoiceNotePolicy.MAX_DURATION_MILLIS + 1L, 100L))
        assertNull(VoiceNotePolicy.create("voice.m4a", "audio/mp4", 1_000L, 0L))
        assertNull(VoiceNotePolicy.create(" ", "audio/mp4", 1_000L, 100L))
    }

    @Test fun stripsPathComponentsAndBoundsFileName() {
        val descriptor = VoiceNotePolicy.create(
            fileName = "../../private/voice.m4a",
            mimeType = "audio/mp4; codecs=mp4a",
            durationMillis = 1_000L,
            byteCount = 10L
        )
        assertEquals("voice.m4a", descriptor?.fileName)
        assertEquals("audio/mp4", descriptor?.mimeType)
        val longName = VoiceNotePolicy.create("x".repeat(150), "audio/mp4", 1_000L, 10L)
        assertEquals(120, longName?.fileName?.length)
    }
}
