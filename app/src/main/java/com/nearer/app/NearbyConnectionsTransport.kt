package com.nearer.app

import android.content.Context
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
        fun onMessage(id: String, senderName: String, body: String)
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
    private val completedPayloads = ConcurrentHashMap<Long, Payload>()
    private val successfulIncomingPayloadIds = ConcurrentHashMap.newKeySet<Long>()
    private val outgoingFiles = ConcurrentHashMap<Long, Pair<String, String>>()
    private val fileIo = Executors.newSingleThreadExecutor()
    private val seenMessageIds = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var relayEnabled = false
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
            // Nearby also reports progress for ordinary BYTES messages and file metadata.
            // Only render progress for the actual file payload, never for chat packets.
            if (outgoing == null && incoming == null) return
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
                    outgoingFiles.remove(payloadId)
                    listener.onFileProgress(id, payloadId, name, update.totalBytes, update.totalBytes, false)
                    listener.onFileStatus("File sent to the nearby phone: $name")
                }
            } else {
                outgoingFiles.remove(payloadId)
                incomingMetadata.remove(payloadId)
                incomingPayloads.remove(payloadId)?.close()
                completedPayloads.remove(payloadId)?.close()
                successfulIncomingPayloadIds.remove(payloadId)
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
                listener.onFileProgress(endpointId, payloadId, name, 0L, size, true)
                processCompletedFile(payloadId)
                return
            }
            val messageId = envelope.optString("id")
            val body = envelope.optString("body")
            val ttl = envelope.optInt("ttl", 0)
            if (messageId.isBlank() || body.isBlank() || body.length > 4000 || !seenMessageIds.add(messageId)) return
            if (seenMessageIds.size > 5000) seenMessageIds.remove(seenMessageIds.first())
            val senderName = synchronized(names) { names[endpointId] ?: "Nearby member" }
            listener.onMessage(endpointId, senderName, body)
            if (relayEnabled && ttl > 0) {
                val forwarded = JSONObject().put("id", messageId).put("body", body)
                    .put("ttl", ttl - 1).put("hops", envelope.optInt("hops", 0) + 1)
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
        if (!completedPayloads.remove(payloadId, payload)) return
        incomingPayloads.remove(payloadId)
        incomingMetadata.remove(payloadId)
        successfulIncomingPayloadIds.remove(payloadId)
        fileIo.execute {
            var destination: File? = null
            try {
                val folder = File(appContext.filesDir, "received").apply { mkdirs() }
                val target = File(folder, "${System.currentTimeMillis()}-${meta.name}")
                destination = target
                val uri: Uri = payload.asFile()?.asUri() ?: throw IllegalStateException("Nearby file content is unavailable")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > meta.size) throw IllegalStateException("Received file exceeded its declared size")
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                        if (copied != meta.size) throw IllegalStateException("Received file size did not match")
                    }
                } ?: throw IllegalStateException("Could not open the received file")
                appContext.contentResolver.delete(uri, null, null)
                listener.onFileReceived(meta.endpointId, meta.name, target)
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
                    client.sendPayload(endpointId, payload)
                        .addOnFailureListener { error ->
                            outgoingFiles.remove(id)
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
        fileIo.shutdownNow()
    }

}