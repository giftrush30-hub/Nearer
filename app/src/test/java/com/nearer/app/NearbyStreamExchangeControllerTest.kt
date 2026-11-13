package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class NearbyStreamExchangeControllerTest {
    private val peerId = "ABCDEF234567"
    private val payloadId = 101L
    private val chunk = TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES
    private val proposal = TransferResumeNegotiation.Proposal(
        "transfer-1", "item-1", "Videos/clip.mp4", chunk * 3L,
        123L, chunk, "a".repeat(64)
    )
    private val metadata = TransferResumeStreamMetadataCodec.Metadata(
        payloadId, "transfer-1",
        TransferBundleItem("item-1", "Videos/clip.mp4", "clip.mp4", chunk * 3L, "video/mp4"),
        123L, "b".repeat(64)
    )
    private val decision = TransferResumeNegotiation.Decision(
        chunk, TransferResumeNegotiation.Reason.RESUME_ACCEPTED
    )

    private fun feed(controller: NearbyStreamExchangeController, event: String) {
        when (event) {
            "metadata" -> controller.recordMetadata(
                "endpoint-1", peerId, TransferResumeStreamMetadataCodec.encode(metadata)
            )
            "proposal" -> controller.recordProposal(
                "endpoint-1", peerId, TransferResumeMessageCodec.encode(payloadId, proposal)
            )
            "decision" -> controller.recordDecision(
                "endpoint-1", peerId, TransferResumeMessageCodec.encodeDecision(payloadId, decision)
            )
            "payload" -> controller.recordStreamPayload("endpoint-1", peerId, payloadId, true)
        }
    }

    @Test fun producesReadyStreamForEveryNearbyCallbackOrder() {
        val orders = listOf(
            listOf("metadata", "proposal", "decision", "payload"),
            listOf("metadata", "payload", "proposal", "decision"),
            listOf("proposal", "metadata", "payload", "decision"),
            listOf("decision", "payload", "metadata", "proposal"),
            listOf("payload", "decision", "proposal", "metadata"),
            listOf("payload", "metadata", "decision", "proposal")
        )
        orders.forEach { order ->
            val controller = NearbyStreamExchangeController()
            order.forEach { feed(controller, it) }
            val ready = controller.takeReady("endpoint-1", peerId, payloadId)
            assertNotNull("Could not complete callback order $order", ready)
            assertEquals(metadata, ready!!.metadata)
            assertEquals(proposal, ready.proposal)
            assertEquals(decision, ready.decision)
            assertNull(controller.takeReady("endpoint-1", peerId, payloadId))
        }
    }

    @Test fun supportsFreshStreamWithoutSavedPartial() {
        val controller = NearbyStreamExchangeController()
        val noResume = TransferResumeNegotiation.Decision(
            0L, TransferResumeNegotiation.Reason.NO_VALID_CHECKPOINT
        )
        controller.recordMetadata("endpoint-1", peerId, TransferResumeStreamMetadataCodec.encode(metadata))
        controller.recordDecision(
            "endpoint-1", peerId,
            TransferResumeMessageCodec.encodeDecision(payloadId, noResume)
        )
        controller.recordStreamPayload("endpoint-1", peerId, payloadId, true)
        val ready = controller.takeReady("endpoint-1", peerId, payloadId)
        assertNotNull(ready)
        assertNull(ready!!.proposal)
        assertEquals(noResume, ready.decision)
    }

    @Test fun rejectsUnverifiedPeersWrongPayloadTypeAndMalformedControlBytes() {
        val controller = NearbyStreamExchangeController()
        assertEquals(
            NearbyStreamExchangeController.InputResult.INVALID_PEER,
            controller.recordMetadata("endpoint-1", "bad", byteArrayOf(1))
        )
        assertEquals(
            NearbyStreamExchangeController.InputResult.MALFORMED,
            controller.recordMetadata("endpoint-1", peerId, byteArrayOf(1, 2, 3))
        )
        assertEquals(
            NearbyStreamExchangeController.InputResult.WRONG_PAYLOAD_TYPE,
            controller.recordStreamPayload("endpoint-1", peerId, payloadId, false)
        )
        assertNull(controller.takeReady("endpoint-1", peerId, payloadId))
    }

    @Test fun aChangedStableIdentityClearsTheOldEndpointExchange() {
        val controller = NearbyStreamExchangeController()
        controller.recordMetadata("endpoint-1", peerId, TransferResumeStreamMetadataCodec.encode(metadata))
        assertEquals(
            NearbyStreamExchangeController.InputResult.CONFLICT,
            controller.recordStreamPayload("endpoint-1", "BCDEFG234567", payloadId, true)
        )
        assertEquals(0, controller.pendingCount())
        assertNull(controller.takeReady("endpoint-1", peerId, payloadId))
    }

    @Test fun endpointCleanupReleasesPeerBindingAndPendingExchange() {
        val controller = NearbyStreamExchangeController()
        controller.recordMetadata("endpoint-1", peerId, TransferResumeStreamMetadataCodec.encode(metadata))
        assertEquals(1, controller.pendingCount())
        controller.clearEndpoint("endpoint-1")
        assertEquals(0, controller.pendingCount())
        assertEquals(
            NearbyStreamExchangeController.InputResult.STORED,
            controller.recordMetadata("endpoint-1", peerId, TransferResumeStreamMetadataCodec.encode(metadata))
        )
    }
}
