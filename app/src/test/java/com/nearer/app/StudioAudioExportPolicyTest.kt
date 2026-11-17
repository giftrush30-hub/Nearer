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

    @Test fun completedMixMustContainReadableAudioAndPositiveDuration() {
        assertTrue(StudioAudioExportPolicy.hasUsableOutput(true, 4096L, true, 1L))
        assertTrue(StudioAudioExportPolicy.hasUsableOutput(true, 4096L, true, 125_000L))
        assertFalse(StudioAudioExportPolicy.hasUsableOutput(false, 4096L, true, 125_000L))
        assertFalse(StudioAudioExportPolicy.hasUsableOutput(true, 0L, true, 125_000L))
        assertFalse(StudioAudioExportPolicy.hasUsableOutput(true, 4096L, false, 125_000L))
        assertFalse(StudioAudioExportPolicy.hasUsableOutput(true, 4096L, true, 0L))
        assertFalse(StudioAudioExportPolicy.hasUsableOutput(true, 4096L, true, -50L))
    }
}
