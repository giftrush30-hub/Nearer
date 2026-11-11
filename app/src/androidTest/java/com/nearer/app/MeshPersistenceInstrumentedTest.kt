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
        context.getSharedPreferences("nearer_mesh_route_withdrawals_v1", 0).edit().clear().commit()
    }

    @After
    fun cleanMeshStores() {
        context.getSharedPreferences("nearer_dead_drop_v1", 0).edit().clear().commit()
        context.getSharedPreferences("nearer_mesh_receipt_outbox_v1", 0).edit().clear().commit()
        context.getSharedPreferences("nearer_mesh_route_withdrawals_v1", 0).edit().clear().commit()
    }

    private fun recipientKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    @Test
    fun deadDropEncryptsQueueMetadataAtRest() {
        val now = System.currentTimeMillis()
        val recipient = recipientKeyPair()
        val packet = MeshRelayPacketV1.create(
            originId = NeaRerIdentity.generate(),
            destinationId = NeaRerIdentity.generate(),
            packetId = "mesh-private-meta-001",
            createdAtMs = now,
            expiresAtMs = now + 60_000L,
            hopLimit = 4,
            recipientPublicKey = recipient.public.encoded,
            plaintext = "private transit body".toByteArray()
        )
        val previousHop = NeaRerIdentity.generate()
        val nextHop = NeaRerIdentity.generate()
        val store = DeadDropStore(context)
        assertTrue(store.enqueueTransit(packet, previousHop, now))
        assertNotNull(store.prepareForForwarding(packet.packetId, NeaRerIdentity.generate(), now + 1L, nextHop))

        val stored = context.getSharedPreferences("nearer_dead_drop_v1", 0)
            .getString("queue_v1", "").orEmpty()
        assertTrue(stored.isNotBlank())
        assertFalse(stored.contains(packet.packetId))
        assertFalse(stored.contains(previousHop))
        assertFalse(stored.contains(nextHop))
        assertEquals(1, DeadDropStore(context).size())
    }

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
        val nextHop = NeaRerIdentity.generate()
        val acceptingIdentity = recipientKeyPair()
        val firstStore = DeadDropStore(context)
        assertTrue(firstStore.enqueueTransit(packet, previousHop, now))

        val reopenedStore = DeadDropStore(context)
        val claimed = reopenedStore.claimDue(now + 1L, 1)
        assertEquals(1, claimed.size)
        assertEquals(packet.packetId, claimed.single().packetId)
        assertEquals(previousHop, claimed.single().previousHopPeerId)
        assertTrue(MeshRelayPacketV1.decode(claimed.single().bytes).verifyEmbeddedOrigin(now + 1L))

        val prepared = reopenedStore.prepareForForwarding(packet.packetId, localRelay, now + 2L, nextHop)
        assertNotNull(prepared)
        assertEquals(1, prepared!!.hopsTaken)

        val wrongHopAck = MeshHopAcknowledgement.create(
            packet.packetId, NeaRerIdentity.generate(), localRelay, now + 3L
        ) { bytes ->
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initSign(acceptingIdentity.private)
                update(bytes)
                sign()
            }
        }
        assertFalse(reopenedStore.recordHopAccepted(
            wrongHopAck, acceptingIdentity.public.encoded, wrongHopAck.acceptingPeerId, localRelay, now + 4L
        ))
        val correctHopAck = MeshHopAcknowledgement.create(
            packet.packetId, nextHop, localRelay, now + 5L
        ) { bytes ->
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initSign(acceptingIdentity.private)
                update(bytes)
                sign()
            }
        }
        assertTrue(reopenedStore.recordHopAccepted(
            correctHopAck, acceptingIdentity.public.encoded, nextHop, localRelay, now + 6L
        ))

        // Reopening and retrying must reuse the prepared hop count, not increment again.
        val afterRestart = DeadDropStore(context)
        val retry = afterRestart.claimDue(now + 31_000L, 1)
        assertEquals(1, retry.size)
        val preparedAgain = afterRestart.prepareForForwarding(packet.packetId, localRelay, now + 31_001L, nextHop)
        assertNotNull(preparedAgain)
        assertEquals(1, preparedAgain!!.hopsTaken)
        assertTrue(afterRestart.recordHopAccepted(
            correctHopAck, acceptingIdentity.public.encoded, nextHop, localRelay, now + 31_002L
        ))

        // A replay cannot roll the persisted hop count backwards.
        assertFalse(afterRestart.enqueueTransit(packet.copy(hopsTaken = 0), NeaRerIdentity.generate(), now + 31_002L))
    }

    @Test
    fun deadDropKeepsRetryingAtCappedBackoffUntilPacketExpiry() {
        val now = System.currentTimeMillis()
        val recipient = recipientKeyPair()
        val packet = MeshRelayPacketV1.create(
            originId = NeaRerIdentity.generate(),
            destinationId = NeaRerIdentity.generate(),
            packetId = "mesh-retry-until-expiry",
            createdAtMs = now,
            expiresAtMs = now + 3L * 24L * 60L * 60L * 1000L,
            hopLimit = 4,
            recipientPublicKey = recipient.public.encoded,
            plaintext = "retry until expiry".toByteArray()
        )
        val store = DeadDropStore(context)
        assertTrue(store.enqueueTransit(packet, NeaRerIdentity.generate(), now))
        var current = now + 1L
        var lastAttempt = 0
        repeat(20) {
            val claimed = store.claimDue(current, 1)
            assertEquals(1, claimed.size)
            lastAttempt = claimed.single().attempts
            current += 3_600_001L
        }
        assertEquals(16, lastAttempt)
        assertEquals(1, store.claimDue(current, 1).size)
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
    fun routeWithdrawalTombstonesSurviveStoreRecreationAndRejectOldAdvertisements() {
        val sender = recipientKeyPair()
        val destination = recipientKeyPair()
        val tombstone = MeshRouteTable.WithdrawalTombstone("BBBBBB", "AAAAAA", 7L)
        assertTrue(MeshRouteWithdrawalStore(context).record(tombstone))

        val table = MeshRouteTable("CCCCCC")
        assertEquals(1, MeshRouteWithdrawalStore(context).restore(table))
        val stale = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 7L, 0, 1000L, 9000L
        ) { bytes ->
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initSign(sender.private)
                update(bytes)
                sign()
            }
        }
        assertFalse(table.update(stale, "AAAAAA", sender.public.encoded, 2000L))
        val fresh = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 8L, 0, 2100L, 9000L
        ) { bytes ->
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initSign(sender.private)
                update(bytes)
                sign()
            }
        }
        assertTrue(table.update(fresh, "AAAAAA", sender.public.encoded, 2200L))
    }

    @Test
    fun olderWithdrawalReplayDoesNotLowerPersistentSequenceWatermark() {
        val store = MeshRouteWithdrawalStore(context)
        assertTrue(store.record(MeshRouteTable.WithdrawalTombstone("BBBBBB", "AAAAAA", 12L)))
        assertTrue(store.record(MeshRouteTable.WithdrawalTombstone("BBBBBB", "AAAAAA", 9L)))
        val restored = MeshRouteTable("CCCCCC")
        assertEquals(1, store.restore(restored))
        val sender = recipientKeyPair()
        val destination = recipientKeyPair()
        val replay = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 12L, 0, 1000L, 9000L
        ) { bytes ->
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initSign(sender.private)
                update(bytes)
                sign()
            }
        }
        assertFalse(restored.update(replay, "AAAAAA", sender.public.encoded, 2000L))
    }

    @Test
    fun corruptedWithdrawalStoreFailsClosedAndDoesNotRestoreUnverifiedTombstones() {
        val prefs = context.getSharedPreferences("nearer_mesh_route_withdrawals_v1", 0)
        prefs.edit().putString("withdrawals_encrypted_v1", "not-valid-ciphertext").commit()
        val table = MeshRouteTable("CCCCCC")
        assertEquals(-1, MeshRouteWithdrawalStore(context).restore(table))
        val sender = recipientKeyPair()
        val destination = recipientKeyPair()
        val stale = SignedRouteAdvertisement.create(
            "AAAAAA", "BBBBBB", destination.public.encoded, 7L, 0, 1000L, 9000L
        ) { bytes ->
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initSign(sender.private)
                update(bytes)
                sign()
            }
        }
        // The negative restore result is a hard health failure. The transport
        // must pause route exchange instead of treating corruption as "no tombstones".
        assertTrue(table.update(stale, "AAAAAA", sender.public.encoded, 2000L))
    }

    @Test
    fun withdrawalStoreFailsClosedInsteadOfEvictingLiveReplayWatermarks() {
        val store = MeshRouteWithdrawalStore(context)
        val tombstones = (0 until 512).map {
            MeshRouteTable.WithdrawalTombstone(
                destinationId = NeaRerIdentity.generate(),
                advertiserId = NeaRerIdentity.generate(),
                sequence = it.toLong()
            )
        }
        assertEquals(512, tombstones.distinctBy { it.destinationId + "|" + it.advertiserId }.size)
        assertTrue(store.saveAll(tombstones))
        val overflow = MeshRouteTable.WithdrawalTombstone(
            NeaRerIdentity.generate(), NeaRerIdentity.generate(), 999L
        )
        assertFalse(store.record(overflow))
        val restored = MeshRouteTable("CCCCCC")
        assertEquals(-1, MeshRouteWithdrawalStore(context).restore(restored))
    }

    @Test
    fun invalidWithdrawalRecordPermanentlyDisablesRouteRestore() {
        val invalid = MeshRouteTable.WithdrawalTombstone(
            destinationId = "NOT-VALID",
            advertiserId = NeaRerIdentity.generate(),
            sequence = 12L
        )
        assertFalse(MeshRouteWithdrawalStore(context).saveAll(listOf(invalid)))
        assertEquals(-1, MeshRouteWithdrawalStore(context).restore(MeshRouteTable("CCCCCC")))
    }

    @Test
    fun receiptOutboxEncryptsRoutingMetadataAtRest() {
        val receipt = MeshDeliveryReceipt.create(
            "receipt-private-meta-01",
            NeaRerIdentity.generate(),
            NeaRerIdentity.generate(),
            System.currentTimeMillis()
        ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
        val previousHop = NeaRerIdentity.generate()
        val nextHop = NeaRerIdentity.generate()
        val outbox = MeshReceiptOutbox(context)
        assertTrue(outbox.enqueue(receipt, previousHop))
        assertTrue(outbox.setExpectedNextHop(receipt.packetId, nextHop))

        val stored = context.getSharedPreferences("nearer_mesh_receipt_outbox_v1", 0)
            .getString("receipts_v1", "").orEmpty()
        assertTrue(stored.isNotBlank())
        assertFalse(stored.contains(previousHop))
        assertFalse(stored.contains(nextHop))
        assertFalse(stored.contains(receipt.packetId))
        assertEquals(1, MeshReceiptOutbox(context).pending().size)
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
        val intendedNextHop = NeaRerIdentity.generate()
        val wrongNextHop = NeaRerIdentity.generate()
        assertTrue(reopened.setExpectedNextHop(receipt.packetId, intendedNextHop))
        val afterRestart = MeshReceiptOutbox(context)
        assertEquals(intendedNextHop, afterRestart.pending().single().expectedNextHopPeerId)
        assertFalse(afterRestart.acknowledge(receipt.packetId, wrongNextHop))
        assertEquals(1, afterRestart.size())
        assertTrue(afterRestart.acknowledge(receipt.packetId, intendedNextHop))
        assertEquals(0, MeshReceiptOutbox(context).size())
        assertFalse(afterRestart.acknowledge(receipt.packetId, intendedNextHop))
    }
}
