package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class MeshReceiptRecoveryPolicyTest {
    private fun receipt(deliveredAt: Long): MeshDeliveryReceipt {
        val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        return MeshDeliveryReceipt.create("packet-1", "AAAAAA", "BBBBBB", deliveredAt) { bytes ->
            Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(bytes)
                sign()
            }
        }
    }

    @Test fun retryReusesExactPersistedReceiptInsteadOfResigning() {
        val persisted = receipt(400_000L)
        var createCalled = false
        val selected = MeshReceiptRecoveryPolicy.reuseOrCreate(persisted) {
            createCalled = true
            receipt(401_000L)
        }
        assertSame(persisted, selected)
        assertArrayEquals(persisted.encode(), selected.encode())
        assertTrue(!createCalled)
    }

    @Test fun firstDeliveryCreatesReceiptWhenNoneWasPersisted() {
        val created = receipt(400_000L)
        val selected = MeshReceiptRecoveryPolicy.reuseOrCreate(null) { created }
        assertSame(created, selected)
        assertEquals("packet-1", selected.packetId)
    }
}
