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

    @Test fun preservesExplicitUsbRoutingOnVersionsBeforeDeviceIdRouting() {
        assertFalse(StudioAudioEnginePolicy.useOboe(27, 12))
        assertFalse(StudioAudioEnginePolicy.useOboe(26, 12))
    }

    @Test fun allowsExplicitUsbRoutingFromAndroid28() {
        assertTrue(StudioAudioEnginePolicy.useOboe(28, 12))
    }
}
