package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferRadioReadinessTest {
    @Test
    fun bothRadiosReadyNeedsNoWarning() {
        assertNull(TransferRadioReadiness.message(true, true))
    }

    @Test
    fun disabledBluetoothExplainsDiscoveryImpact() {
        assertEquals(
            "Bluetooth is off. Turn it on for nearby discovery; Wi-Fi is ready for fast file transfers.",
            TransferRadioReadiness.message(false, true)
        )
    }

    @Test
    fun disabledWifiExplainsBulkTransferImpact() {
        assertEquals(
            "Wi-Fi is off. Turn it on for fast file transfers; Bluetooth is ready for nearby discovery.",
            TransferRadioReadiness.message(true, false)
        )
    }

    @Test
    fun bothDisabledGetsCombinedInstruction() {
        assertEquals(
            "Turn on Bluetooth and Wi-Fi in your phone settings for the best NeaRer experience.",
            TransferRadioReadiness.message(false, false)
        )
    }

    @Test
    fun unknownRadioStateDoesNotPretendItIsEnabled() {
        assertEquals(
            "Check that Bluetooth and Wi-Fi are enabled in your phone settings for the best nearby connection.",
            TransferRadioReadiness.message(null, true)
        )
    }
}
