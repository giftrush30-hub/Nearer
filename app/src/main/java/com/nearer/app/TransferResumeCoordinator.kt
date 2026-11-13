package com.nearer.app

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Transport-neutral coordinator for the durable HyperTransfer STREAM path.
 *
 * The caller must establish the authenticated peer identity and negotiate these
 * values over that authenticated session. The sender must position its stream at
 * the accepted offset. This class deliberately does not pretend a Nearby FILE
 * payload can be resumed by the stream writer: the transport must use a STREAM
 * payload and pass the authenticated stable peer ID here.
 */
internal class TransferResumeCoordinator(
    private val store: TransferResumeStore,
    private val writer: TransferResumeStreamWriter
) {

    // Serialize only identical transfer/item receives. A bounded lock map is
    // maintained by reference counts so duplicate Nearby callbacks cannot write
    // the same partial concurrently, while unrelated files can still progress.
    private data class ReceiveLockEntry(val lock: java.util.concurrent.locks.ReentrantLock, var users: Int)
    private val receiveLockMonitor = Any()
    private val receiveLocks = HashMap<String, ReceiveLockEntry>()

    private fun acquireReceiveLock(key: String): ReceiveLockEntry = synchronized(receiveLockMonitor) {
        val entry = receiveLocks.getOrPut(key) {
            ReceiveLockEntry(java.util.concurrent.locks.ReentrantLock(), 0)
        }
        entry.users++
        if (!entry.lock.tryLock()) {
            entry.users--
            if (entry.users == 0 && receiveLocks[key] === entry) receiveLocks.remove(key)
            throw IllegalStateException("This file is already being received.")
        }
        entry
    }

    private fun releaseReceiveLock(key: String, entry: ReceiveLockEntry) {
        entry.lock.unlock()
        synchronized(receiveLockMonitor) {
            entry.users--
            if (entry.users == 0 && !entry.lock.isLocked && receiveLocks[key] === entry) {
                receiveLocks.remove(key)
            }
        }
    }

    data class ReceiverOffer(
        val checkpoint: TransferResumeCheckpoint,
        val proposal: TransferResumeNegotiation.Proposal
    )

    data class ReceiveResult(
        val completedFile: File,
        val resumedFromBytes: Long,
        val totalBytes: Long,
        val sha256: String
    )

    /**
     * Discover verified receiver partials and offer only those owned by the
     * currently authenticated sender. Discovery itself re-hashes each prefix.
     */
    fun offersForAuthenticatedSender(
        authenticatedSourcePeerId: String?,
        senderSupportsOffsets: Boolean,
        receiverSupportsOffsets: Boolean
    ): List<ReceiverOffer> {
        val peerId = NeaRerIdentity.normalize(authenticatedSourcePeerId) ?: return emptyList()
        if (!senderSupportsOffsets || !receiverSupportsOffsets) return emptyList()
        return store.discoverVerifiedCandidates().mapNotNull { candidate ->
            if (candidate.checkpoint.sourcePeerId != peerId) return@mapNotNull null
            val proposal = TransferResumeNegotiation.propose(
                checkpoint = candidate.checkpoint,
                receiverPrefixVerified = true,
                senderSupportsOffsets = senderSupportsOffsets,
                receiverSupportsOffsets = receiverSupportsOffsets,
                authenticatedSourcePeerId = peerId
            ) ?: return@mapNotNull null
            ReceiverOffer(candidate.checkpoint, proposal)
        }
    }

    /**
     * Sender-side validation. The source must still be the same file (size and
     * modification time) and the bytes before the proposed offset must hash to
     * the receiver's committed-prefix digest. The authenticated peer is supplied
     * by the transport; it is never inferred from a display name or endpoint ID.
     */
    fun validateOfferAtSender(
        proposal: TransferResumeNegotiation.Proposal?,
        transferId: String,
        item: TransferBundleItem,
        sourceFile: File,
        sourceModifiedAtMillis: Long?,
        authenticatedReceiverPeerId: String?,
        senderSupportsOffsets: Boolean,
        receiverSupportsOffsets: Boolean
    ): TransferResumeNegotiation.Decision {
        val receiverId = NeaRerIdentity.normalize(authenticatedReceiverPeerId)
        if (receiverId == null || !senderSupportsOffsets || !receiverSupportsOffsets) {
            return TransferResumeNegotiation.Decision(0L, TransferResumeNegotiation.Reason.PEER_UNSUPPORTED)
        }
        if (proposal == null) {
            return TransferResumeNegotiation.Decision(0L, TransferResumeNegotiation.Reason.NO_VALID_CHECKPOINT)
        }
        if (!sourceMetadataMatches(sourceFile, item, sourceModifiedAtMillis)) {
            return TransferResumeNegotiation.Decision(0L, TransferResumeNegotiation.Reason.SOURCE_CHANGED)
        }
        val offset = proposal.committedOffsetBytes
        if (offset !in 1L until item.sizeBytes ||
            offset % TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES != 0L) {
            return TransferResumeNegotiation.Decision(0L, TransferResumeNegotiation.Reason.OFFSET_INVALID)
        }
        val prefixDigest = try {
            sha256Prefix(sourceFile, offset)
        } catch (_: Exception) {
            return TransferResumeNegotiation.Decision(0L, TransferResumeNegotiation.Reason.OFFSET_INVALID)
        }
        return TransferResumeNegotiation.decide(
            proposal = proposal,
            transferId = transferId,
            item = item,
            sourceModifiedAtMillis = sourceModifiedAtMillis,
            senderSupportsOffsets = senderSupportsOffsets,
            receiverSupportsOffsets = receiverSupportsOffsets,
            senderPrefixSha256 = prefixDigest
        )
    }

    /**
     * Opens the sender's source at the exact receiver-approved offset.
     *
     * The prefix is re-hashed from the same open file descriptor that will feed
     * the outgoing stream. This closes the gap between validating a path and
     * later reopening it for transmission. The returned stream is bounded to
     * the declared remaining byte count, so a growing source cannot append
     * unexpected bytes to this transfer.
     */
    fun openValidatedSourceAtOffset(
        proposal: TransferResumeNegotiation.Proposal?,
        transferId: String,
        item: TransferBundleItem,
        sourceFile: File,
        sourceModifiedAtMillis: Long?,
        authenticatedReceiverPeerId: String?,
        senderSupportsOffsets: Boolean,
        receiverSupportsOffsets: Boolean
    ): ValidatedSourceStream? {
        if (NeaRerIdentity.normalize(authenticatedReceiverPeerId) == null) return null
        if (!senderSupportsOffsets || !receiverSupportsOffsets || proposal == null) return null
        if (!sourceMetadataMatches(sourceFile, item, sourceModifiedAtMillis)) return null

        val source = try { java.io.FileInputStream(sourceFile) } catch (_: Exception) { return null }
        var ownershipTransferred = false
        try {
            if (!sourceMetadataMatches(sourceFile, item, sourceModifiedAtMillis)) return null
            val offset = proposal.committedOffsetBytes
            if (offset !in 1L until item.sizeBytes ||
                offset % TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES != 0L) return null

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var remaining = offset
            while (remaining > 0L) {
                val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) return null
                digest.update(buffer, 0, read)
                remaining -= read.toLong()
            }
            if (!sourceMetadataMatches(sourceFile, item, sourceModifiedAtMillis)) return null

            val decision = TransferResumeNegotiation.decide(
                proposal = proposal,
                transferId = transferId,
                item = item,
                sourceModifiedAtMillis = sourceModifiedAtMillis,
                senderSupportsOffsets = true,
                receiverSupportsOffsets = true,
                senderPrefixSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            )
            if (decision.reason != TransferResumeNegotiation.Reason.RESUME_ACCEPTED ||
                decision.acceptedOffsetBytes != offset) return null

            source.channel.position(offset)
            val bounded = object : java.io.FilterInputStream(source) {
                private var left = item.sizeBytes - offset
                override fun read(): Int {
                    if (left == 0L) return -1
                    val value = super.read()
                    if (value < 0) throw java.io.EOFException("Source ended before its declared size")
                    left--
                    return value
                }
                override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                    if (len == 0) return 0
                    if (left == 0L) return -1
                    val read = super.read(bytes, off, minOf(len.toLong(), left).toInt())
                    if (read < 0) throw java.io.EOFException("Source ended before its declared size")
                    if (read > 0) left -= read.toLong()
                    return read
                }
                override fun close() = super.close()
            }
            ownershipTransferred = true
            return ValidatedSourceStream(bounded, offset, item.sizeBytes - offset, item.sizeBytes)
        } catch (_: Exception) {
            return null
        } finally {
            if (!ownershipTransferred) runCatching { source.close() }
        }
    }

    data class ValidatedSourceStream(
        val input: InputStream,
        val acceptedOffsetBytes: Long,
        val remainingBytes: Long,
        val totalBytes: Long
    ) : java.io.Closeable {
        override fun close() = input.close()
    }

    /**
     * Receiver-side completion for a STREAM whose first byte is exactly the
     * sender-approved offset. The writer independently verifies the local
     * checkpoint and sender identity; the finalizer verifies the whole-file
     * digest and publishes without replacing an existing destination.
     *
     * Interrupted streams leave a synced checkpoint for a later reconnect.
     */
    fun receiveStreamAndFinalize(
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?,
        authenticatedSourcePeerId: String,
        acceptedOffsetBytes: Long,
        input: InputStream,
        destination: File,
        expectedWholeFileSha256: String,
        isCancelled: () -> Boolean = { false }
    ): ReceiveResult {
        val key = "$transferId\\u0000${item.id}"
        val lock = acquireReceiveLock(key)
        try {
            return receiveStreamAndFinalizeLocked(
                transferId = transferId,
                item = item,
                sourceModifiedAtMillis = sourceModifiedAtMillis,
                authenticatedSourcePeerId = authenticatedSourcePeerId,
                acceptedOffsetBytes = acceptedOffsetBytes,
                input = input,
                destination = destination,
                expectedWholeFileSha256 = expectedWholeFileSha256,
                isCancelled = isCancelled
            )
        } finally {
            releaseReceiveLock(key, lock)
        }
    }

    private fun receiveStreamAndFinalizeLocked(
        transferId: String,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?,
        authenticatedSourcePeerId: String,
        acceptedOffsetBytes: Long,
        input: InputStream,
        destination: File,
        expectedWholeFileSha256: String,
        isCancelled: () -> Boolean = { false }
    ): ReceiveResult {
        val peerId = NeaRerIdentity.normalize(authenticatedSourcePeerId)
            ?: throw SecurityException("The sender identity is not authenticated")
        require(acceptedOffsetBytes in 0L..item.sizeBytes) { "Invalid accepted resume offset" }
        require(acceptedOffsetBytes == 0L ||
            (acceptedOffsetBytes < item.sizeBytes &&
                acceptedOffsetBytes % TransferResumeCheckpoint.CHECKPOINT_GRANULARITY_BYTES == 0L)
        ) { "Accepted resume offset is not a valid checkpoint boundary" }
        require(Regex("^[a-fA-F0-9]{64}$").matches(expectedWholeFileSha256)) {
            "A valid whole-file SHA-256 is required"
        }

        // Compare the transport's accepted offset with the receiver's current
        // peer-bound checkpoint before opening the partial for append. If the
        // sender chose a clean restart (offset zero), discard only this same
        // authenticated sender's verified partial first.
        val existing = store.findValid(transferId, item, sourceModifiedAtMillis, peerId)
        val locallyVerifiedOffset = existing?.checkpoint?.committedOffsetBytes ?: 0L
        if (acceptedOffsetBytes == 0L && locallyVerifiedOffset > 0L) {
            store.discard(transferId, item.id)
        } else if (acceptedOffsetBytes != locallyVerifiedOffset) {
            throw SecurityException("The sender-approved offset does not match the receiver's verified checkpoint")
        }

        val written = writer.write(
            transferId = transferId,
            item = item,
            sourceModifiedAtMillis = sourceModifiedAtMillis,
            input = input,
            sourcePeerId = peerId,
            isCancelled = isCancelled
        )
        if (written.resumedFromBytes != acceptedOffsetBytes) {
            store.discard(transferId, item.id)
            throw SecurityException("The local verified offset differs from the sender-approved offset")
        }
        if (!written.sha256.equals(expectedWholeFileSha256, ignoreCase = true)) {
            store.discard(transferId, item.id)
            throw SecurityException("The completed stream does not match the sender's whole-file digest")
        }
        val completed = store.finalizeVerifiedComplete(
            transferId = transferId,
            item = item,
            sourceModifiedAtMillis = sourceModifiedAtMillis,
            destination = destination,
            expectedSha256 = expectedWholeFileSha256,
            sourcePeerId = peerId
        )
        return ReceiveResult(completed, written.resumedFromBytes, written.bytesReceived, written.sha256)
    }

    private fun sourceMetadataMatches(
        sourceFile: File,
        item: TransferBundleItem,
        sourceModifiedAtMillis: Long?
    ): Boolean = sourceFile.isFile &&
        sourceFile.length() == item.sizeBytes &&
        (sourceModifiedAtMillis == null || sourceModifiedAtMillis == sourceFile.lastModified())

    private fun sha256Prefix(file: File, length: Long): String {
        require(length >= 0L && length <= file.length()) { "Prefix length is outside the source file" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) throw IllegalStateException("Source ended before the proposed offset")
                digest.update(buffer, 0, read)
                remaining -= read.toLong()
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
