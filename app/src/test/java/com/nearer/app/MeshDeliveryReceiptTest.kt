package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class MeshDeliveryReceiptTest {
    private fun pair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    @Test fun finalReceiptMustBeSignedByPinnedDestinationAndUntampered() {
        val destination = pair()
        val attacker = pair()
        val receipt = MeshDeliveryReceipt.create(
            "packet-1", "AAAAAA", "BBBBBB", 2000L
        ) { bytes ->
            Signature.getInstance("SHA256withECDSA").run {
                initSign(destination.private); update(bytes); sign()
            }
        }
        assertTrue(receipt.verify(destination.public.encoded, 3000L))
        assertFalse(receipt.verify(attacker.public.encoded, 3000L))
        assertFalse(receipt.copy(packetId = "packet-2").verify(destination.public.encoded, 3000L))
        assertFalse(receipt.verify(destination.public.encoded, 1000L))
    }
}
