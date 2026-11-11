package com.nearer.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class NearbyConnectionsTransport(context: Context, ultraSpeedMode: Boolean = false, private val listener: Listener) {
    interface Listener {
        fun onStatus(message: String)
        fun onFound(id: String, name: String, stableId: String)
        fun onLost(id: String, stableId: String)
        fun onRequest(id: String, name: String, token: String)
        fun onConnected(id: String, name: String, stableId: String)
        fun onDisconnected(id: String, stableId: String, name: String)
        fun onMessage(id: String, senderName: String, messageId: String, body: String, acknowledge: () -> Unit)
        fun onMessageStatus(messageId: String, status: String)
        fun onFileProgress(id: String, payloadId: Long, name: String, transferred: Long, total: Long, incoming: Boolean)
        /** Optional live application-throughput callback; default keeps existing listeners source-compatible. */
        fun onFileSpeed(id: String, payloadId: Long, name: String, snapshot: TransferSpeedSnapshot, incoming: Boolean) {}
        fun onFileReceived(id: String, name: String, file: File)
        fun onFileStatus(message: String)
    }

    private val appContext = context.applicationContext
    private val client = Nearby.getConnectionsClient(appContext)
    // Both peers must choose the same mode before discovery. The distinct service ID
    // prevents automatic-mode peers from accidentally matching Ultra Speed peers.
    private val connectionStrategy = if (ultraSpeedMode) Strategy.P2P_POINT_TO_POINT else Strategy.P2P_CLUSTER
    private val serviceId = context.packageName + if (ultraSpeedMode) ".ultraspeed" else ""
    private val names = mutableMapOf<String, String>()
    private val stableIds = mutableMapOf<String, String>()
    private val activeEndpoints = mutableSetOf<String>()
    private val authenticatedEndpoints = ConcurrentHashMap.newKeySet<String>()
    private data class PeerHandshakeState(
        val localNonce: ByteArray,
        val localPublicKey: ByteArray,
        val localEphemeralKeyPair: java.security.KeyPair,
        var remoteNonce: ByteArray? = null,
        var remotePublicKey: ByteArray? = null,
        var remoteEphemeralPublicKey: ByteArray? = null,
        var remotePeerId: String = "",
        var localProofSent: Boolean = false
    )
    private val peerHandshakes = ConcurrentHashMap<String, PeerHandshakeState>()
    private val messageSessions = ConcurrentHashMap<String, NearbyMessageCrypto.Session>()
    private val localStableId = context.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        .let { prefs ->
            val stored = prefs.getString("nearer_stable_id", null)
            val identity = NeaRerIdentity.getOrCreate(stored)
            if (identity != stored) prefs.edit().putString("nearer_stable_id", identity).apply()
            identity
        }
    private fun encodedName(): String = "${localName.take(23)}|$localStableId"
    private fun decodeName(value: String): Pair<String, String> {
        val split = value.lastIndexOf('|')
        if (split in 1 until value.lastIndex) {
            val identity = NeaRerIdentity.normalize(value.substring(split + 1))
            if (identity != null) return value.substring(0, split).take(23) to identity
        }
        return value.take(23) to ""
    }

internal object IncomingFilePayloadPolicy {
    /**
     * A file payload must come from the peer that supplied its metadata (or the
     * peer that first supplied the payload). Stable identities survive reconnects;
     * legacy peers are restricted to the original endpoint.
     */
    fun canAccept(
        expectedEndpointId: String?,
        expectedPeerId: String?,
        incomingEndpointId: String,
        incomingPeerId: String
    ): Boolean {
        if (incomingEndpointId.isBlank()) return false
        // Once metadata establishes a stable sender, an empty identity is not
        // a legacy fallback: it is an unidentified sender and must be rejected.
        if (!expectedPeerId.isNullOrBlank()) {
            return incomingPeerId.isNotBlank() && expectedPeerId == incomingPeerId
        }
        return expectedEndpointId == null || expectedEndpointId == incomingEndpointId
    }
}

    private data class FileMeta(val endpointId: String, val name: String, val size: Long, val sha256: String = "")
    private data class OutgoingFile(
        @Volatile var endpointId: String,
        val stablePeerId: String,
        val name: String,
        val size: Long,
        val payload: Payload,
        val temporarySourceFile: java.io.File? = null,
        @Volatile var sha256: String = "",
        @Volatile var metadataReady: Boolean = false,
        @Volatile var awaitingResume: Boolean = false
    )
    private val incomingMetadata = ConcurrentHashMap<Long, FileMeta>()
    private val incomingPeerIds = ConcurrentHashMap<Long, String>()
    private val incomingPayloadEndpoints = ConcurrentHashMap<Long, String>()
    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()
    private val completedPayloads = ConcurrentHashMap<Long, Payload>()
    private val outgoingFiles = ConcurrentHashMap<Long, OutgoingFile>()
    private val transferSpeedMeters = ConcurrentHashMap<Long, TransferSpeedMeter>()
    /** Snapshot access for diagnostics and future speed UI. */
    fun transferSpeedSnapshot(payloadId: Long): TransferSpeedSnapshot? = transferSpeedMeters[payloadId]?.snapshot()
    private val fileIo = Executors.newSingleThreadExecutor()
    // Hash large files away from the UI thread before the receiver is given the payload.
    private val transferPreparation = Executors.newFixedThreadPool(2)
    private val seenMessageIdsLock = Any()
    private val seenMessageIds = LinkedHashSet<String>().apply {
        val preferences = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val ordered = preferences.getString("nearby_seen_message_ids_ordered", null)
        if (ordered != null) {
            try {
                val savedIds = org.json.JSONArray(ordered)
                for (index in 0 until savedIds.length()) {
                    val id = savedIds.optString(index)
                    if (id.isNotBlank()) add(id)
                }
            } catch (_: Exception) {
                preferences.getStringSet("nearby_seen_message_ids", HashSet<String>()).orEmpty().forEach { add(it) }
            }
        } else {
            preferences.getStringSet("nearby_seen_message_ids", HashSet<String>()).orEmpty().forEach { add(it) }
        }
        MessageIdHistoryPolicy.trimOldest(this)
    }
    // Prevent two copies arriving at once while the first copy is still being saved.
    // An ID becomes "seen" only after the UI confirms local persistence.
    private val pendingIncomingMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val outgoingMessageTargets = ConcurrentHashMap<String, String>()
    private data class PendingOutgoingMessage(@field:Volatile var endpointId: String, val stablePeerId: String, val payload: ByteArray, var attempts: Int = 0, var retryTask: Runnable? = null)
    private val pendingOutgoingMessages = ConcurrentHashMap<String, PendingOutgoingMessage>()
    private val deliveryHandler = Handler(Looper.getMainLooper())
    private val maxMessageAttempts = MessageRetryPolicy.MAX_ATTEMPTS
    init { restorePendingOutbox() }
    private fun persistPendingOutbox(): Boolean {
        return try {
            val array = org.json.JSONArray()
            pendingOutgoingMessages.forEach { (id, p) ->
                val encryptedPayload = LocalDataCipher.encrypt(appContext, p.payload)
                array.put(JSONObject().put("id", id).put("peer", p.stablePeerId).put("payload", encryptedPayload))
            }
            val saved = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit().putString("nearby_outbox", array.toString()).commit()
            if (!saved) listener.onStatus("NeaRer could not save its pending-message queue.")
            saved
        } catch (_: Exception) {
            listener.onStatus("NeaRer could not save its pending-message queue.")
            false
        }
    }
    private fun restorePendingOutbox() {
        try {
            val raw = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .getString("nearby_outbox", "[]") ?: "[]"
            val array = org.json.JSONArray(raw)
            var skipped = false
            var migratedLegacyPayload = false
            for (i in 0 until array.length()) {
                try {
                    val item = array.optJSONObject(i)
                    if (item == null) {
                        skipped = true
                        continue
                    }
                    val id = item.optString("id")
                    val peer = item.optString("peer")
                    val storedPayload = item.optString("payload")
                    if (id.isBlank() || id.length > 80 || peer.isBlank() || peer.length > 64 ||
                        storedPayload.isBlank() || storedPayload.length > 24000) {
                        skipped = true
                        continue
                    }
                    val payloadBytes = if (storedPayload.startsWith("v1:")) {
                        LocalDataCipher.decrypt(appContext, storedPayload)
                    } else {
                        // One-time migration from the older plaintext outbox format.
                        migratedLegacyPayload = true
                        storedPayload.toByteArray(Charsets.UTF_8)
                    }
                    if (payloadBytes == null || payloadBytes.isEmpty() || payloadBytes.size > 12000) {
                        skipped = true
                        continue
                    }
                    val payload = payloadBytes.toString(Charsets.UTF_8)
                    val envelope = JSONObject(payload)
                    if (envelope.optString("kind") != "message" ||
                        envelope.optString("id") != id ||
                        envelope.optString("body").isBlank() ||
                        envelope.optString("body").length > 4000) {
                        skipped = true
                        continue
                    }
                    pendingOutgoingMessages[id] = PendingOutgoingMessage("", peer, payloadBytes)
                } catch (_: Exception) {
                    skipped = true
                }
            }
            if (migratedLegacyPayload && !persistPendingOutbox()) {
                listener.onStatus("NeaRer could not encrypt the older saved message queue. It remains on this phone unchanged.")
            }
            if (skipped) listener.onStatus("Some damaged or locked queued messages were skipped. Other saved messages are still available.")
        } catch (_: Exception) {
            listener.onStatus("The saved pending-message queue could not be read.")
        }
    }
    fun pendingMessageIds(): Set<String> = pendingOutgoingMessages.keys.toSet()

    private fun retryQueuedMessagesForPeer(peerId: String, endpointId: String) {
        if (peerId.isBlank()) return
        pendingOutgoingMessages.forEach { (id, p) -> if (p.stablePeerId == peerId) { p.retryTask?.let(deliveryHandler::removeCallbacks); p.endpointId = endpointId; p.attempts = 0; outgoingMessageTargets[id] = endpointId; persistPendingOutbox(); attemptOutgoingMessage(id) } }
    }

    private fun attemptOutgoingMessage(messageId: String) {
        val pending = pendingOutgoingMessages[messageId] ?: return
        if (pending.attempts >= maxMessageAttempts) { pending.endpointId = ""; pending.attempts = 0; outgoingMessageTargets.remove(messageId); persistPendingOutbox(); listener.onMessageStatus(messageId, "Queued"); return }
        // Count unavailable-link attempts too, so a disconnected peer cannot cause
        // an endless retry loop.
        if (pending.endpointId.isBlank()) return
        pending.attempts += 1
        if (!authenticatedEndpoints.contains(pending.endpointId) || synchronized(activeEndpoints) { !activeEndpoints.contains(pending.endpointId) }) {
            scheduleMessageRetry(messageId, MessageRetryPolicy.delayMillis(pending.attempts, 1800L))
            return
        }
        val session = messageSessions[pending.endpointId]
        if (session == null) {
            scheduleMessageRetry(messageId, MessageRetryPolicy.delayMillis(pending.attempts, 1800L))
            return
        }
        val securePayload = try {
            val clear = JSONObject(pending.payload.toString(Charsets.UTF_8))
            NearbyMessageCrypto.encrypt(session, localStableId, pending.stablePeerId, messageId, pending.payload)
        } catch (_: Exception) {
            listener.onStatus("NeaRer could not encrypt a queued message for this connection.")
            scheduleMessageRetry(messageId, MessageRetryPolicy.delayMillis(pending.attempts, 1800L))
            return
        }
        val timeout = Runnable { scheduleMessageRetry(messageId, MessageRetryPolicy.delayMillis(pending.attempts, 1800L)) }
        pending.retryTask?.let(deliveryHandler::removeCallbacks)
        pending.retryTask = timeout
        deliveryHandler.postDelayed(timeout, 5000L)
        try {
            client.sendPayload(pending.endpointId, Payload.fromBytes(securePayload))
                .addOnFailureListener { scheduleMessageRetry(messageId, MessageRetryPolicy.delayMillis(pending.attempts, 1200L)) }
        } catch (_: Exception) {
            scheduleMessageRetry(messageId, MessageRetryPolicy.delayMillis(pending.attempts, 1200L))
        }
    }

    private fun scheduleMessageRetry(messageId: String, delayMs: Long) {
        val pending = pendingOutgoingMessages[messageId] ?: return
        pending.retryTask?.let(deliveryHandler::removeCallbacks)
        if (pending.attempts >= maxMessageAttempts) {
            pending.endpointId = ""
            pending.attempts = 0
            outgoingMessageTargets.remove(messageId)
            persistPendingOutbox()
            listener.onMessageStatus(messageId, "Queued")
            return
        }
        val retry = Runnable { attemptOutgoingMessage(messageId) }
        pending.retryTask = retry
        deliveryHandler.postDelayed(retry, delayMs)
    }

    private fun clearPendingOutgoingMessage(messageId: String) {
        pendingOutgoingMessages.remove(messageId)?.retryTask?.let(deliveryHandler::removeCallbacks)
        outgoingMessageTargets.remove(messageId)
        persistPendingOutbox()
    }

    private fun hasSeenMessageId(messageId: String): Boolean =
        synchronized(seenMessageIdsLock) { messageId in seenMessageIds }

    /**
     * A delivery receipt is allowed only after this ID is durably committed.
     * Restore the previous in-memory history if SharedPreferences cannot commit.
     */
    private fun rememberMessageId(messageId: String): Boolean = synchronized(seenMessageIdsLock) {
        val previous = LinkedHashSet(seenMessageIds)
        seenMessageIds.add(messageId)
        MessageIdHistoryPolicy.trimOldest(seenMessageIds)
        try {
            val ordered = org.json.JSONArray()
            seenMessageIds.forEach { ordered.put(it) }
            val saved = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .edit()
                .putString("nearby_seen_message_ids_ordered", ordered.toString())
                .putStringSet("nearby_seen_message_ids", HashSet(seenMessageIds))
                .commit()
            if (!saved) {
                seenMessageIds.clear()
                seenMessageIds.addAll(previous)
                listener.onStatus("NeaRer saved the message but could not secure its duplicate-protection record. Delivery was not confirmed.")
            }
            saved
        } catch (_: Exception) {
            seenMessageIds.clear()
            seenMessageIds.addAll(previous)
            listener.onStatus("NeaRer saved the message but could not secure its duplicate-protection record. Delivery was not confirmed.")
            false
        }
    }
    private var started = false
    @Volatile private var localName = "NeaRer User"

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            val (name, stableId) = decodeName(info.endpointName)
            synchronized(names) { names[id] = name; stableIds[id] = stableId }
            listener.onFound(id, name, stableId)
        }
        override fun onEndpointLost(id: String) {
            if (synchronized(activeEndpoints) { activeEndpoints.contains(id) }) return
            val stableId = synchronized(names) { stableIds.remove(id).orEmpty().also { names.remove(id) } }
            listener.onLost(id, stableId)
        }
    }
    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            val (name, stableId) = decodeName(info.endpointName)
            synchronized(names) { names[id] = name; stableIds[id] = stableId }
            listener.onRequest(id, name, info.authenticationToken)
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                synchronized(activeEndpoints) { activeEndpoints.add(id) }
                val details = synchronized(names) { (names[id] ?: "Nearby NeaRer user") to stableIds[id].orEmpty() }
                beginPeerHandshake(id)
                listener.onStatus("Nearby link established. Verifying this phone’s NeaRer identity…")
            } else listener.onStatus("Nearby connection was not approved. Try again.")
        }
        override fun onDisconnected(id: String) {
            val wasAuthenticated = authenticatedEndpoints.remove(id)
            peerHandshakes.remove(id)
            messageSessions.remove(id)
            val empty = synchronized(activeEndpoints) { activeEndpoints.remove(id); activeEndpoints.isEmpty() }
            pauseOutgoingFilesForEndpoint(id)
            val details = synchronized(names) { (stableIds.remove(id).orEmpty()) to (names.remove(id) ?: "Nearby NeaRer user") }
            if (wasAuthenticated && empty) listener.onDisconnected(id, details.first, details.second)
            else if (wasAuthenticated) {
                listener.onLost(id, details.first)
            } else {
                listener.onLost(id, details.first)
                listener.onStatus("One nearby person disconnected. Your other direct connections remain active.")
            }
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleBytes(id, payload.asBytes())
                Payload.Type.FILE -> {
                    if (!authenticatedEndpoints.contains(id)) {
                        try { payload.close() } catch (_: Exception) { }
                        listener.onFileStatus("An unverified nearby phone tried to send a file. The payload was rejected.")
                        return
                    }
                    val incomingPeerId = synchronized(names) { stableIds[id].orEmpty() }
                    val existingPeerId = incomingPeerIds[payload.id]
                    val meta = incomingMetadata[payload.id]
                    val expectedEndpoint = meta?.endpointId ?: incomingPayloadEndpoints[payload.id]
                    if (!IncomingFilePayloadPolicy.canAccept(
                            expectedEndpoint,
                            existingPeerId,
                            id,
                            incomingPeerId
                        )) {
                        try { payload.close() } catch (_: Exception) { }
                        listener.onFileStatus("A different nearby phone tried to supply data for this transfer. The file payload was rejected.")
                        return
                    }
                    incomingPayloads[payload.id] = payload
                    incomingPayloadEndpoints.putIfAbsent(payload.id, id)
                    if (existingPeerId == null || existingPeerId.isBlank()) {
                        incomingPeerIds[payload.id] = incomingPeerId
                    }
                    val incomingName = meta?.name ?: "Incoming file"
                    val incomingSize = meta?.size ?: -1L
                    // onPayloadReceived is the completed-file callback; report completion,
                    // while live speed is emitted by the transfer-update callbacks.
                    val completedBytes = incomingSize.coerceAtLeast(0L)
                    listener.onFileProgress(id, payload.id, incomingName, completedBytes, incomingSize, true)
                    processCompletedFile(payload.id)
                }
                else -> listener.onFileStatus("NeaRer received an unsupported nearby transfer type.")
            }
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val outgoing = outgoingFiles[payloadId]
            val incoming = incomingMetadata[payloadId]
            val name = outgoing?.name ?: incoming?.name ?: "Incoming file"
            val isIncoming = outgoing == null
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) {
                val speed = transferSpeedMeters.computeIfAbsent(payloadId) { TransferSpeedMeter() }
                    .record(update.bytesTransferred, update.totalBytes)
                listener.onFileProgress(id, payloadId, name, update.bytesTransferred, update.totalBytes, isIncoming)
                listener.onFileSpeed(id, payloadId, name, speed, isIncoming)
                return
            }
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val finalSpeed = transferSpeedMeters.computeIfAbsent(payloadId) { TransferSpeedMeter() }
                    .record(update.totalBytes, update.totalBytes)
                if (!isIncoming) listener.onFileProgress(id, payloadId, name, update.totalBytes, update.totalBytes, false)
                listener.onFileSpeed(id, payloadId, name, finalSpeed, isIncoming)
                transferSpeedMeters.remove(payloadId)
                if (isIncoming) {
                    incomingPayloads[payloadId]?.let { completedPayloads[payloadId] = it }
                    processCompletedFile(payloadId)
                } else {
                    outgoing?.let { file ->
                        if (outgoingFiles.remove(payloadId, file)) {
                            try { file.payload.close() } catch (_: Exception) { }
                            file.temporarySourceFile?.delete()
                        }
                    }
                    listener.onFileStatus("File sent to the nearby phone: $name")
                }
            } else {
                if (outgoing != null) {
                    // Keep the same Payload instance and its SDK-maintained offset.
                    outgoing.awaitingResume = true
                    outgoing.endpointId = ""
                    listener.onFileStatus("Nearby outgoing file transfer paused. Reconnect to the same phone to resume.")
                } else {
                    // Keep the incoming payload and metadata: its SDK offset is needed
                    // by the sender when the same peer reconnects.
                    listener.onFileStatus("Nearby incoming file transfer paused. Waiting to resume with the same phone.")
                }
            }
        }
    }

    private fun beginPeerHandshake(endpointId: String) {
        val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (NeaRerIdentity.normalize(peerId) == null) {
            failPeerHandshake(endpointId, "This nearby phone did not provide a valid NeaRer identity.")
            return
        }
        try {
            val publicKey = SecurePeerIdentity.publicKeyEncoded()
            val ephemeral = NearbyMessageCrypto.newEphemeralKeyPair()
            val state = PeerHandshakeState(NearbyPeerHandshake.newNonce(), publicKey, ephemeral)
            peerHandshakes[endpointId] = state
            client.sendPayload(endpointId, Payload.fromBytes(NearbyPeerHandshake.hello(
                localStableId, publicKey, state.localNonce, ephemeral.public.encoded
            )))
                .addOnFailureListener { failPeerHandshake(endpointId, "Could not start secure peer verification.") }
        } catch (_: Exception) {
            failPeerHandshake(endpointId, "This phone could not create its secure NeaRer identity.")
        }
    }

    private fun handlePeerHello(endpointId: String, envelope: JSONObject) {
        val state = peerHandshakes[endpointId] ?: return
        try {
            val hello = NearbyPeerHandshake.decodeHello(envelope)
            val advertisedId = synchronized(names) { stableIds[endpointId].orEmpty() }
            require(hello.peerId == advertisedId && hello.peerId != localStableId)
            require(state.remotePublicKey == null || java.security.MessageDigest.isEqual(state.remotePublicKey, hello.publicKey))
            require(state.remoteNonce == null || java.security.MessageDigest.isEqual(state.remoteNonce, hello.nonce))
            require(state.remoteEphemeralPublicKey == null || java.security.MessageDigest.isEqual(state.remoteEphemeralPublicKey, hello.ephemeralPublicKey))
            state.remotePeerId = hello.peerId
            state.remotePublicKey = hello.publicKey
            state.remoteNonce = hello.nonce
            state.remoteEphemeralPublicKey = hello.ephemeralPublicKey
            if (!state.localProofSent) {
                val challenge = NearbyPeerHandshake.transcriptChallenge(
                    localStableId, state.localNonce, state.localEphemeralKeyPair.public.encoded,
                    hello.peerId, hello.nonce, hello.ephemeralPublicKey
                )
                val signature = SecurePeerIdentity.signChallenge(localStableId, state.localPublicKey, challenge)
                state.localProofSent = true
                client.sendPayload(endpointId, Payload.fromBytes(NearbyPeerHandshake.proof(localStableId, signature)))
                    .addOnFailureListener { failPeerHandshake(endpointId, "Could not send this phone’s secure identity proof.") }
            }
        } catch (error: Exception) {
            failPeerHandshake(endpointId, error.localizedMessage ?: "The nearby identity challenge was invalid.")
        }
    }

    private fun handlePeerProof(endpointId: String, envelope: JSONObject) {
        val state = peerHandshakes[endpointId] ?: return
        try {
            val (peerId, signature) = NearbyPeerHandshake.decodeProof(envelope)
            require(state.localProofSent) { "Local proof has not been sent." }
            val remoteKey = state.remotePublicKey ?: throw IllegalArgumentException("Peer key has not been received.")
            val remoteNonce = state.remoteNonce ?: throw IllegalArgumentException("Peer challenge has not been received.")
            val remoteEphemeral = state.remoteEphemeralPublicKey ?: throw IllegalArgumentException("Peer encryption key has not been received.")
            require(peerId == state.remotePeerId)
            val challenge = NearbyPeerHandshake.transcriptChallenge(
                localStableId, state.localNonce, state.localEphemeralKeyPair.public.encoded,
                peerId, remoteNonce, remoteEphemeral
            )
            require(SecurePeerIdentity.verifyChallenge(peerId, remoteKey, challenge, signature))
            when (TrustedPeerKeyStore.checkAndPin(appContext, peerId, remoteKey)) {
                TrustedPeerKeyStore.Result.FIRST_SEEN, TrustedPeerKeyStore.Result.MATCH -> {
                    val remoteEcdhKey = NearbyMessageCrypto.decodeEphemeralPublicKey(remoteEphemeral)
                    val session = NearbyMessageCrypto.deriveSession(
                        localStableId, peerId, state.localEphemeralKeyPair.private, remoteEcdhKey, challenge
                    )
                    messageSessions[endpointId] = session
                    if (authenticatedEndpoints.add(endpointId)) {
                        peerHandshakes.remove(endpointId)
                        val details = synchronized(names) {
                            (names[endpointId] ?: "Nearby NeaRer user") to stableIds[endpointId].orEmpty()
                        }
                        retryQueuedMessagesForPeer(details.second, endpointId)
                        listener.onConnected(endpointId, details.first, details.second)
                        listener.onStatus("Secure NeaRer identity verified for ${details.first}.")
                    }
                }
                TrustedPeerKeyStore.Result.KEY_CHANGED ->
                    throw SecurityException("This phone’s saved NeaRer identity key has changed. Verify the person before reconnecting.")
                TrustedPeerKeyStore.Result.STORAGE_ERROR ->
                    throw IllegalStateException("NeaRer could not safely save this phone’s identity key.")
            }
        } catch (error: Exception) {
            failPeerHandshake(endpointId, error.localizedMessage ?: "Secure peer verification failed.")
        }
    }

    private fun failPeerHandshake(endpointId: String, reason: String) {
        peerHandshakes.remove(endpointId)
        messageSessions.remove(endpointId)
        authenticatedEndpoints.remove(endpointId)
        listener.onStatus(reason)
        try { client.disconnectFromEndpoint(endpointId) } catch (_: Exception) { }
    }

    private fun handleBytes(endpointId: String, bytes: ByteArray?) {
        try {
            val envelope = JSONObject(bytes?.toString(Charsets.UTF_8).orEmpty())
            when (envelope.optString("kind")) {
                "peer_hello_v2" -> { handlePeerHello(endpointId, envelope); return }
                "peer_proof_v2" -> { handlePeerProof(endpointId, envelope); return }
            }
            if (!authenticatedEndpoints.contains(endpointId)) return
            if (envelope.optString("kind") == "secure_packet_v1") {
                val session = messageSessions[endpointId] ?: return
                val senderId = synchronized(names) { stableIds[endpointId].orEmpty() }
                val (packetId, cleartext) = NearbyMessageCrypto.decrypt(session, localStableId, senderId, envelope)
                val inner = JSONObject(cleartext.toString(Charsets.UTF_8))
                require(inner.optString("id") == packetId) { "Encrypted message ID did not match its authenticated envelope." }
                handleAuthenticatedMessage(endpointId, inner)
                return
            }
            // Chat and delivery receipts are never accepted as plaintext after v2 pairing.
            if (envelope.optString("kind") == "message" || envelope.optString("kind") == "ack") return
            if (envelope.optString("kind") == "file_meta") {
                val payloadId = envelope.optLong("payloadId", -1L)
                val name = safeFileName(envelope.optString("name", "shared-file"))
                val size = envelope.optLong("size", -1L)
                val sha256 = envelope.optString("sha256", "").lowercase()
                if (payloadId < 0L || size < 0L || (sha256.isNotEmpty() && !sha256.matches(Regex("[a-f0-9]{64}")))) {
                    listener.onFileStatus("A nearby phone sent invalid file information. The transfer was ignored.")
                    return
                }
                val incomingPeerId = synchronized(names) { stableIds[endpointId].orEmpty() }
                val previousPeerId = incomingPeerIds[payloadId].orEmpty()
                if (previousPeerId.isNotBlank() && incomingPeerId.isNotBlank() && previousPeerId != incomingPeerId) {
                    listener.onFileStatus("A different nearby phone tried to replace this file's transfer details. The update was rejected.")
                    return
                }
                val existing = incomingMetadata[payloadId]
                val existingIdentity = existing?.let {
                    IncomingFileMetadataIdentity(
                        endpointId = it.endpointId,
                        peerId = previousPeerId,
                        name = it.name,
                        size = it.size,
                        sha256 = it.sha256
                    )
                }
                val proposedIdentity = IncomingFileMetadataIdentity(
                    endpointId = endpointId,
                    peerId = incomingPeerId,
                    name = name,
                    size = size,
                    sha256 = sha256
                )
                if (!IncomingFileMetadataPolicy.canAccept(existingIdentity, proposedIdentity)) {
                    listener.onFileStatus("Conflicting file details were received for this transfer. The update was rejected.")
                    return
                }
                val acceptedHash = sha256.ifBlank { existing?.sha256.orEmpty() }
                // A repeated metadata packet for the same payload is the resume handshake.
                // Start a fresh speed interval at the last known byte offset, excluding
                // time spent disconnected, while retaining absolute transfer progress.
                val resumeOffset = if (existing != null) {
                    transferSpeedMeters[payloadId]?.snapshot()?.transferredBytes ?: 0L
                } else 0L
                val meter = transferSpeedMeters.computeIfAbsent(payloadId) { TransferSpeedMeter() }
                if (existing != null) meter.reset()
                val speed = meter.record(resumeOffset, size)
                incomingMetadata[payloadId] = FileMeta(endpointId, name, size, acceptedHash)
                if (incomingPeerId.isNotBlank() || previousPeerId.isBlank()) {
                    incomingPeerIds[payloadId] = incomingPeerId
                }
                listener.onFileProgress(endpointId, payloadId, name, resumeOffset, size, true)
                listener.onFileSpeed(endpointId, payloadId, name, speed, true)
                processCompletedFile(payloadId)
                return
            }
            if (envelope.optString("kind") == "file_resume_request") {
                val payloadId = envelope.optLong("payloadId", -1L)
                val offset = envelope.optLong("offset", -1L)
                val outgoing = outgoingFiles[payloadId] ?: return
                val requesterStableId = synchronized(names) { stableIds[endpointId].orEmpty() }
                if (!outgoing.awaitingResume || requesterStableId.isBlank() ||
                    outgoing.stablePeerId != requesterStableId || offset < 0L || offset > outgoing.size) return
                outgoing.endpointId = endpointId
                outgoing.awaitingResume = false
                val resumedMeter = transferSpeedMeters.computeIfAbsent(payloadId) { TransferSpeedMeter() }
                resumedMeter.reset()
                val resumedSpeed = resumedMeter.record(offset, outgoing.size)
                listener.onFileProgress(endpointId, payloadId, outgoing.name, offset, outgoing.size, false)
                listener.onFileSpeed(endpointId, payloadId, outgoing.name, resumedSpeed, false)
                sendPreparedFile(payloadId, outgoing, offset)
                return
            }
            if (envelope.optString("kind") == "file_resume_request") {
                // File-control packets remain link-authenticated but are not yet end-to-end encrypted.
            }
            handleAuthenticatedMessage(endpointId, envelope)
        } catch (_: Exception) {
            listener.onStatus("A nearby message could not be read and was ignored.")
        }
    }

    private fun handleAuthenticatedMessage(endpointId: String, envelope: JSONObject) {
        val kind = envelope.optString("kind", "message")
        if (kind == "ack") {
            val acknowledgedId = envelope.optString("id")
            if (acknowledgedId.isNotBlank() && outgoingMessageTargets[acknowledgedId] == endpointId) {
                clearPendingOutgoingMessage(acknowledgedId)
                listener.onMessageStatus(acknowledgedId, "Delivered")
            }
            return
        }
        if (kind != "message") return
        val messageId = envelope.optString("id")
        val body = envelope.optString("body")
        val ttl = envelope.optInt("ttl", 0)
        if (messageId.isBlank() || body.isBlank() || body.length > 4000) return
        if (messageId.length > 80 || ttl !in 0..4) return
        if (hasSeenMessageId(messageId)) {
            sendAcknowledgement(endpointId, messageId)
            return
        }
        if (!pendingIncomingMessageIds.add(messageId)) return
        val senderName = synchronized(names) { names[endpointId] ?: "Nearby member" }
        try {
            listener.onMessage(endpointId, senderName, messageId, body) {
                if (messageId in pendingIncomingMessageIds) {
                    val dedupeRecordSaved = rememberMessageId(messageId)
                    pendingIncomingMessageIds.remove(messageId)
                    if (dedupeRecordSaved) sendAcknowledgement(endpointId, messageId)
                }
            }
        } catch (error: Exception) {
            pendingIncomingMessageIds.remove(messageId)
            throw error
        }
        // Direct/private chat only. No relay forwarding is enabled.
    }

    private fun processCompletedFile(payloadId: Long) {
        val payload = completedPayloads[payloadId] ?: return
        val meta = incomingMetadata[payloadId] ?: return
        if (!completedPayloads.remove(payloadId, payload)) return
        incomingPayloads.remove(payloadId)
        incomingMetadata.remove(payloadId)
        incomingPeerIds.remove(payloadId)
        incomingPayloadEndpoints.remove(payloadId)
        fileIo.execute {
            var destination: File? = null
            try {
                val folder = File(appContext.filesDir, "received").apply { mkdirs() }
                val safeName = TransferFileNamePolicy.safeName(meta.name)
                val target = File(folder, "${System.currentTimeMillis()}-${safeName}")
                destination = target
                val receivedFile = payload.asFile() ?: throw IllegalStateException("Nearby file content is unavailable")
                val sourceFile = receivedFile.asJavaFile()
                // Nearby has already written the completed payload to disk. Move it into
                // NeaRer's private received folder when possible, avoiding a second full
                // read/write pass for multi-gigabyte videos.
                val moved = sourceFile != null && sourceFile.exists() && sourceFile.renameTo(target)
                var copiedSha256 = ""
                var copiedBytes = -1L
                var copiedSourceUri: Uri? = null
                if (!moved) {
                    val uri: Uri = receivedFile.asUri() ?: throw IllegalStateException("Nearby file content is unavailable")
                    copiedSourceUri = uri
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { output ->
                            val copyResult = TransferStreamCopy.copyAndSha256(input, output)
                            copiedBytes = copyResult.bytesCopied
                            copiedSha256 = copyResult.sha256
                            output.fd.sync()
                        }
                    } ?: throw IllegalStateException("Could not open the received file")
                }
                if (!target.isFile || target.length() != meta.size ||
                    (copiedBytes >= 0L && copiedBytes != target.length())) {
                    throw IllegalStateException("Received file size did not match the expected and copied byte counts")
                }
                // The fallback copy already calculated the digest. Only the rename path
                // needs a separate read to verify the completed file.
                val actualHash = copiedSha256.ifBlank {
                    target.inputStream().use { input -> sha256Hex(input) }
                }
                if (meta.sha256.isNotBlank() && !actualHash.equals(meta.sha256, ignoreCase = true)) {
                    throw IllegalStateException("File integrity check failed. The received file was not kept.")
                }
                listener.onFileReceived(meta.endpointId, meta.name, target)
                // Keep the provider's original payload until the staged copy has passed
                // size/hash checks and the app has accepted the saved file.
                copiedSourceUri?.let { appContext.contentResolver.delete(it, null, null) }
            } catch (error: Exception) {
                destination?.delete()
                listener.onFileStatus("Could not save the received file: ${error.localizedMessage ?: "transfer error"}")
            } finally {
                transferSpeedMeters.remove(payloadId)
                try { payload.close() } catch (_: Exception) { }
            }
        }
    }

    private fun sha256Hex(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        val hex = "0123456789abcdef"
        return buildString(64) {
            digest.digest().forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }

    private fun safeFileName(raw: String): String = TransferFileNamePolicy.safeName(raw)

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(connectionStrategy).build()
        client.startAdvertising(encodedName(), serviceId, lifecycle, options).addOnFailureListener {
            // Do not leave the transport marked as started when advertising failed.
            // Stop the other half of discovery too, so the next user retry starts cleanly.
            stopSearching()
            listener.onStatus("Nearby advertising could not start: ${it.localizedMessage ?: "check Bluetooth permissions"}. You can try again.")
        }
    }

    fun localIdentityId(): String = localStableId

    fun stableIdentityForEndpoint(endpointId: String): String =
        synchronized(names) { stableIds[endpointId].orEmpty() }

    fun displayNameForEndpoint(endpointId: String): String =
        synchronized(names) { names[endpointId].orEmpty() }

    fun setDisplayName(name: String) {
        val clean = name.trim().replace("|", " ").take(23).ifBlank { "NeaRer User" }
        if (clean == localName) return
        localName = clean
        if (started) { client.stopAdvertising(); startAdvertising() }
    }

    fun start() {
        if (started) return
        started = true
        val discovering = DiscoveryOptions.Builder().setStrategy(connectionStrategy).build()
        startAdvertising()
        client.startDiscovery(serviceId, discovery, discovering).addOnFailureListener {
            // Advertising may already have succeeded; roll both operations back together.
            // This resets started so a later tap can retry instead of silently doing nothing.
            stopSearching()
            listener.onStatus("Nearby discovery could not start: ${it.localizedMessage ?: "check Bluetooth permissions"}. You can try again.")
        }
        listener.onStatus("Searching over Bluetooth and nearby Wi-Fi…")
    }

    fun request(id: String) {
        client.requestConnection(encodedName(), id, lifecycle).addOnFailureListener {
            listener.onStatus("Connection request failed: ${it.localizedMessage ?: "try again"}")
        }
    }
    fun accept(id: String) { client.acceptConnection(id, payloads).addOnFailureListener { listener.onStatus("Could not accept nearby connection: ${it.localizedMessage ?: "try again"}") } }
    fun reject(id: String) { client.rejectConnection(id).addOnFailureListener { listener.onStatus("Could not reject nearby connection: ${it.localizedMessage ?: "try again"}") } }

    fun send(
        body: String,
        messageId: String = UUID.randomUUID().toString(),
        targetEndpointId: String? = null
    ): Boolean {
        if (body.isBlank() || body.length > 4000) return false
        val envelope = JSONObject().put("kind", "message").put("version", 1)
            .put("id", messageId).put("body", body)
            .put("ttl", 0).put("hops", 0).put("origin", localName)
            .put("createdAt", System.currentTimeMillis()).toString()
        if (targetEndpointId != null) {
            if (!authenticatedEndpoints.contains(targetEndpointId) || synchronized(activeEndpoints) { !activeEndpoints.contains(targetEndpointId) }) return false
            return try {
                val payload = envelope.toByteArray(Charsets.UTF_8)
                val stablePeerId = synchronized(names) { stableIds[targetEndpointId].orEmpty() }
                if (stablePeerId.isBlank()) return false
                outgoingMessageTargets[messageId] = targetEndpointId
                pendingOutgoingMessages[messageId] = PendingOutgoingMessage(targetEndpointId, stablePeerId, payload)
                if (!persistPendingOutbox()) {
                    pendingOutgoingMessages.remove(messageId)
                    outgoingMessageTargets.remove(messageId)
                    return false
                }
                attemptOutgoingMessage(messageId)
                true
            } catch (_: Exception) {
                clearPendingOutgoingMessage(messageId)
                false
            }
        }
        val targets = synchronized(activeEndpoints) { activeEndpoints.filter { authenticatedEndpoints.contains(it) } }
        if (targets.isEmpty()) return false
        val clear = envelope.toByteArray(Charsets.UTF_8)
        var sent = false
        targets.forEach { endpoint ->
            val peerId = synchronized(names) { stableIds[endpoint].orEmpty() }
            val session = messageSessions[endpoint] ?: return@forEach
            try {
                val packet = NearbyMessageCrypto.encrypt(session, localStableId, peerId, messageId, clear)
                client.sendPayload(endpoint, Payload.fromBytes(packet))
                    .addOnFailureListener { listener.onStatus("A nearby group message could not be sent.") }
                sent = true
            } catch (_: Exception) { }
        }
        sent
    }

    private fun sendAcknowledgement(endpointId: String, messageId: String) {
        try {
            val session = messageSessions[endpointId] ?: return
            val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
            val receipt = JSONObject().put("kind", "ack").put("version", 1).put("id", messageId)
            val packet = NearbyMessageCrypto.encrypt(
                session, localStableId, peerId, messageId, receipt.toString().toByteArray(Charsets.UTF_8)
            )
            client.sendPayload(endpointId, Payload.fromBytes(packet))
        } catch (_: Exception) {
            // The message itself is already safely stored; the sender will retain a pending state.
        }
    }

    fun sendToNames(body: String, targetNames: Set<String>): Boolean {
        if (body.isBlank() || targetNames.isEmpty()) return false
        val targets = synchronized(activeEndpoints) {
            activeEndpoints.filter { endpoint ->
                authenticatedEndpoints.contains(endpoint) && synchronized(names) { names[endpoint]?.let { it in targetNames } == true }
            }
        }
        if (targets.isEmpty()) return false
        // Use the exact versioned envelope understood by handleBytes(). Sending the
        // community packet as raw JSON here caused receivers to reject it as malformed.
        val envelope = JSONObject()
            .put("kind", "message")
            .put("version", 1)
            .put("id", UUID.randomUUID().toString())
            .put("body", body)
            .put("ttl", 0)
            .put("hops", 0)
            .put("origin", localName)
            .put("createdAt", System.currentTimeMillis())
            .toString()
            .toByteArray(Charsets.UTF_8)
        targets.forEach { endpoint ->
            val peerId = synchronized(names) { stableIds[endpoint].orEmpty() }
            val session = messageSessions[endpoint] ?: return@forEach
            try {
                val packet = NearbyMessageCrypto.encrypt(session, localStableId, peerId, messageId, envelope)
                client.sendPayload(endpoint, Payload.fromBytes(packet))
                    .addOnFailureListener { listener.onStatus("A group message could not be sent to one nearby member.") }
            } catch (_: Exception) {
                listener.onStatus("A group message could not be encrypted for one nearby member.")
            }
        }
        return true
    }

    fun sendFile(endpointId: String, uri: Uri, displayName: String): Long? {
        if (!authenticatedEndpoints.contains(endpointId) || synchronized(activeEndpoints) { !activeEndpoints.contains(endpointId) }) {
            listener.onFileStatus("The nearby connection is not securely verified or is no longer active.")
            return null
        }
        var descriptor: android.os.ParcelFileDescriptor? = null
        var filePayload: Payload? = null
        var stagedSource: java.io.File? = null
        var stagedSha256 = ""
        try {
            val openedDescriptor = runCatching {
                appContext.contentResolver.openFileDescriptor(uri, "r")
            }.getOrNull()
            descriptor = openedDescriptor
            val descriptorSize = openedDescriptor?.statSize ?: -1L
            val size: Long
            val payload: Payload
            if (openedDescriptor != null && descriptorSize >= 0L) {
                size = descriptorSize
                payload = Payload.fromFile(openedDescriptor)
                filePayload = payload
                descriptor = null
            } else {
                // Providers may expose a stream without a seekable, size-bearing descriptor.
                // Stage those sources to disk rather than rejecting them or buffering in RAM.
                try { descriptor?.close() } catch (_: Exception) { }
                descriptor = null
                val stagingDir = java.io.File(appContext.cacheDir, "nearer-transfer-staging").apply { mkdirs() }
                stagedSource = java.io.File.createTempFile("nearer-source-", ".bin", stagingDir)
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("The selected file could not be opened as a stream")
                val copyResult = input.use { source ->
                    stagedSource!!.outputStream().buffered().use { output ->
                        TransferStreamCopy.copyAndSha256(source, output)
                    }
                }
                val stagedLength = stagedSource!!.length()
                if (copyResult.bytesCopied != stagedLength) {
                    throw IllegalStateException("Staged file length did not match the number of bytes copied")
                }
                stagedSha256 = copyResult.sha256
                size = copyResult.bytesCopied
                payload = Payload.fromFile(stagedSource!!)
                filePayload = payload
            }
            val name = safeFileName(displayName)
            payload.setFileName(name)
            payload.setSensitive(true)
            val id = payload.id
            val peerStableId = synchronized(names) { stableIds[endpointId].orEmpty() }
            val outgoing = OutgoingFile(endpointId, peerStableId, name, size, payload, stagedSource)
            // Provider-backed sources were already hashed during the staging copy.
            outgoing.sha256 = stagedSha256
            outgoingFiles[id] = outgoing
            val initialSpeed = transferSpeedMeters.computeIfAbsent(id) { TransferSpeedMeter() }
                .record(0L, size)
            listener.onFileProgress(endpointId, id, name, 0L, size, false)
            listener.onFileSpeed(endpointId, id, name, initialSpeed, false)
            listener.onFileStatus("Preparing and verifying $name before sending…")
            transferPreparation.execute {
                try {
                    val sha256 = outgoing.sha256.ifBlank {
                        if (outgoing.temporarySourceFile != null) {
                            outgoing.temporarySourceFile.inputStream().use { sha256Hex(it) }
                        } else {
                            appContext.contentResolver.openInputStream(uri)?.use { sha256Hex(it) }
                                ?: throw IllegalStateException("The selected file can no longer be read")
                        }
                    }
                    outgoing.sha256 = sha256
                    if (!synchronized(activeEndpoints) { activeEndpoints.contains(endpointId) }) {
                        throw IllegalStateException("Nearby connection ended before transfer started")
                    }
                    sendPreparedFile(id, outgoing)
                } catch (error: Exception) {
                    outgoingFiles.remove(id)
                    transferSpeedMeters.remove(id)
                    try { client.cancelPayload(id) } catch (_: Exception) { }
                    try { payload.close() } catch (_: Exception) { }
                    outgoing.temporarySourceFile?.delete()
                    listener.onFileStatus("File could not be verified before sending: " + (error.localizedMessage ?: "please try again"))
                }
            }
            return id
        } catch (error: Exception) {
            try { filePayload?.close() } catch (_: Exception) { }
            try { descriptor?.close() } catch (_: Exception) { }
            stagedSource?.delete()
            listener.onFileStatus("File could not be prepared: " + (error.localizedMessage ?: "choose another file"))
            return null
        }
    }

    private fun sendPreparedFile(payloadId: Long, outgoing: OutgoingFile, resumeOffset: Long = 0L) {
        val endpointId = outgoing.endpointId
        if (endpointId.isBlank() || !authenticatedEndpoints.contains(endpointId) || outgoing.sha256.isBlank()) return
        val metadata = JSONObject().put("kind", "file_meta").put("payloadId", payloadId)
            .put("name", outgoing.name).put("size", outgoing.size).put("sha256", outgoing.sha256)
        client.sendPayload(endpointId, Payload.fromBytes(metadata.toString().toByteArray(Charsets.UTF_8)))
            .addOnSuccessListener {
                outgoing.metadataReady = true
                try {
                    if (resumeOffset < 0L || resumeOffset > outgoing.size) throw IllegalArgumentException("Invalid resume offset")
                    outgoing.payload.setOffset(resumeOffset)
                    client.sendPayload(endpointId, outgoing.payload)
                        .addOnSuccessListener { outgoing.awaitingResume = false }
                        .addOnFailureListener {
                            outgoing.awaitingResume = true
                            listener.onFileStatus("Nearby file transfer paused. Reconnect to the same phone to resume.")
                        }
                } catch (_: Exception) {
                    outgoing.awaitingResume = true
                    listener.onFileStatus("Nearby file transfer paused. Reconnect to the same phone to resume.")
                }
            }
            .addOnFailureListener { error ->
                outgoing.awaitingResume = true
                listener.onFileStatus("Nearby file transfer paused. Reconnect to the same phone to resume. ${error.localizedMessage ?: ""}".trim())
            }
    }

    private fun pauseOutgoingFilesForEndpoint(endpointId: String) {
        outgoingFiles.values.filter { it.endpointId == endpointId && it.metadataReady }.forEach {
            it.awaitingResume = true
            it.endpointId = ""
        }
    }

    fun requestPendingIncomingFilesForPeer(peerStableId: String, endpointId: String): Boolean {
        if (peerStableId.isBlank() || !authenticatedEndpoints.contains(endpointId) || !synchronized(activeEndpoints) { activeEndpoints.contains(endpointId) }) return false
        var requested = false
        incomingMetadata.entries.toList().forEach { (payloadId, meta) ->
            if (incomingPeerIds[payloadId] != peerStableId) return@forEach
            val receivedPayload = incomingPayloads[payloadId]
            val offset = try { receivedPayload?.offset ?: 0L } catch (_: Exception) { 0L }
            if (offset < 0L || offset > meta.size) return@forEach
            incomingMetadata[payloadId] = meta.copy(endpointId = endpointId)
            requestIncomingFileResume(payloadId, peerStableId, endpointId, offset)
            requested = true
        }
        return requested
    }

    /**
     * Retry a transient failure while asking the original sender for the SDK's
     * saved byte offset. Re-check peer identity and connection before every retry
     * so a delayed callback can never send a resume request to a different phone.
     */
    private fun requestIncomingFileResume(
        payloadId: Long,
        peerStableId: String,
        endpointId: String,
        offset: Long,
        attempt: Int = 0
    ) {
        val meta = incomingMetadata[payloadId] ?: return
        if (incomingPeerIds[payloadId] != peerStableId ||
            !authenticatedEndpoints.contains(endpointId) || meta.endpointId != endpointId ||
            !synchronized(activeEndpoints) { activeEndpoints.contains(endpointId) }) return

        val request = JSONObject().put("kind", "file_resume_request")
            .put("payloadId", payloadId).put("offset", offset)
        val retryOrReport = {
            if (attempt < 2) {
                deliveryHandler.postDelayed(
                    { requestIncomingFileResume(payloadId, peerStableId, endpointId, offset, attempt + 1) },
                    1200L * (attempt + 1)
                )
            } else {
                listener.onFileStatus("Could not request the saved transfer offset after several attempts. Reconnect to try again.")
            }
        }
        try {
            client.sendPayload(endpointId, Payload.fromBytes(request.toString().toByteArray(Charsets.UTF_8)))
                .addOnFailureListener { retryOrReport() }
        } catch (_: Exception) {
            retryOrReport()
        }
    }

    fun cancelFile(payloadId: Long) {
        try { client.cancelPayload(payloadId) } catch (_: Exception) { }
        outgoingFiles.remove(payloadId)?.let {
            try { it.payload.close() } catch (_: Exception) { }
            it.temporarySourceFile?.delete()
        }
        transferSpeedMeters.remove(payloadId)
    }

    private fun broadcast(content: String, excludeEndpoint: String?): Boolean {
        val targets = synchronized(activeEndpoints) { activeEndpoints.filter { it != excludeEndpoint && authenticatedEndpoints.contains(it) } }
        if (targets.isEmpty()) return false
        var sent = false
        targets.forEach { endpoint ->
            try {
                client.sendPayload(endpoint, Payload.fromBytes(content.toByteArray(Charsets.UTF_8)))
                sent = true
            } catch (_: Exception) { }
        }
        return sent
    }

    fun stopSearching() {
        started = false
        try { client.stopAdvertising() } catch (_: Exception) { }
        try { client.stopDiscovery() } catch (_: Exception) { }
    }

    fun disconnect() {
        try { client.stopAllEndpoints() } catch (_: Exception) { }
        synchronized(activeEndpoints) { activeEndpoints.clear() }
        authenticatedEndpoints.clear()
        peerHandshakes.clear()
        messageSessions.clear()
    }
    fun stop() {
        stopSearching()
        disconnect()
        pendingOutgoingMessages.values.forEach { it.retryTask?.let(deliveryHandler::removeCallbacks); it.retryTask = null }
        deliveryHandler.removeCallbacksAndMessages(null)
        outgoingFiles.values.forEach {
            try { it.payload.close() } catch (_: Exception) { }
            it.temporarySourceFile?.delete()
        }
        outgoingFiles.clear()
        fileIo.shutdownNow()
        transferPreparation.shutdownNow()
    }

}