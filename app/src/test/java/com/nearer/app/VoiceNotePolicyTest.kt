package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class VoiceNotePolicyTest {
    @Test fun acceptsAndroidFriendlyMetadata() {
        val note = VoiceNotePolicy.create("voice-note-01.m4a", "audio/mp4", 12500L, 48000L)!!
        assertEquals("voice-note-01.m4a", note.fileName)
        assertEquals("audio/mp4", note.mimeType)
        assertEquals(12500L, note.durationMillis)
        assertEquals(48000L, note.byteCount)
    }

    @Test fun stripsPathsAndControlCharacters() {
        val note = VoiceNotePolicy.create("../../voice\nnote.3gp", "audio/3gpp", 1L, 1L)!!
        assertEquals("voice note.3gp", note.fileName)
    }

    @Test fun rejectsUnsupportedMimeAndInvalidDuration() {
        assertNull(VoiceNotePolicy.create("note.m4a", "text/plain", 1000L, 100L))
        assertNull(VoiceNotePolicy.create("note.m4a", "audio/mp4", 0L, 100L))
        assertNull(VoiceNotePolicy.create("note.m4a", "audio/mp4", VoiceNotePolicy.MAX_DURATION_MILLIS + 1L, 100L))
    }

    @Test fun rejectsUnsafeOrEmptyMetadata() {
        assertNull(VoiceNotePolicy.create("", "audio/mp4", 1000L, 100L))
        assertNull(VoiceNotePolicy.create("..", "audio/mp4", 1000L, 100L))
        assertNull(VoiceNotePolicy.create("note.m4a", "audio/mp4", 1000L, 0L))
    }

    @Test fun normalizesMimeParameters() {
        assertEquals("audio/mp4", VoiceNotePolicy.create(
            "note.m4a", " Audio/MP4; codecs=mp4a.40.2 ", 1000L, 100L
        )!!.mimeType)
    }
}
