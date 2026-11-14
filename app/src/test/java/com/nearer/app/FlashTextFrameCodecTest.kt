package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashTextFrameCodecTest {
    @Test fun roundTripsShortAndLongCustomMessages() {
        val messages = listOf(
            "Meet me at the gate",
            "I'm at the hostel. Please come outside.",
            "Hello 👋🏽 — are you coming?"
        )
        messages.forEachIndexed { index, message ->
            val frames = FlashTextFrameCodec.encode(message, index + 1)
            assertEquals(message, FlashTextFrameCodec.decode(frames))
        }
    }

    @Test fun framesCanBeSerializedParsedAndReordered() {
        val frames = FlashTextFrameCodec.encode("A longer custom Flash message for NeaRer", 0x42)
        val parsed = frames.map { FlashTextFrameCodec.parse(it.serialize()) }.mapNotNull { it }
        assertEquals(frames.size, parsed.size)
        assertEquals("A longer custom Flash message for NeaRer", FlashTextFrameCodec.decode(parsed.reversed()))
    }

    @Test fun rejectsMissingDuplicateOrMixedFrames() {
        val frames = FlashTextFrameCodec.encode("This message needs more than one frame", 7)
        assertTrue(frames.size > 1)
        assertNull(FlashTextFrameCodec.decode(frames.dropLast(1)))
        assertNull(FlashTextFrameCodec.decode(frames + frames.first()))
        val other = FlashTextFrameCodec.encode("another", 8).first()
        assertNull(FlashTextFrameCodec.decode(frames.dropLast(1) + other))
    }

    @Test fun rejectsCorruptedPayloadAndInvalidSerializedFrames() {
        val frame = FlashTextFrameCodec.encode("Check integrity", 9).first()
        val corrupted = frame.copy(payloadHex = if (frame.payloadHex.startsWith("41")) "42" + frame.payloadHex.drop(2) else "41" + frame.payloadHex.drop(2))
        assertNull(FlashTextFrameCodec.decode(listOf(corrupted)))
        assertNull(FlashTextFrameCodec.parse("NF1:not-an-id:00:01:AA:00000000"))
        assertNull(FlashTextFrameCodec.parse("NF1:0001:02:01:AA:00000000"))
    }

    @Test fun enforcesTextAndMessageIdLimits() {
        assertTrue(runCatching { FlashTextFrameCodec.encode("", 1) }.isFailure)
        assertTrue(runCatching { FlashTextFrameCodec.encode("x".repeat(161), 1) }.isFailure)
        assertTrue(runCatching { FlashTextFrameCodec.encode("hello", 65536) }.isFailure)
        assertNotNull(FlashTextFrameCodec.encode("hello", 65535).first())
    }
}
