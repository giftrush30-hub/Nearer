package com.nearer.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Explicit Wi-Fi Direct group/discovery coordinator. It does not start until
 * start() is called and never changes the device Wi-Fi toggle or hotspot.
 * The returned group-owner address is used by the separate TCP data engine.
 */
internal class WifiDirectConnectionCoordinator(
    context: Context,
    private val listener: Listener,
    private val callbackHandler: Handler = Handler(Looper.getMainLooper())
) : Closeable, WifiP2pManager.ChannelListener {
    enum class State { IDLE, DISCOVERING, GROUP_CREATING, CONNECTING, CONNECTED, DISCONNECTED, FAILED, CLOSED }
    data class Peer(val name: String, val address: String, val status: Int)
    data class Link(val isGroupOwner: Boolean, val groupOwnerAddress: InetAddress)

    interface Listener {
        fun onStateChanged(state: State, detail: String)
        fun onPeersChanged(peers: List<Peer>)
        fun onLinkReady(link: Link)
    }

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    @Volatile private var channel: WifiP2pManager.Channel? = null
    @Volatile private var state = State.IDLE
    @Volatile private var registered = false
    @Volatile private var closed = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (!enabled) publish(State.DISCONNECTED, "Wi-Fi Direct is disabled on this phone.")
                }
            }
        }
    }

    override fun onChannelDisconnected() {
        publish(State.FAILED, "The Wi-Fi Direct control channel was disconnected. Restart the transfer session.")
    }

    /** Registers for P2P state changes and initializes the Android P2P channel. */
    fun start(): Boolean {
        if (closed) return fail("Coordinator is closed.")
        val missing = missingRuntimePermissions()
        if (missing.isNotEmpty()) return fail("Missing Android permission: ${missing.joinToString()}")
        val p2pManager = manager ?: return fail("This Android device does not expose Wi-Fi Direct.")
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") appContext.registerReceiver(receiver, filter)
            registered = true
        }
        channel = p2pManager.initialize(appContext, callbackHandler.looper, this)
        if (channel == null) return fail("Android could not initialize the Wi-Fi Direct channel.")
        publish(State.IDLE, "Wi-Fi Direct is ready. Choose discovery, create a group, or connect to a discovered peer.")
        return true
    }

    fun discoverPeers() {
        val active = requireReady() ?: return
        publish(State.DISCOVERING, "Searching for nearby Wi-Fi Direct devices…")
        manager?.discoverPeers(active, actionListener("Discovery started.") { "Could not start discovery." })
    }

    /** Call on the receiving phone when the user explicitly chooses to host a group. */
    fun createGroup() {
        val active = requireReady() ?: return
        publish(State.GROUP_CREATING, "Creating a temporary Wi-Fi Direct group…")
        manager?.createGroup(active, actionListener("Group creation requested.") { "Could not create a Wi-Fi Direct group." })
    }

    /** Connects to a peer returned by this coordinator; both users must initiate compatible flows. */
    fun connectToPeer(deviceAddress: String, groupOwnerIntent: Int = 0) {
        val active = requireReady() ?: return
        if (!MAC_ADDRESS.matches(deviceAddress)) { fail("The selected peer address is invalid."); return }
        if (groupOwnerIntent !in 0..15) { fail("Group-owner preference must be between 0 and 15."); return }
        val config = WifiP2pConfig().apply { this.deviceAddress = deviceAddress; this.groupOwnerIntent = groupOwnerIntent }
        publish(State.CONNECTING, "Negotiating a direct Wi-Fi connection…")
        manager?.connect(active, config, actionListener("Connection request sent.") { "Could not connect to the selected peer." })
    }

    fun refreshConnectionInfo() { requestConnectionInfo() }

    /** Group owner only: create a listening socket after onLinkReady reports isGroupOwner=true. */
    @Throws(IOException::class)
    fun openServerSocket(port: Int, backlog: Int = 1): ServerSocket {
        requireConnected()
        require(port in 1..65535)
        require(backlog > 0)
        val socket = ServerSocket()
        try { socket.reuseAddress = false; socket.bind(InetSocketAddress(port), backlog); return socket }
        catch (error: IOException) { try { socket.close() } catch (_: IOException) { }; throw error }
    }

    /** Group client only: connect to the group-owner address supplied by onLinkReady. */
    @Throws(IOException::class)
    fun openClientSocket(link: Link, port: Int, connectTimeoutMillis: Int = 10_000): Socket {
        requireConnected()
        require(!link.isGroupOwner) { "The group owner must accept a server socket." }
        require(port in 1..65535 && connectTimeoutMillis > 0)
        val socket = Socket()
        try { socket.tcpNoDelay = true; socket.connect(InetSocketAddress(link.groupOwnerAddress, port), connectTimeoutMillis); return socket }
        catch (error: IOException) { try { socket.close() } catch (_: IOException) { }; throw error }
    }

    fun stopDiscovery() {
        val active = channel ?: return
        manager?.stopPeerDiscovery(active, actionListener("Discovery stopped.") { "Could not stop discovery." })
    }

    fun removeGroup() {
        val active = channel ?: return
        manager?.removeGroup(active, actionListener("Wi-Fi Direct group removed.") { "Could not remove Wi-Fi Direct group." })
        publish(State.DISCONNECTED, "Wi-Fi Direct group removal requested.")
    }

    override fun close() {
        if (closed) return
        closed = true
        val active = channel
        if (active != null) {
            try { manager?.stopPeerDiscovery(active, null) } catch (_: Exception) { }
            try { manager?.removeGroup(active, null) } catch (_: Exception) { }
        }
        if (registered) { try { appContext.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }; registered = false }
        channel = null
        publish(State.CLOSED, "Wi-Fi Direct session closed.")
    }

    private fun requestPeers() {
        val active = channel ?: return
        try {
            manager?.requestPeers(active) { list ->
                val peers = list.deviceList.map { Peer(it.deviceName.orEmpty(), it.deviceAddress.orEmpty(), it.status) }
                callbackHandler.post { if (!closed) listener.onPeersChanged(peers) }
            }
        } catch (_: SecurityException) { fail("Wi-Fi Direct permission was revoked.") }
    }

    private fun requestConnectionInfo() {
        val active = channel ?: return
        try {
            manager?.requestConnectionInfo(active) { info: WifiP2pInfo ->
                if (closed) return@requestConnectionInfo
                if (info.groupFormed && info.groupOwnerAddress != null) {
                    val link = Link(info.isGroupOwner, info.groupOwnerAddress)
                    publish(State.CONNECTED, if (info.isGroupOwner) "Connected as Wi-Fi Direct group owner." else "Connected to Wi-Fi Direct group owner.")
                    callbackHandler.post { if (!closed) listener.onLinkReady(link) }
                } else if (state == State.CONNECTED) {
                    publish(State.DISCONNECTED, "The Wi-Fi Direct group connection ended.")
                }
            }
        } catch (_: SecurityException) { fail("Wi-Fi Direct permission was revoked.") }
    }

    private fun missingRuntimePermissions(): List<String> {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
        return if (ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED) emptyList() else listOf(permission)
    }

    private fun requireReady(): WifiP2pManager.Channel? {
        if (closed) { fail("Coordinator is closed."); return null }
        if (channel == null && !start()) return null
        return channel
    }

    private fun requireConnected() { check(!closed && state == State.CONNECTED) { "Wi-Fi Direct group is not connected" } }

    private fun actionListener(success: String, failure: () -> String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() { publish(state, success) }
        override fun onFailure(reason: Int) { fail("${failure()} (Android error $reason)") }
    }

    private fun fail(detail: String): Boolean { publish(State.FAILED, detail); return false }
    private fun publish(next: State, detail: String) {
        state = next
        callbackHandler.post { if (!closed || next == State.CLOSED) listener.onStateChanged(next, detail) }
    }

    companion object { private val MAC_ADDRESS = Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$") }
}