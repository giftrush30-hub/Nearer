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
import java.nio.ByteBuffer

data class MeshRecipient(
    val peerId: String,
    val displayName: String,
    /** Number of intermediate relays between this phone and the recipient. */
    val hopCount: Int,
    val isDirect: Boolean
)

class NearbyConnectionsTransport(context: Context, ultraSpeedMode: Boolean = false, private val listener: Listener) {
    interface Listener {
        fun onStatus(message: String)
        fun onFound(id: String, name: String, stableId: String)
        fun onLost(id: String, stableId: String)
        fun onRequest(id: String, name: String, token: String)
        fun onConnected(id: String, name: String, stableId: String)
        fun onDisconnected(id: String, stableId: String, name: String)
        fun onMessage(id: String, senderName: String, messageId: String, body: String, acknowledge: () -> Unit)
        /** Message whose sender is the original end-to-end identity, not the immediate relay. */
        fun onMeshMessage(originPeerId: String, senderName: String, messageId: String, body: String, acknowledge: () -> Unit) {
            onMessage(originPeerId, senderName, messageId, body, acknowledge)
        }
        fun onMessageStatus(messageId: String, status: String)
        /** Available routes only to recipients whose signing and encryption keys are pinned. */
        fun onMeshRoutesChanged(recipients: List<MeshRecipient>) {}
        fun onFileProgress(id: String, payloadId: Long, name: String, transferred: Long, total: Long, incoming: Boolean)
        /** Optional live application-throughput callback; default keeps existing listeners source-compatible. */
        fun onFileSpeed(id: String, payloadId: Long, name: String, snapshot: TransferSpeedSnapshot, incoming: Boolean) {}
        /**
         * Delivers progress and measured speed together so UI listeners can update
         * once per SDK callback instead of posting two separate main-thread tasks.
         * Existing listeners retain their old behavior through this default bridge.
         */
        fun onFileTransferUpdate(
            id: String,
            payloadId: Long,
            name: String,
            transferred: Long,
            total: Long,
            incoming: Boolean,
            snapshot: TransferSpeedSnapshot
        ) {
            onFileProgress(id, payloadId, name, transferred, total, incoming)
            onFileSpeed(id, payloadId, name, snapshot, incoming)
        }
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
        val localRecipientEncryptionPublicKey: ByteArray,
        var remoteNonce: ByteArray? = null,
        var remotePublicKey: ByteArray? = null,
        var remoteEphemeralPublicKey: ByteArray? = null,
        var remoteRecipientEncryptionPublicKey: ByteArray? = null,
        var remotePeerId: String = "",
        var localProofSent: Boolean = false,
        var localProofSendStarted: Boolean = false,
        var pendingRemoteProof: String? = null,
        var remoteProofVerified: Boolean = false,
        var remoteBindingVerified: Boolean = false,
        var localBindingSent: Boolean = false,
        var localBindingSendStarted: Boolean = false,
        var pendingRemoteBinding: String? = null,
        var remoteFirstSeen: Boolean = false
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
    private val meshRouteTable = MeshRouteTable(localStableId)
    private val meshDestinationEncryptionKeys = ConcurrentHashMap<String, ByteArray>()
    private val routeAdvertisementWindows = ConcurrentHashMap<String, Pair<Long, Int>>()
    private val deadDropStore = DeadDropStore(appContext)
    private val meshRoutePersistence = MeshRoutePersistence(appContext)
    private val meshRouteWithdrawalStore = MeshRouteWithdrawalStore(appContext)
    @Volatile private var meshRouteWithdrawalStoreHealthy = true
    @Volatile private var meshTransitEnabled = false
    private val meshReceiptOutbox = MeshReceiptOutbox(appContext)
    private val receiptSendInFlightUntil = ConcurrentHashMap<String, Long>()
    @Volatile private var receiptRetryScheduled = false
    @Volatile private var meshForwardRetryScheduled = false
    private val localRecipientIdentity by lazy { RecipientEncryptionIdentity.getOrCreate(appContext) }


