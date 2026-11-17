package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAudioEnginePolicyTest {
    @Test fun usesJavaFallbackOnAndroid26() {
        assertFalse(StudioAudioEnginePolicy.useOboe(26, null))
    }

    @Test fun usesOboeForDefaultInputOnAndroid27AndNewer() {
        assertTrue(StudioAudioEnginePolicy.useOboe(27, null))
        assertTrue(StudioAudioEnginePolicy.useOboe(35, null))
    }

    @Test fun preservesJavaFallbackOnAndroid26ForExplicitUsbInput() {
        assertFalse(StudioAudioEnginePolicy.useOboe(26, 12))
    }

    @Test fun allowsExplicitUsbRoutingFromAndroid27() {
        assertTrue(StudioAudioEnginePolicy.useOboe(27, 12))
        assertTrue(StudioAudioEnginePolicy.useOboe(28, 12))
    }

    @Test fun selectedInputMustBeTheActuallyRoutedDevice() {
        assertTrue(StudioAudioEnginePolicy.matchesPreferredInput(12, 12))
        assertFalse(StudioAudioEnginePolicy.matchesPreferredInput(12, 7))
        assertFalse(StudioAudioEnginePolicy.matchesPreferredInput(12, null))
        assertTrue(StudioAudioEnginePolicy.matchesPreferredInput(null, 7))
        assertTrue(StudioAudioEnginePolicy.matchesPreferredInput(null, null))
    }

    @Test fun unprocessedPresetIsOnlyRequestedFromApi28() {
        assertFalse(StudioAudioEnginePolicy.useUnprocessedInput(27, true))
        assertTrue(StudioAudioEnginePolicy.useUnprocessedInput(28, true))
        assertTrue(StudioAudioEnginePolicy.useUnprocessedInput(35, true))
    }

    @Test fun standardCaptureRemainsStandardWhenUnprocessedWasNotRequested() {
        assertFalse(StudioAudioEnginePolicy.useUnprocessedInput(35, false))
        assertFalse(StudioAudioEnginePolicy.useUnprocessedInput(26, false))
    }

    @Test fun explicitPrivacySensitiveCaptureStartsOnAndroid11() {
        assertFalse(StudioAudioEnginePolicy.usePrivacySensitiveCapture(29))
        assertTrue(StudioAudioEnginePolicy.usePrivacySensitiveCapture(30))
        assertTrue(StudioAudioEnginePolicy.usePrivacySensitiveCapture(35))
    }
}
