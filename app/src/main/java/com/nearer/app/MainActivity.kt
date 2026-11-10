package com.nearer.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
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
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.os.Environment
import android.webkit.MimeTypeMap
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Handler
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

// NeaRer signature palette: rich brand blue, deep ink surfaces, clear blue and warm gold.
internal val Midnight = Color(0xFF07182A)
internal val Panel = Color(0xFF102B46)
internal val BlueAccent = Color(0xFF78B9FF)
internal val Muted = Color(0xFFB2C2D3)
internal val White = Color(0xFFF7FAFF)
internal val BrandBlue = Color(0xFF004B90)
internal val Gold = Color(0xFFFFC928)

class MainActivity : ComponentActivity() {
    private var wifiManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var scanning by mutableStateOf(false)
    private var nearbyTransport: NearbyConnectionsTransport? = null
    private var ultraSpeedMode by mutableStateOf(false)
    private var ultraSpeedRestartRequired by mutableStateOf(false)
    private val hybridPeers = mutableStateMapOf<String, String>()
    private val recentlySeenPeers = mutableStateMapOf<String, NearbyPerson>()
    private var displayName by mutableStateOf("NeaRer User")
    private var hybridSearching by mutableStateOf(false)
    private var pendingHybridEndpoint: String? = null
    private var hybridConnected = false
    private var hybridConnectedEndpoint: String? = null
    private var hybridConnectedStableId: String? = null
    private var nearbyQueuePeerStableId: String? = null
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
    private val communityGroups = mutableStateListOf<CommunityGroup>()
    private val communityMessages = mutableStateListOf<CommunityChatMessage>()
    private var selectedCommunityCode by mutableStateOf<String?>(null)
    private var communityNotice by mutableStateOf("Create or join a nearby group.")
    private var incomingGameInvite by mutableStateOf<GameInvite?>(null)
    private var activeGame by mutableStateOf<LudoNetworkMatch?>(null)
    private val gameChatMessages = mutableStateListOf<String>()
    private val receivedFiles = mutableStateListOf<ReceivedFile>()
    private val nearbyTransferHistory = mutableStateListOf<NearbyTransferRecord>()
    private var activeOutgoingFileName = "shared-file"
    private var activeOutgoingFileBytes = 0L
    private val pendingDirectOutgoing = ConcurrentHashMap<String, DirectOutgoingTransfer>()
    // Legacy Wi-Fi Direct sends multi-select baskets sequentially to bound memory and receiver load.
    private val directFileQueue = SequentialTransferQueue<Uri>()
    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()
    private val cancelledOutgoingTransfers = ConcurrentHashMap.newKeySet<String>()
    private var outgoingTransferId by mutableStateOf<String?>(null)
    private var outgoingProgress by mutableStateOf(0f)
    // Nearby multi-file jobs are sent one file at a time to protect slower phones.
    private val pendingNearbyFileQueue = java.util.ArrayDeque<Uri>()
    // Keep the active item separate so a dropped connection or app restart can retry it.
    private var currentNearbyQueuedFile: Uri? = null
    private var nearbyQueueTotal = 0
    private var nearbyQueueCompleted = 0
    private var incomingTransferName by mutableStateOf<String?>(null)
    private var incomingProgress by mutableStateOf(0f)
    private var outgoingLastSampleBytes = 0L
    private var outgoingLastSampleAt = 0L
    private var outgoingRateBytesPerSecond = 0L
    private var incomingLastSampleBytes = 0L
    private var incomingLastSampleAt = 0L
    private var incomingRateBytesPerSecond = 0L
    private var pendingExternalShareUris by mutableStateOf<List<Uri>>(emptyList())
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
        val diagnosticPreferences = getSharedPreferences("nearer_diagnostics", Context.MODE_PRIVATE)
        val startupCrash = runCatching {
            diagnosticPreferences.getString("nearer_startup_crash", null)
        }.getOrNull()
        if (!startupCrash.isNullOrBlank()) {
            // Do not immediately retry a known-crashing startup path. Show the saved
            // exception first so a phone-only tester can share the actual stack trace.
            window.statusBarColor = android.graphics.Color.rgb(7, 24, 42)
            window.navigationBarColor = android.graphics.Color.rgb(7, 24, 42)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme(
                    background = Midnight, surface = Panel, primary = BlueAccent,
                    secondary = Gold, onBackground = White, onSurface = White
                )) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Midnight) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("NeaRer found a startup error", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("The previous launch crashed. This report records the real error so it can be repaired.", color = White)
                            Text(startupCrash.take(12000), color = Muted, fontSize = 12.sp)
                            Button(
                                onClick = {
                                    val reportIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "NeaRer startup crash report")
                                        putExtra(Intent.EXTRA_TEXT, startupCrash)
                                    }
                                    startActivity(Intent.createChooser(reportIntent, "Share NeaRer crash report"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Share crash report") }
                            Button(
                                onClick = {
                                    diagnosticPreferences.edit().remove("nearer_startup_crash").apply()
                                    recreate()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                            ) { Text("Try opening NeaRer again") }
                        }
                    }
                }
            }
            return
        }
        captureExternalShareIntent(intent)
        window.statusBarColor = android.graphics.Color.rgb(7, 24, 42)
        window.navigationBarColor = android.graphics.Color.rgb(7, 24, 42)
        window.decorView.systemUiVisibility = 0
        try {
            wifiManager = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            channel = wifiManager?.initialize(this, mainLooper, null)
        } catch (error: Exception) {
            wifiManager = null
            channel = null
            statusMessage = "Wi-Fi Direct could not initialise on this phone. Bluetooth nearby features can still be used."
        }
        ultraSpeedMode = runCatching {
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .getBoolean("ultra_speed_mode", false)
        }.getOrDefault(false)
        ultraSpeedRestartRequired = false
        nearbyTransport = try {
            NearbyConnectionsTransport(this, ultraSpeedMode, object : NearbyConnectionsTransport.Listener {
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
                    hybridConnectedStableId = stableId.ifBlank { null }
                    val requestedIncomingResume = nearbyTransport?.requestPendingIncomingFilesForPeer(stableId, id) == true
                    val hasQueuedOutgoingFile = currentNearbyQueuedFile != null || pendingNearbyFileQueue.isNotEmpty()
                    if (hasQueuedOutgoingFile) {
                        // Schedule the sender-side fallback independently of incoming resume requests.
                        // A phone can have a queued outgoing file and a partial incoming file at once.
                        // Wait for any matching resume response before restarting from byte zero.
                        discoveryHandler.postDelayed({
                            if (hybridConnected && hybridConnectedStableId == stableId &&
                                outgoingTransferId == null &&
                                (currentNearbyQueuedFile != null || pendingNearbyFileQueue.isNotEmpty())) {
                                chatNotice = "No matching partial transfer was found. Restarting this file safely from the beginning…"
                                startNextNearbyQueuedFile()
                            }
                        }, 5000L)
                    }
                    pendingHybridEndpoint = null
                    incomingRequesterName = null
                    connectedDeviceName = name
                    chatReady = true
                    resumeLudoAfterReconnect(name)
                    connectionMessage = "Secure nearby connection active"
                    approvalMessage = "Connection approved"
                    chatNotice = when {
                        requestedIncomingResume -> "Connection restored. Requesting the saved receive position…"
                        hasQueuedOutgoingFile -> "Connection restored. Checking for a saved transfer position…"
                        else -> "Connected to $name. Offline chat is ready."
                    }
                    statusMessage = "Connected to $name using Bluetooth / nearby Wi-Fi."
                }
            }
            override fun onDisconnected(id: String, stableId: String, name: String) {
                runOnUiThread {
                    hybridConnected = false
                    hybridConnectedEndpoint = null
                    hybridConnectedStableId = null
                    if (outgoingTransferId?.startsWith("nearby:") == true) {
                        outgoingTransferId = null
                        outgoingProgress = 0f
                        persistNearbyFileQueue()
                    }
                    activeGame = activeGame?.let { it.copy(ready = false) }
                    incomingGameInvite = null
                    val lastName = hybridPeers.remove(id) ?: name
                    recordRecentlySeen(stableId.ifBlank { id }, lastName)
                    pendingHybridEndpoint = null
                    connectedDeviceName = null
                    chatReady = false
                    connectionMessage = "Not connected"
                    chatNotice = if (activeGame != null) "Connection paused. Your Ludo match is saved here; reconnect to $lastName to continue." else "Nearby connection ended. Search again to reconnect."
                    if (activeGame != null) communityNotice = "Ludo paused — waiting for $lastName to reconnect."
                }
            }
            override fun onMessage(id: String, senderName: String, messageId: String, body: String, acknowledge: () -> Unit) {
                runOnUiThread {
                    var safelyStored = false
                    if (body.startsWith("NR_COMMUNITY:")) {
                        handleCommunityPacket(senderName, body.removePrefix("NR_COMMUNITY:"))
                        // Acknowledge only after the updated community state reaches disk.
                        safelyStored = persistCommunityData()
                    } else if (chatMessages.any { it.id == messageId }) {
                        safelyStored = true
                    } else {
                        chatMessages.add(ChatMessage(
                            messageId, body, outgoing = false, delivery = "Received",
                            conversationId = nearbyTransport?.stableIdentityForEndpoint(id).orEmpty(),
                            conversationName = senderName
                        ))
                        safelyStored = persistMessages()
                        if (safelyStored) {
                            trimChatMessages()
                            chatNotice = "New offline message received from $senderName."
                        }
                        else {
                            chatMessages.removeAll { it.id == messageId }
                            chatNotice = "Message received, but could not be saved. Delivery was not confirmed."
                        }
                    }
                    // Never tell the sender "Delivered" unless this phone saved the message.
                    if (safelyStored) acknowledge()
                }
            }
            override fun onMessageStatus(messageId: String, status: String) {
                runOnUiThread {
                    val index = chatMessages.indexOfFirst { it.id == messageId }
                    if (index >= 0) {
                        chatMessages[index] = chatMessages[index].copy(delivery = status)
                        persistMessages()
                        chatNotice = when (status) {
                            "Delivered" -> "Your message was received by the other NeaRer phone."
                            "Queued" -> "Not delivered yet. NeaRer saved it and will retry when this person reconnects."
                            else -> "Message delivery failed. Reconnect and try again."
                        }
                    }
                }
            }
            override fun onFileProgress(id: String, payloadId: Long, name: String, transferred: Long, total: Long, incoming: Boolean) {
                runOnUiThread {
                    val fraction = if (total > 0L) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    val now = android.os.SystemClock.elapsedRealtime()
                    val previousBytes = if (incoming) incomingLastSampleBytes else outgoingLastSampleBytes
                    val previousAt = if (incoming) incomingLastSampleAt else outgoingLastSampleAt
                    var rate = if (incoming) incomingRateBytesPerSecond else outgoingRateBytesPerSecond
                    val sampleDue = previousAt == 0L || transferred < previousBytes || now - previousAt >= 500L
                    if (previousAt == 0L || transferred < previousBytes) {
                        rate = 0L
                    } else if (sampleDue) {
                        rate = ((transferred - previousBytes) * 1000L / (now - previousAt).coerceAtLeast(1L)).coerceAtLeast(0L)
                    }
                    if (incoming) {
                        if (sampleDue) {
                            incomingLastSampleBytes = transferred
                            incomingLastSampleAt = now
                        }
                        incomingRateBytesPerSecond = rate
                        incomingTransferName = name
                        incomingProgress = fraction
                    } else {
                        if (sampleDue) {
                            outgoingLastSampleBytes = transferred
                            outgoingLastSampleAt = now
                        }
                        outgoingRateBytesPerSecond = rate
                        activeOutgoingFileName = name
                        activeOutgoingFileBytes = total
                        outgoingTransferId = "nearby:$payloadId"
                        outgoingProgress = fraction
                    }
                    val rateText = if (rate >= 1_000_000L) " • ${String.format(java.util.Locale.US, "%.1f", rate / 1_000_000.0)} MB/s"
                        else if (rate >= 1_000L) " • ${String.format(java.util.Locale.US, "%.0f", rate / 1_000.0)} KB/s" else ""
                    val remaining = if (total > transferred && rate > 0L) (total - transferred) / rate else -1L
                    val etaText = if (remaining >= 0L) " • ~${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')} left" else ""
                    val action = if (incoming) "Receiving" else "Sending"
                    chatNotice = if (total > 0L) "$action $name • ${(fraction * 100).toInt()}%$rateText$etaText" else "$action $name…"
                }
            }
            override fun onFileSpeed(id: String, payloadId: Long, name: String, snapshot: TransferSpeedSnapshot, incoming: Boolean) {
                runOnUiThread {
                    // Use the transport's monotonic rolling-window measurement rather
                    // than estimating speed from UI callback timing.
                    val rate = snapshot.recentBytesPerSecond.toLong().coerceAtLeast(0L)
                    val peak = snapshot.peakWindowBytesPerSecond.toLong().coerceAtLeast(0L)
                    if (incoming) incomingRateBytesPerSecond = rate else outgoingRateBytesPerSecond = rate
                    val total = snapshot.totalBytes
                    val transferred = snapshot.transferredBytes
                    val fraction = snapshot.progressFraction ?: 0f
                    val currentRateText = when {
                        rate >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f MB/s", rate / 1_000_000.0)
                        rate >= 1_000L -> String.format(java.util.Locale.US, "%.0f KB/s", rate / 1_000.0)
                        else -> ""
                    }
                    val peakText = if (peak > rate && peak >= 1_000L) {
                        if (peak >= 1_000_000L) String.format(java.util.Locale.US, " • peak %.1f MB/s", peak / 1_000_000.0)
                        else String.format(java.util.Locale.US, " • peak %.0f KB/s", peak / 1_000.0)
                    } else ""
                    val remaining = if (total > transferred && rate > 0L) (total - transferred) / rate else -1L
                    val etaText = if (remaining >= 0L) " • ~${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')} left" else ""
                    val action = if (incoming) "Receiving" else "Sending"
                    val speedText = if (currentRateText.isBlank()) "" else " • $currentRateText$peakText"
                    chatNotice = if (total > 0L) {
                        "$action $name • ${(fraction * 100).toInt()}%$speedText$etaText"
                    } else "$action $name…"
                }
            }
            override fun onFileReceived(id: String, name: String, file: File) {
                val isAppBundle = isNeaRerAppBundle(file)
                runOnUiThread {
                    incomingTransferName = null
                    incomingProgress = 0f
                    receivedFiles.add(0, ReceivedFile(name, file, isAppBundle))
                    recordNearbyTransfer("Received", name, file.length(), "Complete")
                    chatNotice = if (isAppBundle) {
                        "Received an app bundle: $name. Review it and tap Install when ready."
                    } else "Received and saved: $name. Tap it below to open."
                }
            }
            override fun onFileStatus(message: String) {
                runOnUiThread {
                    chatNotice = message
                    if (message.startsWith("File sent to the nearby phone") ||
                        message.startsWith("Nearby file transfer failed") ||
                        message.startsWith("Nearby file transfer paused") ||
                        message.startsWith("Could not start file transfer") ||
                        message.startsWith("Could not send file details") ||
                        message.startsWith("File could not be prepared")) {
                        outgoingTransferId = null
                        outgoingProgress = 0f
                        if (message.startsWith("File sent to the nearby phone")) {
                            recordNearbyTransfer("Sent", activeOutgoingFileName, activeOutgoingFileBytes, "Complete")
                            deleteGeneratedFolderArchive(currentNearbyQueuedFile)
                            activeOutgoingFileName = "shared-file"
                            activeOutgoingFileBytes = 0L
                            currentNearbyQueuedFile = null
                            nearbyQueueCompleted += 1
                            persistNearbyFileQueue()
                            if (nearbyQueueTotal > 0) startNextNearbyQueuedFile()
                        } else if (nearbyQueueTotal > 0) {
                            // Keep the active file and remaining queue for retry after reconnect.
                            persistNearbyFileQueue()
                            chatNotice = message + " Your transfer is saved; reconnect to retry it."
                        }
                    }
                    if (message.startsWith("Nearby file transfer failed")) {
                        incomingTransferName = null
                        incomingProgress = 0f
                    }
                }
            }
            })
        } catch (error: Exception) {
            statusMessage = "Nearby services could not initialise: ${error.javaClass.simpleName}. NeaRer opened in safe mode."
            null
        }
        if (wifiManager == null && statusMessage == "Ready to find nearby Android phones.") {
            statusMessage = "Wi-Fi Direct is unavailable. Bluetooth nearby features may still work."
        }
        runCatching { restoreLocalData() }.onFailure {
            statusMessage = "NeaRer opened, but some saved local data could not be restored."
        }
        runCatching { restoreNearbyTransferHistory() }
        runCatching { restoreNearbyFileQueue() }
        val queuedAfterRestart = runCatching { nearbyTransport?.pendingMessageIds().orEmpty() }.getOrDefault(emptySet())
        val recoveredChatStates = runCatching {
            ChatRestartRecoveryPolicy.reconcile(chatMessages, queuedAfterRestart)
        }.getOrDefault(false)
        if (recoveredChatStates) {
            runCatching { persistMessages() }
            chatNotice = when {
                queuedAfterRestart.isNotEmpty() -> "Some messages are safely queued. NeaRer will retry when those people reconnect."
                chatMessages.any { it.outgoing && it.delivery == "Interrupted" } ->
                    "A message was interrupted when NeaRer closed before its delivery could be confirmed. Check with the recipient before sending it again."
                else -> chatNotice
            }
        }
        runCatching { restoreCommunityData() }
        displayName = runCatching {
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .getString("display_name", "NeaRer User")?.take(24)?.ifBlank { "NeaRer User" } ?: "NeaRer User"
        }.getOrDefault("NeaRer User")
        runCatching { nearbyTransport?.setDisplayName(displayName) }
        runCatching { restoreRecentlySeen() }
        // Multi-hop relay is paused until recipient routing and end-to-end encryption are ready.
        meshRelayEnabled = false
        runCatching {
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit()
                .putBoolean("mesh_relay_enabled", false).apply()
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Midnight,
                surface = Panel,
                surfaceVariant = Color(0xFF173A5B),
                primary = BlueAccent,
                onPrimary = Midnight,
                secondary = Gold,
                onSecondary = Midnight,
                tertiary = BrandBlue,
                onBackground = White,
                onSurface = White,
                onSurfaceVariant = Muted
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
                    externalShareUris = pendingExternalShareUris,
                    onExternalShareConsumed = { pendingExternalShareUris = emptyList() },
                    receivedFiles = receivedFiles,
                    transferHistory = nearbyTransferHistory.toList(),
                    onSendMessage = ::sendChatMessage,
                    groups = communityGroups.toList(),
                    groupMessages = communityMessages.toList(),
                    selectedGroupCode = selectedCommunityCode,
                    communityNotice = communityNotice,
                    incomingGameInvite = incomingGameInvite,
                    activeGame = activeGame,
                    gameChatMessages = gameChatMessages.toList(),
                    onCreateGroup = ::createCommunityGroup,
                    onJoinGroup = ::joinCommunityGroup,
                    onSelectGroup = { selectedCommunityCode = it },
                    onSendGroupMessage = ::sendCommunityGroupMessage,
                    onBroadcast = ::sendCommunityBroadcast,
                    onGameInvite = ::inviteToGame,
                    onAcceptGameInvite = ::acceptGameInvite,
                    onRejectGameInvite = ::rejectGameInvite,
                    onGameMove = ::playGameMove,
                    onGameChat = ::sendGameChat,
                    onGameRematch = ::requestGameRematch,
                    onEndGame = ::endGame,
                    onSendFiles = ::sendFiles,
                    onSendFolder = ::prepareAndSendFolder,
                    onCancelTransfer = ::cancelOutgoingFileTransfer,
                    outgoingTransferId = outgoingTransferId,
                    outgoingProgress = outgoingProgress,
                    incomingTransferName = incomingTransferName,
                    incomingProgress = incomingProgress,
                    onOpenFile = ::openReceivedFile,
                    onForwardReceivedFile = ::forwardReceivedFile,
                    onSaveReceivedFile = ::saveReceivedFileToDownloads,
                    onInstallAppBundle = ::installReceivedAppBundle,
                    onDisconnect = ::disconnectFromPeer,
                    onAccept = ::approveIncoming,
                    onReject = ::rejectIncoming,
                    hybridPeers = hybridPeers.toMap(),
                    recentlySeenPeers = recentlySeenPeers.toMap(),
                    displayName = displayName,
                    nearbyIdentityId = nearbyTransport?.localIdentityId().orEmpty(),
                    ultraSpeedMode = ultraSpeedMode,
                    ultraSpeedRestartRequired = ultraSpeedRestartRequired,
                    onUltraSpeedModeChange = ::updateUltraSpeedMode,
                    onSaveDisplayName = ::saveDisplayName,
                    meshRelayEnabled = meshRelayEnabled,
                    onMeshRelayChange = ::updateMeshRelayEnabled,
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
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    registerReceiver(receiver, filter)
                }
                receiverRegistered = true
            } catch (error: Exception) {
                receiverRegistered = false
                statusMessage = "NeaRer opened with limited Wi-Fi discovery: ${error.javaClass.simpleName}. Bluetooth nearby features may still be available."
            }
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
            runCatching { unregisterReceiver(receiver) }
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

    private fun updateMeshRelayEnabled(enabled: Boolean) {
        meshRelayEnabled = enabled
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit().putBoolean("mesh_relay_enabled", enabled).apply()
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit().putBoolean("mesh_relay_enabled", false).apply()
        statusMessage = "Community relay is temporarily paused while recipient routing and end-to-end encryption are being built."
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

    private fun updateUltraSpeedMode(enabled: Boolean) {
        if (enabled == ultraSpeedMode) return
        if (hybridSearching || hybridConnected || scanning) {
            statusMessage = "Stop nearby searching and disconnect before changing transfer mode."
            return
        }
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit()
            .putBoolean("ultra_speed_mode", enabled)
            .apply()
        ultraSpeedMode = enabled
        ultraSpeedRestartRequired = true
        statusMessage = if (enabled) {
            "Ultra Speed Mode saved. Restart NeaRer on BOTH phones before searching. It uses a one-to-one high-throughput connection."
        } else {
            "Standard mode saved. Restart NeaRer to use flexible nearby connections again."
        }
    }

    private fun startDiscovery() {
        if (ultraSpeedRestartRequired) {
            statusMessage = "Restart NeaRer on both phones to apply the selected transfer mode, then search again."
            return
        }
        if (!hasNearbyPermissions()) {
            ActivityCompat.requestPermissions(this, nearbyPermissionList(), REQUEST_DISCOVERY_PERMISSION)
            statusMessage = "Allow Nearby devices and Bluetooth permissions so NeaRer can find phones without internet."
            return
        }
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothOn = try { bluetoothManager?.adapter?.isEnabled == true } catch (_: SecurityException) { false }
        val wifiOn = isWifiEnabled()
        if (!bluetoothOn && !wifiOn) {
            finishDiscovery()
            statusMessage = "Turn Bluetooth or Wi-Fi ON on both phones. NeaRer does not need mobile data or an internet connection for nearby play."
            return
        }
        // Start Nearby Connections whenever at least one suitable radio is already enabled.
        // Android no longer silently enables radios for apps; the user stays in control.
        nearbyTransport?.start()
        hybridSearching = true
        if (!wifiOn) {
            finishDiscovery()
            statusMessage = "Bluetooth discovery is active. Turn Wi-Fi ON too for faster nearby play and file sharing. Hotspot and mobile data are not required."
            return
        }
        if (!bluetoothOn) statusMessage = "Wi-Fi is active. Turn Bluetooth ON as well for the widest range of nearby connection options."
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
                        if (directFileQueue.hasPending) startNextDirectQueuedFile()
                        resumeLudoAfterReconnect(name)
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
                            if (directFileQueue.hasPending) startNextDirectQueuedFile()
                            resumeLudoAfterReconnect(connectedDeviceName ?: "Nearby device")
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
        activeGame = null
        incomingGameInvite = null
        gameChatMessages.clear()
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
                        delivery = item.optString("delivery", "Sent"),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        conversationId = item.optString("conversationId", ""),
                        conversationName = item.optString("conversationName", "")
                    )
                    chatMessages.add(message)
                    if (!message.outgoing) receivedMessageIds.add(message.id)
                }
            } catch (_: Exception) {
                chatMessages.clear()
            }
        }
        trimChatMessages()
        val folder = File(filesDir, "received")
        if (folder.exists()) {
            folder.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach {
                receivedFiles.add(ReceivedFile(it.name.substringAfter('-', it.name), it))
            }
        }
    }

    private fun recordNearbyTransfer(direction: String, fileName: String, bytes: Long, status: String) {
        nearbyTransferHistory.add(0, NearbyTransferRecord(direction, fileName.take(140), bytes.coerceAtLeast(0L), System.currentTimeMillis(), status))
        while (nearbyTransferHistory.size > 100) nearbyTransferHistory.removeAt(nearbyTransferHistory.lastIndex)
        try {
            val array = JSONArray()
            nearbyTransferHistory.forEach { item ->
                array.put(JSONObject().put("direction", item.direction).put("fileName", item.fileName)
                    .put("bytes", item.bytes).put("timestamp", item.timestamp).put("status", item.status))
            }
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit()
                .putString("nearby_transfer_history", array.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun restoreNearbyTransferHistory() {
        try {
            val raw = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .getString("nearby_transfer_history", "[]") ?: "[]"
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val direction = item.optString("direction")
                if (direction != "Sent" && direction != "Received") continue
                nearbyTransferHistory.add(NearbyTransferRecord(
                    direction, item.optString("fileName", "shared-file").take(140),
                    item.optLong("bytes").coerceAtLeast(0L), item.optLong("timestamp"),
                    item.optString("status", "Complete")
                ))
            }
        } catch (_: Exception) { nearbyTransferHistory.clear() }
    }

    private fun trimChatMessages() { ChatHistoryPolicy.trim(chatMessages) }

    private fun persistMessages(): Boolean {
        return try {
            val array = JSONArray()
            chatMessages.takeLast(500).forEach { message ->
                array.put(JSONObject()
                    .put("id", message.id)
                    .put("body", message.body)
                    .put("outgoing", message.outgoing)
                    .put("delivery", message.delivery)
                    .put("timestamp", message.timestamp)
                    .put("conversationId", message.conversationId)
                    .put("conversationName", message.conversationName))
            }
            // commit() is intentional here: a delivery receipt must follow the disk write,
            // not just an in-memory SharedPreferences update queued by apply().
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .edit().putString("messages", array.toString()).commit()
        } catch (_: Exception) { false }
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
            val targetEndpoint = hybridConnectedEndpoint
            if (targetEndpoint.isNullOrBlank()) {
                chatNotice = "Your private connection is not ready. Reconnect to the person before sending."
                return
            }
            // Add the pending bubble before dispatch so a very fast receipt cannot race the UI.
            chatMessages.add(ChatMessage(
                id, body, outgoing = true, delivery = "Sending…",
                conversationId = nearbyTransport?.stableIdentityForEndpoint(targetEndpoint).orEmpty(),
                conversationName = nearbyTransport?.displayNameForEndpoint(targetEndpoint).orEmpty()
            ))
            if (!persistMessages()) {
                chatMessages.removeAll { it.id == id }
                chatNotice = "NeaRer could not save this message on your phone. It was not sent."
                return
            }
            trimChatMessages()
            if (nearbyTransport?.send(body, id, targetEndpoint) != true) {
                val index = chatMessages.indexOfFirst { it.id == id }
                if (index >= 0) chatMessages[index] = chatMessages[index].copy(delivery = "Failed")
                persistMessages()
                chatNotice = "Message could not be sent. Check the nearby connection."
                return
            }
            chatNotice = "Sent over the offline link • waiting for the recipient's NeaRer app to acknowledge it."
            return
        }
        val encoded = Base64.getEncoder().encodeToString(body.toByteArray(Charsets.UTF_8))
        chatMessages.add(ChatMessage(
            id, body, outgoing = true, delivery = "Sending…",
            conversationId = connectedDeviceName?.let { "direct:$it" }.orEmpty(),
            conversationName = connectedDeviceName.orEmpty()
        ))
        if (!persistMessages()) {
            chatMessages.removeAll { it.id == id }
            chatNotice = "NeaRer could not save this message on your phone. It was not sent."
            return
        }
        trimChatMessages()
        if (!sendWireLine("MSG|$id|$encoded")) {
            val index = chatMessages.indexOfFirst { it.id == id }
            if (index >= 0) chatMessages[index] = chatMessages[index].copy(delivery = "Failed")
            persistMessages()
            chatNotice = "Message could not be sent. Check the nearby connection."
            return
        }
        chatNotice = "Sent directly • waiting for the recipient app to save and acknowledge it."
    }


    private fun persistCommunityData(): Boolean {
        return try {
            val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val groups = JSONArray()
            communityGroups.forEach { groups.put(JSONObject().put("code", it.code).put("name", it.name).put("members", JSONArray(it.members))) }
            val messages = JSONArray()
            communityMessages.takeLast(300).forEach { messages.put(JSONObject().put("id", it.id).put("code", it.groupCode).put("sender", it.sender).put("body", it.body).put("time", it.time)) }
            prefs.edit().putString("community_groups", groups.toString()).putString("community_messages", messages.toString()).commit()
        } catch (_: Exception) { false }
    }
    private fun restoreCommunityData() {
        val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        try {
            val groups = JSONArray(prefs.getString("community_groups", "[]"))
            for (i in 0 until groups.length()) {
                val item = groups.getJSONObject(i)
                val members = item.optJSONArray("members") ?: JSONArray()
                communityGroups.add(CommunityGroup(item.getString("code"), item.optString("name"), (0 until members.length()).map { members.optString(it) }))
            }
            val messages = JSONArray(prefs.getString("community_messages", "[]"))
            for (i in 0 until messages.length()) {
                val item = messages.getJSONObject(i)
                communityMessages.add(CommunityChatMessage(item.getString("id"), item.getString("code"), item.optString("sender"), item.getString("body"), item.optLong("time")))
            }
        } catch (_: Exception) { communityGroups.clear(); communityMessages.clear() }
    }
    private fun sendCommunityPacket(packet: JSONObject): Boolean {
        val body = "NR_COMMUNITY:$packet"
        if (hybridConnected) return nearbyTransport?.send(body) == true
        if (chatReady && connectedDeviceName != null) {
            val encoded = Base64.getEncoder().encodeToString(body.toByteArray(Charsets.UTF_8))
            return sendWireLine("COMMUNITY|$encoded")
        }
        communityNotice = "Connect to a nearby NeaRer phone first."
        return false
    }

    private fun sendCommunityToNames(body: String, targetNames: Set<String>): Boolean {
        if (hybridConnected) return nearbyTransport?.sendToNames(body, targetNames) == true
        val peerName = connectedDeviceName
        if (chatReady && peerName != null && peerName in targetNames) {
            val encoded = Base64.getEncoder().encodeToString(body.toByteArray(Charsets.UTF_8))
            return sendWireLine("COMMUNITY|$encoded")
        }
        return false
    }

    private fun sendLudoPacket(packet: JSONObject, targetNames: Set<String>): Boolean {
        // The Wi-Fi Direct fallback has exactly one connected peer, but its Android
        // device label may not match the NeaRer profile name used by the game room.
        return if (!hybridConnected && chatReady) sendCommunityPacket(packet)
        else sendCommunityToNames("NR_COMMUNITY:$packet", targetNames)
    }
    private fun createCommunityGroup(raw: String) {
        val name = raw.trim().take(32)
        if (name.isBlank() || communityGroups.size >= 20) return
        var code = (100000..999999).random().toString()
        while (communityGroups.any { it.code == code }) code = (100000..999999).random().toString()
        communityGroups.add(0, CommunityGroup(code, name, listOf(displayName)))
        selectedCommunityCode = code
        persistCommunityData()
        communityNotice = "Created $name. Share group code $code with nearby people."
        sendCommunityPacket(JSONObject().put("type", "group_created").put("code", code).put("name", name).put("sender", displayName))
    }
    private fun joinCommunityGroup(raw: String) {
        val code = raw.filter { it.isDigit() }.take(6)
        if (code.length != 6) return
        if (communityGroups.none { it.code == code }) communityGroups.add(0, CommunityGroup(code, "Nearby group $code", listOf(displayName)))
        selectedCommunityCode = code
        persistCommunityData()
        sendCommunityPacket(JSONObject().put("type", "group_join").put("code", code).put("sender", displayName))
        communityNotice = "Group $code saved locally. Join notice sent if connected."
    }
    private fun sendGroupMembers(code: String) {
        val group = communityGroups.firstOrNull { it.code == code } ?: return
        val packet = JSONObject().put("type", "group_members").put("code", code).put("members", JSONArray(group.members))
        sendCommunityToNames("NR_COMMUNITY:$packet", group.members.toSet())
    }

    private fun sendCommunityGroupMessage(raw: String) {
        val code = selectedCommunityCode ?: return
        val group = communityGroups.firstOrNull { it.code == code } ?: return
        val body = raw.trim().take(1500)
        if (body.isBlank()) return
        val id = UUID.randomUUID().toString()
        val packet = JSONObject().put("type", "group_message").put("code", code).put("sender", displayName).put("body", body).put("id", id)
        if (sendCommunityToNames("NR_COMMUNITY:$packet", group.members.toSet()) == true) {
            communityMessages.add(CommunityChatMessage(id, code, displayName, body, System.currentTimeMillis()))
            persistCommunityData()
        } else communityNotice = "No connected member of this group is available right now."
    }
    private fun sendCommunityBroadcast(raw: String) {
        val body = raw.trim().take(500)
        if (body.isNotBlank() && sendCommunityPacket(JSONObject().put("type", "broadcast").put("sender", displayName).put("body", body))) {
            communityNotice = "Broadcast sent to connected nearby phones."
        }
    }
    private fun handleCommunityPacket(sender: String, raw: String) {
        try {
            val packet = JSONObject(raw)
            val type = packet.optString("type")
            val code = packet.optString("code")
            when (type) {
                "group_created", "group_join" -> {
                    val i = communityGroups.indexOfFirst { it.code == code }
                    if (i >= 0) {
                        val old = communityGroups[i]
                        val member = sender
                        if (member !in old.members) communityGroups[i] = old.copy(members = old.members + member)
                        persistCommunityData()
                        sendGroupMembers(code)
                    }
                }
                "group_members" -> {
                    val i = communityGroups.indexOfFirst { it.code == code }
                    if (i >= 0) {
                        val array = packet.optJSONArray("members") ?: JSONArray()
                        val members = (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.distinct()
                        communityGroups[i] = communityGroups[i].copy(members = members)
                        persistCommunityData()
                    }
                }
                "group_message" -> {
                    val group = communityGroups.firstOrNull { it.code == code }
                    if (group != null && sender in group.members) {
                        val id = packet.optString("id")
                        if (communityMessages.none { it.id == id }) {
                            communityMessages.add(CommunityChatMessage(id, code, sender, packet.optString("body").take(1500), System.currentTimeMillis()))
                            persistCommunityData()
                        }
                    }
                }
                "broadcast" -> communityNotice = "Broadcast from " + sender + ": " + packet.optString("body").take(500)
                "ludo_invite" -> {
                    val matchId = packet.optString("matchId")
                    if (activeGame == null && matchId.isNotBlank() && matchId.length <= 80) incomingGameInvite = GameInvite(matchId, sender)
                }
                "ludo_reject" -> activeGame?.let { game ->
                    if (game.matchId == packet.optString("matchId")) {
                        if (game.isHost) {
                            communityNotice = "$sender declined the invitation. Your Ludo room remains open."
                        } else if (sender == game.opponentName) {
                            activeGame = null
                            gameChatMessages.clear()
                            communityNotice = "The host can no longer add players to this Ludo room."
                        }
                    }
                }
                "ludo_accept" -> activeGame?.let { game ->
                    val rosterOpen = game.isHost && game.matchId == packet.optString("matchId") &&
                        game.state.turnNumber == 1L && game.state.diceValue == null &&
                        !game.state.isFinished && game.state.players.size < 4
                    if (rosterOpen && sender !in game.remotePlayers.values) {
                        val nextColor = LudoColor.entries.firstOrNull { color -> game.state.players.none { it.color == color } }
                        if (nextColor != null) {
                            val names = game.state.players.associate { it.color to it.name }.toMutableMap()
                            names[nextColor] = sender
                            val expanded = LudoEngine.newGame(names, playerColors = game.state.players.map { it.color } + nextColor)
                                .copy(currentPlayerIndex = game.state.currentPlayerIndex, turnNumber = game.state.turnNumber)
                            val remotes = game.remotePlayers + (nextColor to sender)
                            val updated = game.copy(
                                state = expanded,
                                opponentName = game.opponentName.takeIf { it != "Waiting for players…" } ?: sender,
                                ready = true,
                                revision = game.revision + 1L,
                                remotePlayers = remotes
                            )
                            activeGame = updated
                            sendLudoState(updated)
                            communityNotice = "$sender joined as ${nextColor.name}. ${expanded.players.size}/4 seats filled."
                        }
                    } else if (game.isHost && game.matchId == packet.optString("matchId") &&
                        sender !in game.remotePlayers.values) {
                        sendCommunityToNames(
                            "NR_COMMUNITY:" + JSONObject().put("type", "ludo_reject")
                                .put("matchId", game.matchId).put("reason", "room_closed"),
                            setOf(sender)
                        )
                    }
                }
                "ludo_state" -> activeGame?.let { game ->
                    val revision = packet.optLong("revision", -1L)
                    if (!game.isHost && game.matchId == packet.optString("matchId") &&
                        sender == game.opponentName && revision > game.revision) {
                        parseLudoState(packet.optJSONObject("state"))?.let { state ->
                            val assignedColor = runCatching { LudoColor.valueOf(packet.optString("localColor", game.localColor.name)) }.getOrDefault(game.localColor)
                            if (state.players.any { it.color == assignedColor }) {
                                activeGame = game.copy(state = state, localColor = assignedColor, ready = true, revision = revision)
                            }
                        }
                    }
                }
                "ludo_sync" -> activeGame?.let { game ->
                    if (game.isHost && game.matchId == packet.optString("matchId") &&
                        sender in game.remotePlayers.values) {
                        // A room may have several remote players. One player's reconnect
                        // must not prevent another player from syncing later.
                        val resumed = game.copy(ready = true, revision = game.revision + 1L)
                        activeGame = resumed
                        sendLudoState(resumed)
                        communityNotice = "$sender reconnected. Ludo board synchronised."
                    }
                }
                "ludo_rematch" -> activeGame?.let { game ->
                    if (game.isHost && game.matchId == packet.optString("matchId") &&
                        game.ready && game.state.isFinished && sender in game.remotePlayers.values &&
                        packet.optLong("revision", -1L) == game.revision) {
                        val reset = newNetworkLudoState(game)
                        activeGame = reset
                        gameChatMessages.clear()
                        sendLudoState(reset)
                        communityNotice = "$sender accepted a rematch. New game started!"
                    }
                }
                "ludo_action" -> activeGame?.let { game ->
                    if (game.isHost && game.ready && game.matchId == packet.optString("matchId")) {
                        val senderColor = game.remotePlayers.entries.firstOrNull { it.value == sender }?.key
                        if (senderColor != null && senderColor == game.state.currentPlayer.color &&
                            packet.optLong("revision", -1L) == game.revision) {
                            applyHostLudoAction(game, packet.optString("action"), packet.optInt("token", -1))
                        } else sendLudoState(game)
                    }
                }
                "ludo_chat" -> activeGame?.let { game ->
                    if (game.matchId == packet.optString("matchId") &&
                        (sender == game.opponentName || sender in game.remotePlayers.values)) {
                        val chatSender = packet.optString("sender").ifBlank { sender }.take(23)
                        val body = packet.optString("body").take(300)
                        gameChatMessages.add("$chatSender: $body")
                        while (gameChatMessages.size > 60) gameChatMessages.removeAt(0)
                        if (game.isHost) {
                            val otherPlayers = game.remotePlayers.values.filter { it != sender }.toSet()
                            if (otherPlayers.isNotEmpty()) {
                                sendCommunityToNames(
                                    "NR_COMMUNITY:" + JSONObject().put("type", "ludo_chat")
                                        .put("matchId", game.matchId).put("sender", chatSender).put("body", body),
                                    otherPlayers
                                )
                            }
                        }
                    }
                }
                "ludo_end" -> activeGame?.let { game ->
                    if (game.matchId == packet.optString("matchId")) {
                        if (!game.isHost && sender == game.opponentName) {
                            activeGame = null
                            gameChatMessages.clear()
                            communityNotice = "$sender ended the Ludo match."
                        } else if (game.isHost && sender in game.remotePlayers.values) {
                            removeLudoParticipant(game, sender)
                        }
                    }
                }
            }
        } catch (_: Exception) { communityNotice = "A community packet was invalid and ignored." }
    }

    private fun removeLudoParticipant(game: LudoNetworkMatch, sender: String) {
        val leavingColor = game.remotePlayers.entries.firstOrNull { it.value == sender }?.key ?: return
        if (game.state.isFinished) {
            val others = game.remotePlayers.values.filter { it != sender }.toSet()
            if (others.isNotEmpty()) sendCommunityToNames(
                "NR_COMMUNITY:" + JSONObject().put("type", "ludo_end").put("matchId", game.matchId),
                others
            )
            activeGame = null
            gameChatMessages.clear()
            communityNotice = "$sender left after the match ended. The table is now closed."
            return
        }
        val remainingPlayers = game.state.players.filterNot { it.color == leavingColor }
        val remainingRemotes = game.remotePlayers.filterValues { it != sender }
        if (remainingPlayers.size < 2) {
            val others = remainingRemotes.values.toSet()
            if (others.isNotEmpty()) {
                sendCommunityToNames(
                    "NR_COMMUNITY:" + JSONObject().put("type", "ludo_end").put("matchId", game.matchId),
                    others
                )
            }
            activeGame = null
            gameChatMessages.clear()
            communityNotice = "$sender left the table. The match has ended."
            return
        }
        val removedIndex = game.state.players.indexOfFirst { it.color == leavingColor }
        val currentWasRemoved = game.state.currentPlayer.color == leavingColor
        var nextIndex = game.state.currentPlayerIndex
        if (removedIndex < nextIndex) nextIndex -= 1
        if (currentWasRemoved && nextIndex >= remainingPlayers.size) nextIndex = 0
        val nextState = game.state.copy(
            players = remainingPlayers,
            currentPlayerIndex = nextIndex.coerceIn(0, remainingPlayers.lastIndex),
            diceValue = if (currentWasRemoved) null else game.state.diceValue,
            consecutiveSixes = if (currentWasRemoved) 0 else game.state.consecutiveSixes
        )
        val updated = game.copy(
            state = nextState,
            revision = game.revision + 1L,
            remotePlayers = remainingRemotes,
            opponentName = remainingRemotes.values.firstOrNull() ?: "Nearby player"
        )
        activeGame = updated
        sendLudoState(updated)
        communityNotice = "$sender left the table. The remaining players can continue."
    }

    private fun inviteToGame() {
        val existing = activeGame
        if (existing != null) {
            val canAdd = existing.isHost && existing.state.players.size < 4 &&
                existing.state.turnNumber == 1L && existing.state.diceValue == null && !existing.state.isFinished
            if (!canAdd) {
                communityNotice = "This Ludo table is already playing or has four players."
                return
            }
            val sent = sendCommunityPacket(JSONObject().put("type", "ludo_invite")
                .put("matchId", existing.matchId).put("sender", displayName))
            communityNotice = if (sent) "Invitation sent. ${existing.state.players.size}/4 seats filled."
                else "No additional nearby players are connected."
            return
        }
        if (!hybridConnected && !chatReady) {
            communityNotice = "Connect to nearby NeaRer phones first."
            return
        }
        val id = UUID.randomUUID().toString()
        val initial = LudoEngine.newGame(mapOf(LudoColor.RED to displayName), playerColors = listOf(LudoColor.RED))
        activeGame = LudoNetworkMatch(id, initial, LudoColor.RED, true, "Waiting for players…", false)
        gameChatMessages.clear()
        if (!sendCommunityPacket(JSONObject().put("type", "ludo_invite").put("matchId", id).put("sender", displayName))) {
            activeGame = null
        } else communityNotice = "Ludo room opened. Invite up to three nearby players."
    }

    private fun rejectGameInvite() {
        val invite = incomingGameInvite ?: return
        sendLudoPacket(
            JSONObject().put("type", "ludo_reject").put("matchId", invite.matchId).put("sender", displayName),
            setOf(invite.sender)
        )
        incomingGameInvite = null
    }

    private fun acceptGameInvite() {
        val invite = incomingGameInvite ?: return
        val initial = LudoEngine.newGame(
            mapOf(LudoColor.RED to invite.sender, LudoColor.YELLOW to displayName),
            playerColors = listOf(LudoColor.RED, LudoColor.YELLOW)
        )
        activeGame = LudoNetworkMatch(invite.matchId, initial, LudoColor.YELLOW, false, invite.sender, false)
        incomingGameInvite = null
        gameChatMessages.clear()
        val sent = sendLudoPacket(
            JSONObject().put("type", "ludo_accept").put("matchId", invite.matchId).put("sender", displayName),
            setOf(invite.sender)
        )
        if (!sent) {
            activeGame = null
            communityNotice = "The invitation could not reach the host. Reconnect and try again."
        } else communityNotice = "Joining ${invite.sender}'s Ludo table…"
    }

    private fun sendLudoState(match: LudoNetworkMatch) {
        val players = JSONArray()
        match.state.players.forEach { player ->
            players.put(JSONObject()
                .put("color", player.color.name)
                .put("name", player.name)
                .put("computer", player.isComputer)
                .put("tokens", JSONArray(player.tokens.map { it.steps })))
        }
        val snapshot = JSONObject()
            .put("players", players)
            .put("current", match.state.currentPlayerIndex)
            .put("dice", match.state.diceValue ?: 0)
            .put("winner", match.state.winner?.name ?: "")
            .put("turn", match.state.turnNumber)
            .put("sixes", match.state.consecutiveSixes)
        val recipients = if (match.isHost) match.remotePlayers.entries.map { it.value to it.key }
            else listOf(match.opponentName to match.localColor)
        var sentAny = false
        recipients.forEach { (name, color) ->
            val packet = JSONObject().put("type", "ludo_state").put("matchId", match.matchId)
                .put("revision", match.revision).put("localColor", color.name).put("state", snapshot)
            // Wi-Fi Direct fallback is a single-peer socket and may expose the Android
            // device name rather than the NeaRer profile name. The match ID and sender
            // checks still authenticate the room at the application layer.
            val sent = if (!hybridConnected && chatReady && match.isHost && match.remotePlayers.size == 1) {
                sendCommunityPacket(packet)
            } else {
                sendCommunityToNames("NR_COMMUNITY:$packet", setOf(name)) == true
            }
            if (sent) sentAny = true
        }
        if (!sentAny) communityNotice = "The Ludo board could not sync. Check that the nearby players are still connected."
    }

    private fun parseLudoState(snapshot: JSONObject?): LudoGameState? {
        snapshot ?: return null
        val array = snapshot.optJSONArray("players") ?: return null
        if (array.length() !in 2..4) return null
        val players = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val color = LudoColor.valueOf(item.getString("color"))
            val tokenArray = item.getJSONArray("tokens")
            if (tokenArray.length() != 4) throw IllegalArgumentException("Invalid token count")
            val tokens = (0 until 4).map { LudoToken(tokenArray.getInt(it)) }
            LudoPlayer(color, item.optString("name").take(20), item.optBoolean("computer"), tokens)
        }
        if (players.map { it.color }.distinct().size != players.size) return null
        val current = snapshot.optInt("current", -1)
        if (current !in players.indices) return null
        val rawDice = snapshot.optInt("dice", 0)
        if (rawDice !in 0..6) return null
        val dice = rawDice.takeIf { it in 1..6 }
        val winner = snapshot.optString("winner").takeIf { it.isNotBlank() }?.let { LudoColor.valueOf(it) }
        if (winner != null && players.none { it.color == winner }) return null
        val turn = snapshot.optLong("turn", 1L)
        if (turn < 1L) return null
        return LudoGameState(players, current, dice, winner, turn, snapshot.optInt("sixes", 0).coerceIn(0, 3))
    }

    private fun applyHostLudoAction(game: LudoNetworkMatch, action: String, token: Int) {
        try {
            val next = when (action) {
                "roll" -> LudoEngine.rollDice(game.state)
                "move" -> LudoEngine.moveToken(game.state, token).state
                "pass" -> LudoEngine.passTurn(game.state)
                else -> return
            }
            val updated = game.copy(state = next, ready = true, revision = game.revision + 1L)
            activeGame = updated
            sendLudoState(updated)
        } catch (_: Exception) {
            communityNotice = "That Ludo move was not legal. The board was not changed."
            sendLudoState(game)
        }
    }

    private fun playGameMove(action: String, token: Int) {
        val game = activeGame ?: return
        if (!game.ready || game.state.isFinished || game.state.currentPlayer.color != game.localColor) return
        if (game.isHost) {
            applyHostLudoAction(game, action, token)
        } else {
            val sent = sendLudoPacket(
                JSONObject().put("type", "ludo_action").put("matchId", game.matchId)
                    .put("action", action).put("token", token).put("revision", game.revision).put("sender", displayName),
                setOf(game.opponentName)
            )
            if (!sent) communityNotice = "Your move could not reach the host. Check the nearby connection."
        }
    }

    private fun resumeLudoAfterReconnect(peerName: String) {
        val game = activeGame ?: return
        val expectedPeer = if (hybridConnected) {
            if (game.isHost) peerName in game.remotePlayers.values else peerName == game.opponentName
        } else {
            // Wi-Fi Direct exposes only one live socket; its Android device label can
            // differ from the profile name stored in the Ludo room.
            chatReady && connectedDeviceName != null &&
                (if (game.isHost) game.remotePlayers.size == 1 else true)
        }
        if (!expectedPeer || game.ready) return
        if (game.isHost) {
            val resumed = game.copy(ready = true, revision = game.revision + 1L)
            activeGame = resumed
            sendLudoState(resumed)
        } else {
            // Keep the guest board locked until the host sends the authoritative snapshot.
            activeGame = game.copy(ready = false)
            val syncPacket = JSONObject().put("type", "ludo_sync")
                .put("matchId", game.matchId).put("revision", game.revision).put("sender", displayName)
            val sent = if (hybridConnected) {
                nearbyTransport?.sendToNames("NR_COMMUNITY:$syncPacket", setOf(game.opponentName)) == true
            } else {
                sendCommunityPacket(syncPacket)
            }
            if (!sent) communityNotice = "Reconnected, but the host has not resumed the match yet."
        }
        communityNotice = "Reconnected to $peerName. Restoring your Ludo match…"
    }

    private fun newNetworkLudoState(game: LudoNetworkMatch): LudoNetworkMatch {
        val names = game.state.players.associate { it.color to it.name }.toMutableMap()
        names[game.localColor] = if (game.isHost) displayName else names[game.localColor].orEmpty()
        val fresh = LudoEngine.newGame(names, playerColors = game.state.players.map { it.color })
        return game.copy(state = fresh, ready = true, revision = game.revision + 1L)
    }

    private fun requestGameRematch() {
        val game = activeGame ?: return
        if (!game.ready || !game.state.isFinished) return
        if (game.isHost) {
            val reset = newNetworkLudoState(game)
            activeGame = reset
            gameChatMessages.clear()
            sendLudoState(reset)
            communityNotice = "Rematch started. Good luck!"
        } else {
            val sent = sendLudoPacket(
                JSONObject().put("type", "ludo_rematch")
                    .put("matchId", game.matchId).put("revision", game.revision).put("sender", displayName),
                setOf(game.opponentName)
            )
            communityNotice = if (sent) "Rematch request sent to ${game.opponentName}."
                else "Could not request a rematch. Check the nearby connection."
        }
    }

    private fun endGame() {
        activeGame?.let { game ->
            sendLudoPacket(
                JSONObject().put("type", "ludo_end").put("matchId", game.matchId),
                (if (game.isHost) game.remotePlayers.values.toSet() else setOf(game.opponentName))
            )
        }
        activeGame = null
        incomingGameInvite = null
        gameChatMessages.clear()
        communityNotice = "Ludo match closed."
    }

    private fun sendGameChat(raw: String) {
        val game = activeGame ?: return
        val body = raw.trim().take(300)
        if (body.isNotBlank() && sendLudoPacket(
                JSONObject().put("type", "ludo_chat").put("matchId", game.matchId).put("sender", displayName).put("body", body),
                (if (game.isHost) game.remotePlayers.values.toSet() else setOf(game.opponentName))
            )) {
            gameChatMessages.add("$displayName: $body")
            while (gameChatMessages.size > 60) gameChatMessages.removeAt(0)
        }
    }

    private fun installReceivedAppBundle(item: ReceivedFile) {
        if (!item.isAppBundle) {
            chatNotice = "This received ZIP is not a NeaRer app bundle."
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            chatNotice = "Allow NeaRer to install apps in Android settings, then tap Install again."
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + packageName)))
            } catch (_: Exception) {
                chatNotice = "Open Android Settings and allow NeaRer to install unknown apps, then try again."
            }
            return
        }
        chatNotice = "Preparing the app bundle for Android's installer…"
        io.execute {
            var sessionId = -1
            try {
                ZipFile(item.file).use { archive ->
                    if (archive.getEntry("INSTALLATION.txt") == null) {
                        throw IllegalArgumentException("The app bundle installation guide is missing.")
                    }
                    val apkEntries = ArrayList<ZipEntry>()
                    val sessionNames = HashSet<String>()
                    val entries = archive.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!AppBundleEntryPolicy.isAllowedApkEntry(entry.name)) continue
                        if (entry.isDirectory || entry.size <= 0L) {
                            throw IllegalArgumentException("The app bundle contains an invalid APK entry.")
                        }
                        val sessionName = AppBundleEntryPolicy.sessionName(entry.name)
                        if (!sessionNames.add(sessionName)) {
                            throw IllegalArgumentException("The app bundle contains duplicate APK parts.")
                        }
                        apkEntries.add(entry)
                    }
                    if (apkEntries.none { it.name == "base.apk" }) {
                        throw IllegalArgumentException("The app bundle does not contain base.apk.")
                    }
                    if (apkEntries.size > 128) {
                        throw IllegalArgumentException("The app bundle contains too many APK parts.")
                    }
                    val totalBytes = apkEntries.fold(0L) { total, entry ->
                        Math.addExact(total, entry.size)
                    }
                    // NeaRer writes APK entries without compression. Reject suspicious
                    // archives whose expanded APK data is wildly larger than the received ZIP.
                    val archiveAllowance = Math.addExact(item.file.length(), apkEntries.size.toLong() * 1_048_576L)
                    if (totalBytes > archiveAllowance) {
                        throw IllegalArgumentException("The app bundle expands beyond its received archive size.")
                    }
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    if (totalBytes > 0L) params.setSize(totalBytes)
                    sessionId = packageManager.packageInstaller.createSession(params)
                    packageManager.packageInstaller.openSession(sessionId).use { session ->
                        apkEntries.forEach { entry ->
                            val sessionName = AppBundleEntryPolicy.sessionName(entry.name)
                            session.openWrite(sessionName, 0L, entry.size).use { output ->
                                archive.getInputStream(entry).use { input ->
                                    input.copyTo(output, 256 * 1024)
                                }
                                session.fsync(output)
                            }
                        }
                        val resultIntent = Intent(this, AppInstallResultActivity::class.java)
                            .putExtra("received_app_name", item.displayName)
                        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                        val callback = PendingIntent.getActivity(this, sessionId, resultIntent, pendingFlags)
                        session.commit(callback.intentSender)
                    }
                }
                runOnUiThread { chatNotice = "App bundle prepared. Android will ask you to confirm installation." }
            } catch (error: Exception) {
                if (sessionId >= 0) runCatching { packageManager.packageInstaller.abandonSession(sessionId) }
                runOnUiThread {
                    chatNotice = "Could not prepare this app for installation: " +
                        (error.localizedMessage ?: "invalid app bundle")
                }
            }
        }
    }

    private fun captureExternalShareIntent(sharedIntent: Intent?) {
        if (sharedIntent == null) return
        if (sharedIntent.action != Intent.ACTION_SEND && sharedIntent.action != Intent.ACTION_SEND_MULTIPLE) return
        val uris = linkedSetOf<Uri>()
        if (sharedIntent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            (sharedIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { uris.add(it) }
        } else {
            @Suppress("DEPRECATION")
            sharedIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { uris.add(it) }
        }
        sharedIntent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Many Android share providers grant temporary read access only.
            }
        }
        pendingExternalShareUris = (pendingExternalShareUris + uris).distinct()
        chatNotice = uris.size.toString() + " file(s) received from another app. Choose a nearby recipient in Share."
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureExternalShareIntent(intent)
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

    private fun forwardReceivedFile(item: ReceivedFile) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.files", item.file)
            sendFiles(listOf(uri))
        } catch (error: Exception) {
            chatNotice = "Could not prepare ${item.displayName} to send again: ${error.localizedMessage ?: "file access error"}"
        }
    }

    private fun saveReceivedFileToDownloads(item: ReceivedFile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            chatNotice = "Saving directly to shared media folders is supported on Android 10 and newer. You can still open this file and use your phone's Save or Share option."
            return
        }
        chatNotice = "Saving ${item.displayName} to your device…"
        io.execute {
            var destination: Uri? = null
            try {
                val extension = item.file.extension.lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
                val safeName = item.displayName.replace(Regex("[\\\\/:*?\\\"<>|]"), "_").ifBlank { "received-file" }
                val (collection, relativeFolder) = when {
                    mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to (Environment.DIRECTORY_PICTURES + "/NeaRer")
                    mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to (Environment.DIRECTORY_MOVIES + "/NeaRer")
                    mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to (Environment.DIRECTORY_MUSIC + "/NeaRer")
                    else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to (Environment.DIRECTORY_DOWNLOADS + "/NeaRer")
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                destination = contentResolver.insert(collection, values)
                    ?: throw IllegalStateException("Android could not create a saved-file entry")
                contentResolver.openOutputStream(destination!!, "w")?.use { output ->
                    item.file.inputStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                } ?: throw IllegalStateException("Android could not open the saved file")
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (contentResolver.update(destination!!, ready, null, null) <= 0) {
                    throw IllegalStateException("Android could not finish saving the file")
                }
                runOnUiThread { chatNotice = "Saved ${item.displayName} to ${relativeFolder.replace("/", " ")}." }
            } catch (error: Exception) {
                destination?.let { runCatching { contentResolver.delete(it, null, null) } }
                runOnUiThread { chatNotice = "Could not save ${item.displayName}: ${error.localizedMessage ?: "please try again"}" }
            }
        }
    }

    /** Deletes only archives generated by NeaRer folder sharing; ordinary user files are untouched. */
    private fun deleteGeneratedFolderArchive(uri: Uri?) {
        if (uri == null || uri.authority != packageName + ".files") return
        val fileName = uri.lastPathSegment ?: return
        if (!fileName.startsWith("nearer-folder-") || !fileName.endsWith(".zip")) return
        val archive = File(cacheDir, "nearer-folder-shares/$fileName")
        if (archive.exists()) archive.delete()
    }

    private fun cancelOutgoingFileTransfer() {
        val id = outgoingTransferId ?: return
        if (id.startsWith("nearby:")) {
            val payloadId = id.substringAfter("nearby:").toLongOrNull()
            val endpoint = hybridConnectedEndpoint
            if (payloadId != null && endpoint != null) nearbyTransport?.cancelFile(payloadId)
            outgoingTransferId = null
            outgoingProgress = 0f
            val discarded = pendingNearbyFileQueue.size
            deleteGeneratedFolderArchive(currentNearbyQueuedFile)
            pendingNearbyFileQueue.forEach(::deleteGeneratedFolderArchive)
            pendingNearbyFileQueue.clear()
            currentNearbyQueuedFile = null
            nearbyQueueTotal = 0
            nearbyQueueCompleted = 0
            nearbyQueuePeerStableId = null
            persistNearbyFileQueue()
            chatNotice = if (discarded > 0) "Transfer cancelled. " + discarded + " queued files were not sent." else "Cancelling nearby file transfer…"
            return
        }
        cancelledOutgoingTransfers.add(id)
        sendWireLine("FILE_CANCEL|$id")
        directFileQueue.clear()
        chatNotice = "Cancelling file transfer and clearing the remaining file queue…"
    }

    private fun prepareAndSendFolder(treeUri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Some document providers grant access only for the current app session.
        }
        chatNotice = "Preparing the folder archive. Large folders are streamed to storage; they are not loaded into memory."
        io.execute {
            var archive: File? = null
            try {
                val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
                var rootName = "Shared folder"
                contentResolver.query(rootUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) rootName = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: rootName
                }
                fun safeSegment(value: String): String {
                    val cleaned = value.replace("/", "_").replace("\\", "_").trim().trim('.')
                    return cleaned.ifBlank { "folder-item" }
                }
                val stagingDirectory = File(cacheDir, "nearer-folder-shares").apply { mkdirs() }
                archive = File.createTempFile("nearer-folder-", ".zip", stagingDirectory)
                val visited = HashSet<String>()
                var fileCount = 0
                ZipOutputStream(FileOutputStream(archive!!).buffered()).use { zip ->
                    fun addChildren(parentId: String, parentPath: String) {
                        if (!visited.add(parentId)) return
                        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                        val projection = arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        )
                        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                            while (cursor.moveToNext()) {
                                val childId = cursor.getString(idColumn) ?: continue
                                val childName = safeSegment(cursor.getString(nameColumn).orEmpty())
                                val childPath = parentPath + childName
                                val childMime = cursor.getString(mimeColumn)
                                if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                    zip.putNextEntry(ZipEntry(childPath + "/"))
                                    zip.closeEntry()
                                    addChildren(childId, childPath + "/")
                                } else {
                                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                    val input = contentResolver.openInputStream(childUri)
                                        ?: throw IllegalStateException("Could not read " + childName)
                                    zip.putNextEntry(ZipEntry(childPath))
                                    input.use { it.copyTo(zip, 256 * 1024) }
                                    zip.closeEntry()
                                    fileCount += 1
                                }
                            }
                        } ?: throw IllegalStateException("The selected folder could not be listed")
                    }
                    val safeRootName = safeSegment(rootName)
                    zip.putNextEntry(ZipEntry(safeRootName + "/"))
                    zip.closeEntry()
                    addChildren(rootId, safeRootName + "/")
                }
                if (fileCount == 0) throw IllegalStateException("The selected folder is empty or contains no readable files")
                val shareUri = FileProvider.getUriForFile(this, packageName + ".files", archive!!)
                runOnUiThread {
                    chatNotice = "Folder prepared: " + fileCount + " files. Sending as a ZIP so its folder structure is preserved."
                    sendFiles(listOf(shareUri))
                }
            } catch (error: Exception) {
                archive?.delete()
                runOnUiThread {
                    chatNotice = "Could not prepare this folder: " + (error.localizedMessage ?: "check folder access and try again")
                }
            }
        }
    }

    private fun sendFiles(uris: List<Uri>) {
        val selected = uris.distinct()
        if (selected.isEmpty()) return
        if (!hybridConnected) {
            if (!chatReady) {
                chatNotice = "Connect to the other phone before sending files."
                return
            }
            if (outgoingTransferId != null || directFileQueue.hasPending) {
                chatNotice = "Finish or cancel the current transfer job first."
                return
            }
            val readable = selected.filter { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                    // Temporary picker grants remain valid for this foreground session.
                }
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                } catch (_: Exception) {
                    false
                }
            }
            if (readable.isEmpty()) {
                chatNotice = "NeaRer could not access the selected files. Choose them again from a shareable location."
                return
            }
            directFileQueue.enqueue(readable)
            chatNotice = readable.size.toString() + " files queued for direct Wi-Fi sharing."
            if (readable.size != selected.size) {
                chatNotice += " " + (selected.size - readable.size) + " unavailable file(s) were skipped."
            }
            startNextDirectQueuedFile()
            return
        }
        if (outgoingTransferId != null || pendingNearbyFileQueue.isNotEmpty()) {
            chatNotice = "Finish or cancel the current transfer job first."
            return
        }
        val peerStableId = hybridConnectedStableId
        if (peerStableId.isNullOrBlank()) {
            chatNotice = "This phone's nearby identity is unavailable. Reconnect and try again before sending files."
            return
        }
        val readable = mutableListOf<Uri>()
        var unavailableCount = 0
        selected.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Some providers grant temporary access only; the current session may still read them.
            }
            val canRead = try {
                contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
            if (canRead) readable.add(uri) else unavailableCount += 1
        }
        if (readable.isEmpty()) {
            chatNotice = "NeaRer could not access the selected file(s). Choose them again from a location that allows sharing."
            return
        }
        readable.forEach { pendingNearbyFileQueue.addLast(it) }
        currentNearbyQueuedFile = null
        nearbyQueuePeerStableId = peerStableId
        nearbyQueueTotal = pendingNearbyFileQueue.size
        nearbyQueueCompleted = 0
        persistNearbyFileQueue()
        chatNotice = if (unavailableCount > 0) {
            "Preparing " + nearbyQueueTotal + " readable files. " + unavailableCount + " unavailable file(s) were skipped."
        } else {
            "Preparing " + nearbyQueueTotal + " files for one transfer job…"
        }
        startNextNearbyQueuedFile()
    }

    private fun startNextNearbyQueuedFile() {
        if (outgoingTransferId != null) return
        val uri = currentNearbyQueuedFile ?: pendingNearbyFileQueue.pollFirst()?.also { currentNearbyQueuedFile = it }
        if (uri == null) {
            val total = nearbyQueueTotal
            val completed = nearbyQueueCompleted
            nearbyQueueTotal = 0
            nearbyQueueCompleted = 0
            currentNearbyQueuedFile = null
            nearbyQueuePeerStableId = null
            persistNearbyFileQueue()
            if (total > 0) chatNotice = "Transfer job finished: " + completed + " of " + total + " files sent."
            return
        }
        val endpoint = hybridConnectedEndpoint
        if (!hybridConnected || endpoint == null) {
            persistNearbyFileQueue()
            chatNotice = "Connection lost. Your transfer is saved. Reconnect to continue."
            return
        }
        val expectedPeer = nearbyQueuePeerStableId
        if (expectedPeer.isNullOrBlank() || hybridConnectedStableId != expectedPeer) {
            chatNotice = "Your saved files are reserved for the phone you originally selected. Reconnect to that same phone to send them."
            return
        }
        persistNearbyFileQueue()
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
            outgoingTransferId = "nearby:" + payloadId
            outgoingProgress = 0f
            chatNotice = "Sending file " + (nearbyQueueCompleted + 1) + " of " + nearbyQueueTotal + ": " + fileName + "…"
        } else {
            persistNearbyFileQueue()
            chatNotice = "Could not start " + fileName + ". Reconnect to retry, or cancel the saved transfer."
        }
    }

    private fun persistNearbyFileQueue() {
        try {
            val items = JSONArray()
            currentNearbyQueuedFile?.let { items.put(it.toString()) }
            pendingNearbyFileQueue.forEach { items.put(it.toString()) }
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit()
                .putString("nearby_file_queue", items.toString())
                .putInt("nearby_file_queue_total", nearbyQueueTotal)
                .putInt("nearby_file_queue_completed", nearbyQueueCompleted)
                .putString("nearby_file_queue_peer", nearbyQueuePeerStableId)
                .commit()
        } catch (_: Exception) {
            chatNotice = "NeaRer could not save the transfer queue. Keep this screen open until sending finishes."
        }
    }

    private fun restoreNearbyFileQueue() {
        try {
            val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val items = JSONArray(prefs.getString("nearby_file_queue", "[]") ?: "[]")
            val inaccessible = mutableListOf<String>()
            for (i in 0 until items.length()) {
                val raw = items.optString(i)
                if (raw.isBlank()) continue
                val uri = Uri.parse(raw)
                val readable = try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                } catch (_: Exception) {
                    false
                }
                if (readable) pendingNearbyFileQueue.addLast(uri) else inaccessible.add(uri.lastPathSegment ?: "selected file")
            }
            nearbyQueueCompleted = prefs.getInt("nearby_file_queue_completed", 0).coerceAtLeast(0)
            nearbyQueueTotal = prefs.getInt("nearby_file_queue_total", pendingNearbyFileQueue.size + nearbyQueueCompleted)
                .coerceAtLeast(nearbyQueueCompleted + pendingNearbyFileQueue.size)
            nearbyQueuePeerStableId = prefs.getString("nearby_file_queue_peer", null)
            if (inaccessible.isNotEmpty()) {
                nearbyQueueTotal = nearbyQueueCompleted + pendingNearbyFileQueue.size
                persistNearbyFileQueue()
                chatNotice = inaccessible.size.toString() + " saved file(s) are no longer accessible. " +
                    pendingNearbyFileQueue.size + " readable file(s) remain in the queue."
            } else if (pendingNearbyFileQueue.isNotEmpty()) {
                chatNotice = if (nearbyQueuePeerStableId.isNullOrBlank()) {
                    "An older saved transfer has no verified recipient. Cancel it and select the files again for safety."
                } else {
                    "A saved transfer is waiting. Reconnect to the same phone to continue."
                }
            }
        } catch (_: Exception) {
            pendingNearbyFileQueue.clear()
            currentNearbyQueuedFile = null
            nearbyQueueTotal = 0
            nearbyQueueCompleted = 0
            nearbyQueuePeerStableId = null
            chatNotice = "A saved transfer list could not be restored. Please select those files again."
        }
    }

    /** Sends a legacy Wi-Fi Direct multi-select basket one file at a time. */
    private fun startNextDirectQueuedFile() {
        if (outgoingTransferId != null || !chatReady) return
        val uri = directFileQueue.currentOrNext()
        if (uri == null) {
            val total = directFileQueue.total
            val completed = directFileQueue.completed
            directFileQueue.clear()
            if (total > 0) chatNotice = "Transfer job finished: " + completed + " of " + total + " files delivered and verified."
            return
        }
        var fileName = "shared-file"
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) fileName = cursor.getString(index) ?: fileName
                }
            }
        }
        chatNotice = "Sending file " + (directFileQueue.completed + 1) + " of " + directFileQueue.total + ": " + fileName + "…"
        sendFile(uri)
    }

    /** Advance only after the receiver confirms exact size and SHA-256. */
    private fun completeDirectQueuedFile(success: Boolean) {
        if (!directFileQueue.hasPending) return
        if (!success) {
            chatNotice = "The receiver could not verify this file. It remains queued; reconnect and retry."
            return
        }
        directFileQueue.completeCurrent()
        if (directFileQueue.hasPending) {
            startNextDirectQueuedFile()
        } else {
            val total = directFileQueue.total
            val completed = directFileQueue.completed
            directFileQueue.clear()
            chatNotice = "Transfer job finished: " + completed + " of " + total + " files delivered and verified."
        }
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
                val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open selected file")
                input.use { stream ->
                    val id = transferId
                    val encodedName = Base64.getEncoder().encodeToString(fileName.toByteArray(Charsets.UTF_8))
                    if (!sendWireLine("FILE_START|$id|$encodedName|$fileSize")) throw IllegalStateException("Connection unavailable")
                    pendingDirectOutgoing[id] = DirectOutgoingTransfer(fileName, fileSize)
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
                pendingDirectOutgoing.remove(transferId)?.let { failed ->
                    runOnUiThread { recordNearbyTransfer("Sent", failed.fileName, failed.bytes, "Interrupted") }
                }
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
                                if (id.isNotBlank() && id.length <= 80 && body.isNotBlank() && body.length <= 4000) {
                                    runOnUiThread {
                                        val alreadySaved = chatMessages.any { it.id == id }
                                        if (alreadySaved) {
                                            receivedMessageIds.add(id)
                                            synchronized(writerLock) { peerWriter?.println("ACK|$id") }
                                        } else {
                                            chatMessages.add(ChatMessage(
                                                id, body, outgoing = false, delivery = "Received",
                                                conversationId = connectedDeviceName?.let { "direct:$it" }.orEmpty(),
                                                conversationName = connectedDeviceName.orEmpty()
                                            ))
                                            if (persistMessages()) {
                                                trimChatMessages()
                                                receivedMessageIds.add(id)
                                                chatNotice = "New message received and saved directly."
                                                // Confirm only after the message is durably saved.
                                                synchronized(writerLock) { peerWriter?.println("ACK|$id") }
                                            } else {
                                                chatMessages.removeAll { it.id == id }
                                                receivedMessageIds.remove(id)
                                                chatNotice = "Message could not be saved. The sender has not been told it was delivered."
                                            }
                                        }
                                    }
                                }
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
                                if (expected < 0L) {
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
                                        if (transfer.expectedBytes > 0L && transfer.receivedBytes > transfer.expectedBytes) {
                                            incomingTransfers.remove(parts[1])?.close()
                                            transfer.file.delete()
                                            runOnUiThread {
                                                incomingTransferName = null
                                                incomingProgress = 0f
                                                chatNotice = "The received file was larger than its declared size. Transfer stopped."
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
                                            recordNearbyTransfer("Received", transfer.name, transfer.receivedBytes, "Complete")
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
                            val completed = transferId?.let { pendingDirectOutgoing.remove(it) }
                            if (completed != null) {
                                runOnUiThread {
                                    recordNearbyTransfer("Sent", completed.fileName, completed.bytes, if (ok) "Complete" else "Not verified")
                                }
                            }
                            runOnUiThread {
                                if (!ok && transferId != null && outgoingTransferId == transferId) {
                                    cancelledOutgoingTransfers.add(transferId)
                                }
                                if (outgoingTransferId == transferId) {
                                    outgoingTransferId = null
                                    outgoingProgress = 0f
                                }
                                val completesDirectQueueItem = completed != null && outgoingTransferId == transferId && directFileQueue.hasPending
                                if (completesDirectQueueItem) completeDirectQueuedFile(ok)
                                else chatNotice = if (ok) "File delivered and verified by the nearby phone." else "The other phone could not verify the file."
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
                        line.startsWith("COMMUNITY|") -> {
                            val encoded = line.substringAfter("COMMUNITY|")
                            try {
                                val body = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                                if (body.startsWith("NR_COMMUNITY:") && body.length <= 24 * 1024) {
                                    runOnUiThread {
                                        handleCommunityPacket(connectedDeviceName ?: "Nearby member", body.removePrefix("NR_COMMUNITY:"))
                                        persistCommunityData()
                                    }
                                }
                            } catch (_: Exception) {
                                runOnUiThread { communityNotice = "A nearby game or community packet could not be read." }
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
                        activeGame = activeGame?.let { it.copy(ready = false) }
                        incomingGameInvite = null
                        chatNotice = if (activeGame != null) "Connection paused. Reconnect to resume your Ludo match." else "The nearby connection ended. Reconnect to continue chatting."
                        if (activeGame != null) communityNotice = "Ludo paused — reconnect to your opponent to resume."
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
    externalShareUris: List<Uri>,
    onExternalShareConsumed: () -> Unit,
    transferHistory: List<NearbyTransferRecord>,
    onSendMessage: (String) -> Unit,
    groups: List<CommunityGroup>,
    groupMessages: List<CommunityChatMessage>,
    selectedGroupCode: String?,
    communityNotice: String,
    incomingGameInvite: GameInvite?,
    activeGame: LudoNetworkMatch?,
    gameChatMessages: List<String>,
    onCreateGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onSelectGroup: (String) -> Unit,
    onSendGroupMessage: (String) -> Unit,
    onBroadcast: (String) -> Unit,
    onGameInvite: () -> Unit,
    onAcceptGameInvite: () -> Unit,
    onRejectGameInvite: () -> Unit,
    onGameMove: (String, Int) -> Unit,
    onGameChat: (String) -> Unit,
    onGameRematch: () -> Unit,
    onEndGame: () -> Unit,
    onSendFiles: (List<Uri>) -> Unit,
    onSendFolder: (Uri) -> Unit,
    onCancelTransfer: () -> Unit,
    outgoingTransferId: String?,
    outgoingProgress: Float,
    incomingTransferName: String?,
    incomingProgress: Float,
    onOpenFile: (ReceivedFile) -> Unit,
    onForwardReceivedFile: (ReceivedFile) -> Unit,
    onSaveReceivedFile: (ReceivedFile) -> Unit,
    onInstallAppBundle: (ReceivedFile) -> Unit,
    onDisconnect: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    hybridPeers: Map<String, String>,
    recentlySeenPeers: Map<String, NearbyPerson>,
    displayName: String,
    nearbyIdentityId: String,
    ultraSpeedMode: Boolean,
    ultraSpeedRestartRequired: Boolean,
    onUltraSpeedModeChange: (Boolean) -> Unit,
    onSaveDisplayName: (String) -> Unit,
    meshRelayEnabled: Boolean,
    onMeshRelayChange: (Boolean) -> Unit,
    onHybridConnect: (String) -> Unit,
    onFindNearby: () -> Unit,
    onStopSearch: () -> Unit,
    onConnect: (WifiP2pDevice) -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Chats") }
    var selectedConversationKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(externalShareUris) { if (externalShareUris.isNotEmpty()) selectedTab = "Share" }
    var showWifiDirectFallback by remember { mutableStateOf(false) }
    var nameDraft by remember(displayName) { mutableStateOf(displayName) }
    val fastShare = remember(context) { FastShareController(context) }
    val unifiedTransferHistory = remember(transferHistory, fastShare.history) {
        (transferHistory.map { UnifiedTransferHistoryEntry(it.direction, it.fileName, it.bytes, it.status, it.timestamp, "Nearby") } +
            fastShare.history.map { UnifiedTransferHistoryEntry("Fast Share", it.fileName, it.bytesTransferred, it.result, it.timestampMillis, "Fast Share") })
            .sortedByDescending { it.timestamp }
    }
    var pendingFastShareUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val fastSharePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allowed = grants.values.all { it }
        val pending = pendingFastShareUris
        pendingFastShareUris = emptyList()
        if (allowed && pending.isNotEmpty()) fastShare.start(pending)
        else if (!allowed) android.widget.Toast.makeText(context, "Nearby Wi-Fi permission is needed for Fast Share.", android.widget.Toast.LENGTH_LONG).show()
    }
    val fastSharePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) fastShare.start(uris)
            else {
                pendingFastShareUris = uris
                fastSharePermissionLauncher.launch(arrayOf(permission))
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(fastShare) {
        onDispose { fastShare.stop() }
    }
    val searchMotion = rememberInfiniteTransition(label = "nearby-search")
    val orbitRotation by searchMotion.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing)), label = "orbit")
    val pulse by searchMotion.animateFloat(0.72f, 1f, infiniteRepeatable(tween(1050), RepeatMode.Reverse), label = "pulse")
    // Native Android sharing allows Bluetooth/Quick Share recipients without NeaRer.
    val anyPhonePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(uri) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "File to share", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Send file to another phone"))
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, "Could not open sharing. Please try again.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val anyPhoneMultiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    val first = uris.first()
                    clipData = ClipData.newUri(context.contentResolver, "Files to share", first).apply {
                        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Send files to another phone"))
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, "Could not open multi-file sharing. Please try again.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Midnight) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().then(if (selectedTab == "Share") Modifier else Modifier.verticalScroll(rememberScrollState())).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nea", color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
                        Text("R", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
                        Text("er", color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
                    }
                    Text("NeaRer You, NeaRer Everything", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.35.sp)
                }
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.nearer_logo),
                    contentDescription = "NeaRer logo",
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                )
            }

            if (selectedTab == "Home") {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Panel) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("YOUR OFFLINE CONNECTION", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text("Stay close. Share freely.", color = White, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.nearer_logo),
                                contentDescription = "NeaRer logo",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Text("Find people around you, chat and share files — without mobile data.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                        Button(
                            onClick = { selectedTab = "Nearby"; onFindNearby() },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                        ) { Text(if (scanning) "Finding people…" else "Find people nearby", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { selectedTab = "Share" },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD12D), contentColor = Midnight)
                        ) { Text("Open phone file centre", fontWeight = FontWeight.Bold) }
                        Text(
                            if (chatReady) "Choose photos, videos, music or documents. You can browse the phone at any time."
                            else "Browse your phone now; connect to a nearby NeaRer user when you are ready to send.",
                            color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                        )
                        Button(
                            onClick = { fastSharePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                        ) { Text("Fast Share • multiple large files", fontWeight = FontWeight.Bold) }
                        Text("Send a large video to a phone without NeaRer using a temporary local Wi-Fi connection. No mobile data.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        Text(fastShare.status, color = if (fastShare.details != null) BlueAccent else Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        if (fastShare.activeTransfers.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().background(Midnight, RoundedCornerShape(16.dp)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("LIVE DOWNLOADS · ${fastShare.activeTransfers.count { !it.isQueued }}/5 · ${fastShare.activeTransfers.count { it.isQueued }} waiting", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                fastShare.activeTransfers.forEach { transfer ->
                                    val percent = if (transfer.totalBytes > 0L) ((transfer.bytesSent.toDouble() / transfer.totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100) else -1
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(transfer.fileName, color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                if (transfer.isQueued) "Waiting for a download slot…"
                                                else if (percent >= 0) "${formatTransferBytes(transfer.bytesSent)} / ${formatTransferBytes(transfer.totalBytes)} · $percent%"
                                                else "${formatTransferBytes(transfer.bytesSent)} sent · size unknown",
                                                color = Muted, fontSize = 10.sp
                                            )
                                            Text("${formatTransferBytes(transfer.bytesPerSecond)}/s", color = BlueAccent, fontSize = 10.sp)
                                        }
                                        if (percent >= 0 && !transfer.isQueued) {
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { percent / 100f },
                                                modifier = Modifier.fillMaxWidth().height(5.dp),
                                                color = Gold,
                                                trackColor = Panel
                                            )
                                        } else {
                                            androidx.compose.material3.LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth().height(5.dp),
                                                color = BlueAccent,
                                                trackColor = Panel
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (unifiedTransferHistory.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("RECENT TRANSFERS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Clear Fast Share", color = Muted, fontSize = 10.sp, modifier = Modifier.clickable { fastShare.clearHistory() })
                            }
                            unifiedTransferHistory.take(8).forEach { entry ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${entry.source} · ${entry.direction} · ${entry.fileName}", color = White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatTransferBytes(entry.bytes)} · ${entry.result}", color = Muted, fontSize = 10.sp)
                                    }
                                    Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(entry.timestamp)), color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                        fastShare.details?.let { share ->
                            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Midnight) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("CONNECT A PHONE OR COMPUTER", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("1. On the receiving phone or computer, join this Wi-Fi network:", color = Muted, fontSize = 12.sp)
                                    Text(share.ssid, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Text("Password: ${share.password}", color = White, fontSize = 14.sp)
                                    Text("2. Open a web browser on that device and visit:", color = Muted, fontSize = 12.sp)
                                    Text(share.url, color = BlueAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("3. Choose the files to download. Keep NeaRer open and stay connected until they finish.", color = Muted, fontSize = 12.sp)
                                    Button(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("NeaRer Fast Share link", share.url))
                                        android.widget.Toast.makeText(context, "Download link copied.", android.widget.Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = BlueAccent)) {
                                        Text("Copy download link")
                                    }
                                    Button(onClick = { fastShare.stop() }, modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)) {
                                        Text("Stop Fast Share")
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { anyPhonePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = BlueAccent)
                        ) { Text("Send to any phone • no NeaRer needed", fontWeight = FontWeight.Bold) }
                        Text("Choose a file, then select Bluetooth or Quick Share. The receiving phone does not need NeaRer.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        Button(
                            onClick = { anyPhoneMultiplePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)
                        ) { Text("Send multiple files to any phone", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = {
                                try {
                                    val shareFolder = java.io.File(context.cacheDir, "nearer-share").apply { mkdirs() }
                                    val apkCopy = java.io.File(shareFolder, "NeaRer.apk")
                                    java.io.File(context.applicationInfo.sourceDir).copyTo(apkCopy, overwrite = true)
                                    val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkCopy)
                                    val apkIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.android.package-archive"
                                        putExtra(Intent.EXTRA_STREAM, apkUri)
                                        clipData = ClipData.newUri(context.contentResolver, "NeaRer APK", apkUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(apkIntent, "Share NeaRer APK"))
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Could not prepare the NeaRer APK to share.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)
                        ) { Text("Share NeaRer app (APK)", fontWeight = FontWeight.Bold) }
                    }
                }
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("YOUR NEARBY NAME", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it.take(24) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Choose a name people will see", color = Muted) },
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = { onSaveDisplayName(nameDraft) },
                            enabled = nameDraft.trim().isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = BlueAccent)
                        ) { Text("Save my nearby name") }
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Midnight))
                        Text("YOUR NEARER ID", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("NR-$nearbyIdentityId", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("This is your public nearby identity. Share it so people can recognise the right NeaRer profile.", color = Muted, fontSize = 12.sp)
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("NeaRer ID", "NR-$nearbyIdentityId"))
                                android.widget.Toast.makeText(context, "NeaRer ID copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)
                        ) { Text("Copy my NeaRer ID") }
                    }
                }
            }

            if (selectedTab == "Share") {
                FileCentreScreen(connected = chatReady || usingNearbyConnections, onSendFiles = onSendFiles, externalShareUris = externalShareUris, onExternalShareConsumed = onExternalShareConsumed, modifier = Modifier.weight(1f))
            }

            if (selectedTab == "Nearby") {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Panel) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("NEARBY CONNECTION", color = BlueAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Find your people nearby.", color = White, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "NeaRer advertises your chosen name while searching for other NeaRer phones. Nearby Connections can use Bluetooth and nearby Wi-Fi automatically; Wi-Fi Direct is an additional discovery path. Hotspot and mobile data are not required.",
                        color = Muted, fontSize = 14.sp, lineHeight = 21.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onFindNearby, modifier = Modifier.weight(1f).height(52.dp), enabled = !scanning,
                            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)) {
                            Text(if (scanning) "Searching…" else "Find people / Receive", fontWeight = FontWeight.Bold)
                        }
                        if (scanning) {
                            Button(onClick = onStopSearch, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = White)) { Text("Stop") }
                        }
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = Midnight, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ULTRA SPEED MODE", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(if (ultraSpeedMode) "Enabled • one-to-one connection" else "Standard • flexible multi-device", color = White, fontSize = 13.sp)
                                }
                                Button(onClick = { onUltraSpeedModeChange(!ultraSpeedMode) }, enabled = !scanning && !chatReady && !usingNearbyConnections && !ultraSpeedRestartRequired,
                                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (ultraSpeedMode) Gold else BlueAccent, contentColor = Midnight)) {
                                    Text(if (ultraSpeedMode) "Turn off" else "Enable")
                                }
                            }
                            Text(
                                if (ultraSpeedMode) "Both phones must enable this mode and restart NeaRer before searching. It uses Google's one-to-one highest-throughput connection strategy. It may improve speed, but 280 MB/s is not guaranteed." else "Standard mode keeps flexible multi-device connections. For a single large-file transfer, both phones can enable Ultra Speed Mode, which uses Google's highest-throughput one-to-one strategy.",
                                color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                            )
                        }
                    }
                    Text("To receive a file from another NeaRer phone, tap Find people / Receive and keep this screen open. Accept the request when it appears. Phones without NeaRer can receive files through Bluetooth or Quick Share instead.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Text(status, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Surface(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp), color = if (chatReady) Color(0xFF173C3A) else Panel) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CONNECTION STATUS", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        if (chatReady) Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFF174B43), modifier = Modifier.alpha(pulse)) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("✓", color = BlueAccent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Text("CONNECTED", color = BlueAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(connectedDeviceName?.let { "You are connected to $it" } ?: connectionMessage, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(connectionMessage, color = Muted, fontSize = 11.sp)
                    Text(approvalMessage, color = if (chatReady) BlueAccent else Muted, fontSize = 12.sp)
                    pairingCode?.let { code ->
                        Text("PAIRING CODE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(code, color = BlueAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
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

            }

            if (selectedTab == "Chats") {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("YOUR CHATS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Keep your people close.", color = White, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Surface(shape = RoundedCornerShape(30.dp), color = if (chatReady) Color(0xFF174B43) else Panel) {
                        Row(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(modifier = Modifier.size(7.dp), shape = CircleShape, color = if (chatReady) Gold else Muted) {}
                            Text(if (chatReady) "CONNECTED" else "OFFLINE", color = if (chatReady) Gold else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                        }
                    }
                }
                Text("Private conversations and nearby sharing, even without mobile data.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Panel) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PRIVATE CHAT", color = BlueAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(if (chatReady) "LOCAL LINK" else "NOT CONNECTED", color = Muted, fontSize = 9.sp)
                    }
                    val conversations = ConversationIndex.build(messages)
                    val activeConversation = conversations.firstOrNull { it.conversationId == selectedConversationKey }
                        ?: conversations.firstOrNull()
                    if (conversations.isEmpty()) {
                        Text(
                            "No conversations yet. Find someone nearby and connect to start your first private chat.",
                            color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                        )
                    } else {
                        Text("RECENT CONVERSATIONS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        conversations.forEach { conversation ->
                            val latest = conversation.lastMessage
                            val isSelected = conversation.conversationId == activeConversation?.conversationId
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedConversationKey = conversation.conversationId },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) BrandBlue.copy(alpha = 0.42f) else Midnight
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(38.dp)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text(conversation.displayName.take(1).uppercase(), color = Gold, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(conversation.displayName, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(latest.body, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(formatChatTime(latest.timestamp), color = Muted, fontSize = 9.sp)
                                        Text("${conversation.messageCount}", color = if (isSelected) Gold else Muted, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().height(220.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            activeConversation?.let { ConversationIndex.messagesFor(messages, it.conversationId) }?.forEach { message ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start
                                ) {
                                    Surface(
                                        color = if (message.outgoing) BrandBlue.copy(alpha = 0.82f) else Midnight,
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                            Text(message.body, color = White, fontSize = 14.sp)
                                            Text(
                                                "${if (message.outgoing) message.delivery else "Received"} • ${formatChatTime(message.timestamp)}",
                                                color = if (message.outgoing) Gold else BlueAccent, fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(if (chatReady) chatNotice else "Chat history is saved on this phone. Connect nearby to send or receive new messages.", color = Muted, fontSize = 10.sp)
                        if (transferHistory.isNotEmpty()) {
                            Text("Recent transfers", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            transferHistory.take(5).forEach { record ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${if (record.direction == "Sent") "↑ Sent" else "↓ Received"} • ${record.fileName}", color = White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatTransferBytes(record.bytes)} • ${record.status}", color = Muted, fontSize = 9.sp)
                                    }
                                    Text(record.timeLabel, color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                        receivedFiles.take(3).forEach { fileItem ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(fileItem.displayName, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Button(
                                        onClick = { onOpenFile(fileItem) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = White)
                                    ) { Text("Open", maxLines = 1) }
                                    Button(
                                        onClick = { onSaveReceivedFile(fileItem) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                                    ) { Text("Save to device", maxLines = 1) }
                                    if (fileItem.isAppBundle) {
                                        Button(
                                            onClick = { onInstallAppBundle(fileItem) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                                        ) { Text("Install", maxLines = 1) }
                                    }
                                    Button(
                                        onClick = { onForwardReceivedFile(fileItem) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                                    ) { Text("Send again", maxLines = 1) }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it.take(2000) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (chatReady) "Write a message…" else "Connect to send a message", color = Muted) },
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                enabled = chatReady
                            )
                            Button(
                                onClick = { onSendMessage(draft); draft = "" },
                                enabled = draft.isNotBlank() && chatReady,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                            ) { Text("Send", fontWeight = FontWeight.Bold) }
                        }
                        incomingTransferName?.let { incomingName ->
                            Text("Receiving $incomingName", color = Muted, fontSize = 11.sp)
                            LinearProgressIndicator(
                                progress = { incomingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = BlueAccent,
                                trackColor = Panel
                            )
                        }
                        if (outgoingTransferId != null) {
                            LinearProgressIndicator(
                                progress = { outgoingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = BlueAccent,
                                trackColor = Panel
                            )
                            Button(
                                onClick = onCancelTransfer,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)
                            ) { Text("Cancel file transfer") }
                        }
                        Button(
                            onClick = { selectedTab = "Share" },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                        ) { Text("Open phone file centre", fontWeight = FontWeight.Bold) }
                        Text(
                            "Browse pictures, videos, music, apps, documents, downloads and folders. Select several items and send them together.",
                            color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    if (!chatReady) {
                        Text("Connect to a nearby NeaRer phone to send messages. Your saved conversations remain visible while offline.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        Button(
                            onClick = { selectedTab = "Nearby"; onFindNearby() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                        ) { Text("Find people nearby", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            }

            if (selectedTab == "Games") {
            CommunityHub(groups, groupMessages, selectedGroupCode, communityNotice, incomingGameInvite,
                activeGame, gameChatMessages, (usingNearbyConnections || chatReady), onCreateGroup, onJoinGroup, onSelectGroup,
                onSendGroupMessage, onBroadcast, onGameInvite, onAcceptGameInvite,
                onRejectGameInvite, onGameMove, onGameChat, onGameRematch, onEndGame)

            }

            if (selectedTab == "Nearby") {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("COMMUNITY MESH RELAY", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Multi-hop delivery is being upgraded with recipient routing and end-to-end encryption.", color = White, fontSize = 12.sp, lineHeight = 17.sp)
                        Text("Temporarily paused for privacy. Direct phone-to-phone chat still works.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    androidx.compose.material3.Switch(checked = false, onCheckedChange = { }, enabled = false)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEARBY DEVICES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("${devices.size} found", color = BlueAccent, fontSize = 11.sp)
            }

            Surface(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp), color = Panel) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PEOPLE AROUND YOU", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (hybridPeers.isEmpty()) "Your nearby circle" else "${hybridPeers.size} active nearby", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (scanning && hybridPeers.isEmpty()) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(142.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(118.dp).alpha(pulse)) {}
                            Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(82.dp).rotate(orbitRotation)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.TopCenter) { Surface(shape = CircleShape, color = BlueAccent, modifier = Modifier.size(12.dp)) {} }
                            }
                            Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(54.dp)) {
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
                                    Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(54.dp).rotate(drift)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(entry.value.take(1).uppercase(), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                                    }
                                    Text(entry.value, color = White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("Active now", color = BlueAccent, fontSize = 9.sp)
                                }
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(78.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(68.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text("•••", color = BlueAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Text("Nearby names will appear here as phones are discovered.", color = Muted, fontSize = 11.sp)
                    }
                    AnimatedVisibility(visible = recentlySeenPeers.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("RECENTLY SEEN ON THIS PHONE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            recentlySeenPeers.values.sortedByDescending { it.lastSeenAt }.take(5).forEach { person ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(32.dp)) {
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
                    Text("CONNECT WITH A NEARBY PERSON", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    hybridPeers.forEach { (id, name) ->
                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Panel) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(38.dp)) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = White, fontWeight = FontWeight.Bold) }
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(name, color = White, fontWeight = FontWeight.SemiBold)
                                    Text("Active now • Nearby Connections", color = BlueAccent, fontSize = 10.sp)
                                }
                                Button(onClick = { onHybridConnect(id) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)) { Text("Connect") }
                            }
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Panel) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = { showWifiDirectFallback = !showWifiDirectFallback }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = White)) {
                        Text(if (showWifiDirectFallback) "Hide other connection options" else "Other connection options • Wi-Fi Direct (" + devices.size + ")")
                    }
                    Text("Use this only if the NeaRer nearby-name connection does not appear. Both phones need Wi-Fi Direct support.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    AnimatedVisibility(visible = showWifiDirectFallback) {
                        if (devices.isEmpty()) {
                            Text("No Wi-Fi Direct phones found yet. Tap Find people / Receive to scan again.", color = Muted, fontSize = 12.sp)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                devices.forEach { device ->
                                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Midnight) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(device.deviceName.ifBlank { "Nearby Android device" }, color = White, fontWeight = FontWeight.SemiBold)
                                                Text(deviceStatus(device.status), color = Muted, fontSize = 11.sp)
                                            }
                                            Button(onClick = { onConnect(device) }, enabled = connectingAddress == null && connectedDeviceName == null && device.status != WifiP2pDevice.UNAVAILABLE, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)) {
                                                Text(if (connectingAddress == device.deviceAddress) "Requesting…" else "Connect", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            if (selectedTab == "More") {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Panel) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("YOUR NEARER", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("Everything closer, even without mobile data.", color = White, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Your nearby identity and direct connections stay on this phone. NeaRer’s core nearby chat and file sharing are designed to work without mobile data.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                        Button(
                            onClick = {
                                try {
                                    val shareFolder = java.io.File(context.cacheDir, "nearer-share").apply { mkdirs() }
                                    val apkCopy = java.io.File(shareFolder, "NeaRer.apk")
                                    java.io.File(context.applicationInfo.sourceDir).copyTo(apkCopy, overwrite = true)
                                    val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkCopy)
                                    val apkIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.android.package-archive"
                                        putExtra(Intent.EXTRA_STREAM, apkUri)
                                        clipData = ClipData.newUri(context.contentResolver, "NeaRer APK", apkUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(apkIntent, "Share NeaRer APK"))
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Could not prepare the NeaRer APK to share.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                        ) { Text("Share NeaRer APK", fontWeight = FontWeight.Bold) }
                        Text("Send the installed NeaRer app file using Bluetooth, Quick Share or another sharing app. The other phone can install it.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Try NeaRer")
                                    putExtra(Intent.EXTRA_TEXT, "Let's connect with NeaRer — chat and share files nearby without mobile data. Get the project here: https://github.com/giftrush30-hub/Nearer")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share NeaRer link"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                        ) { Text("Share NeaRer link", fontWeight = FontWeight.Bold) }
                    }
                }
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Panel) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CONNECTION & PRIVACY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Direct nearby chat is available after both phones approve a connection. Multi-hop relay remains paused while secure recipient routing is developed.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f, fill = false))
            Text("NEARER  •  ${selectedTab.uppercase()}", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Panel,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Chats" to "▤", "Share" to "⇧", "Games" to "♟").forEach { item ->
                        val selected = selectedTab == item.first
                        Surface(
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = if (selected) BrandBlue.copy(alpha = 0.42f) else Panel
                        ) {
                            Column(
                                modifier = Modifier.clickable { selectedTab = item.first }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(item.second, color = if (selected) Gold else Muted, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                Text(item.first, color = if (selected) White else Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                Surface(
                                    modifier = Modifier.width(16.dp).height(2.dp),
                                    shape = RoundedCornerShape(2.dp),
                                    color = if (selected) Gold else Panel
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }

    // Connection approval is global: users must not miss a request just because
    // they navigated away from the Nearby tab.
    incomingRequesterName?.let { requester ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onReject,
            containerColor = Panel,
            title = { Text("NeaRer connection request", color = BlueAccent, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$requester wants to connect with you.", color = White)
                    Text("Compare the six-digit code on both phones. Accept only when it matches and you recognise this person.", color = Muted)
                    pairingCode?.let { code ->
                        Text("PAIRING CODE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(code, color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onAccept) {
                    Text("Accept", color = BlueAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onReject) {
                    Text("Not now", color = Muted)
                }
            }
        )
    }
}


@androidx.compose.runtime.Composable
private fun CommunityHub(
    groups: List<CommunityGroup>, messages: List<CommunityChatMessage>, selectedCode: String?, notice: String,
    invite: GameInvite?, game: LudoNetworkMatch?, gameChat: List<String>, nearbyConnected: Boolean,
    onCreate: (String) -> Unit, onJoin: (String) -> Unit, onSelect: (String) -> Unit,
    onGroupMessage: (String) -> Unit, onBroadcast: (String) -> Unit, onInviteGame: () -> Unit,
    onAccept: () -> Unit, onReject: () -> Unit, onMove: (String, Int) -> Unit, onGameChat: (String) -> Unit, onGameRematch: () -> Unit, onEndGame: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var groupText by remember { mutableStateOf("") }
    var broadcastText by remember { mutableStateOf("") }
    var gameText by remember { mutableStateOf("") }
    val selected = groups.firstOrNull { it.code == selectedCode }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NEARER COMMUNITY", color = BlueAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Groups and nearby broadcasts", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(32) }, Modifier.weight(1f), singleLine = true, label = { Text("Group name") })
                Button(onClick = { onCreate(name); name = "" }, enabled = name.isNotBlank()) { Text("Create") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), singleLine = true, label = { Text("6-digit code") })
                Button(onClick = { onJoin(code); code = "" }, enabled = code.length == 6) { Text("Join") }
            }
            groups.forEach { group ->
                Button(onClick = { onSelect(group.code) }, Modifier.fillMaxWidth()) { Text(group.name + " • " + group.code + " • " + group.members.size + " known") }
            }
            if (selected != null) {
                Text("GROUP CHAT • " + selected.name, color = BlueAccent, fontWeight = FontWeight.Bold)
                Text("Share code " + selected.code + " with connected members.", color = Muted, fontSize = 10.sp)
                Column(Modifier.fillMaxWidth().height(130.dp).verticalScroll(rememberScrollState())) {
                    messages.filter { it.groupCode == selected.code }.takeLast(40).forEach { Text(it.sender + ": " + it.body, color = White, fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(groupText, { groupText = it.take(1500) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Group message") })
                    Button(onClick = { onGroupMessage(groupText); groupText = "" }, enabled = groupText.isNotBlank()) { Text("Send") }
                }
            }
            Text("BROADCAST TO CONNECTED PEOPLE", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(broadcastText, { broadcastText = it.take(500) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Announcement") })
                Button(onClick = { onBroadcast(broadcastText); broadcastText = "" }, enabled = broadcastText.isNotBlank()) { Text("Send") }
            }
            Text("Only currently connected NeaRer devices receive these broadcasts.", color = Muted, fontSize = 10.sp)
            Text(notice, color = BlueAccent, fontSize = 10.sp)
        }
    }
    LudoGamePanel(game, invite, nearbyConnected, onInviteGame, onAccept, onReject, onMove, gameChat, onGameChat, onGameRematch, onEndGame)
}
private data class CommunityGroup(val code: String, val name: String, val members: List<String>)
private data class CommunityChatMessage(val id: String, val groupCode: String, val sender: String, val body: String, val time: Long)
data class GameInvite(val matchId: String, val sender: String)

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

internal data class ChatMessage(
    val id: String,
    val body: String,
    val outgoing: Boolean,
    val delivery: String,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: String = "",
    val conversationName: String = ""
)

private fun isNeaRerAppBundle(file: File): Boolean {
    if (!file.isFile || !file.extension.equals("zip", ignoreCase = true)) return false
    return runCatching {
        ZipFile(file).use { archive ->
            archive.getEntry("INSTALLATION.txt") != null && archive.getEntry("base.apk") != null
        }
    }.getOrDefault(false)
}

private data class ReceivedFile(val displayName: String, val file: File, val isAppBundle: Boolean = false)

private data class UnifiedTransferHistoryEntry(
    val direction: String,
    val fileName: String,
    val bytes: Long,
    val result: String,
    val timestamp: Long,
    val source: String
)

private data class DirectOutgoingTransfer(val fileName: String, val bytes: Long)

private data class NearbyTransferRecord(
    val direction: String,
    val fileName: String,
    val bytes: Long,
    val timestamp: Long,
    val status: String
) {
    val timeLabel: String get() = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}

private fun formatChatTime(timestamp: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private fun formatTransferBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

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

internal class AppInstallResultActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            }
            if (confirmation != null) startActivity(confirmation)
            finish()
            return
        }
        val success = status == PackageInstaller.STATUS_SUCCESS
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val message = if (success) {
            "Android reports that the app was installed successfully."
        } else {
            "Android did not complete the installation." + if (detail.isBlank()) "" else "\n\n" + detail
        }
        AlertDialog.Builder(this)
            .setTitle(if (success) "App installed" else "Installation not completed")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
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
