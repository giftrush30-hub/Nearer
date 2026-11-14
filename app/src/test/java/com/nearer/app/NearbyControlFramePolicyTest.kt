package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyControlFramePolicyTest {
    @Test fun acceptsNonEmptyFramesUpToTheDocumentedBoundary() {
        assertTrue(NearbyControlFramePolicy.accepts(byteArrayOf(1)))
        assertTrue(NearbyControlFramePolicy.accepts(ByteArray(NearbyControlFramePolicy.MAX_BYTES)))
    }

    @Test fun rejectsNullEmptyAndOversizedFrames() {
        assertFalse(NearbyControlFramePolicy.accepts(null))
        assertFalse(NearbyControlFramePolicy.accepts(byteArrayOf()))
        assertFalse(NearbyControlFramePolicy.accepts(ByteArray(NearbyControlFramePolicy.MAX_BYTES + 1)))
    }
}
