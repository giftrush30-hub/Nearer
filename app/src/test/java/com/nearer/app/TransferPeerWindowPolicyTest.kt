package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferPeerWindowPolicyTest {
    @Test fun usesWeakerEndpointWindow() {
        assertEquals(1, TransferPeerWindowPolicy.recommended(2, 1))
        assertEquals(1, TransferPeerWindowPolicy.recommended(1, 2))
        assertEquals(2, TransferPeerWindowPolicy.recommended(2, 2))
    }

    @Test fun unknownOrLegacyPeerUsesOneFileAtATime() {
        assertEquals(1, TransferPeerWindowPolicy.recommended(2, null))
    }

    @Test fun rejectsUnsupportedWindowValues() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferPeerWindowPolicy.recommended(0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferPeerWindowPolicy.recommended(2, 3)
        }
    }
}
