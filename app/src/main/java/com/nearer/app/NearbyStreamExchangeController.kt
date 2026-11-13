package com.nearer.app

/**
 * Authenticated BYTES/STREAM handshake controller for the transport layer.
 *
 * The Nearby callback owner feeds decoded BYTES payloads and the STREAM callback
 * here. The controller binds pending exchanges to the stable authenticated peer,
 * handles arbitrary callback order through the bounded tracker, and returns a
 * ready exchange only after metadata, proposal/decision (or explicit fresh
 * start), and the matching STREAM payload are all present.
 */
internal class NearbyStreamExchangeController(
    private val tracker: TransferResumeExchangeTracker = TransferResumeExchangeTracker()
) {
    private val endpointPeers = HashMap<String, String>()

    enum class InputResult {
        STORED,
        DUPLICATE,
        CONFLICT,
        CAPACITY_REACHED,
        INVALID_ENDPOINT,
        INVALID_PEER,
        WRONG_PAYLOAD_TYPE,
        MALFORMED
    }

    data class ReadyStream(
        val endpointId: String,
        val authenticatedPeerId: String,
        val metadata: TransferResumeStreamMetadataCodec.Metadata,
        val proposal: TransferResumeNegotiation.Proposal?,
        val decision: TransferResumeNegotiation.Decision
    )

    @Synchronized
    fun recordMetadata(endpointId: String, authenticatedPeerId: String, bytes: ByteArray): InputResult {
        if (!bind(endpointId, authenticatedPeerId)) return bindingFailure(endpointId, authenticatedPeerId)
        val metadata = try {
            TransferResumeStreamMetadataCodec.decode(bytes)
        } catch (_: Exception) {
            return InputResult.MALFORMED
        }
        return map(tracker.recordMetadata(endpointId, metadata))
    }

    @Synchronized
    fun recordProposal(endpointId: String, authenticatedPeerId: String, bytes: ByteArray): InputResult {
        if (!bind(endpointId, authenticatedPeerId)) return bindingFailure(endpointId, authenticatedPeerId)
        val envelope = try {
            TransferResumeMessageCodec.decode(bytes)
        } catch (_: Exception) {
            return InputResult.MALFORMED
        }
        return map(tracker.recordProposal(endpointId, envelope))
    }

    /** Records the receiver's explicit no-checkpoint response for a fresh transfer. */
    @Synchronized
    fun recordNoResume(endpointId: String, authenticatedPeerId: String, bytes: ByteArray): InputResult {
        if (!bind(endpointId, authenticatedPeerId)) return bindingFailure(endpointId, authenticatedPeerId)
        val envelope = try {
            TransferResumeMessageCodec.decodeNoResume(bytes)
        } catch (_: Exception) {
            return InputResult.MALFORMED
        }
        val decision = TransferResumeNegotiation.Decision(
            0L, TransferResumeNegotiation.Reason.NO_VALID_CHECKPOINT
        )
        return map(tracker.recordDecision(
            endpointId, TransferResumeMessageCodec.DecisionEnvelope(envelope.payloadId, decision)
        ))
    }

    @Synchronized
    fun recordDecision(endpointId: String, authenticatedPeerId: String, bytes: ByteArray): InputResult {
        if (!bind(endpointId, authenticatedPeerId)) return bindingFailure(endpointId, authenticatedPeerId)
        val envelope = try {
            TransferResumeMessageCodec.decodeDecision(bytes)
        } catch (_: Exception) {
            return InputResult.MALFORMED
        }
        return map(tracker.recordDecision(endpointId, envelope))
    }

    @Synchronized
    fun recordStreamPayload(
        endpointId: String,
        authenticatedPeerId: String,
        payloadId: Long,
        isStreamPayload: Boolean
    ): InputResult {
        if (!bind(endpointId, authenticatedPeerId)) return bindingFailure(endpointId, authenticatedPeerId)
        if (!isStreamPayload) return InputResult.WRONG_PAYLOAD_TYPE
        return map(tracker.recordDataPayload(endpointId, payloadId))
    }

    @Synchronized
    fun takeReady(endpointId: String, authenticatedPeerId: String, payloadId: Long): ReadyStream? {
        val peerId = NeaRerIdentity.normalize(authenticatedPeerId) ?: return null
        if (endpointPeers[endpointId] != peerId) return null
        val ready = tracker.takeReadyForStream(endpointId, payloadId) ?: return null
        val metadata = ready.metadata ?: return null
        if (metadata.payloadId != payloadId ||
            !HyperTransferStreamAdmissionPolicy.mayConsume(
                authenticatedSourcePeerId = peerId,
                expectedPayloadId = metadata.payloadId,
                actualPayloadId = payloadId,
                isStreamPayload = true,
                proposal = ready.proposal,
                decision = ready.decision
            )) return null
        return ReadyStream(endpointId, peerId, metadata, ready.proposal, ready.decision)
    }

    @Synchronized
    fun clearEndpoint(endpointId: String) {
        tracker.clearEndpoint(endpointId)
        endpointPeers.remove(endpointId)
    }

    @Synchronized
    fun clear() {
        tracker.clear()
        endpointPeers.clear()
    }

    @Synchronized
    fun pendingCount(): Int = tracker.pendingCount()

    private fun bind(endpointId: String, authenticatedPeerId: String): Boolean {
        if (endpointId.isBlank()) return false
        val normalized = NeaRerIdentity.normalize(authenticatedPeerId) ?: return false
        val existing = endpointPeers[endpointId]
        if (existing != null && existing != normalized) {
            tracker.clearEndpoint(endpointId)
            endpointPeers.remove(endpointId)
            return false
        }
        endpointPeers[endpointId] = normalized
        return true
    }

    private fun bindingFailure(endpointId: String, authenticatedPeerId: String): InputResult =
        if (endpointId.isBlank()) InputResult.INVALID_ENDPOINT else
            if (NeaRerIdentity.normalize(authenticatedPeerId) == null) InputResult.INVALID_PEER
            else InputResult.CONFLICT

    private fun map(update: TransferResumeExchangeTracker.Update): InputResult = when (update) {
        TransferResumeExchangeTracker.Update.STORED -> InputResult.STORED
        TransferResumeExchangeTracker.Update.DUPLICATE -> InputResult.DUPLICATE
        TransferResumeExchangeTracker.Update.CONFLICT -> InputResult.CONFLICT
        TransferResumeExchangeTracker.Update.CAPACITY_REACHED -> InputResult.CAPACITY_REACHED
        TransferResumeExchangeTracker.Update.INVALID_KEY -> InputResult.INVALID_ENDPOINT
    }
}
