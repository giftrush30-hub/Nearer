package com.nearer.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.bluetooth.BluetoothManager
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Handler
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

private val Midnight = Color(0xFF101A2B)
private val Panel = Color(0xFF1A2940)
private val Mint = Color(0xFF69E0C0)
private val Muted = Color(0xFFA8B6C9)
private val White = Color(0xFFF6F8FC)
private val BrandBlue = Color(0xFF2563EB)
private val Gold = Color(0xFFFFD12D)

class MainActivity : ComponentActivity() {
    private var wifiManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var scanning by mutableStateOf(false)
    private var nearbyTransport: NearbyConnectionsTransport? = null
    private val hybridPeers = mutableStateMapOf<String, String>()
    private val recentlySeenPeers = mutableStateMapOf<String, NearbyPerson>()
    private var displayName by mutableStateOf("NeaRer User")
    private var hybridSearching by mutableStateOf(false)
    private var pendingHybridEndpoint: String? = null
    private var hybridConnected = false
    private var hybridConnectedEndpoint: String? = null
    private var meshRelayEnabled by mutableStateOf(false)
    private val discoveryHandler = Handler(Looper.getMainLooper())
    private var discoveryTimeout: Runnable? = null
    private var discoveryGeneration = 0
    private var connectingAddress by mutableStateOf<String?>(null)
    private var connectedDeviceName by mutableStateOf<String?>(null)
    private var connectionMessage by mutableStateOf("Not connected")
    private var incomingRequesterName by mutableStateOf<String?>(null)
    private var approvalMessage by mutableStateOf("Waiting for app approval")
    private var pairingCode by mutableStateOf<String?>(null)
    private var chatReady by mutableStateOf(false)
    private var transportStarted = false
    private var serverSocket: ServerSocket? = null
    private var peerSocket: Socket? = null
    private var peerWriter: PrintWriter? = null
    private val writerLock = Any()
    private val receivedMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val io = Executors.newCachedThreadPool()
    private val approvalDecision = LinkedBlockingQueue<Boolean>()
    private val chatMessages = mutableStateListOf<ChatMessage>()
    private val receivedFiles = mutableStateListOf<ReceivedFile>()
    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()
    private val cancelledOutgoingTransfers = ConcurrentHashMap.newKeySet<String>()
    private var outgoingTransferId by mutableStateOf<String?>(null)
    private var outgoingProgress by mutableStateOf(0f)
    private var incomingTransferName by mutableStateOf<String?>(null)
    private var incomingProgress by mutableStateOf(0f)
    private val maxTransferBytes = 200L * 1024L * 1024L
    private var chatNotice by mutableStateOf("Messages are sent directly between connected phones.")
    private var statusMessage by mutableStateOf("Ready to find nearby Android phones.")
    private val nearbyDevices = mutableStateListOf<WifiP2pDevice>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        scanning = false
                        statusMessage = "Turn on Wi-Fi to discover nearby phones."
                    } else {
                        statusMessage = "Wi-Fi Direct is ready. Tap Find people nearby."
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (hasDiscoveryPermission()) {
                        wifiManager?.requestPeers(channel) { peers ->
                            nearbyDevices.clear()
                            nearbyDevices.addAll(peers.deviceList)
                            finishDiscovery()
                            statusMessage = if (nearbyDevices.isEmpty()) {
                                "No nearby Wi-Fi Direct devices found yet. Keep both phones nearby and scan again."
                            } else {
                                "${nearbyDevices.size} nearby device(s) found. Select one to request a connection."
                            }
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                        scanning = true
                        statusMessage = "Searching nearby…"
                    } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED && scanning) {
                        if (hasDiscoveryPermission()) {
                            wifiManager?.requestPeers(channel) { peers ->
                                nearbyDevices.clear()
                                nearbyDevices.addAll(peers.deviceList)
                                finishDiscovery()
                                if (nearbyDevices.isEmpty() && hybridPeers.isEmpty()) {
                                    statusMessage = "No nearby phones found yet. Keep Bluetooth and Wi-Fi ON on both phones, then search again."
                                } else {
                                    statusMessage = "Nearby search finished. Select a phone below to connect."
                                }
                            }
                        } else {
                            finishDiscovery()
                            statusMessage = "Nearby search finished. Check the Nearby devices permission if no phones appear."
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (hasDiscoveryPermission()) {
                        wifiManager?.requestConnectionInfo(channel) { info ->
                            updateConnectionState(info)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 26, 43)
        window.navigationBarColor = android.graphics.Color.rgb(16, 26, 43)
        window.decorView.systemUiVisibility = 0
        wifiManager = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = wifiManager?.initialize(this, mainLooper, null)
        nearbyTransport = NearbyConnectionsTransport(this, object : NearbyConnectionsTransport.Listener {
            override fun onStatus(message: String) { runOnUiThread { statusMessage = message } }
            override fun onFound(id: String, name: String, stableId: String) { runOnUiThread { hybridPeers[id] = name; if (stableId.isNotBlank()) recentlySeenPeers.remove(stableId) } }
            override fun onLost(id: String, stableId: String) { runOnUiThread {
                val name = hybridPeers.remove(id)
                if (name != null) recordRecentlySeen(stableId.ifBlank { id }, name)
            } }
            override fun onRequest(id: String, name: String, token: String) {
                runOnUiThread {
                    pendingHybridEndpoint = id
                    incomingRequesterName = name
                    pairingCode = token
                    approvalMessage = "Compare the security code on both phones before accepting"
                    statusMessage = "$name is requesting a nearby connection."
                }
            }
            override fun onConnected(id: String, name: String, stableId: String) {
                runOnUiThread {
                    hybridConnected = true
                    hybridConnectedEndpoint = id
                    pendingHybridEndpoint = null
                    incomingRequesterName = null
                    connectedDeviceName = name
                    chatReady = true
                    connectionMessage = "Secure nearby connection active"
                    approvalMessage = "Connection approved"
                    chatNotice = "Connected to $name. Offline chat is ready."
                    statusMessage = "Connected to $name using Bluetooth / nearby Wi-Fi."
                }
            }
            override fun onDisconnected(id: String, stableId: String, name: String) {
                runOnUiThread {
                    hybridConnected = false
                    hybridConnectedEndpoint = null
                    val lastName = hybridPeers.remove(id) ?: name
                    recordRecentlySeen(stableId.ifBlank { id }, lastName)
                    pendingHybridEndpoint = null
                    connectedDeviceName = null
                    chatReady = false
                    connectionMessage = "Not connected"
                    chatNotice = "Nearby connection ended. Search again to reconnect."
                }
            }
            override fun onMessage(id: String, body: String) {
                runOnUiThread {
                    chatMessages.add(ChatMessage("hybrid-" + System.currentTimeMillis(), body, outgoing = false, delivery = "Received"))
                    persistMessages()
                    chatNotice = "New offline message received."
                }
            }
            override fun onFileProgress(id: String, payloadId: Long, name: String, transferred: Long, total: Long, incoming: Boolean) {
                runOnUiThread {
                    val fraction = if (total > 0L) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    if (incoming) {
                        incomingTransferName = name
                        incomingProgress = fraction
                        chatNotice = if (total > 0L) "Receiving $name • ${(fraction * 100).toInt()}%" else "Receiving $name…"
                    } else {
                        outgoingTransferId = "nearby:$payloadId"
                        outgoingProgress = fraction
                        chatNotice = if (total > 0L) "Sending $name • ${(fraction * 100).toInt()}%" else "Sending $name…"
                    }
                }
            }
            override fun onFileReceived(id: String, name: String, file: File) {
                runOnUiThread {
                    incomingTransferName = null
                    incomingProgress = 0f
                    receivedFiles.add(0, ReceivedFile(name, file))
                    chatNotice = "Received and saved: $name. Tap it below to open."
                }
            }
            override fun onFileStatus(message: String) {
                runOnUiThread {
                    chatNotice = message
                    if (message.startsWith("File sent to the nearby phone") ||
                        message.startsWith("Nearby file transfer failed") ||
                        message.startsWith("Could not start file transfer") ||
                        message.startsWith("Could not send file details") ||
                        message.startsWith("File could not be prepared")) {
                        outgoingTransferId = null
                        outgoingProgress = 0f
                    }
                    if (message.startsWith("Nearby file transfer failed")) {
                        incomingTransferName = null
                        incomingProgress = 0f
                    }
                }
            }
        })
        if (wifiManager == null) statusMessage = "This phone does not provide Wi-Fi Direct support."
        restoreLocalData()
        displayName = getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getString("display_name", "NeaRer User")?.take(24)?.ifBlank { "NeaRer User" } ?: "NeaRer User"
        nearbyTransport?.setDisplayName(displayName)
        restoreRecentlySeen()
        meshRelayEnabled = getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getBoolean("mesh_relay_enabled", false)
        nearbyTransport?.setRelayEnabled(meshRelayEnabled)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Midnight,
                surface = Panel,
                primary = Mint,
                onPrimary = Midnight,
                onBackground = White,
                onSurface = White
            )) {
                NeaRerHome(
                    status = statusMessage,
                    scanning = scanning || hybridSearching,
                    devices = nearbyDevices,
                    connectingAddress = connectingAddress,
                    connectedDeviceName = connectedDeviceName,
                    connectionMessage = connectionMessage,
                    incomingRequesterName = incomingRequesterName,
                    approvalMessage = approvalMessage,
                    pairingCode = pairingCode,
                    chatReady = chatReady,
                    usingNearbyConnections = hybridConnected,
                    messages = chatMessages,
                    chatNotice = chatNotice,
                    receivedFiles = receivedFiles,
                    onSendMessage = ::sendChatMessage,
                    onSendFile = ::sendFile,
                    onCancelTransfer = ::cancelOutgoingFileTransfer,
                    outgoingTransferId = outgoingTransferId,
                    outgoingProgress = outgoingProgress,
                    incomingTransferName = incomingTransferName,
                    incomingProgress = incomingProgress,
                    onOpenFile = ::openReceivedFile,
                    onDisconnect = ::disconnectFromPeer,
                    onAccept = ::approveIncoming,
                    onReject = ::rejectIncoming,
                    hybridPeers = hybridPeers.toMap(),
                    recentlySeenPeers = recentlySeenPeers.toMap(),
                    displayName = displayName,
                    onSaveDisplayName = ::saveDisplayName,
                    meshRelayEnabled = meshRelayEnabled,
                    onMeshRelayChange = ::setMeshRelayEnabled,
                    onHybridConnect = { id -> nearbyTransport?.request(id) },
                    onFindNearby = ::startDiscovery,
                    onStopSearch = ::stopDiscovery,
                    onConnect = ::connectToDevice
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        }
    }

    override fun onDestroy() {
        finishDiscovery()
        nearbyTransport?.stop()
        closePeerTransport()
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun hasDiscoveryPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isWifiEnabled(): Boolean {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifi?.isWifiEnabled == true
    }

    private fun isLocationEnabled(): Boolean {
        val location = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            location.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            location.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun finishDiscovery() {
        discoveryGeneration += 1
        discoveryTimeout?.let(discoveryHandler::removeCallbacks)
        discoveryTimeout = null
        scanning = false
    }

    private fun stopDiscovery() {
        finishDiscovery()
        hybridSearching = false
        nearbyTransport?.stopSearching()
        val manager = wifiManager
        val activeChannel = channel
        if (manager != null && activeChannel != null) {
            try {
                manager.stopPeerDiscovery(activeChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { statusMessage = "Search stopped. You can search again whenever you are ready." }
                    override fun onFailure(reason: Int) { statusMessage = "Search stopped. Android reported code $reason." }
                })
            } catch (_: SecurityException) { statusMessage = "Search stopped. Check nearby-device permission before searching again." }
        } else statusMessage = "Search stopped."
    }

    private fun setMeshRelayEnabled(enabled: Boolean) {
        meshRelayEnabled = enabled
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit().putBoolean("mesh_relay_enabled", enabled).apply()
        nearbyTransport?.setRelayEnabled(enabled)
        statusMessage = if (enabled) "Community relay is ON. Your phone may forward nearby NeaRer text messages while connected." else "Community relay is OFF. Your phone only receives messages from its direct connections."
    }

    private fun saveDisplayName(rawName: String) {
        val cleanName = rawName.trim().replace(Regex("[\\r\\n|]"), " ").replace(Regex("\\s+"), " ").take(24)
        if (cleanName.isBlank()) { statusMessage = "Choose a name so nearby people know what to call you."; return }
        displayName = cleanName
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit().putString("display_name", cleanName).apply()
        nearbyTransport?.setDisplayName(cleanName)
        statusMessage = "Your nearby name is now $cleanName."
    }

    private fun recordRecentlySeen(id: String, name: String) {
        val person = NearbyPerson(name, System.currentTimeMillis())
        recentlySeenPeers[id] = person
        val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val saved = org.json.JSONArray(prefs.getString("recently_seen", "[]"))
        val updated = org.json.JSONArray()
        for (index in 0 until saved.length()) {
            val item = saved.optJSONObject(index) ?: continue
            if (item.optString("id") != id) updated.put(item)
        }
        updated.put(org.json.JSONObject().put("id", id).put("name", name).put("time", person.lastSeenAt))
        val trimmed = org.json.JSONArray()
        for (index in (updated.length() - 30).coerceAtLeast(0) until updated.length()) trimmed.put(updated.get(index))
        prefs.edit().putString("recently_seen", trimmed.toString()).apply()
    }

    private fun restoreRecentlySeen() {
        try {
            val array = org.json.JSONArray(getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getString("recently_seen", "[]"))
            val now = System.currentTimeMillis()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id"); val name = item.optString("name"); val time = item.optLong("time")
                if (id.isNotBlank() && name.isNotBlank() && now - time < 7L * 24L * 60L * 60L * 1000L) recentlySeenPeers[id] = NearbyPerson(name, time)
            }
        } catch (_: Exception) { }
    }

    private fun nearbyPermissionList(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        else permissions += Manifest.permission.ACCESS_FINE_LOCATION
        return permissions.distinct().toTypedArray()
    }

    private fun hasNearbyPermissions(): Boolean =
        nearbyPermissionList().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun startDiscovery() {
        if (!hasNearbyPermissions()) {
            ActivityCompat.requestPermissions(this, nearbyPermissionList(), REQUEST_DISCOVERY_PERMISSION)
            statusMessage = "Allow Nearby devices and Bluetooth permissions so NeaRer can find phones without internet."
            return
        }
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothOn = try { bluetoothManager?.adapter?.isEnabled == true } catch (_: SecurityException) { false }
        if (!bluetoothOn) {
            finishDiscovery()
            statusMessage = "Turn Bluetooth ON on both phones. Wi-Fi ON also helps with faster connections; hotspot and mobile data are not required."
            return
        }
        nearbyTransport?.start()
        hybridSearching = true
        if (!isWifiEnabled()) {
            finishDiscovery()
            statusMessage = "Bluetooth discovery is active. Turn Wi-Fi ON on both phones for faster connections and file sharing. Hotspot and mobile data are not required."
            return
        }
        if (!hasDiscoveryPermission()) {
            statusMessage = "Bluetooth discovery is running. Allow Nearby devices permission to add Wi-Fi Direct discovery."
            return
        }
        val manager = wifiManager ?: run { finishDiscovery(); statusMessage = "This phone does not provide Wi-Fi Direct."; return }
        val activeChannel = channel ?: run { finishDiscovery(); statusMessage = "Bluetooth discovery is active, but Wi-Fi Direct is unavailable."; return }
        if (!isLocationEnabled()) {
            finishDiscovery()
            statusMessage = "Bluetooth discovery is active. Turn Location ON if you also want Wi-Fi Direct discovery on this Android version."
            return
        }
        finishDiscovery()
        val generation = discoveryGeneration
        scanning = true
        statusMessage = "Looking for nearby phones. Keep NeaRer open on both devices…"
        val timeout = Runnable {
            if (generation == discoveryGeneration && scanning) {
                try {
                    manager.requestPeers(activeChannel) { peers ->
                        if (generation != discoveryGeneration) return@requestPeers
                        nearbyDevices.clear()
                        nearbyDevices.addAll(peers.deviceList)
                        finishDiscovery()
                        statusMessage = if (nearbyDevices.isEmpty()) "No Wi-Fi Direct phones found in 20 seconds. Check Wi-Fi on both phones, Location for Wi-Fi Direct, and keep NeaRer open." else "${nearbyDevices.size} nearby phone(s) found. Choose one to connect."
                    }
                } catch (_: SecurityException) {
                    finishDiscovery()
                    statusMessage = "Android blocked nearby discovery. Check NeaRer's Nearby devices permission."
                }
            }
        }
        discoveryTimeout = timeout
        discoveryHandler.postDelayed(timeout, 20_000L)
        try {
            manager.discoverPeers(activeChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (generation == discoveryGeneration) statusMessage = "Search is active. Waiting for nearby phones…"
                }
                override fun onFailure(reason: Int) {
                    if (generation != discoveryGeneration) return
                    finishDiscovery()
                    statusMessage = when (reason) {
                        WifiP2pManager.BUSY -> "Android Wi-Fi is busy. Wait a few seconds and try again."
                        WifiP2pManager.ERROR -> "Android could not start nearby search. Check Wi-Fi and Location settings."
                        WifiP2pManager.P2P_UNSUPPORTED -> "This phone does not support Wi-Fi Direct."
                        else -> "Nearby search could not start (code $reason). Check phone settings and retry."
                    }
                }
            })
        } catch (_: SecurityException) {
            finishDiscovery()
            statusMessage = "Android denied nearby-device access. Allow Nearby devices in app permissions."
        }
    }
    private fun connectToDevice(device: WifiP2pDevice) {
        if (!hasDiscoveryPermission()) {
            statusMessage = "Nearby-device permission is needed before connecting."
            startDiscovery()
            return
        }
        val manager = wifiManager ?: run {
            statusMessage = "This phone does not provide Wi-Fi Direct support."
            return
        }
        val activeChannel = channel ?: run {
            statusMessage = "The nearby connection service is not ready. Please reopen NeaRer."
            return
        }
        if (device.status == WifiP2pDevice.CONNECTED) {
            statusMessage = "This device already reports a Wi-Fi Direct connection."
            return
        }

        connectingAddress = device.deviceAddress
        statusMessage = "Sending a connection invitation to ${device.deviceName.ifBlank { "nearby device" }}…"
        connectionMessage = "Waiting for the other phone to approve"

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        manager.connect(activeChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                statusMessage = "Invitation sent. The other phone may need to accept the Wi-Fi Direct prompt."
            }

            override fun onFailure(reason: Int) {
                connectingAddress = null
                connectionMessage = "Not connected"
                statusMessage = when (reason) {
                    WifiP2pManager.BUSY -> "Wi-Fi is busy. Wait a moment, then try again."
                    WifiP2pManager.P2P_UNSUPPORTED -> "This phone does not support Wi-Fi Direct."
                    else -> "Could not send the connection request (code $reason). Try again."
                }
            }
        })
    }

    private fun updateConnectionState(info: WifiP2pInfo) {
        if (info.groupFormed) {
            val active = nearbyDevices.firstOrNull { it.deviceAddress == connectingAddress }
            connectedDeviceName = active?.deviceName?.ifBlank { null } ?: "Nearby device"
            connectingAddress = null
            connectionMessage = if (info.isGroupOwner) {
                "Connected • this phone hosts the local link"
            } else {
                "Connected • secure local link established"
            }
            statusMessage = "Direct link formed. Checking whether the other phone has NeaRer…"
            if (!transportStarted) {
                transportStarted = true
                if (info.isGroupOwner) startApprovalServer(info) else connectToApprovalServer(info)
            }
        } else {
            connectedDeviceName = null
            connectingAddress = null
            connectionMessage = "Not connected"
            chatReady = false
            incomingRequesterName = null
            transportStarted = false
            closePeerTransport()
            statusMessage = "The Wi-Fi Direct connection is not active. You can scan and connect again."
        }
    }

    private fun startApprovalServer(info: WifiP2pInfo) {
        io.execute {
            try {
                // Listen only on the Wi-Fi Direct group-owner interface, not every network interface.
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(info.groupOwnerAddress, 8988), 1)
                }
                serverSocket = server
                val socket = server.accept()
                peerSocket = socket
                socket.soTimeout = 60_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)
                peerWriter = writer
                val hello = reader.readLine().orEmpty()
                if (!hello.startsWith("NEARER_HELLO|")) {
                    writer.println("REJECT")
                    runOnUiThread { statusMessage = "A device connected, but it did not identify as NeaRer." }
                    closePeerTransport()
                    return@execute
                }
                val name = hello.substringAfter("|").take(40).ifBlank { "Nearby NeaRer user" }
                val code = (100000 + SecureRandom().nextInt(900000)).toString()
                writer.println("PAIR|$code")
                runOnUiThread {
                    pairingCode = code
                    incomingRequesterName = name
                    approvalMessage = "Compare the six-digit code before accepting"
                    statusMessage = "$name wants to connect on NeaRer."
                }
                val accepted = approvalDecision.poll(60, java.util.concurrent.TimeUnit.SECONDS) ?: false
                writer.println(if (accepted) "ACCEPT" else "REJECT")
                if (accepted) {
                    runOnUiThread {
                        chatReady = true
                        incomingRequesterName = null
                        approvalMessage = "Connection approved. Private chat can now be opened."
                        connectionMessage = "NeaRer connection approved"
                        chatNotice = "Connected directly to $name. Say hello."
                        statusMessage = "You approved $name. Private chat is ready."
                    }
                    startMessageReader(socket)
                } else {
                    runOnUiThread {
                        incomingRequesterName = null
                        pairingCode = null
                        approvalMessage = "Request declined or expired"
                        statusMessage = "The NeaRer connection request was declined or expired."
                    }
                    transportStarted = false
                    closePeerTransport()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    incomingRequesterName = null
                    statusMessage = "Could not complete the NeaRer approval handshake. Keep both apps open and retry."
                }
                closePeerTransport()
            }
        }
    }

    private fun connectToApprovalServer(info: WifiP2pInfo) {
        io.execute {
            try {
                var connectedSocket: Socket? = null
                var lastError: Exception? = null
                for (attempt in 0 until 5) {
                    val candidate = Socket()
                    try {
                        candidate.connect(InetSocketAddress(info.groupOwnerAddress, 8988), 8_000)
                        connectedSocket = candidate
                        break
                    } catch (e: Exception) {
                        lastError = e
                        try { candidate.close() } catch (_: Exception) { }
                        Thread.sleep(350L * (attempt + 1))
                    }
                }
                val socket = connectedSocket ?: throw (lastError ?: IllegalStateException("Could not connect to the NeaRer host"))
                peerSocket = socket
                socket.soTimeout = 65_000
                val writer = PrintWriter(socket.getOutputStream(), true)
                peerWriter = writer
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val localName = Build.MODEL.orEmpty().replace("|", " ").take(40).ifBlank { "Android phone" }
                writer.println("NEARER_HELLO|$localName")
                val pairingLine = reader.readLine().orEmpty()
                if (!pairingLine.startsWith("PAIR|")) throw IllegalStateException("The other phone did not provide a pairing code")
                val code = pairingLine.substringAfter("PAIR|").take(6)
                runOnUiThread {
                    pairingCode = code
                    statusMessage = "Compare code $code with the other phone before approving."
                }
                when (reader.readLine()) {
                    "ACCEPT" -> {
                        runOnUiThread {
                            chatReady = true
                            approvalMessage = "Your connection request was approved."
                            connectionMessage = "NeaRer connection approved"
                            chatNotice = "Connected directly to the other phone. Say hello."
                            statusMessage = "Connection approved. Private chat is ready."
                        }
                        startMessageReader(socket)
                    }
                    else -> {
                        runOnUiThread {
                            chatReady = false
                            pairingCode = null
                            approvalMessage = "Request declined or expired"
                            statusMessage = "The other person declined the request or it expired."
                            transportStarted = false
                        }
                        closePeerTransport()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    chatReady = false
                    statusMessage = "The other phone did not complete NeaRer approval. Both phones must have NeaRer open."
                }
                closePeerTransport()
            }
        }
    }

    private fun disconnectFromPeer() {
        nearbyTransport?.disconnect()
        hybridConnected = false
        val activeChannel = channel
        transportStarted = false
        closePeerTransport()
        connectedDeviceName = null
        connectingAddress = null
        pairingCode = null
        connectionMessage = "Disconnecting…"
        statusMessage = "Closing the nearby NeaRer connection…"
        val manager = wifiManager
        if (activeChannel != null && manager != null) {
            manager.removeGroup(activeChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    connectionMessage = "Not connected"
                    statusMessage = "Disconnected. You can find nearby people again."
                }
                override fun onFailure(reason: Int) {
                    connectionMessage = "Connection may still be active"
                    statusMessage = "Android could not close the Wi-Fi Direct group (code $reason)."
                }
            })
        } else {
            connectionMessage = "Wi-Fi Direct is unavailable"
            statusMessage = "This phone does not support the current NeaRer connection method."
        }
    }

    private fun approveIncoming() {
        val endpoint = pendingHybridEndpoint
        if (endpoint != null) {
            nearbyTransport?.accept(endpoint)
            approvalMessage = "Accepting secure nearby connection…"
        } else {
            approvalDecision.offer(true)
            approvalMessage = "Approving connection…"
        }
        incomingRequesterName = null
    }

    private fun rejectIncoming() {
        val endpoint = pendingHybridEndpoint
        if (endpoint != null) {
            nearbyTransport?.reject(endpoint)
            pendingHybridEndpoint = null
            pairingCode = null
            approvalMessage = "Nearby connection declined"
        } else {
            approvalDecision.offer(false)
            approvalMessage = "Declining connection…"
        }
        incomingRequesterName = null
    }

    private fun closePeerTransport() {
        try { peerSocket?.close() } catch (_: Exception) { }
        try { serverSocket?.close() } catch (_: Exception) { }
        peerSocket = null
        peerWriter = null
        serverSocket = null
        incomingTransfers.values.forEach {
            it.close()
            it.file.delete()
        }
        incomingTransfers.clear()
        cancelledOutgoingTransfers.clear()
        val resetUi = {
            incomingTransferName = null
            incomingProgress = 0f
            outgoingTransferId = null
            outgoingProgress = 0f
            chatReady = false
        }
        if (Looper.myLooper() == Looper.getMainLooper()) resetUi() else runOnUiThread { resetUi() }
    }

    private fun restoreLocalData() {
        val stored = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            .getString("messages", null)
        if (!stored.isNullOrBlank()) {
            try {
                val array = JSONArray(stored)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val message = ChatMessage(
                        id = item.getString("id"),
                        body = item.getString("body"),
                        outgoing = item.getBoolean("outgoing"),
                        delivery = item.optString("delivery", "Sent")
                    )
                    chatMessages.add(message)
                    if (!message.outgoing) receivedMessageIds.add(message.id)
                }
            } catch (_: Exception) {
                chatMessages.clear()
            }
        }
        val folder = File(filesDir, "received")
        if (folder.exists()) {
            folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach {
                receivedFiles.add(ReceivedFile(it.name.substringAfter('-', it.name), it))
            }
        }
    }

    private fun persistMessages() {
        try {
            val array = JSONArray()
            chatMessages.takeLast(500).forEach { message ->
                array.put(JSONObject()
                    .put("id", message.id)
                    .put("body", message.body)
                    .put("outgoing", message.outgoing)
                    .put("delivery", message.delivery))
            }
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .edit().putString("messages", array.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sendWireLine(line: String): Boolean {
        val writer = peerWriter ?: return false
        synchronized(writerLock) {
            writer.println(line)
            return !writer.checkError()
        }
    }

    private fun sendChatMessage(rawText: String) {
        val body = rawText.trim()
        if (body.isEmpty() || !chatReady) return
        val id = UUID.randomUUID().toString()
        if (hybridConnected) {
            if (nearbyTransport?.send(body) != true) {
                chatNotice = "Message could not be sent. Check the nearby connection."
                return
            }
            chatMessages.add(ChatMessage(id, body, outgoing = true, delivery = "Sent"))
            persistMessages()
            chatNotice = "Sent securely over the offline nearby connection."
            return
        }
        val encoded = Base64.getEncoder().encodeToString(body.toByteArray(Charsets.UTF_8))
        if (!sendWireLine("MSG|$id|$encoded")) {
            chatNotice = "Message could not be sent. Check the nearby connection."
            return
        }
        chatMessages.add(ChatMessage(id, body, outgoing = true, delivery = "Sent"))
        persistMessages()
        chatNotice = "Sent directly • waiting for delivery confirmation"
    }

    private fun openReceivedFile(item: ReceivedFile) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.files", item.file)
            val extension = item.file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            chatNotice = "No app is available to open this file, or the file could not be accessed."
        }
    }

    private fun cancelOutgoingFileTransfer() {
        val id = outgoingTransferId ?: return
        if (id.startsWith("nearby:")) {
            val payloadId = id.substringAfter("nearby:").toLongOrNull()
            val endpoint = hybridConnectedEndpoint
            if (payloadId != null && endpoint != null) nearbyTransport?.cancelFile(payloadId)
            outgoingTransferId = null
            outgoingProgress = 0f
            chatNotice = "Cancelling nearby file transfer…"
            return
        }
        cancelledOutgoingTransfers.add(id)
        sendWireLine("FILE_CANCEL|$id")
        chatNotice = "Cancelling file transfer…"
    }

    private fun sendFile(uri: Uri) {
        if (hybridConnected) {
            val endpoint = hybridConnectedEndpoint
            if (endpoint == null) {
                chatNotice = "The nearby connection is not ready. Reconnect and try again."
                return
            }
            if (outgoingTransferId != null) {
                chatNotice = "Finish or cancel the current file transfer first."
                return
            }
            var fileName = "shared-file"
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) fileName = cursor.getString(index) ?: fileName
                    }
                }
            } catch (_: Exception) { }
            val payloadId = nearbyTransport?.sendFile(endpoint, uri, fileName)
            if (payloadId != null) {
                outgoingTransferId = "nearby:$payloadId"
                outgoingProgress = 0f
                chatNotice = "Preparing to send $fileName over Nearby Connections…"
            }
            return
        }
        if (!chatReady) return
        if (outgoingTransferId != null) {
            chatNotice = "Finish or cancel the current file transfer first."
            return
        }
        val transferId = UUID.randomUUID().toString()
        outgoingTransferId = transferId
        outgoingProgress = 0f
        io.execute {
            var fileName = "shared-file"
            var fileSize = 0L
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
                    }
                }
                if (fileSize > maxTransferBytes) throw IllegalStateException("Files must be 200 MB or smaller")
                val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open selected file")
                input.use { stream ->
                    val id = transferId
                    val encodedName = Base64.getEncoder().encodeToString(fileName.toByteArray(Charsets.UTF_8))
                    if (!sendWireLine("FILE_START|$id|$encodedName|$fileSize")) throw IllegalStateException("Connection unavailable")
                    runOnUiThread { chatNotice = "Sending $fileName…" }
                    val buffer = ByteArray(12 * 1024)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var sentBytes = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (cancelledOutgoingTransfers.contains(id)) {
                            sendWireLine("FILE_CANCEL|$id")
                            throw IllegalStateException("Transfer cancelled")
                        }
                        sentBytes += count
                        if (sentBytes > maxTransferBytes) throw IllegalStateException("Files must be 200 MB or smaller")
                        digest.update(buffer, 0, count)
                        val chunk = Base64.getEncoder().encodeToString(buffer.copyOf(count))
                        if (!sendWireLine("FILE_CHUNK|$id|0|$chunk")) throw IllegalStateException("Connection lost during transfer")
                        val fraction = if (fileSize > 0L) (sentBytes.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f
                        if (sentBytes % (256 * 1024) < count || fraction >= 1f) {
                            val progress = if (fileSize > 0L) " • ${(fraction * 100).toInt()}%" else " • ${sentBytes / 1024} KB"
                            runOnUiThread {
                                outgoingProgress = fraction
                                chatNotice = "Sending $fileName$progress"
                            }
                        }
                    }
                    if (cancelledOutgoingTransfers.contains(id)) {
                        sendWireLine("FILE_CANCEL|$id")
                        throw IllegalStateException("Transfer cancelled")
                    }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!sendWireLine("FILE_END|$id|$hash")) throw IllegalStateException("Could not finish file transfer")
                    runOnUiThread { chatNotice = "Sending complete. Waiting for the other phone to confirm…" }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    outgoingTransferId = null
                    outgoingProgress = 0f
                    chatNotice = "File transfer failed: ${e.message ?: "connection problem"}"
                }
            } finally {
                cancelledOutgoingTransfers.clear()
            }
        }
    }

    private fun startMessageReader(socket: Socket) {
        io.execute {
            try {
                socket.soTimeout = 0
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (!socket.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.length > 24 * 1024) throw IllegalStateException("NeaRer message frame exceeded the allowed size")
                    when {
                        line.startsWith("MSG|") -> {
                            val parts = line.split("|", limit = 3)
                            if (parts.size == 3) {
                                val id = parts[1]
                                val body = try {
                                    String(Base64.getDecoder().decode(parts[2]), Charsets.UTF_8)
                                } catch (_: Exception) { "" }
                                if (body.isNotBlank() && receivedMessageIds.add(id)) {
                                    runOnUiThread {
                                        chatMessages.add(ChatMessage(id, body, outgoing = false, delivery = "Received"))
                                        persistMessages()
                                        chatNotice = "New message received directly."
                                    }
                                }
                                synchronized(writerLock) { peerWriter?.println("ACK|$id") }
                            }
                        }
                        line.startsWith("ACK|") -> {
                            val id = line.substringAfter("ACK|")
                            runOnUiThread {
                                val index = chatMessages.indexOfFirst { it.id == id && it.outgoing }
                                if (index >= 0) {
                                    chatMessages[index] = chatMessages[index].copy(delivery = "Delivered")
                                    persistMessages()
                                }
                            }
                        }
                        line.startsWith("FILE_START|") -> {
                            val parts = line.split("|", limit = 4)
                            if (parts.size == 4) {
                                val id = parts[1]
                                val name = try { String(Base64.getDecoder().decode(parts[2]), Charsets.UTF_8) } catch (_: Exception) { "shared-file" }
                                val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "shared-file" }
                                val expected = parts[3].toLongOrNull() ?: 0L
                                if (expected > maxTransferBytes) {
                                    synchronized(writerLock) { peerWriter?.println("FILE_DONE|$id|ERROR") }
                                } else {
                                    val folder = File(filesDir, "received").apply { mkdirs() }
                                    val target = File(folder, "${System.currentTimeMillis()}-$safeName")
                                    incomingTransfers[id] = IncomingTransfer(safeName, expected, target, FileOutputStream(target))
                                    runOnUiThread {
                                        incomingTransferName = safeName
                                        incomingProgress = 0f
                                    }
                                }
                            }
                        }
                        line.startsWith("FILE_CHUNK|") -> {
                            val parts = line.split("|", limit = 4)
                            if (parts.size == 4) {
                                val transfer = incomingTransfers[parts[1]]
                                if (transfer != null) {
                                    try {
                                        val bytes = Base64.getDecoder().decode(parts[3])
                                        transfer.receivedBytes += bytes.size
                                        if (transfer.receivedBytes > maxTransferBytes) {
                                            incomingTransfers.remove(parts[1])?.close()
                                            transfer.file.delete()
                                            runOnUiThread {
                                                incomingTransferName = null
                                                incomingProgress = 0f
                                                chatNotice = "This file exceeds NeaRer's 200 MB transfer limit."
                                            }
                                            synchronized(writerLock) { peerWriter?.println("FILE_DONE|${parts[1]}|ERROR") }
                                        } else {
                                            transfer.output.write(bytes)
                                            val fraction = if (transfer.expectedBytes > 0L) {
                                                (transfer.receivedBytes.toFloat() / transfer.expectedBytes.toFloat()).coerceIn(0f, 1f)
                                            } else 0f
                                            runOnUiThread {
                                                incomingProgress = fraction
                                                chatNotice = if (transfer.expectedBytes > 0L) {
                                                    "Receiving ${transfer.name} • ${(fraction * 100).toInt()}%"
                                                } else {
                                                    "Receiving ${transfer.name} • ${transfer.receivedBytes / 1024} KB"
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        incomingTransfers.remove(parts[1])?.close()
                                    }
                                }
                            }
                        }
                        line.startsWith("FILE_END|") -> {
                            val parts = line.split("|", limit = 3)
                            if (parts.size >= 2) {
                                val id = parts[1]
                                val transfer = incomingTransfers.remove(id)
                                if (transfer != null) {
                                    transfer.close()
                                    val sizeMatches = transfer.expectedBytes <= 0L || transfer.receivedBytes == transfer.expectedBytes
                                    val hashMatches = parts.getOrNull(2)?.let { it.equals(sha256(transfer.file), ignoreCase = true) } ?: false
                                    if (sizeMatches && hashMatches) {
                                        runOnUiThread {
                                            incomingTransferName = null
                                            incomingProgress = 0f
                                            receivedFiles.add(0, ReceivedFile(transfer.name, transfer.file))
                                            chatNotice = "Received and verified: ${transfer.name}. Tap it below to open."
                                        }
                                        synchronized(writerLock) { peerWriter?.println("FILE_DONE|$id|OK") }
                                    } else {
                                        transfer.file.delete()
                                        synchronized(writerLock) { peerWriter?.println("FILE_DONE|$id|ERROR") }
                                        runOnUiThread {
                                            incomingTransferName = null
                                            incomingProgress = 0f
                                            chatNotice = "The received file failed its integrity check and was discarded."
                                        }
                                    }
                                }
                            }
                        }
                        line.startsWith("FILE_DONE|") -> {
                            val parts = line.split("|", limit = 3)
                            val ok = parts.getOrNull(2) == "OK"
                            val transferId = parts.getOrNull(1)
                            runOnUiThread {
                                if (!ok && transferId != null && outgoingTransferId == transferId) {
                                    cancelledOutgoingTransfers.add(transferId)
                                }
                                if (outgoingTransferId == transferId) {
                                    outgoingTransferId = null
                                    outgoingProgress = 0f
                                }
                                chatNotice = if (ok) "File delivered and verified by the nearby phone." else "The other phone could not verify the file."
                            }
                        }
                        line.startsWith("FILE_CANCEL|") -> {
                            val id = line.substringAfter("FILE_CANCEL|")
                            incomingTransfers.remove(id)?.let { transfer ->
                                transfer.close()
                                transfer.file.delete()
                            }
                            runOnUiThread {
                                incomingTransferName = null
                                incomingProgress = 0f
                                chatNotice = "The other phone cancelled the file transfer."
                            }
                        }
                        line == "PING" -> synchronized(writerLock) { peerWriter?.println("PONG") }
                    }
                }
            } catch (_: Exception) {
                // Socket closure is handled below; no message body is logged.
            } finally {
                incomingTransfers.values.forEach {
                    it.close()
                    it.file.delete()
                }
                incomingTransfers.clear()
                runOnUiThread {
                    val wasReady = chatReady
                    closePeerTransport()
                    if (wasReady) {
                        chatNotice = "The nearby connection ended. Reconnect to continue chatting."
                        connectionMessage = "Connection ended"
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_DISCOVERY_PERMISSION = 410
    }
}

@androidx.compose.runtime.Composable
private fun NeaRerHome(
    status: String,
    scanning: Boolean,
    devices: List<WifiP2pDevice>,
    connectingAddress: String?,
    connectedDeviceName: String?,
    connectionMessage: String,
    incomingRequesterName: String?,
    approvalMessage: String,
    pairingCode: String?,
    chatReady: Boolean,
    usingNearbyConnections: Boolean,
    messages: List<ChatMessage>,
    chatNotice: String,
    receivedFiles: List<ReceivedFile>,
    onSendMessage: (String) -> Unit,
    onSendFile: (Uri) -> Unit,
    onCancelTransfer: () -> Unit,
    outgoingTransferId: String?,
    outgoingProgress: Float,
    incomingTransferName: String?,
    incomingProgress: Float,
    onOpenFile: (ReceivedFile) -> Unit,
    onDisconnect: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    hybridPeers: Map<String, String>,
    recentlySeenPeers: Map<String, NearbyPerson>,
    displayName: String,
    onSaveDisplayName: (String) -> Unit,
    meshRelayEnabled: Boolean,
    onMeshRelayChange: (Boolean) -> Unit,
    onHybridConnect: (String) -> Unit,
    onFindNearby: () -> Unit,
    onStopSearch: () -> Unit,
    onConnect: (WifiP2pDevice) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var nameDraft by remember(displayName) { mutableStateOf(displayName) }
    val searchMotion = rememberInfiniteTransition(label = "nearby-search")
    val orbitRotation by searchMotion.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing)), label = "orbit")
    val pulse by searchMotion.animateFloat(0.72f, 1f, infiniteRepeatable(tween(1050), RepeatMode.Reverse), label = "pulse")
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSendFile(uri)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Midnight) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nea", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("R", color = Color(0xFFFFD400), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("er", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("No Data? We Are NeaRer.", color = Muted, fontSize = 13.sp)
                }
                Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(46.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text("N", color = Mint, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Panel) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("YOUR NEARER NAME", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("What should people around you call you?", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it.take(24) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("e.g. Hope, Jay Flex, Mimi", color = Muted) }, shape = RoundedCornerShape(14.dp))
                        Button(onClick = { onSaveDisplayName(nameDraft) }, enabled = nameDraft.trim().isNotBlank(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) { Text("Save") }
                    }
                    Text("This is your public nearby name, not your phone model.", color = Muted, fontSize = 10.sp)
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color(0xFF172B4A)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = BrandBlue, modifier = Modifier.size(42.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text("N", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black) }
                        }
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("YOUR OFFLINE WORLD", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("People, sharing and play — closer.", color = Muted, fontSize = 11.sp)
                        }
                        Text("NEARER", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Text("Everything starts nearby.", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Connect without mobile data. Share directly. Build a local community with people around you.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PRIVATE CHAT" to "Ready after connection", "FILE SHARING" to "Ready after connection").forEach { item ->
                            Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFF203858), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(item.first, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(item.second, color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("BLUETOOTH CHAT" to "Hybrid connection enabled", "MESH RELAY" to "Opt-in, up to 4 hops").forEach { item ->
                            Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFF203858), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(item.first, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(item.second, color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LOCAL GROUPS" to "Coming soon", "PLAY TOGETHER" to "Coming soon").forEach { item ->
                            Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFF203858), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(item.first, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(item.second, color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Panel) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("NEARBY CONNECTION", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Bring nearby people closer.", color = White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "NeaRer advertises your chosen name while searching for other NeaRer phones. Nearby Connections can use Bluetooth and nearby Wi-Fi automatically; Wi-Fi Direct is an additional discovery path. Hotspot and mobile data are not required.",
                        color = Muted, fontSize = 14.sp, lineHeight = 21.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onFindNearby, modifier = Modifier.weight(1f).height(52.dp), enabled = !scanning,
                            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                            Text(if (scanning) "Searching…" else "Find people", fontWeight = FontWeight.Bold)
                        }
                        if (scanning) {
                            Button(onClick = onStopSearch, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35445B), contentColor = White)) { Text("Stop") }
                        }
                    }
                    Text(status, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Surface(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp), color = if (chatReady) Color(0xFF173C3A) else Panel) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CONNECTION STATUS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        if (chatReady) Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFF174B43), modifier = Modifier.alpha(pulse)) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("✓", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Text("CONNECTED", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(connectedDeviceName?.let { "You are connected to $it" } ?: connectionMessage, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(connectionMessage, color = Muted, fontSize = 11.sp)
                    Text(approvalMessage, color = if (chatReady) Mint else Muted, fontSize = 12.sp)
                    pairingCode?.let { code ->
                        Text("PAIRING CODE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(code, color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("Check that both phones show the same code before approving.", color = Muted, fontSize = 10.sp)
                    }
                    if (connectedDeviceName != null || connectingAddress != null) {
                        Button(
                            onClick = onDisconnect,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)
                        ) { Text("Disconnect") }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Panel) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PRIVATE CHAT", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(if (chatReady) "LOCAL LINK" else "NOT CONNECTED", color = Muted, fontSize = 9.sp)
                    }
                    if (chatReady) {
                        if (messages.isEmpty()) {
                            Text("Your private conversation starts here. Messages travel over the direct phone connection.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth().height(150.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                messages.forEach { message ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start
                                    ) {
                                        Surface(
                                            color = if (message.outgoing) Color(0xFF24564F) else Color(0xFF26364D),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                                Text(message.body, color = White, fontSize = 14.sp)
                                                Text(if (message.outgoing) message.delivery else "Received", color = Mint, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(chatNotice, color = Muted, fontSize = 10.sp)
                        receivedFiles.take(3).forEach { fileItem ->
                            Button(
                                onClick = { onOpenFile(fileItem) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26364D), contentColor = White)
                            ) { Text("Open ${fileItem.displayName}", maxLines = 1) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it.take(2000) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Write a message…", color = Muted) },
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                            Button(
                                onClick = { onSendMessage(draft); draft = "" },
                                enabled = draft.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                            ) { Text("Send", fontWeight = FontWeight.Bold) }
                        }
                        incomingTransferName?.let { incomingName ->
                            Text("Receiving $incomingName", color = Muted, fontSize = 11.sp)
                            LinearProgressIndicator(
                                progress = { incomingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Mint,
                                trackColor = Color(0xFF26364D)
                            )
                        }
                        if (outgoingTransferId != null) {
                            LinearProgressIndicator(
                                progress = { outgoingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Mint,
                                trackColor = Color(0xFF26364D)
                            )
                            Button(
                                onClick = onCancelTransfer,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)
                            ) { Text("Cancel file transfer") }
                        }
                        Button(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26364D), contentColor = White)
                        ) { Text("Share a photo or file") }
                        Text(if (usingNearbyConnections) "Nearby Connections file transfer • maximum 200 MB per file" else "Wi-Fi Direct file transfer • maximum 200 MB per file", color = Muted, fontSize = 10.sp)
                    } else {
                        Text("Approve a nearby NeaRer connection to unlock private text chat. No mobile data is used for this local conversation.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            if (incomingRequesterName != null) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("NEARER CONNECTION REQUEST", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("$incomingRequesterName wants to connect with you.", color = White, fontWeight = FontWeight.SemiBold)
                        Text("Compare the six-digit code on both phones. Accept only if it matches and you recognise this person.", color = Muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onAccept, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) { Text("Accept") }
                            Button(onClick = onReject, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)) { Text("Reject") }
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("COMMUNITY MESH RELAY", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Allow your phone to pass group text messages to other connected NeaRer phones.", color = White, fontSize = 12.sp, lineHeight = 17.sp)
                        Text("Optional • up to 4 hops • off by default. Relaying phones can read message text, so use only with trusted groups.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    androidx.compose.material3.Switch(checked = meshRelayEnabled, onCheckedChange = onMeshRelayChange)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEARBY DEVICES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("${devices.size} found", color = Mint, fontSize = 11.sp)
            }

            Surface(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp), color = Color(0xFF14243A)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PEOPLE AROUND YOU", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (hybridPeers.isEmpty()) "Your nearby circle" else "${hybridPeers.size} active nearby", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (scanning && hybridPeers.isEmpty()) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(142.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = CircleShape, color = Color(0xFF1C3554), modifier = Modifier.size(118.dp).alpha(pulse)) {}
                            Surface(shape = CircleShape, color = Color(0xFF244A70), modifier = Modifier.size(82.dp).rotate(orbitRotation)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.TopCenter) { Surface(shape = CircleShape, color = Mint, modifier = Modifier.size(12.dp)) {} }
                            }
                            Surface(shape = CircleShape, color = BrandBlue, modifier = Modifier.size(54.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text("N", color = White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
                            }
                        }
                        Text("Looking for NeaRer people…", color = White, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("Your chosen name is visible to nearby NeaRer phones.", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (hybridPeers.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            hybridPeers.entries.take(5).forEachIndexed { index, entry ->
                                val drift by searchMotion.animateFloat(0f, if (index % 2 == 0) 5f else -5f, infiniteRepeatable(tween(1300 + index * 170), RepeatMode.Reverse), label = "bubble-$index")
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Surface(shape = CircleShape, color = Color(0xFF244B70), modifier = Modifier.size(54.dp).rotate(drift)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(entry.value.take(1).uppercase(), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                                    }
                                    Text(entry.value, color = White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("Active now", color = Mint, fontSize = 9.sp)
                                }
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(78.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = CircleShape, color = Color(0xFF1C3554), modifier = Modifier.size(68.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text("•••", color = Mint, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Text("Nearby names will appear here as phones are discovered.", color = Muted, fontSize = 11.sp)
                    }
                    AnimatedVisibility(visible = recentlySeenPeers.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("RECENTLY SEEN ON THIS PHONE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            recentlySeenPeers.values.sortedByDescending { it.lastSeenAt }.take(5).forEach { person ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = Color(0xFF344256), modifier = Modifier.size(32.dp)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase(), color = Muted, fontWeight = FontWeight.Bold) }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                        Text(person.name, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Not currently visible", color = Muted, fontSize = 9.sp)
                                    }
                                    Text(seenAgo(person.lastSeenAt), color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (hybridPeers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CONNECT WITH A NEARBY PERSON", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    hybridPeers.forEach { (id, name) ->
                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF1C3453)) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = BrandBlue, modifier = Modifier.size(38.dp)) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = White, fontWeight = FontWeight.Bold) }
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(name, color = White, fontWeight = FontWeight.SemiBold)
                                    Text("Active now • Nearby Connections", color = Mint, fontSize = 10.sp)
                                }
                                Button(onClick = { onHybridConnect(id) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) { Text("Connect") }
                            }
                        }
                    }
                }
            }

            if (devices.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No devices listed yet", color = White, fontWeight = FontWeight.SemiBold)
                        Text("Scan with another Android phone nearby. For NeaRer chat, both phones will need the app installed.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    devices.forEach { device ->
                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Panel) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = CircleShape, color = Color(0xFF244B4A), modifier = Modifier.size(38.dp)) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Text("N", color = Mint, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(device.deviceName.ifBlank { "Nearby Android device" }, color = White, fontWeight = FontWeight.SemiBold)
                                    Text(deviceStatus(device.status), color = Muted, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { onConnect(device) },
                                    enabled = connectingAddress == null && connectedDeviceName == null && device.status != WifiP2pDevice.UNAVAILABLE,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                                ) {
                                    Text(if (connectingAddress == device.deviceAddress) "Requesting…" else "Connect", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))
            Text("NEARER  •  DIRECT DEVICE DISCOVERY", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

private data class NearbyPerson(val name: String, val lastSeenAt: Long)

private fun seenAgo(timestamp: Long): String {
    val seconds = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 1000L
    return when {
        seconds < 60 -> "Seen just now"
        seconds < 3600 -> "Seen ${seconds / 60}m ago"
        seconds < 86400 -> "Seen ${seconds / 3600}h ago"
        else -> "Seen ${seconds / 86400}d ago"
    }
}

private data class ChatMessage(
    val id: String,
    val body: String,
    val outgoing: Boolean,
    val delivery: String
)

private data class ReceivedFile(val displayName: String, val file: File)

private data class IncomingTransfer(
    val name: String,
    val expectedBytes: Long,
    val file: File,
    val output: FileOutputStream,
    var receivedBytes: Long = 0L
) {
    fun close() {
        try { output.flush(); output.close() } catch (_: Exception) { }
    }
}

private fun deviceStatus(status: Int): String = when (status) {
    WifiP2pDevice.AVAILABLE -> "Available"
    WifiP2pDevice.INVITED -> "Invitation pending"
    WifiP2pDevice.CONNECTED -> "Connected"
    WifiP2pDevice.FAILED -> "Connection failed"
    WifiP2pDevice.UNAVAILABLE -> "Temporarily unavailable"
    else -> "Nearby Wi-Fi Direct device"
}
