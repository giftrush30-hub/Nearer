package com.nearer.app

import android.content.Context
import android.bluetooth.BluetoothManager
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
        fun onMessage(id: String, senderName: String, senderStableId: String, body: String)
        /** Message IDs are preserved end-to-end so the chat layer can deduplicate and acknowledge delivery. */
        fun onMessageWithId(id: String, senderName: String, senderStableId: String, messageId: String, body: String) {
            onMessage(id, senderName, senderStableId, body)
        }
        fun onDeliveryAck(id: String, messageId: String, senderStableId: String = "") {}
        fun onMessageSendFailed(messageId: String) {}
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
            prefs.getString("nearer_stable_id", null) ?: UUID.randomUUID().toString().replace("-", "").uppercase().also {
                prefs.edit().putString("nearer_stable_id", it).apply()
            }
        }
    val localMemberId: String get() = localStableId
    private fun encodedName(): String = "${localName.take(23)}|$localStableId"
    private fun decodeName(value: String): Pair<String, String> {
        val split = value.lastIndexOf('|')
        val idLength = value.length - split - 1
        if (split in 1 until value.lastIndex && idLength in setOf(6, 32)) {
            return value.substring(0, split).take(23) to value.substring(split + 1).uppercase()
        }
        return value.take(23) to ""
    }

    private data class FileMeta(val endpointId: String, val name: String, val size: Long, val sha256: String)
    private val incomingMetadata = ConcurrentHashMap<Long, FileMeta>()
    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()
    private val incomingPayloadEndpoints = ConcurrentHashMap<Long, String>()
    private val completedPayloads = ConcurrentHashMap<Long, Payload>()
    private val successfulIncomingPayloadIds = ConcurrentHashMap.newKeySet<Long>()
    // Some device/service callback schedules may report terminal progress before our
    // payload bookkeeping is populated. Retain a bounded endpoint-scoped success marker.
    private val earlySuccessfulPayloadEndpoints = ConcurrentHashMap<Long, String>()
    private val outgoingFiles = ConcurrentHashMap<Long, Pair<String, String>>()
    private val outgoingPayloads = ConcurrentHashMap<Long, Payload>()
    private val fileHashIo = Executors.newSingleThreadExecutor()
    private val fileIo = Executors.newSingleThreadExecutor()
    private val seenMessageIds = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var relayEnabled = false
    @Volatile private var started = false
    @Volatile private var startGeneration = 0
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
                listener.onConnected(id, details.first, details.second)
            } else listener.onStatus("Nearby connection was not approved. Try again.")
        }
        override fun onDisconnected(id: String) {
            val remainingConnections = synchronized(activeEndpoints) {
                activeEndpoints.remove(id)
                activeEndpoints.size
            }
            val details = synchronized(names) {
                (stableIds.remove(id).orEmpty()) to (names.remove(id) ?: "Nearby NeaRer user")
            }
            // Report every endpoint disconnect. The activity decides whether this was
            // the selected chat or another peer, so one lost peer cannot hide the rest.
            listener.onDisconnected(id, details.first, details.second)
            // A disconnected endpoint can no longer acknowledge or finish its
            // in-flight transfers. Release its partial payloads and make the
            // uncertain delivery explicit instead of leaving a permanent spinner.
            var interruptedFiles = 0
            outgoingFiles.entries.toList().forEach { entry ->
                if (entry.value.first == id && outgoingFiles.remove(entry.key, entry.value)) {
                    interruptedFiles += 1
                    try { client.cancelPayload(entry.key) } catch (_: Exception) { }
                    outgoingPayloads.remove(entry.key)?.let { try { it.close() } catch (_: Exception) { } }
                }
            }
            earlySuccessfulPayloadEndpoints.entries.toList().forEach { entry ->
                if (entry.value == id) earlySuccessfulPayloadEndpoints.remove(entry.key, id)
            }
            val interruptedIncoming = incomingPayloadEndpoints.entries
                .filter { it.value == id }
                .map { it.key }
            interruptedIncoming.forEach { payloadId ->
                incomingPayloads.remove(payloadId)?.let { try { it.close() } catch (_: Exception) { } }
                completedPayloads.remove(payloadId)?.let { try { it.close() } catch (_: Exception) { } }
                incomingPayloadEndpoints.remove(payloadId)
                incomingMetadata.remove(payloadId)
                successfulIncomingPayloadIds.remove(payloadId)
            }
            if (interruptedFiles > 0) {
                listener.onFileStatus(
                    if (interruptedFiles == 1) {
                        "Connection lost before the other phone confirmed saving the file. Delivery is unconfirmed; reconnect and check before resending."
                    } else {
                        "$interruptedFiles file transfers lost their connection before recipient confirmation. Check with the recipients before resending."
                    }
                )
            }
            if (remainingConnections > 0) {
                listener.onStatus("One nearby person disconnected. Your other direct connections remain active.")
            }
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleBytes(id, payload.asBytes())
                Payload.Type.FILE -> {
                    incomingPayloadEndpoints[payload.id] = id
                    incomingPayloads[payload.id] = payload
                    if (earlySuccessfulPayloadEndpoints.remove(payload.id) == id) {
                        successfulIncomingPayloadIds.add(payload.id)
                        completedPayloads[payload.id] = payload
                    }
                    val meta = incomingMetadata[payload.id]
                    listener.onFileProgress(id, payload.id, meta?.name ?: "Incoming file", 0L, meta?.size ?: -1L, true)
                    // The payload callback and SUCCESS update are not guaranteed to arrive
                    // in a particular order. Remember either event and finish once both exist.
                    if (successfulIncomingPayloadIds.contains(payload.id)) {
                        completedPayloads[payload.id] = payload
                    }
                    processCompletedFile(payload.id)
                }
                else -> listener.onFileStatus("NeaRer received an unsupported nearby transfer type.")
            }
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val outgoing = outgoingFiles[payloadId]
            val incoming = incomingMetadata[payloadId]
            val incomingPayload = incomingPayloads[payloadId]
            // File metadata is a BYTES payload and may arrive before or after the FILE
            // payload. Identify the transfer from the FILE callback too, not metadata alone.
            // This prevents a fast file completion from being ignored while metadata is late.
            if (outgoing == null && incoming == null && incomingPayload == null) {
                // Preserve a success event that beats onPayloadReceived(). BYTES
                // metadata may also create an unused marker, so keep this cache bounded.
                if (update.status == PayloadTransferUpdate.Status.SUCCESS &&
                    synchronized(activeEndpoints) { activeEndpoints.contains(id) }) {
                    earlySuccessfulPayloadEndpoints[payloadId] = id
                    while (earlySuccessfulPayloadEndpoints.size > 128) {
                        val oldestKey = earlySuccessfulPayloadEndpoints.keys.firstOrNull() ?: break
                        earlySuccessfulPayloadEndpoints.remove(oldestKey)
                    }
                }
                return
            }
            val name = outgoing?.second ?: incoming?.name ?: "Incoming file"
            val isIncoming = outgoing == null
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) {
                listener.onFileProgress(id, payloadId, name, update.bytesTransferred, update.totalBytes, isIncoming)
                return
            }
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                if (isIncoming) {
                    successfulIncomingPayloadIds.add(payloadId)
                    incomingPayloads[payloadId]?.let { completedPayloads[payloadId] = it }
                    processCompletedFile(payloadId)
                } else {
                    // Transport SUCCESS means the payload reached the peer's Nearby stack,
                    // not that NeaRer finished saving it. Keep this pending until file_ack.
                    listener.onFileProgress(id, payloadId, name, update.totalBytes, update.totalBytes, false)
                    listener.onFileStatus("File transferred; waiting for the other phone to save it: $name")
                }
            } else {
                outgoingFiles.remove(payloadId)
                outgoingPayloads.remove(payloadId)?.let { try { it.close() } catch (_: Exception) { } }
                incomingMetadata.remove(payloadId)
                incomingPayloads.remove(payloadId)?.close()
                completedPayloads.remove(payloadId)?.close()
                incomingPayloadEndpoints.remove(payloadId)
                successfulIncomingPayloadIds.remove(payloadId)
                listener.onFileStatus(
                    if (update.status == PayloadTransferUpdate.Status.CANCELED) {
                        "Nearby file transfer cancelled."
                    } else {
                        "Nearby file transfer failed. Check the connection and try again."
                    }
                )
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
                val sha256 = envelope.optString("sha256", "")
                if (payloadId < 0L || size < 0L ||
                    (sha256.isNotEmpty() && !sha256.matches(Regex("[a-fA-F0-9]{64}")))) {
                    listener.onFileStatus("A nearby phone sent invalid file information. The transfer was ignored.")
                    return
                }
                incomingMetadata[payloadId] = FileMeta(endpointId, name, size, sha256)
                listener.onFileProgress(endpointId, payloadId, name, 0L, size, true)
                processCompletedFile(payloadId)
                return
            }
            val kind = envelope.optString("kind", "chat")
            val messageId = envelope.optString("id")
            if (kind == "file_ack") {
                if (bytes == null || bytes.size > 1024) return
                val payloadId = envelope.optLong("payloadId", -1L)
                val pending = outgoingFiles[payloadId] ?: return
                if (payloadId < 0L || pending.first != endpointId) return
                if (outgoingFiles.remove(payloadId, pending)) {
                    outgoingPayloads.remove(payloadId)?.let { try { it.close() } catch (_: Exception) { } }
                    listener.onFileStatus("File sent to the nearby phone: ${pending.second}")
                }
                return
            }
            if (kind == "chat_ack") {
                if (bytes == null || bytes.size > 1024 || messageId.isBlank() || messageId.length > 80) return
                val ackSenderStableId = synchronized(names) { stableIds[endpointId].orEmpty() }
                listener.onDeliveryAck(endpointId, messageId, ackSenderStableId)
                return
            }
            val body = envelope.optString("body")
            val ttl = envelope.optInt("ttl", 0)
            val hops = envelope.optInt("hops", 0)
            // Nearby packets are untrusted input. Keep the envelope bounded so a
            // malformed peer cannot inject oversized IDs or request unlimited relays.
            if (bytes == null || bytes.size > 16 * 1024 ||
                messageId.isBlank() || messageId.length > 80 ||
                body.isBlank() || body.length > 4000 ||
                ttl !in 0..4 || hops !in 0..4
            ) return
            val senderDetails = synchronized(names) {
                (names[endpointId] ?: "Nearby member") to stableIds[endpointId].orEmpty()
            }
            if (!seenMessageIds.add(messageId)) {
                // A sender may retry when the original delivery receipt was lost.
                // Let the chat layer ACK the already-saved ID again; its persistent
                // message store prevents a second visible bubble. Never relay duplicates.
                if (kind == "chat") {
                    listener.onMessageWithId(endpointId, senderDetails.first, senderDetails.second, messageId, body)
                }
                return
            }
            if (seenMessageIds.size > 5000) seenMessageIds.remove(seenMessageIds.first())
            listener.onMessageWithId(endpointId, senderDetails.first, senderDetails.second, messageId, body)
            // Only explicitly tagged community packets may be relayed.
            // Private one-to-one chat must stay on its approved direct endpoint;
            // never forward its plaintext body through an intermediate phone.
            if (MeshRelayPolicy.shouldRelay(relayEnabled, body, ttl, hops)) {
                val forwarded = JSONObject().put("kind", kind).put("id", messageId).put("body", body)
                    .put("ttl", ttl - 1).put("hops", hops + 1)
                    .put("origin", envelope.optString("origin", "nearby"))
                broadcast(forwarded.toString(), endpointId)
            }
        } catch (_: Exception) {
            listener.onStatus("A nearby message could not be read and was ignored.")
        }
    }

    private fun processCompletedFile(payloadId: Long) {
        val payload = completedPayloads[payloadId] ?: return
        val meta = incomingMetadata[payloadId] ?: return
        val payloadEndpoint = incomingPayloadEndpoints[payloadId] ?: return
        if (meta.endpointId != payloadEndpoint) {
            completedPayloads.remove(payloadId, payload)
            incomingPayloads.remove(payloadId)
            incomingMetadata.remove(payloadId)
            incomingPayloadEndpoints.remove(payloadId)
            successfulIncomingPayloadIds.remove(payloadId)
            try { payload.close() } catch (_: Exception) { }
            listener.onFileStatus("A file transfer did not match its sending phone and was rejected.")
            return
        }
        if (!completedPayloads.remove(payloadId, payload)) return
        incomingPayloads.remove(payloadId)
        incomingPayloadEndpoints.remove(payloadId)
        incomingMetadata.remove(payloadId)
        successfulIncomingPayloadIds.remove(payloadId)
        fileIo.execute {
            var destination: File? = null
            try {
                val folder = File(appContext.filesDir, "received").apply { mkdirs() }
                val uri: Uri = payload.asFile()?.asUri() ?: throw IllegalStateException("Nearby file content is unavailable")
                val target = appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileTransferIntegrity.copyAndVerify(input, folder, meta.name, meta.size, meta.sha256)
                } ?: throw IllegalStateException("Could not open the received file")
                destination = target
                appContext.contentResolver.delete(uri, null, null)
                listener.onFileReceived(meta.endpointId, meta.name, target)
                // Confirm only after the complete file has been copied into NeaRer's
                // persistent received-files directory and passed its size/checksum checks.
                val receipt = JSONObject().put("kind", "file_ack").put("payloadId", payloadId)
                client.sendPayload(payloadEndpoint, Payload.fromBytes(receipt.toString().toByteArray(Charsets.UTF_8)))
                    .addOnFailureListener {
                        listener.onFileStatus("The file is saved here, but its delivery receipt could not reach the sender.")
                    }
            } catch (error: Exception) {
                destination?.delete()
                listener.onFileStatus("Could not save the received file: ${error.localizedMessage ?: "transfer error"}")
            } finally {
                try { payload.close() } catch (_: Exception) { }
            }
        }
    }

    private fun safeFileName(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "shared-file" }

    private fun failStart(generation: Int, message: String) {
        if (generation != startGeneration || !started) return
        started = false
        try { client.stopAdvertising() } catch (_: Exception) { }
        try { client.stopDiscovery() } catch (_: Exception) { }
        listener.onStatus(message)
    }

    private fun startAdvertising(generation: Int = startGeneration) {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(encodedName(), serviceId, lifecycle, options).addOnFailureListener {
            failStart(generation, "Nearby advertising could not start: ${it.localizedMessage ?: "check Bluetooth permissions"}. Tap Find people to retry.")
        }
    }

    fun setDisplayName(name: String) {
        val clean = name.trim().replace("|", " ").take(23).ifBlank { "NeaRer User" }
        if (clean == localName) return
        localName = clean
        if (started) { client.stopAdvertising(); startAdvertising(startGeneration) }
    }

    /**
     * Nearby Connections no longer guarantees that it will enable device radios
     * on the user's behalf. Check the visible radio state before starting and
     * guide the user to Android Quick Settings when both are disabled.
     */
    private fun hasEnabledNearbyRadio(): Boolean {
        val bluetoothEnabled = try {
            val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
        val wifiEnabled = try {
            val manager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            manager?.isWifiEnabled == true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
        return bluetoothEnabled || wifiEnabled
    }

    fun start() {
        if (started) return
        if (!hasEnabledNearbyRadio()) {
            listener.onStatus("Turn on Bluetooth or Wi-Fi in Quick Settings, then tap Find people again. NeaRer will not switch your phone radios on for you.")
            return
        }
        started = true
        val generation = ++startGeneration
        val discovering = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        startAdvertising(generation)
        client.startDiscovery(serviceId, discovery, discovering).addOnFailureListener {
            failStart(generation, "Nearby discovery could not start: ${it.localizedMessage ?: "check Bluetooth and nearby-device permissions"}. Tap Find people to retry.")
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
    fun setRelayEnabled(enabled: Boolean) { relayEnabled = enabled }

    fun send(body: String): Boolean {
        val messageId = UUID.randomUUID().toString()
        seenMessageIds.add(messageId)
        val envelope = JSONObject().put("id", messageId).put("body", body)
            .put("ttl", 4).put("hops", 0).put("origin", localName)
        return broadcast(envelope.toString(), null)
    }

    /**
     * Sends a private chat packet to exactly one approved endpoint.
     * Community broadcasts use send(); one-to-one conversations must never fan out.
     */
    /** Send a private chat message with its original local ID. Delivery is confirmed separately by chat_ack. */
    fun sendChatToEndpoint(endpointId: String, messageId: String, body: String): Boolean {
        if (endpointId.isBlank() || messageId.isBlank() || messageId.length > 80 || body.isBlank() || body.length > 4000) return false
        if (synchronized(activeEndpoints) { !activeEndpoints.contains(endpointId) }) return false
        val envelope = JSONObject().put("kind", "chat").put("id", messageId).put("body", body)
            .put("ttl", 0).put("hops", 0).put("origin", localName).toString()
        return try {
            client.sendPayload(endpointId, Payload.fromBytes(envelope.toByteArray(Charsets.UTF_8)))
                .addOnFailureListener {
                    listener.onMessageSendFailed(messageId)
                    listener.onStatus("Private message could not be delivered. Check the nearby connection.")
                }
            true
        } catch (_: Exception) { false }
    }

    /** Acknowledge only after the chat layer has saved the received message locally. */
    fun sendDeliveryAck(endpointId: String, messageId: String): Boolean {
        if (endpointId.isBlank() || messageId.isBlank() || messageId.length > 80) return false
        if (synchronized(activeEndpoints) { !activeEndpoints.contains(endpointId) }) return false
        val envelope = JSONObject().put("kind", "chat_ack").put("id", messageId).toString()
        return try {
            client.sendPayload(endpointId, Payload.fromBytes(envelope.toByteArray(Charsets.UTF_8)))
                .addOnFailureListener { listener.onStatus("The message was saved, but its delivery receipt could not be returned yet.") }
            true
        } catch (_: Exception) { false }
    }

    fun sendToEndpoint(endpointId: String, body: String): Boolean {
        if (endpointId.isBlank() || body.isBlank() || body.length > 4000) return false
        if (synchronized(activeEndpoints) { !activeEndpoints.contains(endpointId) }) return false
        val messageId = UUID.randomUUID().toString()
        seenMessageIds.add(messageId)
        val envelope = JSONObject().put("id", messageId).put("body", body)
            .put("ttl", 0).put("hops", 0).put("origin", localName).toString()
        return try {
            client.sendPayload(endpointId, Payload.fromBytes(envelope.toByteArray(Charsets.UTF_8)))
                .addOnFailureListener {
                    listener.onStatus("Private message could not be delivered. Check the nearby connection.")
                }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sendToStableIds(body: String, targetStableIds: Set<String>): Boolean {
        if (body.isBlank() || targetStableIds.isEmpty()) return false
        val targets = synchronized(activeEndpoints) {
            activeEndpoints.filter { endpoint ->
                synchronized(names) { stableIds[endpoint]?.let { it in targetStableIds } == true }
            }
        }
        if (targets.isEmpty()) return false
        val envelope = JSONObject().put("id", UUID.randomUUID().toString()).put("body", body)
            .put("ttl", 0).put("hops", 0).put("origin", localName).toString()
        targets.forEach { endpoint ->
            client.sendPayload(endpoint, Payload.fromBytes(envelope.toByteArray(Charsets.UTF_8)))
                .addOnFailureListener { listener.onStatus("A community message could not be sent to one nearby member.") }
        }
        return true
    }

    fun sendToNames(body: String, targetNames: Set<String>): Boolean {
        if (body.isBlank() || targetNames.isEmpty()) return false
        val targets = synchronized(activeEndpoints) {
            activeEndpoints.filter { endpoint ->
                synchronized(names) { names[endpoint]?.let { it in targetNames } == true }
            }
        }
        if (targets.isEmpty()) return false
        val envelope = JSONObject().put("id", UUID.randomUUID().toString()).put("body", body)
            .put("ttl", 0).put("hops", 0).put("origin", localName).toString()
        targets.forEach { endpoint ->
            client.sendPayload(endpoint, Payload.fromBytes(envelope.toByteArray(Charsets.UTF_8)))
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
            if (reportedSize < 0L) throw IllegalStateException("NeaRer could not determine file size. Choose another file.")
            val size = reportedSize
            val payload = Payload.fromFile(openedDescriptor)
            filePayload = payload
            descriptor = null
            val name = safeFileName(displayName)
            payload.setFileName(name)
            payload.setSensitive(true)
            val id = payload.id
            val pending = endpointId to name
            outgoingFiles[id] = pending
            outgoingPayloads[id] = payload
            listener.onFileProgress(endpointId, id, name, 0L, size, false)
            listener.onFileStatus("Checking file integrity before sending: " + name)
            fileHashIo.execute {
                try {
                    val digest = appContext.contentResolver.openInputStream(uri)?.use { input -> FileTransferIntegrity.sha256(input, size) }
                        ?: throw IllegalStateException("The selected file could not be read for integrity checking")
                    if (outgoingFiles[id] != pending || !synchronized(activeEndpoints) { activeEndpoints.contains(endpointId) }) {
                        outgoingFiles.remove(id, pending)
                        outgoingPayloads.remove(id)?.let { try { it.close() } catch (_: Exception) { } }
                        return@execute
                    }
                    val metadata = JSONObject().put("kind", "file_meta").put("payloadId", id)
                        .put("name", name).put("size", size).put("sha256", digest)
                    client.sendPayload(endpointId, Payload.fromBytes(metadata.toString().toByteArray(Charsets.UTF_8)))
                        .addOnSuccessListener {
                            if (outgoingFiles[id] != pending || !synchronized(activeEndpoints) { activeEndpoints.contains(endpointId) }) {
                                outgoingFiles.remove(id, pending)
                                outgoingPayloads.remove(id)?.let { try { it.close() } catch (_: Exception) { } }
                                try { client.cancelPayload(id) } catch (_: Exception) { }
                                return@addOnSuccessListener
                            }
                            client.sendPayload(endpointId, payload).addOnFailureListener { error ->
                                outgoingFiles.remove(id, pending)
                                outgoingPayloads.remove(id)?.let { try { it.close() } catch (_: Exception) { } }
                                listener.onFileStatus("Could not start file transfer: " + (error.localizedMessage ?: "connection error"))
                            }
                        }
                        .addOnFailureListener { error ->
                            outgoingFiles.remove(id, pending)
                            outgoingPayloads.remove(id)?.let { try { it.close() } catch (_: Exception) { } }
                            try { client.cancelPayload(id) } catch (_: Exception) { }
                            listener.onFileStatus("Could not send file details: " + (error.localizedMessage ?: "connection error"))
                        }
                } catch (error: Exception) {
                    outgoingFiles.remove(id, pending)
                    outgoingPayloads.remove(id)?.let { try { it.close() } catch (_: Exception) { } }
                    listener.onFileStatus("File integrity check failed; nothing was sent: " + (error.localizedMessage ?: "file read error"))
                }
            }
            return id
        } catch (error: Exception) {
            try { filePayload?.close() } catch (_: Exception) { }
            try { descriptor?.close() } catch (_: Exception) { }
            listener.onFileStatus("File could not be prepared: " + (error.localizedMessage ?: "choose another file"))
            return null
        }
    }

    fun cancelFile(payloadId: Long) {
        try { client.cancelPayload(payloadId) } catch (_: Exception) { }
        outgoingFiles.remove(payloadId)
        outgoingPayloads.remove(payloadId)?.let { try { it.close() } catch (_: Exception) { } }
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
        startGeneration += 1
        try { client.stopAdvertising() } catch (_: Exception) { }
        try { client.stopDiscovery() } catch (_: Exception) { }
    }

    fun disconnect() {
        try { client.stopAllEndpoints() } catch (_: Exception) { }
        synchronized(activeEndpoints) { activeEndpoints.clear() }
        // Drop partial transfers when a peer disconnects so temporary payloads
        // and stale progress entries cannot leak into the next connection.
        (incomingPayloads.values + completedPayloads.values)
            .distinctBy { it.id }
            .forEach { try { it.close() } catch (_: Exception) { } }
        incomingPayloads.clear()
        incomingPayloadEndpoints.clear()
        completedPayloads.clear()
        incomingMetadata.clear()
        successfulIncomingPayloadIds.clear()
        earlySuccessfulPayloadEndpoints.clear()
        outgoingFiles.keys.forEach { payloadId ->
            try { client.cancelPayload(payloadId) } catch (_: Exception) { }
            outgoingPayloads.remove(payloadId)?.let { try { it.close() } catch (_: Exception) { } }
        }
        outgoingFiles.clear()
        outgoingPayloads.values.forEach { try { it.close() } catch (_: Exception) { } }
        outgoingPayloads.clear()
    }
    fun stop() {
        stopSearching()
        disconnect()
        fileHashIo.shutdownNow()
        fileIo.shutdownNow()
    }

}