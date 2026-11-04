package com.nearer.app

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NearbyConnectionsTransport(context: Context, private val listener: Listener) {
    interface Listener {
        fun onStatus(message: String)
        fun onFound(id: String, name: String)
        fun onLost(id: String)
        fun onRequest(id: String, name: String, token: String)
        fun onConnected(id: String, name: String)
        fun onDisconnected(id: String)
        fun onMessage(id: String, body: String)
    }
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val serviceId = context.packageName
    private val names = mutableMapOf<String, String>()
    private val activeEndpoints = mutableSetOf<String>()
    private val seenMessageIds = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var relayEnabled = false
    private var started = false
    @Volatile private var localName = "NeaRer User"
    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            synchronized(names) { names[id] = info.endpointName }
            listener.onFound(id, info.endpointName)
        }
        override fun onEndpointLost(id: String) { synchronized(names) { names.remove(id) }; listener.onLost(id) }
    }
    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            synchronized(names) { names[id] = info.endpointName }
            listener.onRequest(id, info.endpointName, info.authenticationToken)
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                synchronized(activeEndpoints) { activeEndpoints.add(id) }
                val name = synchronized(names) { names[id] } ?: "Nearby NeaRer user"
                listener.onConnected(id, name)
            } else listener.onStatus("Nearby connection was not approved. Try again.")
        }
        override fun onDisconnected(id: String) {
            val empty = synchronized(activeEndpoints) { activeEndpoints.remove(id); activeEndpoints.isEmpty() }
            if (empty) listener.onDisconnected(id)
            else listener.onStatus("One nearby person disconnected. Your other direct connections remain active.")
        }
    }
    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            try {
                val envelope = JSONObject(payload.asBytes()?.toString(Charsets.UTF_8).orEmpty())
                val messageId = envelope.optString("id")
                val body = envelope.optString("body")
                val ttl = envelope.optInt("ttl", 0)
                if (messageId.isBlank() || body.isBlank() || body.length > 4000 || !seenMessageIds.add(messageId)) return
                if (seenMessageIds.size > 5000) seenMessageIds.remove(seenMessageIds.first())
                listener.onMessage(id, body)
                if (relayEnabled && ttl > 0) {
                    val forwarded = JSONObject().put("id", messageId).put("body", body)
                        .put("ttl", ttl - 1).put("hops", envelope.optInt("hops", 0) + 1)
                        .put("origin", envelope.optString("origin", "nearby"))
                    broadcast(forwarded.toString(), id)
                }
            } catch (_: Exception) {
                listener.onStatus("A nearby message could not be read and was ignored.")
            }
        }
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) = Unit
    }
    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(localName, serviceId, lifecycle, options).addOnFailureListener {
            listener.onStatus("Nearby advertising could not start: ${it.localizedMessage ?: "check Bluetooth permissions"}")
        }
    }

    fun setDisplayName(name: String) {
        val clean = name.trim().replace("|", " ").take(24).ifBlank { "NeaRer User" }
        if (clean == localName) return
        localName = clean
        if (started) client.stopAdvertising().addOnCompleteListener { startAdvertising() }
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
    fun request(id: String) { client.requestConnection(localName, id, lifecycle).addOnFailureListener { listener.onStatus("Connection request failed: ${it.localizedMessage ?: "try again"}") } }
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

    fun disconnect() { try { client.stopAllEndpoints() } catch (_: Exception) {}; synchronized(activeEndpoints) { activeEndpoints.clear() } }
    fun stop() { stopSearching(); disconnect() }
}