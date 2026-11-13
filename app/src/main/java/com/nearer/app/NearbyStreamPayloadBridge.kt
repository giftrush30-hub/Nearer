package com.nearer.app

import com.google.android.gms.nearby.connection.Payload
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small, transport-facing adapter between Nearby STREAM payloads and the
 * verified HyperTransfer resume coordinator.
 *
 * This class deliberately does not own connection authentication or the
 * proposal/decision exchange. The caller must authenticate the stable peer,
 * correlate the payload ID with the accepted resume decision, and only then
 * call receive(). It must call receive promptly from onPayloadReceived so the
 * Nearby stream pipe is drained on a worker rather than blocking its callback.
 */
internal class NearbyStreamPayloadBridge(
    private val coordinator: TransferResumeCoordinator,
    private val ioExecutor: Executor
) {
    /**
     * Creates the Nearby payload before the sender's resume decision is known.
     * This gives the transport a stable payload ID to put in metadata and the
     * proposal/decision envelopes. The underlying source is supplied only after
     * the authenticated decision chooses a verified offset (or a full restart).
     */
    fun prepareOutgoingPayload(): PreparedOutgoingStream {
        val deferred = DeferredTransferInputStream()
        return try {
            PreparedOutgoingStream(Payload.fromStream(deferred), deferred)
        } catch (error: Exception) {
            runCatching { deferred.close() }
            throw error
        }
    }

    class PreparedOutgoingStream internal constructor(
        val payload: Payload,
        private val deferred: DeferredTransferInputStream
    ) : java.io.Closeable {
        val payloadId: Long get() = payload.id

        fun provideSource(input: InputStream) = deferred.provide(input)

        fun provideSource(source: TransferResumeCoordinator.ValidatedSourceStream) =
            deferred.provide(source.input)

        fun reject(error: Throwable) {
            deferred.fail(error)
            runCatching { payload.close() }
        }

        override fun close() {
            runCatching { payload.close() }
            runCatching { deferred.close() }
        }
    }

    /**
     * Wraps an already-positioned source stream in a Nearby STREAM payload.
     * For a resumed transfer, pass ValidatedSourceStream.input so the source
     * begins at the prefix-verified offset and is bounded to the declared size.
     * For a fresh transfer, pass the source opened at byte zero.
     *
     * Nearby closes the stream when the payload is closed or finishes. If
     * payload construction fails, this method closes the supplied input.
     */
    fun createOutgoingPayload(input: InputStream): Payload {
        return try {
            Payload.fromStream(input)
        } catch (error: Exception) {
            runCatching { input.close() }
            throw error
        }
    }

    fun createOutgoingPayload(source: TransferResumeCoordinator.ValidatedSourceStream): Payload {
        return createOutgoingPayload(source.input)
    }

    /**
     * Consumes a Nearby STREAM into the durable partial, verifies its complete
     * digest, and publishes it without overwriting an existing destination.
     *
     * Call only after the proposal/decision exchange has been validated for
     * this exact authenticated peer and payload ID. A zero offset represents a
     * full restart; a positive offset must exactly match the local peer-bound
     * checkpoint or the coordinator rejects the stream before appending.
     *
     * The callback runs on ioExecutor. The receiver owns the payload from the
     * moment this method is called; it is closed on every terminal path.
     */
    fun receive(
        payload: Payload,
        metadata: TransferResumeStreamMetadataCodec.Metadata,
        authenticatedSourcePeerId: String,
        proposal: TransferResumeNegotiation.Proposal?,
        decision: TransferResumeNegotiation.Decision,
        destination: java.io.File,
        isCancelled: () -> Boolean = { false },
        onComplete: (Result<TransferResumeCoordinator.ReceiveResult>) -> Unit
    ): Boolean {
        val metadataValid = runCatching { TransferResumeStreamMetadataCodec.validate(metadata) }.isSuccess
        if (!metadataValid || !HyperTransferStreamAdmissionPolicy.mayConsume(
                authenticatedSourcePeerId = authenticatedSourcePeerId,
                expectedPayloadId = metadata.payloadId,
                actualPayloadId = payload.id,
                isStreamPayload = payload.type == Payload.Type.STREAM,
                proposal = proposal,
                decision = decision
            )) {
            runCatching { payload.close() }
            onComplete(Result.failure(IllegalArgumentException("The incoming stream does not match the verified transfer.")))
            return false
        }

        val callbackDelivered = AtomicBoolean(false)
        val deliver: (Result<TransferResumeCoordinator.ReceiveResult>) -> Unit = { result ->
            if (callbackDelivered.compareAndSet(false, true)) {
                runCatching { onComplete(result) }
            }
        }

        return try {
            ioExecutor.execute {
                try {
                    val stream = payload.asStream()?.asInputStream()
                        ?: throw IllegalStateException("Nearby did not provide a readable stream.")
                    val result = coordinator.receiveStreamAndFinalize(
                        transferId = metadata.transferId,
                        item = metadata.item,
                        sourceModifiedAtMillis = metadata.sourceModifiedAtMillis,
                        authenticatedSourcePeerId = authenticatedSourcePeerId,
                        acceptedOffsetBytes = decision.acceptedOffsetBytes,
                        input = stream,
                        destination = destination,
                        expectedWholeFileSha256 = metadata.wholeFileSha256,
                        isCancelled = isCancelled
                    )
                    deliver(Result.success(result))
                } catch (error: Exception) {
                    deliver(Result.failure(error))
                } finally {
                    runCatching { payload.close() }
                }
            }
            true
        } catch (error: RejectedExecutionException) {
            runCatching { payload.close() }
            deliver(Result.failure(error))
            false
        } catch (error: Exception) {
            runCatching { payload.close() }
            deliver(Result.failure(error))
            false
        }
    }
}
