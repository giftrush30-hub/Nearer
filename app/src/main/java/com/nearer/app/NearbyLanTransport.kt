package com.nearer.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional cross-platform LAN/hotspot adapter for the iOS Network.framework
 * Bonjour/TCP implementation. This adapter is deliberately separate from the
 * production Nearby Connections service until mixed-device tests pass.
 *
 * The Bonjour service name is a discovery hint, not an authenticated identity.
 * The NeaRer v3 handshake must run before accepting any chat or transfer data.
 */
internal class NearbyLanTransport(
    context: Context,
    private val peerId: String,
    private val listener: Listener
) : Closeable {
    interface Listener {
        fun onPeerFound(endpointId: String, advertisedPeerId: String)
        fun onPeerLost(endpointId: String)
        fun onPeerConnected(endpointId: String)
        fun onFrameReceived(endpointId: String, frame: ByteArray)
        fun onTransportError(message: String)
    }

    private data class PeerConnection(
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val writeLock: Any = Any()
    )

    companion object {
        const val SERVICE_TYPE = "_nearer-v1._tcp."
    }

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val connections = ConcurrentHashMap<String, PeerConnection>()
    private val authenticatedEndpoints = ConcurrentHashMap.newKeySet<String>()
    private val discovered = ConcurrentHashMap<String, NsdServiceInfo>()
    private var serverSocket: ServerSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        check(!closed.get()) { "LAN transport has been closed" }
        if (!started.compareAndSet(false, true)) return
        executor.execute {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(0))
                serverSocket = server
                if (closed.get()) { server.close(); return@execute }
                acquireMulticastLock()
                acceptLoop(server)
            } catch (error: Exception) {
                if (!closed.get()) listener.onTransportError("LAN listener failed: ${error.message}")
            }
        }
        registerBonjourService()
        startBonjourDiscovery()
    }

    fun connect(endpointId: String) {
        val service = discovered[endpointId] ?: return
        val host = service.host ?: return
        val port = service.port
        executor.execute {
            try {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(host, port), 8_000)
                attachConnection(endpointId, socket)
            } catch (error: Exception) {
                if (!closed.get()) listener.onTransportError("LAN connection failed: ${error.message}")
            }
        }
    }

    fun sendFrame(endpointId: String, frame: ByteArray) {
        sendFrame(endpointId, frame) { error ->
            if (error != null && !closed.get()) listener.onTransportError("LAN send failed: ${error.message}")
        }
    }

    /** Completion is invoked only after the bounded frame has been flushed to the socket. */
    fun sendFrame(endpointId: String, frame: ByteArray, completion: (Throwable?) -> Unit) {
        runCatching { NearbyLanFrameCodec.validate(frame) }.onFailure {
            completion(it)
            return
        }
        val connection = connections[endpointId]
        if (connection == null) {
            completion(IOException("Peer connection is unavailable"))
            return
        }
        executor.execute {
            try {
                synchronized(connection.writeLock) {
                    NearbyLanFrameCodec.write(connection.output, frame)
                }
                completion(null)
            } catch (error: Exception) {
                removeConnection(endpointId)
                completion(error)
            }
        }
    }

    /** Set only after the authenticated NeaRer v3 peer handshake and trust approval. */
    fun setPeerAuthenticated(endpointId: String, authenticated: Boolean) {
        if (authenticated) authenticatedEndpoints.add(endpointId)
        else authenticatedEndpoints.remove(endpointId)
    }

    fun sendFile(
        endpointId: String,
        source: java.io.File,
        metadata: TransferResumeStreamMetadataCodec.Metadata,
        ackWaiter: NearbyLanAckWaiter,
        completion: (Result<Unit>) -> Unit
    ) {
        if (!connections.containsKey(endpointId)) {
            completion(Result.failure(IOException("Peer connection is unavailable")))
            return
        }
        if (!authenticatedEndpoints.contains(endpointId)) {
            completion(Result.failure(SecurityException("Peer has not been authenticated")))
            return
        }
        NearbyLanFileSender.send(
            source,
            metadata,
            NearbyLanFrameSink { frame, callback -> sendFrame(endpointId, frame, callback) },
            ackWaiter,
            completion
        )
    }

    private fun registerBonjourService() {
        // Registration waits for the listener's ephemeral port to be ready.
        executor.execute {
            repeat(100) {
                if (closed.get()) return@execute
                val ready = serverSocket
                if (ready != null && ready.isBound) {
                    val info = NsdServiceInfo().apply {
                        serviceName = peerId
                        serviceType = SERVICE_TYPE
                        port = ready.localPort
                    }
                    val callback = object : NsdManager.RegistrationListener {
                        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
                        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (!closed.get()) listener.onTransportError("Bonjour registration failed: $errorCode")
                        }
                        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (!closed.get()) listener.onTransportError("Bonjour unregistration failed: $errorCode")
                        }
                    }
                    registrationListener = callback
                    runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, callback) }
                        .onFailure { listener.onTransportError("Bonjour registration error: ${it.message}") }
                    return@execute
                }
                Thread.sleep(50)
            }
            if (!closed.get()) listener.onTransportError("LAN listener did not become ready")
        }
    }

    private fun startBonjourDiscovery() {
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName == peerId || closed.get()) return
                if (NeaRerIdentity.normalize(serviceInfo.serviceName) != serviceInfo.serviceName) return
                runCatching {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            if (!closed.get()) listener.onTransportError("Bonjour resolve failed: $errorCode")
                        }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val endpointId = "lan:${info.host?.hostAddress}:${info.port}"
                            discovered[endpointId] = info
                            listener.onPeerFound(endpointId, info.serviceName)
                        }
                    })
                }.onFailure {
                    if (!closed.get()) listener.onTransportError("Bonjour resolve error: ${it.message}")
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val endpointId = "lan:$host:${serviceInfo.port}"
                discovered.remove(endpointId)
                listener.onPeerLost(endpointId)
                removeConnection(endpointId)
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!closed.get()) listener.onTransportError("Bonjour discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!closed.get()) listener.onTransportError("Bonjour stop failed: $errorCode")
            }
        }
        discoveryListener = callback
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, callback) }
            .onFailure { listener.onTransportError("Bonjour discovery error: ${it.message}") }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!closed.get() && !server.isClosed) {
            try {
                val socket = server.accept()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                val endpointId = "inbound:${UUID.randomUUID()}"
                attachConnection(endpointId, socket)
            } catch (error: IOException) {
                if (!closed.get()) listener.onTransportError("LAN accept failed: ${error.message}")
                return
            }
        }
    }

    private fun attachConnection(endpointId: String, socket: Socket) {
        val connection = PeerConnection(
            socket,
            DataInputStream(BufferedInputStream(socket.getInputStream())),
            DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        )
        connections.put(endpointId, connection)?.socket?.close()
        listener.onPeerConnected(endpointId)
        executor.execute {
            try {
                while (!closed.get()) {
                    val frame = NearbyLanFrameCodec.read(connection.input)
                    listener.onFrameReceived(endpointId, frame)
                }
            } catch (_: EOFException) {
                // Normal peer disconnect.
            } catch (error: Exception) {
                if (!closed.get()) listener.onTransportError("LAN receive failed: ${error.message}")
            } finally {
                removeConnection(endpointId)
            }
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifi.createMulticastLock("nearer-bonjour").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun removeConnection(endpointId: String) {
        authenticatedEndpoints.remove(endpointId)
        connections.remove(endpointId)?.let { runCatching { it.socket.close() } }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registrationListener?.let { runCatching { nsd.unregisterService(it) } }
        runCatching { serverSocket?.close() }
        connections.keys.toList().forEach(::removeConnection)
        authenticatedEndpoints.clear()
        discovered.clear()
        multicastLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        multicastLock = null
        executor.shutdownNow()
    }
}
