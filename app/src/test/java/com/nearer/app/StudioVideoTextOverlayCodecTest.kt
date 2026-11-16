package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioVideoTextOverlayCodecTest {
    @Test
    fun roundTripPreservesCaptionStyle() {
        val value = StudioVideoTextOverlay("NeaRer Studio", 0xFFFFD166.toInt(), 1)
        assertEquals(value, StudioVideoTextOverlayCodec.decode(StudioVideoTextOverlayCodec.encode(value)))
    }

    @Test
    fun limitsTextLengthAndPosition() {
        val safe = StudioVideoTextOverlay("x".repeat(120), 0xFF67E8C5.toInt(), 9).normalized()
        assertEquals(80, safe.text.length)
        assertEquals(2, safe.verticalPosition)
    }

    @Test
    fun unsupportedColoursFallBackToWhite() {
        assertEquals(0xFFFFFFFF.toInt(), StudioVideoTextOverlay("Hi", 0xFF000000.toInt()).normalized().colorArgb)
    }

    @Test
    fun malformedAndUnknownVersionsResetSafely() {
        assertEquals(StudioVideoTextOverlay(), StudioVideoTextOverlayCodec.decode("{broken"))
        assertEquals(StudioVideoTextOverlay(), StudioVideoTextOverlayCodec.decode("""{"version":99}"""))
    }
}
