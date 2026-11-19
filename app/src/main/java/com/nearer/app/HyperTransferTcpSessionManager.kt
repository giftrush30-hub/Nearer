package com.nearer.app

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the Wi-Fi Direct bulk-transfer lifecycle. Offers/decisions stay on the
 * authenticated Nearby control channel; the TCP socket is opened only after
 * the service's one-shot approval ledger authorizes this exact offer.
 */
internal class HyperTransferTcpSessionManager(
    context: Context,
    private val coordinator: () -> WifiDirectConnectionCoordinator?,
    private val currentLink: () -> WifiDirectConnectionCoordinator.Link?,
    private val sendOffer: (String, HyperTransferTcpOfferCodec.Offer) -> Boolean,
    private val respondToOffer: (String, String, Boolean) -> Boolean,
    private val consumeApproval: (
        String, String, HyperTransferTcpOfferCodec.Offer, HyperTransferApprovalLedger.Direction
    ) -> Boolean,
    private val listener: Listener
) : AutoCloseable {
    interface Listener {
        fun onIncomingOffer(endpointId: String, peerId: String, offer: HyperTransferTcpOfferCodec.Offer)
        fun onProgress(
            endpointId: String, payloadId: Long, name: String, transferred: Long,
            total: Long, incoming: Boolean, speed: TransferSpeedSnapshot
        )
        fun onReceived(endpointId: String, name: String, file: File)
        fun onStatus(message: String)
    }

    private data class TransferState(
        val endpointId: String,
        val peerId: String,
        val offer: HyperTransferTcpOfferCodec.Offer,
        val payloadId: Long,
        val incoming: Boolean,
        @Volatile var source: File? = null,
        @Volatile var approved: Boolean = false,
        val started: AtomicBoolean = AtomicBoolean(false),
        @Volatile var cancellation: WifiDirectTcpTransferEngine.Cancellation? = null
    )

    private val appContext = context.applicationContext
    private val transferBufferBytes = HyperTransferTuning.transferBufferBytes(context)
    private val socketBufferBytes = HyperTransferTuning.socketBufferBytes(context)
    private val engine = WifiDirectTcpTransferEngine(
        bufferBytes = transferBufferBytes,
        socketBufferBytes = socketBufferBytes
    )
    private val workers = Executors.newFixedThreadPool(2)
    private val outgoing = ConcurrentHashMap<Long, TransferState>()
    private val incoming = ConcurrentHashMap<String, TransferState>()
    @Volatile private var activeServer: ServerSocket? = null
    @Volatile private var activeSocket: Socket? = null
    private val activeTransfer = AtomicBoolean(false)
    @Volatile private var activePayloadId: Long? = null
    @Volatile private var closed = false

    /** Returns null when P2P is unavailable so the caller can keep using Nearby. */
    fun sendFile(endpointId: String, peerId: String, uri: Uri, displayName: String): Long? {
        if (closed || coordinator() == null || currentLink() == null ||
            NeaRerIdentity.normalize(peerId) != peerId) return null
        val transferId = UUID.randomUUID().toString().replace("-", "") +
            UUID.randomUUID().toString().replace("-", "")
        val payloadId = transferId.take(15).toLong(16)
        val safeName = TransferFileNamePolicy.safeName(displayName)
        val placeholder = HyperTransferTcpOfferCodec.Offer(
            transferId, safeName, 0L, "0".repeat(64)
        )
        val initial = TransferState(endpointId, peerId, placeholder, payloadId, incoming = false)
        if (outgoing.putIfAbsent(payloadId, initial) != null) return null

        workers.execute {
            var stagedSource: File? = null
            try {
                val staging = File(appContext.cacheDir, "nearer-hypertransfer").apply {
                    if (!exists() && !mkdirs()) throw IOException("Could not create transfer staging directory")
                }
                val source = File(staging, "source-$transferId.bin")
                stagedSource = source
                val (size, hash) = copyAndHash(uri, source)
                val offer = placeholder.copy(sizeBytes = size, sha256 = hash)
                val prepared = initial.copy(offer = offer, source = source)
                if (!outgoing.replace(payloadId, initial, prepared)) {
                    source.delete()
                    return@execute
                }
                if (!sendOffer(endpointId, offer)) {
                    outgoing.remove(payloadId, prepared)
                    source.delete()
                    listener.onStatus("The secure HyperTransfer offer could not be sent. No file bytes were transmitted.")
                } else {
                    listener.onStatus("Waiting for the recipient to approve " + safeName + "…")
                }
            } catch (error: Exception) {
                outgoing.remove(payloadId)
                stagedSource?.delete()
                listener.onStatus(
                    "Could not prepare the secure HyperTransfer file: " +
                        (error.localizedMessage ?: "please retry")
                )
            }
        }
        return payloadId
    }

    /** Called only after the service has recorded the authenticated offer in its ledger. */
    fun receiveOffer(endpointId: String, peerId: String, offer: HyperTransferTcpOfferCodec.Offer) {
        if (closed || NeaRerIdentity.normalize(peerId) != peerId || incoming.containsKey(offer.transferId)) return
        val payloadId = offer.transferId.take(15).toLong(16)
        val state = TransferState(endpointId, peerId, offer, payloadId, incoming = true)
        if (incoming.putIfAbsent(offer.transferId, state) == null) {
            listener.onIncomingOffer(endpointId, peerId, offer)
        }
    }

    /** The UI decision is persisted by the service before this manager starts any socket. */
    fun decideIncoming(endpointId: String, peerId: String, transferId: String, accepted: Boolean): Boolean {
        val state = incoming[transferId] ?: return false
        if (state.endpointId != endpointId || state.peerId != peerId || !state.incoming) return false
        if (!respondToOffer(endpointId, transferId, accepted)) return false
        if (!accepted) {
            incoming.remove(transferId, state)
            listener.onStatus("The incoming HyperTransfer was declined. No file bytes were received.")
            return true
        }
        state.approved = true
        startWhenReady(state)
        return true
    }

    /** Called only after the service ledger has accepted the exact remote decision. */
    fun remoteDecision(endpointId: String, peerId: String, decision: HyperTransferTcpOfferCodec.Decision) {
        val state = outgoing.values.firstOrNull { it.offer.transferId == decision.transferId } ?: return
        if (state.endpointId != endpointId || state.peerId != peerId || state.incoming) return
        if (!decision.accepted) {
            if (outgoing.remove(state.payloadId, state)) state.source?.delete()
            listener.onStatus("The recipient declined " + state.offer.name + ".")
            return
        }
        state.approved = true
        startWhenReady(state)
    }

    fun onLinkReady() {
        outgoing.values.filter { it.approved }.forEach(::startWhenReady)
        incoming.values.filter { it.approved }.forEach(::startWhenReady)
    }

    fun clearEndpoint(endpointId: String) {
        outgoing.values.filter { it.endpointId == endpointId }.forEach { cancel(it.payloadId) }
        incoming.values.filter { it.endpointId == endpointId }.forEach { cancel(it.payloadId) }
    }

    fun cancel(payloadId: Long) {
        val state = outgoing.remove(payloadId) ?: incoming.values.firstOrNull { it.payloadId == payloadId }?.let {
            incoming.remove(it.offer.transferId, it)
            it
        } ?: return
        state.cancellation?.cancel()
        if (activePayloadId == payloadId) {
            runCatching { activeServer?.close() }
            runCatching { activeSocket?.close() }
        }
        state.source?.delete()
        listener.onStatus("HyperTransfer cancelled.")
    }

    private fun startWhenReady(state: TransferState) {
        if (closed || !state.approved || currentLink() == null || coordinator() == null) return
        // The Wi-Fi Direct group exposes one authenticated bulk socket at a time.
        if (!activeTransfer.compareAndSet(false, true)) return
        if (!state.started.compareAndSet(false, true)) {
            activeTransfer.set(false)
            return
        }
        activePayloadId = state.payloadId
        workers.execute {
            try { runTransfer(state) }
            finally {
                activePayloadId = null
                activeTransfer.set(false)
                onLinkReady()
            }
        }
    }

    private fun runTransfer(state: TransferState) {
        var socket: Socket? = null
        val cancellation = WifiDirectTcpTransferEngine.Cancellation()
        state.cancellation = cancellation
        try {
            val direction = if (state.incoming) HyperTransferApprovalLedger.Direction.INCOMING
                else HyperTransferApprovalLedger.Direction.OUTGOING
            check(consumeApproval(state.endpointId, state.peerId, state.offer, direction)) {
                "The one-shot file approval is missing, expired, or already used"
            }
            val link = currentLink() ?: throw IOException("Wi-Fi Direct link is no longer ready")
            val p2p = coordinator() ?: throw IOException("Wi-Fi Direct coordinator is unavailable")
            // A deterministic per-transfer port avoids rebinding the same listening
            // port immediately after TCP TIME_WAIT on consecutive large-file sends.
            val port = portFor(state.offer.transferId)
            val connectedSocket = if (link.isGroupOwner) {
                val server = p2p.openServerSocket(port)
                activeServer = server
                server.soTimeout = 60_000
                try { server.accept() } finally {
                    activeServer = null
                    runCatching { server.close() }
                }
            } else {
                p2p.openClientSocket(link, port, 15_000)
            }
            socket = connectedSocket
            activeSocket = connectedSocket
            val session = HyperTransferSocketHandshake.authenticate(
                appContext, connectedSocket, state.offer.transferId, state.peerId,
                localIsSender = !state.incoming,
                transferApproved = state.approved,
                firstContactApproved = false
            )
            val meter = TransferSpeedMeter()
            val progress = WifiDirectTcpTransferEngine.ProgressListener { update ->
                listener.onProgress(
                    state.endpointId, state.payloadId, state.offer.name,
                    update.transferredBytes, update.totalBytes, state.incoming,
                    meter.record(update.transferredBytes, update.totalBytes)
                )
            }

            if (state.incoming) {
                val folder = File(appContext.filesDir, "received").apply {
                    if (!exists() && !mkdirs()) throw IOException("Could not create received files directory")
                }
                val destination = File(
                    folder,
                    System.currentTimeMillis().toString() + "-" +
                        UUID.randomUUID().toString().take(8) + "-" +
                        TransferFileNamePolicy.safeName(state.offer.name)
                )
                val result = engine.receiveSecure(
                    connectedSocket, destination, state.offer.transferId, session.channel,
                    cancellation, progress, expectedSha256 = state.offer.sha256
                )
                listener.onProgress(
                    state.endpointId, state.payloadId, state.offer.name, result.bytesReceived,
                    result.bytesReceived, true, meter.record(result.bytesReceived, result.bytesReceived)
                )
                listener.onReceived(state.endpointId, state.offer.name, result.file)
                listener.onStatus("Verified and saved: " + state.offer.name)
            } else {
                val source = state.source ?: throw IOException("Prepared source file is unavailable")
                val sentHash = engine.sendSecure(
                    connectedSocket, source, state.offer.transferId, session.channel, cancellation, progress
                )
                if (!sentHash.equals(state.offer.sha256, ignoreCase = true)) {
                    throw SecurityException("The source file changed after the approved offer was created")
                }
                listener.onProgress(
                    state.endpointId, state.payloadId, state.offer.name, source.length(),
                    source.length(), false, meter.record(source.length(), source.length())
                )
                listener.onStatus("Verified and saved on the recipient's phone: " + state.offer.name)
            }
        } catch (error: Exception) {
            listener.onStatus(
                if (error is SecurityException) "Secure HyperTransfer authentication failed. The file was not sent."
                else "HyperTransfer stopped safely: " + (error.localizedMessage ?: "connection interrupted")
            )
        } finally {
            activeSocket = null
            state.cancellation = null
            runCatching { socket?.close() }
            if (state.incoming) incoming.remove(state.offer.transferId, state)
            else {
                outgoing.remove(state.payloadId, state)
                state.source?.delete()
            }
        }
    }

    private fun copyAndHash(uri: Uri, destination: File): Pair<Long, String> {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected file could not be opened")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(transferBufferBytes)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    total += count
                }
                output.fd.sync()
            }
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        if (closed) return
        closed = true
        outgoing.values.forEach { it.cancellation?.cancel(); it.source?.delete() }
        incoming.values.forEach { it.cancellation?.cancel() }
        outgoing.clear()
        incoming.clear()
        runCatching { activeServer?.close() }
        runCatching { activeSocket?.close() }
        workers.shutdownNow()
    }

    private fun portFor(transferId: String): Int =
        10_000 + (transferId.take(4).toInt(16) % 10_000)
}
