package com.nearer.app

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

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
    private var activeEndpoint: String? = null
    private var started = false
    private val localName = Build.MODEL.orEmpty().replace("|", " ").take(40).ifBlank { "Android phone" }
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
                activeEndpoint = id
                val name = synchronized(names) { names[id] } ?: "Nearby NeaRer user"
                listener.onConnected(id, name)
            } else listener.onStatus("Nearby connection was not approved. Try again.")
        }
        override fun onDisconnected(id: String) { if (activeEndpoint == id) activeEndpoint = null; listener.onDisconnected(id) }
    }
    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val body = payload.asBytes()?.toString(Charsets.UTF_8).orEmpty()
                if (body.isNotBlank() && body.length <= 4000) listener.onMessage(id, body)
            }
        }
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) = Unit
    }
    fun start() {
        if (started) return
        started = true
        val strategy = Strategy.P2P_CLUSTER
        val advertising = AdvertisingOptions.Builder().setStrategy(strategy).build()
        val discovering = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising(localName, serviceId, lifecycle, advertising).addOnFailureListener {
            listener.onStatus("Nearby advertising could not start: ${it.localizedMessage ?: "check Bluetooth permissions"}")
        }
        client.startDiscovery(serviceId, discovery, discovering).addOnFailureListener {
            listener.onStatus("Nearby discovery could not start: ${it.localizedMessage ?: "check Bluetooth permissions"}")
        }
        listener.onStatus("Searching over Bluetooth and nearby Wi-Fi…")
    }
    fun request(id: String) { client.requestConnection(localName, id, lifecycle).addOnFailureListener { listener.onStatus("Connection request failed: ${it.localizedMessage ?: "try again"}") } }
    fun accept(id: String) { client.acceptConnection(id, payloads).addOnFailureListener { listener.onStatus("Could not accept nearby connection: ${it.localizedMessage ?: "try again"}") } }
    fun reject(id: String) { client.rejectConnection(id).addOnFailureListener { listener.onStatus("Could not reject nearby connection: ${it.localizedMessage ?: "try again"}") } }
    fun send(body: String): Boolean {
        val id = activeEndpoint ?: return false
        return try { client.sendPayload(id, Payload.fromBytes(body.toByteArray(Charsets.UTF_8))); true } catch (_: Exception) { false }
    }
    fun disconnect() { try { client.stopAllEndpoints() } catch (_: Exception) {}; activeEndpoint = null }
    fun stop() { started = false; try { client.stopAdvertising() } catch (_: Exception) {}; try { client.stopDiscovery() } catch (_: Exception) {}; disconnect() }
}