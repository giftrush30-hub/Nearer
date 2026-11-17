package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAudioExportPolicyTest {
    @Test fun acceptsOnlyExistingNonEmptyExportFiles() {
        assertTrue(StudioAudioExportPolicy.hasUsableOutput(true, 1L))
        assertTrue(StudioAudioExportPolicy.hasUsableOutput(true, 4096L))
        assertFalse(StudioAudioExportPolicy.hasUsableOutput(false, 4096L))
        assertFalse(StudioAudioExportPolicy.hasUsableOutput(true, 0L))
        assertFalse(StudioAudioExportPolicy.hasUsableOutput(false, 0L))
    }
}
