package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioVideoCaptionCodecTest {
    @Test fun roundTripPreservesMultipleTimedLayers() {
        val layers = listOf(
            StudioVideoCaptionLayer("a", "Opening title", 0, 0L, 2_500L, 0xFFFFD166.toInt(), 0),
            StudioVideoCaptionLayer("b", "Second line", 1, 1_200L, 3_500L, 0xFF67E8C5.toInt(), 2)
        )
        assertEquals(layers, StudioVideoCaptionCodec.decode(StudioVideoCaptionCodec.encode(layers)))
    }

    @Test fun layerCountIsCappedAtFour() {
        val layers = (0 until 6).map { StudioVideoCaptionLayer("$it", "Layer $it", 0, 0L, 1_000L) }
        assertEquals(4, StudioVideoCaptionCodec.decode(StudioVideoCaptionCodec.encode(layers)).size)
    }

    @Test fun invalidLayerValuesAreNormalized() {
        val safe = StudioVideoCaptionLayer("x".repeat(100), "y".repeat(200), 99, -20L, -40L, 0xFF000000.toInt(), 8).normalized(2)
        assertEquals(64, safe.id.length)
        assertEquals(120, safe.text.length)
        assertEquals(1, safe.clipIndex)
        assertEquals(0L, safe.startMs)
        assertEquals(250L, safe.endMs)
        assertEquals(0xFFFFFFFF.toInt(), safe.colorArgb)
        assertEquals(2, safe.verticalPosition)
    }

    @Test fun malformedAndUnknownVersionsDecodeEmpty() {
        assertTrue(StudioVideoCaptionCodec.decode("{broken").isEmpty())
        assertTrue(StudioVideoCaptionCodec.decode("{\"version\":99}").isEmpty())
    }
}
