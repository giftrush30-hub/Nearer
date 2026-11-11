package com.nearer.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(AndroidJUnit4::class)
class MeshPersistenceInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearMeshStores() {
        context.getSharedPreferences("nearer_dead_drop_v1", 0).edit().clear().commit()
        context.getSharedPreferences("nearer_mesh_receipt_outbox_v1", 0).edit().clear().commit()
    }

    @After
    fun cleanMeshStores() {
        context.getSharedPreferences("nearer_dead_drop_v1", 0).edit().clear().commit()
        context.getSharedPreferences("nearer_mesh_receipt_outbox_v1", 0).edit().clear().commit()
    }

    private fun recipientKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    @Test
    fun deadDropPersistsOpaquePacketAndPreviousHopAcrossStoreRecreation() {
        val now = System.currentTimeMillis()
        val recipient = recipientKeyPair()
        val packet = MeshRelayPacketV1.create(
            originId = NeaRerIdentity.generate(),
            destinationId = NeaRerIdentity.generate(),
            packetId = "mesh-persist-001",
            createdAtMs = now,
            expiresAtMs = now + 60_000L,
            hopLimit = 4,
            recipientPublicKey = recipient.public.encoded,
            plaintext = "opaque test body".toByteArray()
        )
        val previousHop = NeaRerIdentity.generate()
        val localRelay = NeaRerIdentity.generate()
        val firstStore = DeadDropStore(context)
        assertTrue(firstStore.enqueueTransit(packet, previousHop, now))

        val reopenedStore = DeadDropStore(context)
        val claimed = reopenedStore.claimDue(now + 1L, 1)
        assertEquals(1, claimed.size)
        assertEquals(packet.packetId, claimed.single().packetId)
        assertEquals(previousHop, claimed.single().previousHopPeerId)
        assertTrue(MeshRelayPacketV1.decode(claimed.single().bytes).verifyEmbeddedOrigin(now + 1L))

        val prepared = reopenedStore.prepareForForwarding(packet.packetId, localRelay, now + 2L)
        assertNotNull(prepared)
        assertEquals(1, prepared!!.hopsTaken)

        // Reopening and retrying must reuse the prepared hop count, not increment again.
        val afterRestart = DeadDropStore(context)
        val retry = afterRestart.claimDue(now + 31_000L, 1)
        assertEquals(1, retry.size)
        val preparedAgain = afterRestart.prepareForForwarding(packet.packetId, localRelay, now + 31_001L)
        assertNotNull(preparedAgain)
        assertEquals(1, preparedAgain!!.hopsTaken)

        // A replay cannot roll the persisted hop count backwards.
        assertFalse(afterRestart.enqueueTransit(packet.copy(hopsTaken = 0), NeaRerIdentity.generate(), now + 31_002L))
    }

    @Test
    fun deadDropEnforcesPerOriginItemQuota() {
        val now = System.currentTimeMillis()
        val origin = NeaRerIdentity.generate()
        val destination = NeaRerIdentity.generate()
        val previousHop = NeaRerIdentity.generate()
        val recipient = recipientKeyPair()
        val store = DeadDropStore(context)
        repeat(16) { index ->
            val packet = MeshRelayPacketV1.create(
                origin, destination, "quota-$index", now, now + 60_000L, 4,
                recipient.public.encoded, "quota body $index".toByteArray()
            )
            assertTrue(store.enqueueTransit(packet, previousHop, now))
        }
        val overflow = MeshRelayPacketV1.create(
            origin, destination, "quota-overflow", now, now + 60_000L, 4,
            recipient.public.encoded, "must be rejected".toByteArray()
        )
        assertFalse(store.enqueueTransit(overflow, previousHop, now))
        assertEquals(16, store.size())
    }

    @Test
    fun receiptOutboxPersistsUntilExplicitNextHopAcknowledgement() {
        val now = System.currentTimeMillis()
        val receipt = MeshDeliveryReceipt.create(
            "mesh-receipt-001",
            NeaRerIdentity.generate(),
            NeaRerIdentity.generate(),
            now
        ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
        val previousHop = NeaRerIdentity.generate()
        assertTrue(MeshReceiptOutbox(context).enqueue(receipt, previousHop))

        val alternatePreviousHop = NeaRerIdentity.generate()
        assertTrue(MeshReceiptOutbox(context).enqueue(receipt, alternatePreviousHop))

        val reopened = MeshReceiptOutbox(context)
        val pending = reopened.pending()
        assertEquals(1, pending.size)
        assertEquals(alternatePreviousHop, pending.single().previousHopPeerId)
        assertTrue(receipt.encode().contentEquals(pending.single().receipt.encode()))
        assertTrue(reopened.remove(receipt.packetId))
        assertEquals(0, MeshReceiptOutbox(context).size())
        assertFalse(reopened.remove(receipt.packetId))
    }
}