    /**
     * Transit relay remains hard-disabled after proof-chain integration until
     * trust/key-rotation, recovery, security review and physical multi-phone gates pass.
     * Direct delivery and receipt handling for this phone's own messages remain
     * available. This transport-level guard is intentional defense in depth: a
     * future UI toggle or stale preference must not activate unverified transit.
     */
    fun setMeshTransitEnabled(enabled: Boolean) {
        if (enabled) {
            meshTransitEnabled = false
            listener.onStatus("Multi-hop relay is paused until signed forwarding proofs are verified and committed with each stored packet. Direct nearby delivery remains available.")
            return
        }
        val wasEnabled = meshTransitEnabled
        if (wasEnabled && !enabled) {
            // Withdraw every route this phone may have advertised before
            // stopping transit, so neighbors do not keep routing into a dead end.
            broadcastMeshRouteWithdrawals(meshRouteTable.snapshot(System.currentTimeMillis()).map { it.destinationId })
        }
        meshTransitEnabled = enabled
        if (enabled) {
            forwardDueMeshPackets()
            flushPendingMeshReceipts()
        }
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
    private data class OutgoingHyperStream(
        @Volatile var endpointId: String,
        val stablePeerId: String,
        val name: String,
        val sourceFile: File,
        val item: TransferBundleItem,
        val transferId: String,
        val sha256: String,
        val prepared: NearbyStreamPayloadBridge.PreparedOutgoingStream,
        @Volatile var awaitingDecision: Boolean = true,
        @Volatile var timeout: Runnable? = null
    )
    private val outgoingHyperStreams = ConcurrentHashMap<Long, OutgoingHyperStream>()
    private val incomingHyperMetadata = ConcurrentHashMap<Long, TransferResumeStreamMetadataCodec.Metadata>()
    private val pendingIncomingHyperPayloads = ConcurrentHashMap<Long, Payload>()
    private val incomingHyperEndpoints = ConcurrentHashMap<Long, String>()
    private val streamCapablePeers = ConcurrentHashMap.newKeySet<String>()
    private val cancelledStreamPayloads = ConcurrentHashMap.newKeySet<Long>()
    private val streamController = NearbyStreamExchangeController()
    private val transferSpeedMeters = ConcurrentHashMap<Long, TransferSpeedMeter>()
    /** Snapshot access for diagnostics and future speed UI. */
    fun transferSpeedSnapshot(payloadId: Long): TransferSpeedSnapshot? = transferSpeedMeters[payloadId]?.snapshot()
    // Completed receive files are finalized independently, but the two-worker cap
    // limits simultaneous full-file hashing/copy work on lower-end Android phones.
    private val fileIo = TransferIoPolicy.createReceiveExecutor()
    private val resumeDirectory = File(appContext.filesDir, "hypertransfer-resume")
    private val resumeStore = TransferResumeStore(
        File(resumeDirectory, "partials"),
        TransferResumeJournal(File(resumeDirectory, "journal"))
    )
    private val resumeCoordinator = TransferResumeCoordinator(resumeStore, TransferResumeStreamWriter(resumeStore))
    private val streamPayloadBridge = NearbyStreamPayloadBridge(resumeCoordinator, fileIo)
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
    // Bounded ACK history lets the UI reconcile receipt state if the process dies
    // after transport retirement but before the community UI has saved its receipt.
    private val completedOutgoingMessageIds = LinkedHashSet<String>()
    private val deliveryHandler = Handler(Looper.getMainLooper())
    private val maxMessageAttempts = MessageRetryPolicy.MAX_ATTEMPTS
    private companion object { const val RECEIPT_RETRY_MS = 15_000L }
    init {
        restorePendingOutbox()
        // Restore replay protection before any cached route can become usable.
        val restoredWithdrawals = meshRouteWithdrawalStore.restore(meshRouteTable)
        meshRouteWithdrawalStoreHealthy = restoredWithdrawals >= 0
        val restoredRoutes = if (meshRouteWithdrawalStoreHealthy) {
            meshRoutePersistence.restore(
                meshRouteTable,
                { peerId -> TrustedPeerKeyStore.pinnedSigningKey(appContext, peerId) },
                System.currentTimeMillis()
            )
        } else {
            0
        }
        if (meshRouteWithdrawalStoreHealthy) {
            meshRoutePersistence.destinationKeys().forEach { (peerId, key) ->
                meshDestinationEncryptionKeys[peerId] = key.copyOf()
            }
        } else {
            listener.onStatus("Mesh route history could not be verified. Multi-hop routing is paused on this phone until its protected route history is repaired.")
        }
        if (restoredRoutes > 0 || restoredWithdrawals > 0) {
            listener.onStatus("Restored $restoredRoutes verified route(s) and $restoredWithdrawals route-withdrawal protection record(s).")
        }
    }
    @Synchronized
    private fun persistPendingOutbox(): Boolean {
        return try {
            val array = org.json.JSONArray()
            pendingOutgoingMessages.forEach { (id, p) ->
                val encryptedPayload = LocalDataCipher.encrypt(appContext, p.payload)
                array.put(JSONObject().put("id", id).put("peer", p.stablePeerId).put("payload", encryptedPayload))
            }
            val completed = org.json.JSONArray()
            completedOutgoingMessageIds.forEach { completed.put(it) }
            val saved = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit()
                .putString("nearby_outbox", array.toString())
                .putString("nearby_completed_outgoing_ids", completed.toString())
                .commit()
            if (!saved) listener.onStatus("NeaRer could not save its pending-message queue.")
            saved
        } catch (_: Exception) {
            listener.onStatus("NeaRer could not save its pending-message queue.")
            false
        }
    }
    private fun restorePendingOutbox() {
        try {
            val preferences = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val completedRaw = preferences.getString("nearby_completed_outgoing_ids", "[]") ?: "[]"
            completedOutgoingMessageIds.clear()
            val completedArray = runCatching { org.json.JSONArray(completedRaw) }.getOrNull()
            if (completedArray == null) {
                listener.onStatus("Saved delivery receipt history could not be read; pending messages will still be restored.")
            } else {
                for (i in 0 until completedArray.length()) {
                    val id = completedArray.optString(i)
                    if (id.isNotBlank() && id.length <= 80) {
                        val next = OutgoingReceiptHistoryPolicy.append(completedOutgoingMessageIds.toList(), id)
                        completedOutgoingMessageIds.clear()
                        completedOutgoingMessageIds.addAll(next)
                    }
                }
            }
            val raw = preferences.getString("nearby_outbox", "[]") ?: "[]"
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

    @Synchronized
    fun completedMessageIds(): Set<String> = completedOutgoingMessageIds.toSet()

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

    @Synchronized
    private fun clearPendingOutgoingMessage(messageId: String, acknowledged: Boolean = false): Boolean {
        val removed = pendingOutgoingMessages.remove(messageId) ?: return false
        val previousCompleted = completedOutgoingMessageIds.toList()
        if (acknowledged) {
            completedOutgoingMessageIds.clear()
            completedOutgoingMessageIds.addAll(
                OutgoingReceiptHistoryPolicy.append(previousCompleted, messageId)
            )
        }
        removed.retryTask?.let(deliveryHandler::removeCallbacks)
        outgoingMessageTargets.remove(messageId)
        // Commit queue retirement and, for a verified ACK only, its bounded receipt
        // marker together. An exception/failure cleanup must never look delivered.
        if (persistPendingOutbox()) return true
        completedOutgoingMessageIds.clear()
        completedOutgoingMessageIds.addAll(previousCompleted)
        removed.endpointId = ""
        pendingOutgoingMessages[messageId] = removed
        return false
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
            val lostPeerId = synchronized(names) { stableIds[id].orEmpty() }
            // Persist every learned route withdrawal before evicting it in memory.
            // This closes the crash window where a stale encrypted route-cache entry
            // could otherwise be restored after the next launch.
            val now = System.currentTimeMillis()
            val disconnectTombstones = if (lostPeerId.isBlank()) emptyList() else {
                meshRouteTable.snapshot(now)
                    .filter { it.nextHopPeerId == lostPeerId && it.sequence != Long.MAX_VALUE }
                    .map { MeshRouteTable.WithdrawalTombstone(it.destinationId, lostPeerId, it.sequence) }
            }
            val withdrawalHistorySaved = disconnectTombstones.isEmpty() ||
                meshRouteWithdrawalStore.saveAll(disconnectTombstones)
            if (!withdrawalHistorySaved) {
                meshRouteWithdrawalStoreHealthy = false
                listener.onStatus("Mesh route history could not be saved. Multi-hop routing is paused on this phone.")
            }
            val withdrawnDestinations = meshRouteTable.removeNextHop(lostPeerId)
            if (lostPeerId.isNotBlank()) {
                val advertiserCacheRemoved = meshRoutePersistence.removeAdvertiser(lostPeerId)
                // If replay protection could not be committed, remove cached routes
                // from the lost advertiser as a second fail-closed safeguard.
                if (!withdrawalHistorySaved && !advertiserCacheRemoved) {
                    if (!meshRoutePersistence.clearAll()) {
                        listener.onStatus("Old mesh routes could not be cleared from storage. Multi-hop routing remains disabled.")
                    }
                }
                routeAdvertisementWindows.remove(lostPeerId)
            }
            val empty = synchronized(activeEndpoints) { activeEndpoints.remove(id); activeEndpoints.isEmpty() }
            pruneMeshDestinationKeys()
            broadcastMeshRouteWithdrawals(withdrawnDestinations)
            notifyMeshRoutesChanged()
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
                Payload.Type.STREAM -> {
                    if (!authenticatedEndpoints.contains(id)) {
                        runCatching { payload.close() }
                        listener.onFileStatus("An unverified nearby phone tried to send a stream. It was rejected.")
                        return
                    }
                    val peerId = synchronized(names) { stableIds[id].orEmpty() }
                    val result = streamController.recordStreamPayload(id, peerId, payload.id, true)
                    if (result != NearbyStreamExchangeController.InputResult.STORED &&
                        result != NearbyStreamExchangeController.InputResult.DUPLICATE) {
                        runCatching { payload.close() }
                        listener.onFileStatus("The incoming stream did not match an authenticated transfer.")
                        return
                    }
                    pendingIncomingHyperPayloads[payload.id] = payload
                    incomingHyperEndpoints[payload.id] = id
                    tryProcessHyperStream(payload.id)
                }
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
                listener.onFileTransferUpdate(
                    id, payloadId, name, update.bytesTransferred, update.totalBytes, isIncoming, speed
                )
                return
            }
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val finalSpeed = transferSpeedMeters.computeIfAbsent(payloadId) { TransferSpeedMeter() }
                    .record(update.totalBytes, update.totalBytes)
                listener.onFileTransferUpdate(
                    id, payloadId, name, update.totalBytes, update.totalBytes, isIncoming, finalSpeed
                )
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
            val recipientIdentity = RecipientEncryptionIdentity.getOrCreate(appContext)
            val ephemeral = NearbyMessageCrypto.newEphemeralKeyPair()
            val state = PeerHandshakeState(
                NearbyPeerHandshake.newNonce(), publicKey, ephemeral, recipientIdentity.publicKeyEncoded
            )
            peerHandshakes[endpointId] = state
            client.sendPayload(endpointId, Payload.fromBytes(NearbyPeerHandshake.hello(
                localStableId, publicKey, state.localNonce, ephemeral.public.encoded,
                state.localRecipientEncryptionPublicKey
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
            require(state.remoteRecipientEncryptionPublicKey == null || java.security.MessageDigest.isEqual(state.remoteRecipientEncryptionPublicKey, hello.recipientEncryptionPublicKey))
            state.remotePeerId = hello.peerId
            state.remotePublicKey = hello.publicKey
            state.remoteNonce = hello.nonce
            state.remoteEphemeralPublicKey = hello.ephemeralPublicKey
            state.remoteRecipientEncryptionPublicKey = hello.recipientEncryptionPublicKey
            if (!state.localProofSent && !state.localProofSendStarted) {
                val challenge = NearbyPeerHandshake.transcriptChallenge(
                    localStableId, state.localNonce, state.localEphemeralKeyPair.public.encoded, state.localRecipientEncryptionPublicKey,
                    hello.peerId, hello.nonce, hello.ephemeralPublicKey, hello.recipientEncryptionPublicKey
                )
                val signature = SecurePeerIdentity.signChallenge(localStableId, state.localPublicKey, challenge)
                state.localProofSendStarted = true
                client.sendPayload(endpointId, Payload.fromBytes(NearbyPeerHandshake.proof(localStableId, signature)))
                    .addOnSuccessListener {
                        state.localProofSent = true
                        state.pendingRemoteProof?.let { pending ->
                            state.pendingRemoteProof = null
                            handlePeerProof(endpointId, JSONObject(pending))
                        }
                    }
                    .addOnFailureListener { failPeerHandshake(endpointId, "Could not send this phone’s secure identity proof.") }
            }
            state.pendingRemoteProof?.let { pending ->
                state.pendingRemoteProof = null
                handlePeerProof(endpointId, JSONObject(pending))
            }
        } catch (error: Exception) {
            failPeerHandshake(endpointId, error.localizedMessage ?: "The nearby identity challenge was invalid.")
        }
    }

    private fun handlePeerProof(endpointId: String, envelope: JSONObject) {
        val state = peerHandshakes[endpointId] ?: return
        try {
            val (peerId, signature) = NearbyPeerHandshake.decodeProof(envelope)
            if (!state.localProofSent || state.remotePublicKey == null ||
                state.remoteNonce == null || state.remoteEphemeralPublicKey == null ||
                state.remoteRecipientEncryptionPublicKey == null) {
                state.pendingRemoteProof = envelope.toString()
                return
            }
            val remoteKey = state.remotePublicKey ?: throw IllegalArgumentException("Peer key has not been received.")
            val remoteNonce = state.remoteNonce ?: throw IllegalArgumentException("Peer challenge has not been received.")
            val remoteEphemeral = state.remoteEphemeralPublicKey ?: throw IllegalArgumentException("Peer encryption key has not been received.")
            val remoteRecipientKey = state.remoteRecipientEncryptionPublicKey ?: throw IllegalArgumentException("Peer recipient key has not been received.")
            require(peerId == state.remotePeerId)
            val challenge = NearbyPeerHandshake.transcriptChallenge(
                localStableId, state.localNonce, state.localEphemeralKeyPair.public.encoded, state.localRecipientEncryptionPublicKey,
                peerId, remoteNonce, remoteEphemeral, remoteRecipientKey
            )
            require(SecurePeerIdentity.verifyChallenge(peerId, remoteKey, challenge, signature))
            val trustResult = TrustedPeerKeyStore.checkPinned(appContext, peerId, remoteKey, remoteRecipientKey)
            when (trustResult) {
                TrustedPeerKeyStore.Result.FIRST_SEEN, TrustedPeerKeyStore.Result.MATCH -> {
                    state.remoteProofVerified = true
                    state.remoteFirstSeen = trustResult == TrustedPeerKeyStore.Result.FIRST_SEEN
                    sendLocalIdentityBinding(endpointId, state)
                    state.pendingRemoteBinding?.let { pending ->
                        state.pendingRemoteBinding = null
                        handlePeerIdentityBinding(endpointId, JSONObject(pending))
                    }
                    completePeerAuthentication(endpointId, state)
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

    private fun sendLocalIdentityBinding(endpointId: String, state: PeerHandshakeState) {
        if (state.localBindingSent || state.localBindingSendStarted) return
        val now = System.currentTimeMillis()
        val prefs = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val sequence = prefs.getLong("nearby_local_binding_sequence", 0L) + 1L
        if (sequence <= 0L || !prefs.edit().putLong("nearby_local_binding_sequence", sequence).commit()) {
            throw IllegalStateException("NeaRer could not safely advance its identity-binding sequence.")
        }
        val binding = RecipientIdentityBinding.create(
            localStableId,
            state.localPublicKey,
            state.localRecipientEncryptionPublicKey,
            sequence,
            now,
            now + 24L * 60L * 60L * 1000L
        ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
        val wire = JSONObject()
            .put("kind", "mesh_identity_binding_v1")
            .put("deviceId", binding.deviceId)
            .put("signingKeyFingerprint", android.util.Base64.encodeToString(binding.signingKeyFingerprint, android.util.Base64.NO_WRAP))
            .put("recipientEncryptionPublicKey", android.util.Base64.encodeToString(binding.recipientEncryptionPublicKey, android.util.Base64.NO_WRAP))
            .put("sequence", binding.sequence)
            .put("issuedAtMs", binding.issuedAtMs)
            .put("expiresAtMs", binding.expiresAtMs)
            .put("signature", android.util.Base64.encodeToString(binding.signature, android.util.Base64.NO_WRAP))
        state.localBindingSendStarted = true
        client.sendPayload(endpointId, Payload.fromBytes(wire.toString().toByteArray(Charsets.UTF_8)))
            .addOnSuccessListener {
                state.localBindingSent = true
                completePeerAuthentication(endpointId, state)
            }
            .addOnFailureListener { failPeerHandshake(endpointId, "Could not send this phone’s signed recipient identity binding.") }
    }

    private fun handlePeerIdentityBinding(endpointId: String, envelope: JSONObject) {
        val state = peerHandshakes[endpointId] ?: return
        if (!state.remoteProofVerified) {
            state.pendingRemoteBinding = envelope.toString()
            return
        }
        try {
            val remoteId = state.remotePeerId
            val remoteSigningKey = state.remotePublicKey ?: throw IllegalStateException("The peer signing key is unavailable.")
            val binding = RecipientIdentityBinding(
                deviceId = envelope.getString("deviceId"),
                signingKeyFingerprint = android.util.Base64.decode(envelope.getString("signingKeyFingerprint"), android.util.Base64.NO_WRAP),
                recipientEncryptionPublicKey = android.util.Base64.decode(envelope.getString("recipientEncryptionPublicKey"), android.util.Base64.NO_WRAP),
                sequence = envelope.getLong("sequence"),
                issuedAtMs = envelope.getLong("issuedAtMs"),
                expiresAtMs = envelope.getLong("expiresAtMs"),
                signature = android.util.Base64.decode(envelope.getString("signature"), android.util.Base64.NO_WRAP)
            )
            require(binding.deviceId == remoteId)
            require(state.remoteRecipientEncryptionPublicKey?.let {
                java.security.MessageDigest.isEqual(it, binding.recipientEncryptionPublicKey)
            } == true) { "Recipient key binding did not match the handshake key." }
            require(binding.verify(remoteSigningKey, System.currentTimeMillis())) {
                "The nearby phone’s recipient identity binding was invalid or expired."
            }
            require(TrustedPeerKeyStore.acceptBindingSequence(appContext, remoteId, binding.sequence)) {
                "A replayed or older recipient identity binding was rejected."
            }
            val pinResult = TrustedPeerKeyStore.checkAndPin(
                appContext, remoteId, remoteSigningKey, binding.recipientEncryptionPublicKey
            )
            require(pinResult == TrustedPeerKeyStore.Result.FIRST_SEEN ||
                pinResult == TrustedPeerKeyStore.Result.MATCH) {
                "The nearby phone's identity key changed or could not be safely pinned."
            }
            state.remoteBindingVerified = true
            completePeerAuthentication(endpointId, state)
        } catch (error: Exception) {
            failPeerHandshake(endpointId, error.localizedMessage ?: "Recipient identity binding verification failed.")
        }
    }

    private fun completePeerAuthentication(endpointId: String, state: PeerHandshakeState) {
        if (!state.remoteProofVerified || !state.remoteBindingVerified || !state.localBindingSent) return
        if (peerHandshakes[endpointId] !== state) return
        try {
            val peerId = state.remotePeerId
            val remoteEphemeral = state.remoteEphemeralPublicKey ?: throw IllegalStateException("Peer session key is unavailable.")
            val challenge = NearbyPeerHandshake.transcriptChallenge(
                localStableId, state.localNonce, state.localEphemeralKeyPair.public.encoded, state.localRecipientEncryptionPublicKey,
                peerId, state.remoteNonce ?: throw IllegalStateException("Peer nonce is unavailable."),
                remoteEphemeral, state.remoteRecipientEncryptionPublicKey ?: throw IllegalStateException("Peer recipient key is unavailable.")
            )
            val remoteEcdhKey = NearbyMessageCrypto.decodeEphemeralPublicKey(remoteEphemeral)
            val session = NearbyMessageCrypto.deriveSession(
                localStableId, peerId, state.localEphemeralKeyPair.private, remoteEcdhKey, challenge
            )
            messageSessions[endpointId] = session
            val destinationKey = state.remoteRecipientEncryptionPublicKey
                ?: throw IllegalStateException("Verified recipient encryption key is unavailable.")
            meshDestinationEncryptionKeys[peerId] = destinationKey.copyOf()
            require(meshRouteTable.addDirectPeer(peerId, destinationKey, System.currentTimeMillis())) {
                "Could not install the authenticated direct mesh route."
            }
            pruneMeshDestinationKeys()
            if (authenticatedEndpoints.add(endpointId)) {
                peerHandshakes.remove(endpointId, state)
                val details = synchronized(names) {
                    (names[endpointId] ?: "Nearby NeaRer user") to stableIds[endpointId].orEmpty()
                }
                retryQueuedMessagesForPeer(details.second, endpointId)
                listener.onConnected(endpointId, details.first, details.second)
                // Restore any paused incoming Nearby FILE payloads only after the
                // authenticated stable peer identity has been re-established.
                // This resumes SDK-retained offsets within the current process; the
                // separate durable stream journal handles process-death recovery.
                requestPendingIncomingFilesForPeer(details.second, endpointId)
                notifyMeshRoutesChanged()
                val trustNotice = if (state.remoteFirstSeen) {
                    "Encrypted link established with ${details.first}. Signing and recipient keys were verified and pinned; this does not prove the person’s real-world identity."
                } else {
                    "Encrypted link established with ${details.first} using its previously pinned device identity and fresh signed recipient binding."
                }
                listener.onStatus(trustNotice)
                // Advertise authenticated direct neighbors and learned routes only after
                // the recipient identity binding has been verified.
                val endpoints = synchronized(activeEndpoints) { activeEndpoints.filter { authenticatedEndpoints.contains(it) } }
                endpoints.forEach { publishKnownRoutesTo(it) }
                flushPendingMeshReceipts()
                deadDropStore.pendingForDestination(localStableId, System.currentTimeMillis()).forEach {
                    if (it.originId == peerId) deliverMeshPacket(it)
                }
                forwardDueMeshPackets()
            }
        } catch (error: Exception) {
            failPeerHandshake(endpointId, error.localizedMessage ?: "Secure peer authentication could not be completed.")
        }
    }

    private fun reserveMeshRouteSequences(count: Int): LongRange {
        require(count in 1..256)
        val prefs = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val current = prefs.getLong("nearby_mesh_route_sequence", 0L)
        val last = current + count.toLong()
        require(current >= 0L && last > current &&
            prefs.edit().putLong("nearby_mesh_route_sequence", last).commit()) {
            "NeaRer could not persist its route sequence."
        }
        return (current + 1L)..last
    }

    private fun nextMeshRouteSequence(): Long = reserveMeshRouteSequences(1).first

    private fun sendMeshRouteAdvertisement(
        endpointId: String,
        destinationId: String,
        destinationKey: ByteArray,
        hopCount: Int,
        path: List<String> = listOf(localStableId, destinationId)
    ) {
        if (!MeshRelayParticipationPolicy.mayAdvertiseRoutes(meshTransitEnabled) || !meshRouteWithdrawalStoreHealthy || !authenticatedEndpoints.contains(endpointId) || hopCount !in 0..3) return
        try {
            val now = System.currentTimeMillis()
            val advertisement = SignedRouteAdvertisement.create(
                advertiserId = localStableId,
                destinationId = destinationId,
                destinationPublicKey = destinationKey,
                sequence = nextMeshRouteSequence(),
                hopCount = hopCount,
                issuedAtMs = now,
                expiresAtMs = now + 10L * 60L * 1000L,
                path = path
            ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
            val wire = JSONObject()
                .put("kind", "mesh_route_advert_v1")
                .put("advertisement", android.util.Base64.encodeToString(advertisement.encode(), android.util.Base64.NO_WRAP))
                .put("destinationEncryptionPublicKey", android.util.Base64.encodeToString(destinationKey, android.util.Base64.NO_WRAP))
            client.sendPayload(endpointId, Payload.fromBytes(wire.toString().toByteArray(Charsets.UTF_8)))
        } catch (error: Exception) {
            listener.onStatus("A nearby mesh route could not be advertised safely.")
        }
    }

    private fun publishKnownRoutesTo(endpointId: String) {
        if (!MeshRelayParticipationPolicy.mayAdvertiseRoutes(meshTransitEnabled) || !meshRouteWithdrawalStoreHealthy || !authenticatedEndpoints.contains(endpointId)) return
        val targetPeerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (targetPeerId.isBlank()) return
        val sent = HashSet<String>()
        val direct = synchronized(activeEndpoints) {
            activeEndpoints.filter { it != endpointId && authenticatedEndpoints.contains(it) }
        }
        direct.forEach { otherEndpoint ->
            val peerId = synchronized(names) { stableIds[otherEndpoint].orEmpty() }
            val key = meshDestinationEncryptionKeys[peerId]
            if (peerId.isNotBlank() && peerId != targetPeerId && key != null && sent.add(peerId)) {
                sendMeshRouteAdvertisement(endpointId, peerId, key, 0)
            }
        }
        meshRouteTable.snapshot(System.currentTimeMillis()).forEach { route ->
            if (route.nextHopPeerId == targetPeerId || route.destinationId == targetPeerId || !sent.add(route.destinationId)) return@forEach
            val key = meshDestinationEncryptionKeys[route.destinationId] ?: return@forEach
            sendMeshRouteAdvertisement(endpointId, route.destinationId, key, route.hopCount, route.path)
        }
    }

    private fun sendMeshRouteWithdrawal(endpointId: String, destinationId: String, sequence: Long) {
        if (!authenticatedEndpoints.contains(endpointId)) return
        try {
            val withdrawal = MeshRouteWithdrawal.create(
                localStableId, destinationId, sequence, System.currentTimeMillis()
            ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
            val envelope = JSONObject()
                .put("kind", "mesh_route_withdraw_v1")
                .put("withdrawal", android.util.Base64.encodeToString(withdrawal.encode(), android.util.Base64.NO_WRAP))
            client.sendPayload(endpointId, Payload.fromBytes(envelope.toString().toByteArray(Charsets.UTF_8)))
        } catch (_: Exception) {
            listener.onStatus("A mesh route withdrawal could not be sent.")
        }
    }

    private fun broadcastMeshRouteWithdrawals(destinationIds: List<String>, exceptEndpointId: String? = null) {
        if (!MeshRelayParticipationPolicy.mayAdvertiseRoutes(meshTransitEnabled)) return
        val destinations = destinationIds.distinct().filter { NeaRerIdentity.normalize(it) == it }.take(256)
        if (destinations.isEmpty()) return
        val endpoints = synchronized(activeEndpoints) {
            activeEndpoints.filter { it != exceptEndpointId && authenticatedEndpoints.contains(it) }
        }
        if (endpoints.isEmpty()) return
        val sequences = reserveMeshRouteSequences(destinations.size).iterator()
        destinations.forEach { destinationId ->
            val sequence = sequences.next()
            endpoints.forEach { endpointId -> sendMeshRouteWithdrawal(endpointId, destinationId, sequence) }
        }
    }

    private fun pruneMeshDestinationKeys() {
        val reachable = meshRouteTable.snapshot(System.currentTimeMillis()).mapTo(HashSet()) { it.destinationId }
        meshDestinationEncryptionKeys.keys.removeIf { it !in reachable }
    }

    private fun handleMeshRouteWithdrawal(endpointId: String, envelope: JSONObject) {
        if (!meshRouteWithdrawalStoreHealthy || !authenticatedEndpoints.contains(endpointId)) return
        try {
            val senderId = synchronized(names) { stableIds[endpointId].orEmpty() }
            if (senderId.isBlank() || !allowRouteAdvertisement(senderId, System.currentTimeMillis())) return
            val senderKey = TrustedPeerKeyStore.pinnedSigningKey(appContext, senderId) ?: return
            val withdrawal = MeshRouteWithdrawal.decode(
                android.util.Base64.decode(envelope.getString("withdrawal"), android.util.Base64.NO_WRAP)
            )
            require(withdrawal.advertiserId == senderId)
            require(withdrawal.verify(senderKey, System.currentTimeMillis()))
            // Commit replay protection BEFORE changing either in-memory routes or
            // the route cache. If storage fails, the old route remains inert behind
            // the global health gate and cannot be resurrected as a fresh route.
            val tombstone = MeshRouteTable.WithdrawalTombstone(
                withdrawal.destinationId, senderId, withdrawal.sequence
            )
            if (!meshRouteWithdrawalStore.record(tombstone)) {
                val disabledMarkerSaved = meshRouteWithdrawalStore.disableRouting()
                meshRouteWithdrawalStoreHealthy = false
                // A full/corrupt tombstone ledger must not leave an old route
                // cache that could be restored after process death.
                val routeCacheCleared = meshRoutePersistence.clearAll()
                meshDestinationEncryptionKeys.clear()
                listener.onStatus(
                    if (disabledMarkerSaved || routeCacheCleared) {
                        "Mesh route history could not be saved. Routing is paused and stale route recovery is blocked."
                    } else {
                        "Mesh route history could not be saved. Routing is paused for this session, but storage could not confirm the safety marker."
                    }
                )
                return
            }
            val routeBeforeWithdrawal = meshRouteTable.find(
                withdrawal.destinationId, System.currentTimeMillis()
            )
            // Remember even when another neighbor is selected, so this advertiser's
            // stale route cannot return after the alternate route later disappears.
            if (!meshRouteTable.rememberWithdrawal(
                    withdrawal.destinationId, senderId, withdrawal.sequence
                )) {
                val disabledMarkerSaved = meshRouteWithdrawalStore.disableRouting()
                meshRouteWithdrawalStoreHealthy = false
                val routeCacheCleared = meshRoutePersistence.clearAll()
                meshDestinationEncryptionKeys.clear()
                listener.onStatus(
                    if (disabledMarkerSaved || routeCacheCleared) {
                        "Mesh route history could not be applied. Routing is paused and stale route recovery is blocked."
                    } else {
                        "Mesh route history could not be applied. Routing is paused for this session, but storage could not confirm the safety marker."
                    }
                )
                return
            }
            // Equal-sequence signed withdrawals also invalidate the selected route.
            val routeRemoved = routeBeforeWithdrawal?.nextHopPeerId == senderId &&
                routeBeforeWithdrawal.sequence <= withdrawal.sequence &&
                meshRouteTable.find(withdrawal.destinationId, System.currentTimeMillis()) == null
            if (routeRemoved) {
                meshRoutePersistence.removeDestination(withdrawal.destinationId)
                // Re-sign the withdrawal as this node so downstream peers can invalidate
                // routes whose authenticated next hop is this device.
                broadcastMeshRouteWithdrawals(listOf(withdrawal.destinationId), endpointId)
                listener.onStatus("A lost mesh route was withdrawn from the nearby network.")
                notifyMeshRoutesChanged()
                forwardDueMeshPackets()
                flushPendingMeshReceipts()
            }
        } catch (_: Exception) {
            listener.onStatus("An invalid mesh route withdrawal was rejected.")
        }
    }

    private fun allowRouteAdvertisement(senderId: String, nowMs: Long): Boolean {
        var allowed = false
        routeAdvertisementWindows.compute(senderId) { _, previous ->
            if (previous == null || nowMs - previous.first >= 60_000L) {
                allowed = true
                nowMs to 1
            } else if (previous.second < 30) {
                allowed = true
                previous.first to (previous.second + 1)
            } else previous
        }
        return allowed
    }

    private fun handleMeshRouteAdvertisement(endpointId: String, envelope: JSONObject) {
        if (!meshRouteWithdrawalStoreHealthy || !authenticatedEndpoints.contains(endpointId)) return
        try {
            val senderId = synchronized(names) { stableIds[endpointId].orEmpty() }
            val now = System.currentTimeMillis()
            if (!allowRouteAdvertisement(senderId, now)) return
            val senderKey = TrustedPeerKeyStore.pinnedSigningKey(appContext, senderId) ?: return
            val destinationKey = android.util.Base64.decode(
                envelope.getString("destinationEncryptionPublicKey"), android.util.Base64.NO_WRAP
            )
            val advertisement = SignedRouteAdvertisement.decode(
                android.util.Base64.decode(envelope.getString("advertisement"), android.util.Base64.NO_WRAP)
            )
            val expectedFingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(destinationKey)
            require(java.security.MessageDigest.isEqual(expectedFingerprint, advertisement.destinationKeyFingerprint))
            // If this destination was previously paired directly, never let a
            // third party replace its pinned recipient key through route gossip.
            val pinnedDestinationKey = TrustedPeerKeyStore.pinnedRecipientEncryptionKey(appContext, advertisement.destinationId)
            if (pinnedDestinationKey != null) {
                require(java.security.MessageDigest.isEqual(pinnedDestinationKey, destinationKey)) {
                    "Route advertised a different recipient key for a pinned destination."
                }
            }
            val alreadyDirect = synchronized(activeEndpoints) {
                activeEndpoints.any { peerEndpoint ->
                    authenticatedEndpoints.contains(peerEndpoint) &&
                        synchronized(names) { stableIds[peerEndpoint] == advertisement.destinationId }
                }
            }
            if (alreadyDirect) return
            val previous = meshRouteTable.find(advertisement.destinationId, now)
            if (!meshRouteTable.update(advertisement, senderId, senderKey, now, destinationKey)) return
            if (!meshRoutePersistence.save(advertisement, destinationKey)) {
                listener.onStatus("A verified mesh route is active, but could not be saved for the next app session.")
            }
            meshDestinationEncryptionKeys[advertisement.destinationId] = destinationKey.copyOf()
            pruneMeshDestinationKeys()
            // Re-advertise only a materially improved/new route. Refreshes from the
            // same next hop must not trigger endless route-gossip loops.
            val route = meshRouteTable.find(advertisement.destinationId, now) ?: return
            val changed = previous == null ||
                previous.nextHopPeerId != route.nextHopPeerId ||
                previous.hopCount != route.hopCount ||
                previous.path != route.path ||
                !java.security.MessageDigest.isEqual(previous.destinationKeyFingerprint, route.destinationKeyFingerprint)
            if (changed) {
                val peers = synchronized(activeEndpoints) {
                    activeEndpoints.filter { it != endpointId && authenticatedEndpoints.contains(it) }
                }
                peers.forEach { publishKnownRoutesTo(it) }
                listener.onStatus("A verified nearby mesh route was learned (${route.hopCount} hop(s)).")
                notifyMeshRoutesChanged()
                forwardDueMeshPackets()
                flushPendingMeshReceipts()
            }
        } catch (_: Exception) {
            listener.onStatus("An invalid or unauthenticated mesh route was rejected.")
        }
    }

    /** Returns currently usable routes only for recipients this phone has already trusted. */
    fun reachableMeshRecipients(): List<MeshRecipient> {
        if (!meshRouteWithdrawalStoreHealthy) return emptyList()
        val now = System.currentTimeMillis()
        return meshRouteTable.snapshot(now).mapNotNull { route ->
            if (endpointForStablePeer(route.nextHopPeerId) == null) return@mapNotNull null
            if (TrustedPeerKeyStore.pinnedSigningKey(appContext, route.destinationId) == null) return@mapNotNull null
            val pinnedKey = TrustedPeerKeyStore.pinnedRecipientEncryptionKey(appContext, route.destinationId)
                ?: return@mapNotNull null
            val pinnedFingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(pinnedKey)
            if (!java.security.MessageDigest.isEqual(pinnedFingerprint, route.destinationKeyFingerprint)) {
                return@mapNotNull null
            }
            val advertisedKey = meshDestinationEncryptionKeys[route.destinationId]
            if (advertisedKey != null && !java.security.MessageDigest.isEqual(advertisedKey, pinnedKey)) {
                return@mapNotNull null
            }
            val directEndpoint = endpointForStablePeer(route.destinationId)
            val display = directEndpoint?.let { displayNameForEndpoint(it) }
                ?.takeIf { it.isNotBlank() }
                ?: "NeaRer ${route.destinationId.takeLast(4)}"
            MeshRecipient(route.destinationId, display, route.hopCount, route.hopCount == 0)
        }.distinctBy { it.peerId }.sortedBy { it.displayName.lowercase() }
    }

    private fun notifyMeshRoutesChanged() {
        listener.onMeshRoutesChanged(reachableMeshRecipients())
    }

    private fun endpointForStablePeer(peerId: String): String? =
        synchronized(activeEndpoints) {
            activeEndpoints.firstOrNull { endpoint ->
                authenticatedEndpoints.contains(endpoint) &&
                    synchronized(names) { stableIds[endpoint] == peerId }
            }
        }

    private fun sendMeshHopAcknowledgement(endpointId: String, packetId: String, previousHopId: String) {
        try {
            val acknowledgement = MeshHopAcknowledgement.create(
                packetId, localStableId, previousHopId, System.currentTimeMillis()
            ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
            val envelope = JSONObject()
                .put("kind", "mesh_hop_ack_v1")
                .put("ack", android.util.Base64.encodeToString(acknowledgement.encode(), android.util.Base64.NO_WRAP))
            client.sendPayload(endpointId, Payload.fromBytes(envelope.toString().toByteArray(Charsets.UTF_8)))
        } catch (_: Exception) {
            listener.onStatus("A mesh hop acknowledgement could not be sent; the packet will be retried.")
        }
    }

    private fun handleMeshHopAcknowledgement(endpointId: String, envelope: JSONObject) {
        if (!authenticatedEndpoints.contains(endpointId)) return
        val acceptingPeerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (acceptingPeerId.isBlank()) return
        try {
            val acknowledgement = MeshHopAcknowledgement.decode(
                android.util.Base64.decode(envelope.getString("ack"), android.util.Base64.NO_WRAP)
            )
            val trustedKey = TrustedPeerKeyStore.pinnedSigningKey(appContext, acceptingPeerId) ?: return
            if (deadDropStore.recordHopAccepted(
                    acknowledgement, trustedKey, acceptingPeerId, localStableId, System.currentTimeMillis()
                )) {
                listener.onStatus("A nearby relay securely accepted a packet. Waiting for final delivery.")
                scheduleMeshForwardRetry()
            }
        } catch (_: Exception) {
            listener.onStatus("An invalid mesh hop acknowledgement was rejected.")
        }
    }

    private fun sendMeshReceiptHopAcknowledgement(endpointId: String, receipt: MeshDeliveryReceipt, previousHopId: String) {
        try {
            val acknowledgement = MeshHopAcknowledgement.create(
                receipt.packetId, localStableId, previousHopId, System.currentTimeMillis()
            ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
            val envelope = JSONObject()
                .put("kind", "mesh_receipt_hop_ack_v1")
                .put("ack", android.util.Base64.encodeToString(acknowledgement.encode(), android.util.Base64.NO_WRAP))
            client.sendPayload(endpointId, Payload.fromBytes(envelope.toString().toByteArray(Charsets.UTF_8)))
        } catch (_: Exception) {
            listener.onStatus("A mesh receipt acknowledgement could not be sent; the sender will retry.")
        }
    }

    private fun handleMeshReceiptHopAcknowledgement(endpointId: String, envelope: JSONObject) {
        if (!authenticatedEndpoints.contains(endpointId)) return
        val acceptingPeerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (acceptingPeerId.isBlank()) return
        try {
            val acknowledgement = MeshHopAcknowledgement.decode(
                android.util.Base64.decode(envelope.getString("ack"), android.util.Base64.NO_WRAP)
            )
            val trustedKey = TrustedPeerKeyStore.pinnedSigningKey(appContext, acceptingPeerId) ?: return
            if (!acknowledgement.verify(trustedKey, localStableId, System.currentTimeMillis()) ||
                acknowledgement.acceptingPeerId != acceptingPeerId) return
            // A different authenticated neighbor must not be able to retire a
            // receipt merely by signing an ACK with the same packet ID.
            if (meshReceiptOutbox.acknowledge(acknowledgement.packetId, acceptingPeerId)) {
                receiptSendInFlightUntil.remove(acknowledgement.packetId)
                flushPendingMeshReceipts()
            }
        } catch (_: Exception) {
            listener.onStatus("An invalid mesh receipt acknowledgement was rejected.")
        }
    }

    /** Persist first; retain each receipt until the next authenticated hop ACKs durable storage. */
    private fun queueMeshReceipt(receipt: MeshDeliveryReceipt, previousHopPeerId: String = ""): Boolean {
        if (!meshReceiptOutbox.enqueue(receipt, previousHopPeerId)) {
            listener.onStatus("A final delivery receipt could not be saved. NeaRer is keeping the message record for retry.")
            return false
        }
        flushPendingMeshReceipts()
        return true
    }

    private fun flushPendingMeshReceipts() {
        if (!meshRouteWithdrawalStoreHealthy) return
        val now = System.currentTimeMillis()
        var sentAny = false
        var waitingForAck = false
        meshReceiptOutbox.pending().forEach { pending ->
            val receipt = pending.receipt
            if (!MeshRelayParticipationPolicy.mayForwardReceipt(meshTransitEnabled, receipt.destinationId, localStableId)) return@forEach
            if ((receiptSendInFlightUntil[receipt.packetId] ?: 0L) > now) {
                waitingForAck = true
                return@forEach
            }
            val route = meshRouteTable.find(receipt.originId, now) ?: return@forEach
            if (route.nextHopPeerId == pending.previousHopPeerId) return@forEach
            val endpointId = endpointForStablePeer(route.nextHopPeerId) ?: return@forEach
            val envelope = JSONObject()
                .put("kind", "mesh_delivery_receipt_v1")
                .put("receipt", android.util.Base64.encodeToString(receipt.encode(), android.util.Base64.NO_WRAP))
            try {
                // Store the expected authenticated next hop before transmission;
                // receipt ACKs from any other connected peer are ignored.
                if (!meshReceiptOutbox.setExpectedNextHop(receipt.packetId, route.nextHopPeerId)) {
                    listener.onStatus("A mesh receipt could not securely record its next hop. It remains queued.")
                    return@forEach
                }
                receiptSendInFlightUntil[receipt.packetId] = now + RECEIPT_RETRY_MS
                sentAny = true
                client.sendPayload(endpointId, Payload.fromBytes(envelope.toString().toByteArray(Charsets.UTF_8)))
                    .addOnFailureListener {
                        receiptSendInFlightUntil.remove(receipt.packetId)
                        listener.onStatus("A mesh delivery receipt could not reach the next hop. NeaRer will retry.")
                    }
            } catch (_: Exception) {
                receiptSendInFlightUntil.remove(receipt.packetId)
                listener.onStatus("A mesh delivery receipt could not reach the next hop. NeaRer will retry.")
            }
        }
        // Do not wake the app every 15 seconds for receipts with no available route.
        // A newly authenticated neighbor or route advertisement calls this again.
        if ((sentAny || waitingForAck) && meshReceiptOutbox.size() > 0 && !receiptRetryScheduled) {
            receiptRetryScheduled = true
            deliveryHandler.postDelayed({
                receiptRetryScheduled = false
                flushPendingMeshReceipts()
            }, RECEIPT_RETRY_MS)
        }
    }

    private fun handleMeshDeliveryReceipt(endpointId: String, envelope: JSONObject) {
        if (!authenticatedEndpoints.contains(endpointId)) return
        try {
            val receipt = MeshDeliveryReceipt.decode(
                android.util.Base64.decode(envelope.getString("receipt"), android.util.Base64.NO_WRAP)
            )
            val now = System.currentTimeMillis()
            val previousHopId = synchronized(names) { stableIds[endpointId].orEmpty() }
            require(previousHopId.isNotBlank())
            if (!MeshRelayParticipationPolicy.mayAcceptReceipt(meshTransitEnabled, receipt.originId, localStableId)) {
                listener.onStatus("This phone is not participating as a relay. The receipt was not accepted or acknowledged.")
                return
            }
            if (receipt.originId == localStableId) {
                val destinationKey = TrustedPeerKeyStore.pinnedSigningKey(appContext, receipt.destinationId)
                    ?: throw SecurityException("The destination identity has not been verified on this phone.")
                require(receipt.verify(destinationKey, now)) { "The final delivery receipt signature is invalid." }
                require(deadDropStore.markDelivered(receipt, destinationKey, now)) {
                    "The verified final receipt could not be committed to the local delivery queue."
                }
                listener.onMessageStatus(receipt.packetId, "Delivered")
                // The origin has durably accepted the final receipt; acknowledge the previous hop.
                sendMeshReceiptHopAcknowledgement(endpointId, receipt, previousHopId)
                return
            }
            val destinationKey = TrustedPeerKeyStore.pinnedSigningKey(appContext, receipt.destinationId)
            if (destinationKey != null) {
                require(receipt.verify(destinationKey, now)) { "A receipt signed by a pinned destination was invalid." }
                require(deadDropStore.markDelivered(receipt, destinationKey, now)) {
                    "The verified receipt could not be committed before forwarding."
                }
            }
            // Persist the opaque receipt before acknowledging its previous hop.
            require(meshReceiptOutbox.enqueue(receipt, previousHopId)) { "The receipt could not be stored for forwarding." }
            sendMeshReceiptHopAcknowledgement(endpointId, receipt, previousHopId)
            flushPendingMeshReceipts()
        } catch (_: Exception) {
            listener.onStatus("An invalid or unstorable mesh delivery receipt was rejected.")
        }
    }

    private fun deliverMeshPacket(packet: MeshRelayPacketV1) {
        val trustedOriginKey = TrustedPeerKeyStore.pinnedSigningKey(appContext, packet.originId)
        if (trustedOriginKey == null || !java.security.MessageDigest.isEqual(trustedOriginKey, packet.originSigningPublicKey)) {
            listener.onStatus("A relayed message is safely queued, but its sender must be verified on this phone before it can be opened.")
            return
        }
        if (hasSeenMessageId(packet.packetId)) {
            val duplicateReceipt = MeshDeliveryReceipt.create(
                packet.packetId, packet.originId, packet.destinationId, System.currentTimeMillis()
            ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
            if (queueMeshReceipt(duplicateReceipt)) deadDropStore.markLocallyDelivered(packet.packetId)
            return
        }
        if (!pendingIncomingMessageIds.add(packet.packetId)) return
        try {
            val clear = RecipientMessageCrypto.decrypt(
                localRecipientIdentity.privateKey,
                packet.originId, packet.destinationId, packet.packetId,
                packet.createdAtMs, packet.expiresAtMs, packet.hopLimit, packet.encryptedPayload
            )
            val message = JSONObject(clear.toString(Charsets.UTF_8))
            require(message.optString("kind") == "message")
            require(message.optString("id") == packet.packetId)
            val body = message.optString("body")
            require(body.isNotBlank() && body.length <= 4000)
            val senderName = message.optString("origin").ifBlank { "Nearby NeaRer member" }.take(40)
            listener.onMeshMessage(packet.originId, senderName, packet.packetId, body) {
                if (packet.packetId in pendingIncomingMessageIds) {
                    val saved = rememberMessageId(packet.packetId)
                    pendingIncomingMessageIds.remove(packet.packetId)
                    if (saved) {
                        val receipt = MeshDeliveryReceipt.create(
                            packet.packetId, packet.originId, packet.destinationId, System.currentTimeMillis()
                        ) { bytes -> SecurePeerIdentity.signDetached(bytes) }
                        // Persist the final receipt before deleting the locally delivered packet.
                        // If receipt storage fails, keep the packet so a retry can regenerate it.
                        if (queueMeshReceipt(receipt)) {
                            deadDropStore.markLocallyDelivered(packet.packetId)
                        } else {
                            listener.onStatus("Message saved here. Its encrypted packet is retained until the final receipt can be saved.")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            pendingIncomingMessageIds.remove(packet.packetId)
            listener.onStatus("A relayed message could not be decrypted or validated. It was not delivered.")
        }
    }

    private fun handleMeshPacket(endpointId: String, packetBytes: ByteArray, proofBytes: ByteArray) {
        if (!authenticatedEndpoints.contains(endpointId)) return
        val previousHopId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (previousHopId.isBlank()) return
        try {
            val packet = MeshRelayPacketV1.decode(packetBytes)
            val now = System.currentTimeMillis()
            require(packet.verifyEmbeddedOrigin(now)) { "Mesh packet origin signature or expiry is invalid." }
            if (proofBytes.isEmpty()) {
                // The first acceptance must bind the packet's embedded origin key
                // to the authenticated direct sender, not merely trust a self-signed key.
                require(previousHopId == packet.originId) { "An initial packet without a proof chain did not come directly from its claimed origin." }
                val pinnedOriginKey = TrustedPeerKeyStore.pinnedSigningKey(appContext, packet.originId)
                    ?: throw SecurityException("The origin identity is not pinned on this phone.")
                require(packet.verify(pinnedOriginKey, now)) { "The packet origin key does not match the authenticated sender identity." }
            } else {
                require(previousHopId != packet.originId) { "A packet from its origin unexpectedly included a relay proof chain." }
            }
            if (!MeshRelayParticipationPolicy.mayAcceptPacket(meshTransitEnabled, packet.destinationId, localStableId)) {
                listener.onStatus("This phone is not participating as a relay. The packet was not accepted or acknowledged.")
                return
            }
            if (packet.destinationId != localStableId && packet.hopsTaken >= packet.hopLimit) {
                listener.onStatus("A mesh packet reached its forwarding limit before reaching its destination. It was not accepted.")
                return
            }
            if (packet.destinationId != localStableId && !meshRouteWithdrawalStoreHealthy) {
                listener.onStatus("This phone cannot safely relay right now because protected route history is unavailable.")
                return
            }
            val committed = deadDropStore.enqueueTransitWithProof(
                packet = packet,
                previousHopPeerId = previousHopId,
                incomingProofBytes = proofBytes,
                trustedAcceptingSigningKey = { peerId ->
                    TrustedPeerKeyStore.pinnedSigningKey(appContext, peerId)
                },
                localPeerId = localStableId,
                localSigningPublicKey = SecurePeerIdentity.publicKeyEncoded(),
                now = now
            ) { signedBytes -> SecurePeerIdentity.signDetached(signedBytes) }
            if (!committed) {
                listener.onStatus("A mesh packet or its forwarding-proof chain could not be verified and committed. It was not acknowledged.")
                return
            }
            // The hop ACK is sent only after the encrypted packet is committed to disk.
            sendMeshHopAcknowledgement(endpointId, packet.packetId, previousHopId)
            if (packet.destinationId == localStableId) {
                deliverMeshPacket(packet)
            } else {
                forwardDueMeshPackets()
            }
        } catch (_: Exception) {
            listener.onStatus("An invalid or expired mesh packet was rejected.")
        }
    }

    private fun scheduleMeshForwardRetry() {
        if (meshForwardRetryScheduled) return
        val nextWake = deadDropStore.nextWakeAt() ?: return
        val remaining = nextWake - System.currentTimeMillis()
        // No route is available yet: poll slowly rather than spinning every second.
        val delay = if (remaining <= 0L) 30_000L else remaining.coerceIn(1_000L, 60_000L)
        meshForwardRetryScheduled = true
        deliveryHandler.postDelayed({
            meshForwardRetryScheduled = false
            forwardDueMeshPackets()
        }, delay)
    }

    private fun forwardDueMeshPackets() {
        // Do not act as a transit relay if replay-protection history is unavailable.
        if (!meshRouteWithdrawalStoreHealthy) return
        val now = System.currentTimeMillis()
        deadDropStore.purgeExpired(now)
        val due = deadDropStore.claimDue(now, 8) { packet, previousHop ->
            if (!MeshRelayParticipationPolicy.mayForwardPacket(meshTransitEnabled, packet.originId, localStableId)) return@claimDue false
            if (packet.destinationId == localStableId || packet.hopsTaken >= packet.hopLimit) return@claimDue false
            val route = meshRouteTable.find(packet.destinationId, now) ?: return@claimDue false
            if (route.nextHopPeerId == previousHop) return@claimDue false
            endpointForStablePeer(route.nextHopPeerId) != null
        }
        due.forEach { pending ->
            try {
                val queuedPacket = MeshRelayPacketV1.decode(pending.bytes)
                if (queuedPacket.originId == localStableId) {
                    require(queuedPacket.hopsTaken == 0 && pending.proofChain.isEmpty()) {
                        "An origin outbox packet unexpectedly contains relay history."
                    }
                } else {
                    val storedChain = MeshForwardingProofChain.decode(pending.proofChain)
                    require(storedChain.proofs.size == queuedPacket.hopsTaken + 1) {
                        "Stored forwarding-proof count does not match the packet hop counter."
                    }
                    require(storedChain.verifyPrefix(
                        queuedPacket.packetId, queuedPacket.immutablePacketDigest(),
                        queuedPacket.originId, localStableId,
                        { peerId ->
                            if (peerId == localStableId) SecurePeerIdentity.publicKeyEncoded()
                            else TrustedPeerKeyStore.pinnedSigningKey(appContext, peerId)
                        },
                        System.currentTimeMillis()
                    )) { "Stored forwarding-proof chain failed verification before forwarding." }
                }
                val route = meshRouteTable.find(queuedPacket.destinationId, System.currentTimeMillis()) ?: return@forEach
                if (route.nextHopPeerId == pending.previousHopPeerId) return@forEach
                val packet = deadDropStore.prepareForForwarding(
                    pending.packetId, localStableId, System.currentTimeMillis(), route.nextHopPeerId
                ) ?: return@forEach
                require(packet.sameImmutablePacket(queuedPacket)) { "Queued packet changed while preparing its forwarding hop." }
                val endpointId = endpointForStablePeer(route.nextHopPeerId) ?: return@forEach
                val envelope = MeshPacketEnvelopeV2.encode(packet.encode(), pending.proofChain)
                client.sendPayload(endpointId, Payload.fromBytes(envelope))
                    .addOnFailureListener { listener.onStatus("A mesh relay transfer failed. NeaRer will retry it.") }
            } catch (_: Exception) {
                listener.onStatus("A queued mesh packet could not be forwarded and will be retried.")
            }
        }
        scheduleMeshForwardRetry()
    }

    /** Sends an E2E-encrypted chat packet over the verified mesh route to a previously trusted recipient. */
    fun sendMeshMessage(destinationPeerId: String, body: String, messageId: String = UUID.randomUUID().toString()): Boolean {
        if (!meshRouteWithdrawalStoreHealthy) return false
        if (body.isBlank() || body.length > 4000 ||
            NeaRerIdentity.normalize(destinationPeerId) != destinationPeerId ||
            messageId.isBlank() || messageId.length > 80) return false
        val now = System.currentTimeMillis()
        // Both halves of the recipient identity must be pinned by a verified direct handshake.
        if (TrustedPeerKeyStore.pinnedSigningKey(appContext, destinationPeerId) == null) return false
        val pinnedRecipientKey = TrustedPeerKeyStore.pinnedRecipientEncryptionKey(appContext, destinationPeerId)
            ?: return false
        val route = meshRouteTable.find(destinationPeerId, now) ?: return false
        val pinnedFingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(pinnedRecipientKey)
        if (!java.security.MessageDigest.isEqual(pinnedFingerprint, route.destinationKeyFingerprint)) return false
        val routeKey = meshDestinationEncryptionKeys[destinationPeerId] ?: pinnedRecipientKey
        if (!java.security.MessageDigest.isEqual(pinnedRecipientKey, routeKey)) return false
        return try {
            val content = JSONObject()
                .put("kind", "message")
                .put("version", 1)
                .put("id", messageId)
                .put("body", body)
                .put("ttl", 0)
                .put("hops", 0)
                .put("origin", localName)
                .put("createdAt", now)
                .toString().toByteArray(Charsets.UTF_8)
            val packet = MeshRelayPacketV1.create(
                localStableId, destinationPeerId, messageId, now,
                now + 24L * 60L * 60L * 1000L, 4, pinnedRecipientKey, content
            )
            val encodedPacket = packet.encode()
            // Reserve room for the bounded proof sidecar on every later hop.
            MeshPacketEnvelopeV2.encode(encodedPacket, ByteArray(0))
            if (!deadDropStore.enqueueVerified(packet, SecurePeerIdentity.publicKeyEncoded(), now)) return false
            listener.onMessageStatus(messageId, "Queued")
            forwardDueMeshPackets()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun failPeerHandshake(endpointId: String, reason: String) {
        peerHandshakes.remove(endpointId)
        messageSessions.remove(endpointId)
        authenticatedEndpoints.remove(endpointId)
        listener.onStatus(reason)
        try { client.disconnectFromEndpoint(endpointId) } catch (_: Exception) { }
    }

    private fun handleHyperStreamMetadata(endpointId: String, raw: ByteArray) {
        val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        val result = streamController.recordMetadata(endpointId, peerId, raw)
        if (result != NearbyStreamExchangeController.InputResult.STORED &&
            result != NearbyStreamExchangeController.InputResult.DUPLICATE) {
            listener.onFileStatus("A nearby stream transfer had invalid details and was rejected.")
            return
        }
        val metadata = TransferResumeStreamMetadataCodec.decode(raw)
        incomingHyperMetadata[metadata.payloadId] = metadata
        val offer = resumeCoordinator.offersForAuthenticatedSender(peerId, true, true).firstOrNull {
            it.checkpoint.transferId == metadata.transferId &&
                it.checkpoint.itemId == metadata.item.id &&
                it.checkpoint.relativePath == metadata.item.relativePath &&
                it.checkpoint.expectedSizeBytes == metadata.item.sizeBytes &&
                it.checkpoint.sourceModifiedAtMillis == metadata.sourceModifiedAtMillis
        }
        val control = if (offer != null) {
            val bytes = TransferResumeMessageCodec.encode(metadata.payloadId, offer.proposal)
            val recorded = streamController.recordProposal(endpointId, peerId, bytes)
            if (recorded != NearbyStreamExchangeController.InputResult.STORED &&
                recorded != NearbyStreamExchangeController.InputResult.DUPLICATE) return
            bytes
        } else {
            val bytes = TransferResumeMessageCodec.encodeNoResume(metadata.payloadId)
            val recorded = streamController.recordNoResume(endpointId, peerId, bytes)
            if (recorded != NearbyStreamExchangeController.InputResult.STORED &&
                recorded != NearbyStreamExchangeController.InputResult.DUPLICATE) return
            bytes
        }
        client.sendPayload(endpointId, Payload.fromBytes(control))
            .addOnFailureListener { listener.onFileStatus("Could not negotiate the saved transfer offset. Please retry.") }
        tryProcessHyperStream(metadata.payloadId)
    }

    private fun handleHyperStreamProposal(endpointId: String, raw: ByteArray) {
        val proposal = runCatching { TransferResumeMessageCodec.decode(raw) }.getOrNull() ?: return
        val state = outgoingHyperStreams[proposal.payloadId] ?: return
        val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (state.endpointId != endpointId || state.stablePeerId != peerId || !authenticatedEndpoints.contains(endpointId)) return
        startHyperStreamPayload(state, proposal.proposal)
    }

    private fun handleHyperStreamNoResume(endpointId: String, raw: ByteArray) {
        val noResume = runCatching { TransferResumeMessageCodec.decodeNoResume(raw) }.getOrNull() ?: return
        val state = outgoingHyperStreams[noResume.payloadId] ?: return
        val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (state.endpointId != endpointId || state.stablePeerId != peerId || !authenticatedEndpoints.contains(endpointId)) return
        startHyperStreamPayload(state, null)
    }

    private fun startHyperStreamPayload(state: OutgoingHyperStream, proposal: TransferResumeNegotiation.Proposal?) {
        val payloadId = state.prepared.payloadId
        if (!state.awaitingDecision || outgoingHyperStreams[payloadId] !== state) return
        state.timeout?.let(deliveryHandler::removeCallbacks)
        var decision = resumeCoordinator.validateOfferAtSender(
            proposal, state.transferId, state.item, state.sourceFile, null,
            state.stablePeerId, true, true
        )
        var source: TransferResumeCoordinator.ValidatedSourceStream? = null
        if (decision.reason == TransferResumeNegotiation.Reason.RESUME_ACCEPTED) {
            source = resumeCoordinator.openValidatedSourceAtOffset(
                proposal, state.transferId, state.item, state.sourceFile, null,
                state.stablePeerId, true, true
            )
            if (source == null) decision = TransferResumeNegotiation.Decision(
                0L, TransferResumeNegotiation.Reason.SOURCE_CHANGED
            )
        }
        if (proposal != null) {
            val decisionBytes = TransferResumeMessageCodec.encodeDecision(payloadId, decision)
            client.sendPayload(state.endpointId, Payload.fromBytes(decisionBytes))
                .addOnFailureListener {
                    state.prepared.reject(IllegalStateException("Resume decision could not be sent"))
                    listener.onFileStatus("Could not confirm the transfer offset. Please retry.")
                }
        }
        try {
            if (source != null) state.prepared.provideSource(source)
            else state.prepared.provideSource(state.sourceFile.inputStream())
            state.awaitingDecision = false
            client.sendPayload(state.endpointId, state.prepared.payload)
                .addOnFailureListener { listener.onFileStatus("The resumable stream could not start. Please retry.") }
        } catch (error: Exception) {
            state.prepared.reject(error)
            outgoingHyperStreams.remove(payloadId, state)
            listener.onFileStatus("The file could not be opened for sending.")
        }
    }

    private fun tryProcessHyperStream(payloadId: Long) {
        val payload = pendingIncomingHyperPayloads[payloadId] ?: return
        val endpointId = incomingHyperEndpoints[payloadId] ?: return
        val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        val ready = streamController.takeReady(endpointId, peerId, payloadId) ?: return
        if (!pendingIncomingHyperPayloads.remove(payloadId, payload)) return
        val metadata = ready.metadata
        val folder = File(appContext.filesDir, "received").apply { mkdirs() }
        val destination = File(folder, System.currentTimeMillis().toString() + "-" +
            TransferFileNamePolicy.safeName(metadata.item.displayName))
        streamPayloadBridge.receive(
            payload, metadata, ready.authenticatedPeerId, ready.proposal, ready.decision, destination,
            isCancelled = { cancelledStreamPayloads.contains(payloadId) }
        ) { result ->
            incomingHyperMetadata.remove(payloadId)
            incomingHyperEndpoints.remove(payloadId)
            cancelledStreamPayloads.remove(payloadId)
            transferSpeedMeters.remove(payloadId)
            result.onSuccess { completed ->
                val meter = TransferSpeedMeter()
                listener.onFileTransferUpdate(endpointId, payloadId, metadata.item.displayName,
                    completed.totalBytes, completed.totalBytes, true,
                    meter.record(completed.totalBytes, completed.totalBytes))
                listener.onFileReceived(endpointId, metadata.item.displayName, completed.completedFile)
                listener.onFileStatus("Verified transfer complete: " + metadata.item.displayName)
            }.onFailure {
                listener.onFileStatus("The received file was not published because verification failed.")
            }
        }
    }

    private fun handleBytes(endpointId: String, bytes: ByteArray?) {
        try {
            val raw = bytes ?: return
            if (MeshPacketEnvelopeV2.isEnvelope(raw)) {
                if (!authenticatedEndpoints.contains(endpointId)) return
                val decoded = MeshPacketEnvelopeV2.decode(raw)
                val proofChain = if (decoded.proofChainBytes.isEmpty()) {
                    MeshForwardingProofChain(emptyList())
                } else {
                    MeshForwardingProofChain.decode(decoded.proofChainBytes)
                }
                // Decode here as well as in the store to reject malformed sidecars before any ACK.
                require(proofChain.proofs.size <= MeshForwardingProof.MAX_ACCEPTANCE_RECORDS)
                handleMeshPacket(endpointId, decoded.packetBytes, decoded.proofChainBytes)
                return
            }
            val envelope = JSONObject(raw.toString(Charsets.UTF_8))
            when (envelope.optString("kind")) {
                "peer_hello_v3" -> { handlePeerHello(endpointId, envelope); return }
                "peer_proof_v3" -> { handlePeerProof(endpointId, envelope); return }
                "mesh_identity_binding_v1" -> { handlePeerIdentityBinding(endpointId, envelope); return }
                "mesh_route_advert_v1" -> { handleMeshRouteAdvertisement(endpointId, envelope); return }
                "mesh_route_withdraw_v1" -> { handleMeshRouteWithdrawal(endpointId, envelope); return }
                "mesh_hop_ack_v1" -> { handleMeshHopAcknowledgement(endpointId, envelope); return }
                "mesh_receipt_hop_ack_v1" -> { handleMeshReceiptHopAcknowledgement(endpointId, envelope); return }
                "mesh_delivery_receipt_v1" -> { handleMeshDeliveryReceipt(endpointId, envelope); return }
                "peer_hello_v1", "peer_proof_v1", "peer_hello_v2", "peer_proof_v2" -> {
                    failPeerHandshake(endpointId, "This nearby phone is using an older NeaRer security protocol. Update both phones before connecting.")
                    return
                }
            }
            if (!authenticatedEndpoints.contains(endpointId)) return
            if (TransferResumeStreamMetadataCodec.isEnvelope(raw)) {
                handleHyperStreamMetadata(endpointId, raw)
                return
            }
            if (raw.size >= 4) {
                when (ByteBuffer.wrap(raw).int) {
                    0x4E525253 -> { handleHyperStreamProposal(endpointId, raw); return }
                    0x4E52524E -> { handleHyperStreamNoResume(endpointId, raw); return }
                    0x4E525244 -> {
                        val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
                        val decoded = runCatching { TransferResumeMessageCodec.decodeDecision(raw) }.getOrNull() ?: return
                        val result = streamController.recordDecision(endpointId, peerId, raw)
                        if (result != NearbyStreamExchangeController.InputResult.STORED &&
                            result != NearbyStreamExchangeController.InputResult.DUPLICATE) return
                        tryProcessHyperStream(decoded.payloadId)
                        return
                    }
                }
            }
            if (envelope.optString("kind") == "mesh_packet_v1") {
                // Legacy packets carry no verifiable forwarding history and are rejected.
                listener.onStatus("An older mesh packet without forwarding proofs was rejected. Update both phones.")
                return
            }
            if (envelope.optString("kind") == "hypertransfer_stream_capabilities") {
                if (envelope.optInt("version", 0) == 1) handleHyperStreamCapability(endpointId)
                return
            }
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
                listener.onFileTransferUpdate(endpointId, payloadId, name, resumeOffset, size, true, speed)
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
                listener.onFileTransferUpdate(endpointId, payloadId, outgoing.name, offset, outgoing.size, false, resumedSpeed)
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
                val retired = clearPendingOutgoingMessage(acknowledgedId, acknowledged = true)
                listener.onMessageStatus(acknowledgedId, if (retired) "Delivered" else "Queued")
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
        return sent
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
        val messageId = UUID.randomUUID().toString()
        val envelope = JSONObject()
            .put("kind", "message")
            .put("version", 1)
            .put("id", messageId)
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

    /**
     * Queue one independently encrypted delivery for each verified stable peer ID.
     * Known offline peers are persisted with an empty endpoint and retried when that
     * same pinned identity reconnects. Display names are never recipient authority.
     */
    fun sendDurableToPeerIds(body: String, targetPeerIds: Set<String>): Boolean =
        sendDurableToPeerIdsWithReceipts(body, targetPeerIds).isNotEmpty()

    /**
     * Returns transport-message-ID -> stable-recipient-ID for every copy committed
     * to the outbox. Callers can persist this mapping to display independent delivery
     * receipts even after the app process restarts.
     */
    fun sendDurableToPeerIdsWithReceipts(body: String, targetPeerIds: Set<String>): Map<String, String> {
        if (body.isBlank() || body.length > 4000 || targetPeerIds.isEmpty()) return emptyMap()
        return queueDurableFanout(body, targetPeerIds)
    }

    /**
     * Add every trusted recipient copy to the encrypted outbox and commit once before
     * attempting any network send. A missing live endpoint is valid: the durable copy
     * waits for the same pinned peer identity to authenticate again.
     */
    @Synchronized
    private fun queueDurableFanout(body: String, peerIds: Set<String>): Map<String, String> {
        if (peerIds.isEmpty()) return emptyMap()
        if (peerIds.size > 100) {
            listener.onStatus("A community roster exceeds the supported 100-member delivery batch.")
            return emptyMap()
        }
        val queuedIds = mutableListOf<String>()
        val queuedRecipients = linkedMapOf<String, String>()
        var skippedUnverifiedPeer = false
        peerIds.distinct().forEach { peerId ->
            if (peerId == localStableId || NeaRerIdentity.normalize(peerId) != peerId ||
                !TrustedPeerKeyStore.hasPinnedPeer(appContext, peerId)) {
                skippedUnverifiedPeer = true
                return@forEach
            }
            val candidateEndpoint = endpointForStablePeer(peerId).orEmpty()
            // A discovered endpoint may still be completing its identity handshake.
            // Treat that peer as temporarily offline, not unverified: persist the copy
            // without an endpoint so authentication completion can retry it safely.
            val endpoint = CommunityDeliveryPolicy.usableEndpoint(
                candidateEndpoint,
                authenticated = candidateEndpoint.isNotBlank() && authenticatedEndpoints.contains(candidateEndpoint),
                active = candidateEndpoint.isNotBlank() && synchronized(activeEndpoints) { activeEndpoints.contains(candidateEndpoint) },
                sessionReady = candidateEndpoint.isNotBlank() && messageSessions[candidateEndpoint] != null
            )

            val messageId = UUID.randomUUID().toString()
            val envelope = JSONObject()
                .put("kind", "message")
                .put("version", 1)
                .put("id", messageId)
                .put("body", body)
                .put("ttl", 0)
                .put("hops", 0)
                .put("origin", localName)
                .put("createdAt", System.currentTimeMillis())
                .toString()
                .toByteArray(Charsets.UTF_8)

            pendingOutgoingMessages[messageId] = PendingOutgoingMessage(endpoint, peerId, envelope)
            if (endpoint.isNotBlank()) outgoingMessageTargets[messageId] = endpoint
            queuedIds += messageId
            queuedRecipients[messageId] = peerId
        }
        if (skippedUnverifiedPeer) {
            listener.onStatus("Some group members are not identity-verified on this phone; their message copies were not queued.")
        }
        if (queuedIds.isEmpty()) return emptyMap()

        if (!persistPendingOutbox()) {
            queuedIds.forEach { id ->
                pendingOutgoingMessages.remove(id)
                outgoingMessageTargets.remove(id)
            }
            return emptyMap()
        }

        // Defer first transmission until the caller has had a chance to persist
        // the transport-ID -> community-message receipt mapping returned below.
        queuedIds.forEach { id -> deliveryHandler.post { attemptOutgoingMessage(id) } }
        return queuedRecipients
    }

    /**
     * Send an identity-sensitive control packet only to the listed authenticated
     * stable peers. Unlike send(), this never broadcasts a private roster to every
     * nearby connection.
     */
    fun sendToPeerIds(body: String, targetPeerIds: Set<String>): Boolean {
        if (body.isBlank() || body.length > 4000 || targetPeerIds.isEmpty()) return false
        val targets = synchronized(activeEndpoints) {
            activeEndpoints.filter { endpoint ->
                authenticatedEndpoints.contains(endpoint) &&
                    synchronized(names) { stableIds[endpoint]?.let { it in targetPeerIds } == true } &&
                    messageSessions[endpoint] != null
            }
        }
        if (targets.isEmpty()) return false
        val messageId = UUID.randomUUID().toString()
        val envelope = JSONObject()
            .put("kind", "message")
            .put("version", 1)
            .put("id", messageId)
            .put("body", body)
            .put("ttl", 0)
            .put("hops", 0)
            .put("origin", localName)
            .put("createdAt", System.currentTimeMillis())
            .toString()
            .toByteArray(Charsets.UTF_8)
        var sent = false
        targets.forEach { endpoint ->
            val peerId = synchronized(names) { stableIds[endpoint].orEmpty() }
            val session = messageSessions[endpoint] ?: return@forEach
            try {
                val packet = NearbyMessageCrypto.encrypt(session, localStableId, peerId, messageId, envelope)
                client.sendPayload(endpoint, Payload.fromBytes(packet))
                    .addOnFailureListener { listener.onStatus("A private community update could not be sent to one member.") }
                sent = true
            } catch (_: Exception) {
                listener.onStatus("A private community update could not be encrypted for one member.")
            }
        }
        return sent
    }

    private fun sendHyperTransferStream(endpointId: String, uri: Uri, displayName: String): Long? {
        val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (NeaRerIdentity.normalize(peerId) == null) return null
        val prepared = try { streamPayloadBridge.prepareOutgoingPayload() } catch (_: Exception) {
            listener.onFileStatus("Could not prepare a resumable stream for this file.")
            return null
        }
        val payloadId = prepared.payloadId
        prepared.payload.setSensitive(true)
        listener.onFileStatus("Preparing a verified transfer for " + safeFileName(displayName) + "…")
        transferPreparation.execute {
            var staged: File? = null
            try {
                val staging = File(appContext.cacheDir, "nearer-transfer-staging").apply { mkdirs() }
                staged = File.createTempFile("nearer-hyper-", ".bin", staging)
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("The selected file could not be opened")
                val copied = input.use { source ->
                    staged!!.outputStream().buffered().use { output ->
                        TransferStreamCopy.copyAndSha256(source, output)
                    }
                }
                if (!staged!!.isFile || staged!!.length() != copied.bytesCopied) {
                    throw IllegalStateException("The staged file length did not match the copied byte count")
                }
                val name = safeFileName(displayName)
                val item = TransferBundleItem(
                    copied.sha256.take(32), name, name, copied.bytesCopied, null
                )
                val transferId = UUID.nameUUIDFromBytes(
                    (localStableId + "|" + peerId + "|" + item.sizeBytes + "|" + copied.sha256 + "|" + name)
                        .toByteArray(Charsets.UTF_8)
                ).toString()
                val state = OutgoingHyperStream(
                    endpointId, peerId, name, staged!!, item, transferId, copied.sha256, prepared
                )
                outgoingHyperStreams[payloadId] = state
                val metadata = TransferResumeStreamMetadataCodec.Metadata(
                    payloadId, transferId, item, null, copied.sha256
                )
                client.sendPayload(endpointId, Payload.fromBytes(TransferResumeStreamMetadataCodec.encode(metadata)))
                    .addOnSuccessListener {
                        val timeout = Runnable {
                            if (outgoingHyperStreams.remove(payloadId, state)) {
                                // If the peer did not answer the stream handshake, retain the proven FILE fallback.
                                state.prepared.close()
                                val filePayload = Payload.fromFile(state.sourceFile)
                                filePayload.setFileName(state.name)
                                filePayload.setSensitive(true)
                                val legacy = OutgoingFile(
                                    state.endpointId, state.stablePeerId, state.name,
                                    state.item.sizeBytes, filePayload, state.sourceFile
                                )
                                legacy.sha256 = state.sha256
                                outgoingFiles[filePayload.id] = legacy
                                listener.onFileStatus("Using standard file transfer with this nearby phone.")
                                sendPreparedFile(filePayload.id, legacy)
                            }
                        }
                        state.timeout = timeout
                        deliveryHandler.postDelayed(timeout, 12_000L)
                    }
                    .addOnFailureListener {
                        outgoingHyperStreams.remove(payloadId, state)
                        prepared.close()
                        staged?.delete()
                        listener.onFileStatus("Could not send transfer details to the nearby phone.")
                    }
            } catch (error: Exception) {
                outgoingHyperStreams.remove(payloadId)
                prepared.close()
                staged?.delete()
                listener.onFileStatus("File preparation failed: " + (error.localizedMessage ?: "please try again"))
            }
        }
        return payloadId
    }

    private fun handleHyperStreamCapability(endpointId: String) {
        val peerId = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (authenticatedEndpoints.contains(endpointId) && NeaRerIdentity.normalize(peerId) != null) {
            streamCapablePeers.add(peerId)
        }
    }

    private fun announceHyperStreamCapability(endpointId: String) {
        if (!authenticatedEndpoints.contains(endpointId)) return
        val message = JSONObject()
            .put("kind", "hypertransfer_stream_capabilities")
            .put("version", 1)
        client.sendPayload(endpointId, Payload.fromBytes(message.toString().toByteArray(Charsets.UTF_8)))
    }

    fun sendFile(endpointId: String, uri: Uri, displayName: String): Long? {
        val knownPeer = synchronized(names) { stableIds[endpointId].orEmpty() }
        if (streamCapablePeers.contains(knownPeer)) return sendHyperTransferStream(endpointId, uri, displayName)
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
            listener.onFileTransferUpdate(endpointId, id, name, 0L, size, false, initialSpeed)
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