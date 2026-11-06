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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class NearbyConnectionsTransport(context: Context, private val listener: Listener) {
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
        fun onFileReceived(id: String, name: String, file: File)
        fun onFileStatus(message: String)
    }

    private val appContext = context.applicationContext
    private val client = Nearby.getConnectionsClient(appContext)
    private val serviceId = context.packageName
    private val names = mutableMapOf<String, String>()
    private val stableIds = mutableMapOf<String, String>()
    private val activeEndpoints = mutableSetOf<String>()
    private val localStableId = context.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        .let { prefs ->
            prefs.getString("nearer_stable_id", null) ?: UUID.randomUUID().toString().replace("-", "").take(6).uppercase().also {
                prefs.edit().putString("nearer_stable_id", it).apply()
            }
        }
    private fun encodedName(): String = "${localName.take(23)}|$localStableId"
    private fun decodeName(value: String): Pair<String, String> {
        val split = value.lastIndexOf('|')
        if (split in 1 until value.lastIndex && value.length - split - 1 == 6) {
            return value.substring(0, split).take(23) to value.substring(split + 1).uppercase()
        }
        return value.take(23) to ""
    }

    private data class FileMeta(val endpointId: String, val name: String, val size: Long)
    private val incomingMetadata = ConcurrentHashMap<Long, FileMeta>()
    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()
    private val incomingPayloadEndpoints = ConcurrentHashMap<Long, String>()
    private val completedPayloads = ConcurrentHashMap<Long, Payload>()
    private val outgoingFiles = ConcurrentHashMap<Long, Pair<String, String>>()
    private val transferStartedAtNanos = ConcurrentHashMap<Long, Long>()
    private val completedTransferRates = ConcurrentHashMap<Long, String>()
    private val fileIo = Executors.newSingleThreadExecutor()
    private val seenMessageIds = ConcurrentHashMap.newKeySet<String>().apply {
        appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            .getStringSet("nearby_seen_message_ids", HashSet<String>()).orEmpty().forEach { add(it) }
    }
    // Prevent two copies arriving at once while the first copy is still being saved.
    // An ID becomes "seen" only after the UI confirms local persistence.
    private val pendingIncomingMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val outgoingMessageTargets = ConcurrentHashMap<String, String>()
    private data class PendingOutgoingMessage(@field:Volatile var endpointId: String, val stablePeerId: String, val payload: ByteArray, var attempts: Int = 0, var retryTask: Runnable? = null)
    private val pendingOutgoingMessages = ConcurrentHashMap<String, PendingOutgoingMessage>()
    private val deliveryHandler = Handler(Looper.getMainLooper())
    private val maxMessageAttempts = 3
    init { restorePendingOutbox() }
    private fun persistPendingOutbox(): Boolean {
        return try {
            val array = org.json.JSONArray()
            pendingOutgoingMessages.forEach { (id, p) -> array.put(JSONObject().put("id", id).put("peer", p.stablePeerId).put("payload", p.payload.toString(Charsets.UTF_8))) }
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
            val raw = appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getString("nearby_outbox", "[]") ?: "[]"
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i); val id = item.optString("id"); val peer = item.optString("peer"); val payload = item.optString("payload")
                if (id.isNotBlank() && peer.isNotBlank() && payload.isNotBlank() && payload.length <= 12000) pendingOutgoingMessages[id] = PendingOutgoingMessage("", peer, payload.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) { listener.onStatus("Some queued messages could not be restored.") }
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
        if (synchronized(activeEndpoints) { !activeEndpoints.contains(pending.endpointId) }) {
            scheduleMessageRetry(messageId, 1800L)
            return
        }
        val timeout = Runnable { scheduleMessageRetry(messageId, 1800L) }
        pending.retryTask?.let(deliveryHandler::removeCallbacks)
        pending.retryTask = timeout
        deliveryHandler.postDelayed(timeout, 5000L)
        try {
            client.sendPayload(pending.endpointId, Payload.fromBytes(pending.payload))
                .addOnFailureListener { scheduleMessageRetry(messageId, 1200L) }
        } catch (_: Exception) {
            scheduleMessageRetry(messageId, 1200L)
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

    private fun rememberMessageId(messageId: String) {
        seenMessageIds.add(messageId)
        while (seenMessageIds.size > 5000) {
            val oldest = seenMessageIds.firstOrNull() ?: break
            seenMessageIds.remove(oldest)
        }
        appContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            .edit().putStringSet("nearby_seen_message_ids", HashSet(seenMessageIds)).apply()
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
                retryQueuedMessagesForPeer(details.second, id)
                listener.onConnected(id, details.first, details.second)
            } else listener.onStatus("Nearby connection was not approved. Try again.")
        }
        override fun onDisconnected(id: String) {
            val empty = synchronized(activeEndpoints) { activeEndpoints.remove(id); activeEndpoints.isEmpty() }
            val details = synchronized(names) { (stableIds.remove(id).orEmpty()) to (names.remove(id) ?: "Nearby NeaRer user") }
            if (empty) listener.onDisconnected(id, details.first, details.second)
            else {
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
                    incomingPayloads[payload.id] = payload
                    incomingPayloadEndpoints[payload.id] = id
                    val meta = incomingMetadata[payload.id]
                    listener.onFileProgress(id, payload.id, meta?.name ?: "Incoming file", 0L, meta?.size ?: -1L, true)
                    processCompletedFile(payload.id)
                }
                else -> listener.onFileStatus("NeaRer received an unsupported nearby transfer type.")
            }
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val outgoing = outgoingFiles[payloadId]
            val incoming = incomingMetadata[payloadId]
            val name = outgoing?.second ?: incoming?.name ?: "Incoming file"
            val isIncoming = outgoing == null
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) {
                transferStartedAtNanos.putIfAbsent(payloadId, System.nanoTime())
            }
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) {
                listener.onFileProgress(id, payloadId, name, update.bytesTransferred, update.totalBytes, isIncoming)
                return
            }
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val startedAt = transferStartedAtNanos.remove(payloadId) ?: System.nanoTime()
                val elapsedNanos = (System.nanoTime() - startedAt).coerceAtLeast(1L)
                val averageMbps = update.totalBytes.toDouble() * 1_000_000_000.0 /
                    elapsedNanos.toDouble() / (1024.0 * 1024.0)
                val rateLabel = String.format(java.util.Locale.US, "%.1f", averageMbps)
                if (isIncoming) {
                    completedTransferRates[payloadId] = rateLabel
                    incomingPayloads[payloadId]?.let { completedPayloads[payloadId] = it }
                    processCompletedFile(payloadId)
                } else {
                    outgoingFiles.remove(payloadId)
                    listener.onFileProgress(id, payloadId, name, update.totalBytes, update.totalBytes, false)
                    listener.onFileStatus("File sent to the nearby phone: $name • $rateLabel MB/s average")
                }
            } else {
                transferStartedAtNanos.remove(payloadId)
                completedTransferRates.remove(payloadId)
                outgoingFiles.remove(payloadId)
                incomingMetadata.remove(payloadId)
                incomingPayloads.remove(payloadId)?.close()
                completedPayloads.remove(payloadId)?.close()
                incomingPayloadEndpoints.remove(payloadId)
                listener.onFileStatus("Nearby file transfer failed or was cancelled.")
            }
        }
    }

    private fun handleBytes(endpointId: String, bytes: ByteArray?) {
        try {
            val envelope = JSONObject(bytes?.toString(Charsets.UTF_8).orEmpty())
            if (envelope.optString("kind") == "file_meta") {
                val payloadId = envelope.optLong("payloadId", -1L)
                val name = safeFileName(envelope.optString("name", "shared-file"))
                val size = envelope.optLong("size", -1L)
                if (payloadId < 0L || size < 0L) {
                    listener.onFileStatus("A nearby phone sent invalid file information. The transfer was ignored.")
                    return
                }
                incomingMetadata[payloadId] = FileMeta(endpointId, name, size)
                transferStartedAtNanos[payloadId] = System.nanoTime()
                listener.onFileProgress(endpointId, payloadId, name, 0L, size, true)
                processCompletedFile(payloadId)
                return
            }
            val kind = envelope.optString("kind", "message")
            if (kind == "ack") {
                val acknowledgedId = envelope.optString("id")
                // Only the peer we actually sent this message to may confirm it.
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
            // A previously saved message is safe to acknowledge again after a lost receipt.
            if (seenMessageIds.contains(messageId)) {
                sendAcknowledgement(endpointId, messageId)
                return
            }
            // If the same payload arrives while its first copy is being saved, wait for
            // that save to finish; do not acknowledge a message that is not durable yet.
            if (!pendingIncomingMessageIds.add(messageId)) return
            val senderName = synchronized(names) { names[endpointId] ?: "Nearby member" }
            // Persist the dedupe ID only when the UI confirms that the message itself
            // has been saved. This closes the crash window between ID and message storage.
            try {
                listener.onMessage(endpointId, senderName, messageId, body) {
                    if (pendingIncomingMessageIds.remove(messageId)) {
                        rememberMessageId(messageId)
                        sendAcknowledgement(endpointId, messageId)
                    }
                }
            } catch (error: Exception) {
                pendingIncomingMessageIds.remove(messageId)
                throw error
            }
            // Direct/private chat messages use ttl=0. Multi-hop forwarding stays disabled
            // until recipient routing and end-to-end encryption are implemented.
        } catch (_: Exception) {
            listener.onStatus("A nearby message could not be read and was ignored.")
        }
    }

    private fun processCompletedFile(payloadId: Long) {
        val payload = completedPayloads[payloadId] ?: return
        val meta = incomingMetadata[payloadId] ?: return
        val payloadEndpoint = incomingPayloadEndpoints[payloadId]
        if (payloadEndpoint == null) return
        if (payloadEndpoint != meta.endpointId) {
            completedPayloads.remove(payloadId, payload)
            incomingPayloads.remove(payloadId)
            incomingPayloadEndpoints.remove(payloadId)
            incomingMetadata.remove(payloadId)
            try { payload.close() } catch (_: Exception) { }
            listener.onFileStatus("File details did not match the sending phone. The transfer was rejected.")
            return
        }
        if (!completedPayloads.remove(payloadId, payload)) return
        incomingPayloads.remove(payloadId)
        incomingPayloadEndpoints.remove(payloadId)
        incomingMetadata.remove(payloadId)
        try {
            fileIo.execute {
                var temporary: File? = null
                var destination: File? = null
                try {
                    val folder = File(appContext.filesDir, "received").apply {
                        if (!exists() && !mkdirs()) throw IllegalStateException("Could not create the received-files folder")
                        if (!isDirectory) throw IllegalStateException("Received-files location is not a folder")
                    }
                    val usableBytes = folder.usableSpace
                    if (meta.size > usableBytes) {
                        throw IllegalStateException("Not enough free storage for this file")
                    }
                    val uniquePrefix = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
                    val target = File(folder, "$uniquePrefix-${meta.name}")
                    val temp = File(folder, ".$uniquePrefix.part")
                    temporary = temp
                    destination = target

                    val uri: Uri = payload.asFile()?.asUri()
                        ?: throw IllegalStateException("Nearby file content is unavailable")
                    val copied = appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output ->
                            val result = TransferStreamIO.copyExactly(
                                input = input,
                                output = output,
                                expectedBytes = meta.size,
                                bufferBytes = HyperTransferTuning.receiveBufferBytes(appContext)
                            )
                            output.fd.sync()
                            result
                        }
                    } ?: throw IllegalStateException("Could not open the received file")

                    if (copied.bytesCopied != meta.size || temp.length() != meta.size) {
                        throw IllegalStateException("Received file size did not match")
                    }
                    if (target.exists()) throw IllegalStateException("A file with this destination name already exists")
                    if (!temp.renameTo(target)) {
                        throw IllegalStateException("Could not safely finalize the received file")
                    }
                    temporary = null
                    destination = null // The final file is committed; later UI callbacks must not delete it.
                    appContext.contentResolver.delete(uri, null, null)
                    listener.onFileReceived(meta.endpointId, meta.name, target)
                    completedTransferRates.remove(payloadId)?.let { rate ->
                        listener.onFileStatus("Received ${meta.name} • $rate MB/s average • SHA-256 ${TransferStreamIO.sha256Hex(copied.sha256).take(12)}")
                    }
                } catch (error: Exception) {
                    temporary?.delete()
                    destination?.takeIf { it.exists() }?.delete()
                    listener.onFileStatus("Could not save the received file: ${error.localizedMessage ?: "transfer error"}")
                } finally {
                    try { payload.close() } catch (_: Exception) { }
                }
            }
        } catch (error: Exception) {
            completedTransferRates.remove(payloadId)
            try { payload.close() } catch (_: Exception) { }
            listener.onFileStatus("Could not queue the received file for saving: ${error.localizedMessage ?: "storage busy"}")
        }
    }

    private fun safeFileName(raw: String): String = TransferFileName.safeLeafName(raw)

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(encodedName(), serviceId, lifecycle, options).addOnFailureListener {
            listener.onStatus("Nearby advertising could not start: ${it.localizedMessage ?: "check Bluetooth permissions"}")
        }
    }

    fun setDisplayName(name: String) {
        val clean = name.trim().replace("|", " ").take(23).ifBlank { "NeaRer User" }
        if (clean == localName) return
        localName = clean
        if (started) { client.stopAdvertising(); startAdvertising() }
    }

    fun start() {
        if (started) return
        started = true
        val discovering = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        startAdvertising()
        client.startDiscovery(serviceId, discovery, discovering).addOnFailureListener {
            listener.onStatus("Nearby discovery could not start: ${it.localizedMessage ?: "check Bluetooth permissions"}")
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
        seenMessageIds.add(messageId)
        val envelope = JSONObject().put("kind", "message").put("version", 1)
            .put("id", messageId).put("body", body)
            .put("ttl", 0).put("hops", 0).put("origin", localName)
            .put("createdAt", System.currentTimeMillis()).toString()
        if (targetEndpointId != null) {
            if (synchronized(activeEndpoints) { !activeEndpoints.contains(targetEndpointId) }) return false
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
        return broadcast(envelope, null)
    }

    private fun sendAcknowledgement(endpointId: String, messageId: String) {
        try {
            val receipt = JSONObject().put("kind", "ack").put("version", 1).put("id", messageId)
            client.sendPayload(endpointId, Payload.fromBytes(receipt.toString().toByteArray(Charsets.UTF_8)))
        } catch (_: Exception) {
            // The message itself is already safely stored; the sender will retain a pending state.
        }
    }

    fun sendToNames(body: String, targetNames: Set<String>): Boolean {
        if (body.isBlank() || targetNames.isEmpty()) return false
        val targets = synchronized(activeEndpoints) {
            activeEndpoints.filter { endpoint ->
                synchronized(names) { names[endpoint]?.let { it in targetNames } == true }
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
            client.sendPayload(endpoint, Payload.fromBytes(envelope))
                .addOnFailureListener { listener.onStatus("A group message could not be sent to one nearby member.") }
        }
        return true
    }

    fun sendFile(endpointId: String, uri: Uri, displayName: String): Long? {
        if (synchronized(activeEndpoints) { !activeEndpoints.contains(endpointId) }) {
            listener.onFileStatus("The nearby connection is no longer active.")
            return null
        }
        var descriptor: android.os.ParcelFileDescriptor? = null
        var filePayload: Payload? = null
        try {
            val openedDescriptor = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("The selected file could not be opened")
            descriptor = openedDescriptor
            val descriptorSize = openedDescriptor.statSize
            val reportedSize = if (descriptorSize >= 0L) descriptorSize else {
                appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                    } else -1L
                } ?: -1L
            }
            if (reportedSize < 0L) throw IllegalStateException("NeaRer could not determine this file's size. Choose another file.")
            val size = reportedSize
            val payload = Payload.fromFile(openedDescriptor)
            filePayload = payload
            descriptor = null // The Payload now owns the descriptor.
            val name = safeFileName(displayName)
            payload.setFileName(name)
            payload.setSensitive(true)
            val id = payload.id
            outgoingFiles[id] = endpointId to name
            val metadata = JSONObject().put("kind", "file_meta").put("payloadId", id)
                .put("name", name).put("size", size)
            client.sendPayload(endpointId, Payload.fromBytes(metadata.toString().toByteArray(Charsets.UTF_8)))
                .addOnSuccessListener {
                    transferStartedAtNanos[payload.id] = System.nanoTime()
                    client.sendPayload(endpointId, payload)
                        .addOnFailureListener { error ->
                            outgoingFiles.remove(id)
                            transferStartedAtNanos.remove(id)
                            try { payload.close() } catch (_: Exception) { }
                            listener.onFileStatus("Could not start file transfer: ${error.localizedMessage ?: "connection error"}")
                        }
                }
                .addOnFailureListener { error ->
                    outgoingFiles.remove(id)
                    try { client.cancelPayload(id) } catch (_: Exception) { }
                    try { payload.close() } catch (_: Exception) { }
                    listener.onFileStatus("Could not send file details: ${error.localizedMessage ?: "connection error"}")
                }
            listener.onFileProgress(endpointId, id, name, 0L, size, false)
            return id
        } catch (error: Exception) {
            try { filePayload?.close() } catch (_: Exception) { }
            try { descriptor?.close() } catch (_: Exception) { }
            listener.onFileStatus("File could not be prepared: ${error.localizedMessage ?: "choose another file"}")
            return null
        }
    }

    fun cancelFile(payloadId: Long) {
        try { client.cancelPayload(payloadId) } catch (_: Exception) { }
        outgoingFiles.remove(payloadId)
        transferStartedAtNanos.remove(payloadId)
        completedTransferRates.remove(payloadId)
    }

    private fun broadcast(content: String, excludeEndpoint: String?): Boolean {
        val targets = synchronized(activeEndpoints) { activeEndpoints.filter { it != excludeEndpoint } }
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
    }
    fun stop() {
        stopSearching()
        disconnect()
        pendingOutgoingMessages.values.forEach { it.retryTask?.let(deliveryHandler::removeCallbacks); it.retryTask = null }
        deliveryHandler.removeCallbacksAndMessages(null)
        fileIo.shutdownNow()
    }

}