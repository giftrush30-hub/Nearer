package com.nearer.app

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
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
import android.app.role.RoleManager
import android.os.Bundle
import android.os.Looper
import android.media.MediaRecorder
import android.os.Handler
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
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
import javax.crypto.SecretKey
import java.util.concurrent.LinkedBlockingQueue
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nearer.app.games.ludo.LudoGameScreen

private val Midnight = Color(0xFF071A38)
private val Panel = Color(0xFF102B50)
private val AccentBlue = Color(0xFF3B82F6)
private val Muted = Color(0xFFAABBD4)
private val White = Color(0xFFF6F8FF)
private val BrandBlue = Color(0xFF1769FF)
private val Gold = Color(0xFFFFD34E)

class MainActivity : ComponentActivity() {
    private var wifiManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var scanning by mutableStateOf(false)
    private var nearbyTransport: NearbyConnectionsTransport? = null
    private val chatTransportRouter = ChatTransportRouter()
    private val hybridPeers = mutableStateMapOf<String, String>()
    private val recentlySeenPeers = mutableStateMapOf<String, NearbyPerson>()
    private var displayName by mutableStateOf("NeaRer User")
    private var hybridSearching by mutableStateOf(false)
    private var pendingHybridEndpoint: String? = null
    private var hybridConnected = false
    private var hybridConnectedEndpoint: String? = null
    private var activeChatPeerId: String = "legacy"
    private var connectedChatPeerId: String? = null
    private var connectedChatPeerName: String? = null
    private var activeChatPeerName: String = "Nearby person"
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
    @Volatile private var peerSessionKey: SecretKey? = null
    private val writerLock = Any()
    private val receivedMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val persistedMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val messagePersistence = Executors.newSingleThreadExecutor()
    private val io = Executors.newCachedThreadPool()
    private val approvalDecision = LinkedBlockingQueue<Boolean>()
    private val chatMessages = mutableStateListOf<ChatMessage>()
    private val pendingChatFileUris = mutableStateListOf<String>()
    private val communityGroups = mutableStateListOf<CommunityGroup>()
    private val communityMessages = mutableStateListOf<CommunityChatMessage>()
    private var selectedCommunityCode by mutableStateOf<String?>(null)
    private var communityNotice by mutableStateOf("Create or join a nearby group.")
    private var incomingGameInvite by mutableStateOf<GameInvite?>(null)
    private var activeGame by mutableStateOf<TicTacToeMatch?>(null)
    private val gameChatMessages = mutableStateListOf<String>()
    private val receivedFiles = mutableStateListOf<ReceivedFile>()
    private val sentFiles = mutableStateListOf<SentFile>()
    private var pendingSentFileName: String? = null
    private var pendingSentFileUri: String? = null
    private var pendingSentFileSize: Long = 0L
    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()
    private val cancelledOutgoingTransfers = ConcurrentHashMap.newKeySet<String>()
    private var outgoingTransferId by mutableStateOf<String?>(null)
    private var outgoingProgress by mutableStateOf(0f)
    private var outgoingTransferredBytes by mutableStateOf(0L)
    private var outgoingTotalBytes by mutableStateOf(0L)
    private var outgoingSpeedBytesPerSecond by mutableStateOf(0L)
    private var incomingSpeedBytesPerSecond by mutableStateOf(0L)
    private var outgoingRateSampleAt = 0L
    private var incomingRateSampleAt = 0L
    private var outgoingRateSampleBytes = 0L
    private var incomingRateSampleBytes = 0L
    private var incomingTransferName by mutableStateOf<String?>(null)
    private var incomingProgress by mutableStateOf(0f)
    private var incomingTransferredBytes by mutableStateOf(0L)
    private var incomingTotalBytes by mutableStateOf(0L)
    private var chatNotice by mutableStateOf("Messages are sent directly between connected phones.")
    private var isRecordingVoiceNote by mutableStateOf(false)
    private var voiceNoteRecorder: MediaRecorder? = null
    private var activeVoiceNoteFile: File? = null
    private var statusMessage by mutableStateOf("Ready to find nearby Android phones.")
    private var flashRoleReady by mutableStateOf(false)
    private var flashLastSignal by mutableStateOf<String?>(null)
    private val nearbyDevices = mutableStateListOf<WifiP2pDevice>()

    private fun updateTransferRate(incoming: Boolean, bytes: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (incoming) {
            if (incomingRateSampleAt > 0L && bytes < incomingRateSampleBytes) {
                incomingSpeedBytesPerSecond = 0L
                incomingRateSampleAt = now
                incomingRateSampleBytes = bytes
                return
            }
            val elapsed = now - incomingRateSampleAt
            if (incomingRateSampleAt > 0L && elapsed >= 700L && bytes >= incomingRateSampleBytes) {
                val rate = ((bytes - incomingRateSampleBytes) * 1000L / elapsed).coerceAtLeast(0L)
                incomingSpeedBytesPerSecond = if (incomingSpeedBytesPerSecond == 0L) rate else (incomingSpeedBytesPerSecond * 0.65 + rate * 0.35).toLong()
                incomingRateSampleAt = now; incomingRateSampleBytes = bytes
            } else if (incomingRateSampleAt == 0L) { incomingRateSampleAt = now; incomingRateSampleBytes = bytes }
        } else {
            if (outgoingRateSampleAt > 0L && bytes < outgoingRateSampleBytes) {
                outgoingSpeedBytesPerSecond = 0L
                outgoingRateSampleAt = now
                outgoingRateSampleBytes = bytes
                return
            }
            val elapsed = now - outgoingRateSampleAt
            if (outgoingRateSampleAt > 0L && elapsed >= 700L && bytes >= outgoingRateSampleBytes) {
                val rate = ((bytes - outgoingRateSampleBytes) * 1000L / elapsed).coerceAtLeast(0L)
                outgoingSpeedBytesPerSecond = if (outgoingSpeedBytesPerSecond == 0L) rate else (outgoingSpeedBytesPerSecond * 0.65 + rate * 0.35).toLong()
                outgoingRateSampleAt = now; outgoingRateSampleBytes = bytes
            } else if (outgoingRateSampleAt == 0L) { outgoingRateSampleAt = now; outgoingRateSampleBytes = bytes }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                NeaRerFlashScreeningService.ACTION_FLASH_MESSAGE_RECEIVED -> {
                    // The screening service has already persisted the event. Import it
                    // through the same idempotent path used when the app resumes.
                    importPendingFlashMessages()
                }
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
                        val requestGeneration = discoveryGeneration
                        wifiManager?.requestPeers(channel) { peers ->
                            // Ignore delayed results from a scan that has since been stopped or restarted.
                            if (requestGeneration != discoveryGeneration) return@requestPeers
                            nearbyDevices.clear()
                            nearbyDevices.addAll(peers.deviceList)
                            if (scanning) {
                                statusMessage = if (nearbyDevices.isEmpty()) {
                                    "Searching… keep NeaRer open on both phones."
                                } else {
                                    "${nearbyDevices.size} nearby device(s) found. You can select one now or keep searching."
                                }
                            } else if (nearbyDevices.isNotEmpty()) {
                                statusMessage = "${nearbyDevices.size} nearby Wi-Fi Direct device(s) found."
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
                        val stoppedGeneration = discoveryGeneration
                        if (hasDiscoveryPermission()) {
                            wifiManager?.requestPeers(channel) { peers ->
                                if (stoppedGeneration != discoveryGeneration || !scanning) return@requestPeers
                                nearbyDevices.clear()
                                nearbyDevices.addAll(peers.deviceList)
                                finishDiscovery()
                                if (nearbyDevices.isEmpty() && hybridPeers.isEmpty()) {
                                    statusMessage = "Wi-Fi Direct search ended. Bluetooth discovery can continue; check both phones and search again if needed."
                                } else {
                                    statusMessage = "Wi-Fi Direct search finished. Select a phone below; Bluetooth discovery can continue."
                                }
                            }
                        } else {
                            if (stoppedGeneration == discoveryGeneration) {
                                finishDiscovery()
                                statusMessage = "Wi-Fi Direct search finished. Check Nearby devices permission if no phones appear."
                            }
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

    private fun requestFlashScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            statusMessage = "NeaRer Flash call signalling requires Android 10 or newer."
            return
        }
        val roles = getSystemService(RoleManager::class.java) ?: return
        if (roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            flashRoleReady = true
            return
        }
        if (!roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            statusMessage = "This Android device does not offer the call-screening role."
            return
        }
        startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 7041)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 26, 56)
        window.navigationBarColor = android.graphics.Color.rgb(7, 26, 56)
        window.decorView.systemUiVisibility = 0
        wifiManager = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = wifiManager?.initialize(this, mainLooper, null)
        nearbyTransport = NearbyConnectionsTransport(this, object : NearbyConnectionsTransport.Listener {
            override fun onStatus(message: String) { runOnUiThread { statusMessage = message } }
            override fun onFound(id: String, name: String, stableId: String) { runOnUiThread { hybridPeers[id] = name; if (stableId.isNotBlank()) recentlySeenPeers.remove(stableId) } }
            override fun onLost(id: String, stableId: String) { runOnUiThread {
                val name = hybridPeers.remove(id)
                if (name != null) recordRecentlySeen(stableId.ifBlank { id }, name)
                if (hybridConnectedEndpoint == id) {
                    hybridConnected = false
                    hybridConnectedEndpoint = null
                    val legacyLinkAlive = peerSocket?.isConnected == true && peerSocket?.isClosed == false
                    if (!legacyLinkAlive) {
                        connectedDeviceName = null
                        chatReady = false
                        connectionMessage = "Nearby connection ended"
                        chatNotice = "The selected nearby person disconnected. Search again to reconnect."
                    }
                }
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
                    activeChatPeerId = stableId.ifBlank { "nearby:$id" }
                    connectedChatPeerId = activeChatPeerId
                    connectedChatPeerName = name
                    activeChatPeerName = name
                    pendingHybridEndpoint = null
                    incomingRequesterName = null
                    connectedDeviceName = name
                    chatReady = true
                    connectionMessage = "Secure nearby connection active"
                    approvalMessage = "Connection approved"
                    chatNotice = "Connected to $name. Offline chat is ready."
                    statusMessage = "Connected to $name using Bluetooth / nearby Wi-Fi."
                    // Retry only messages addressed to this exact peer. Their IDs stay
                    // unchanged, so a recipient that already saved one can ACK the retry
                    // without showing a duplicate bubble.
                    flushQueuedChatMessages()
                }
            }
            override fun onDisconnected(id: String, stableId: String, name: String) {
                runOnUiThread {
                    hybridConnected = false
                    hybridConnectedEndpoint = null
                    if (connectedChatPeerId == stableId || connectedChatPeerId == "nearby:$id") {
                        connectedChatPeerId = null
                        connectedChatPeerName = null
                    }
                    val lastName = hybridPeers.remove(id) ?: name
                    recordRecentlySeen(stableId.ifBlank { id }, lastName)
                    // Keep the last peer identity after disconnect so queued messages
                    // remain attached to that conversation and can be retried later.
                    pendingHybridEndpoint = null
                    val legacyLinkAlive = peerSocket?.isConnected == true && peerSocket?.isClosed == false
                    chatReady = legacyLinkAlive
                    if (!legacyLinkAlive) {
                        queueUnconfirmedMessagesForPeer(stableId.ifBlank { "nearby:$id" })
                    }
                    if (legacyLinkAlive) {
                        connectionMessage = "Wi-Fi Direct chat link still active"
                        chatNotice = "Nearby Bluetooth/Wi-Fi link ended. Your chat is continuing over Wi-Fi Direct."
                    } else {
                        connectedDeviceName = null
                        connectionMessage = "Not connected"
                        chatNotice = "Nearby connection ended. Your messages remain safely queued."
                    }
                }
            }
            override fun onMessage(id: String, senderName: String, senderStableId: String, body: String) {
                // Compatibility path for older NeaRer builds that do not preserve message IDs.
                runOnUiThread {
                    if (body.startsWith("NR_COMMUNITY:")) handleCommunityPacket(senderName, senderStableId, body.removePrefix("NR_COMMUNITY:"))
                    else {
                        chatMessages.add(ChatMessage("legacy-" + System.currentTimeMillis(), body, outgoing = false, delivery = "Received"))
                        persistMessages()
                        chatNotice = "New offline message received from $senderName."
                    }
                }
            }
            override fun onMessageWithId(id: String, senderName: String, senderStableId: String, messageId: String, body: String) {
                runOnUiThread {
                    if (body.startsWith("NR_COMMUNITY:")) {
                        handleCommunityPacket(senderName, senderStableId, body.removePrefix("NR_COMMUNITY:"))
                    } else {
                        if (receivedMessageIds.add(messageId)) {
                            chatMessages.add(ChatMessage(
                                messageId, body, outgoing = false, delivery = "Received",
                                conversationId = senderStableId.ifBlank { "nearby:$id" },
                                conversationName = senderName
                            ))
                            chatNotice = "Saving message from $senderName before confirming delivery…"
                            persistReceivedMessageThenAcknowledge(messageId, nearbyEndpointId = id)
                        } else if (persistedMessageIds.contains(messageId)) {
                            // Re-send receipts for already-saved duplicates if an earlier ACK was lost.
                            nearbyTransport?.sendDeliveryAck(id, messageId)
                        } else {
                            // A previous disk write may have failed; retry saving before acknowledging.
                            persistReceivedMessageThenAcknowledge(messageId, nearbyEndpointId = id)
                        }
                    }
                }
            }
            override fun onDeliveryAck(id: String, messageId: String) {
                runOnUiThread {
                    val index = chatMessages.indexOfFirst { it.id == messageId && it.outgoing }
                    if (index >= 0 && chatMessages[index].delivery != "Delivered") {
                        chatMessages[index] = chatMessages[index].copy(delivery = "Delivered")
                        persistMessages()
                        chatNotice = "Your message was delivered and saved on the other phone."
                    }
                }
            }
            override fun onMessageSendFailed(messageId: String) {
                runOnUiThread {
                    val index = chatMessages.indexOfFirst { it.id == messageId && it.outgoing }
                    if (index >= 0 && chatMessages[index].delivery != "Delivered") {
                        chatMessages[index] = chatMessages[index].copy(delivery = "Queued")
                        persistMessages()
                        chatNotice = "Connection interrupted. Your message is safely queued on this phone."
                    }
                }
            }
            override fun onFileProgress(id: String, payloadId: Long, name: String, transferred: Long, total: Long, incoming: Boolean) {
                runOnUiThread {
                    val fraction = if (total > 0L) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    if (incoming) {
                        incomingTransferName = name
                        incomingProgress = fraction
                        incomingTransferredBytes = transferred.coerceAtLeast(0L)
                        updateTransferRate(true, transferred.coerceAtLeast(0L))
                        incomingTotalBytes = total.coerceAtLeast(0L)
                        chatNotice = if (total > 0L) "Receiving $name • ${(fraction * 100).toInt()}%" else "Receiving $name…"
                    } else {
                        outgoingTransferId = "nearby:$payloadId"
                        outgoingProgress = fraction
                        outgoingTransferredBytes = transferred.coerceAtLeast(0L)
                        updateTransferRate(false, transferred.coerceAtLeast(0L))
                        outgoingTotalBytes = total.coerceAtLeast(0L)
                        chatNotice = if (total > 0L) "Sending $name • ${(fraction * 100).toInt()}%" else "Sending $name…"
                    }
                }
            }
            override fun onFileReceived(id: String, name: String, file: File) {
                runOnUiThread {
                    incomingTransferName = null
                    incomingProgress = 0f
                    incomingTransferredBytes = 0L
                    incomingTotalBytes = 0L
                    incomingSpeedBytesPerSecond = 0L
                    incomingRateSampleAt = 0L
                    incomingRateSampleBytes = 0L
                    receivedFiles.add(0, ReceivedFile(name, file))
                    chatNotice = "Received and saved: $name. Tap it below to open."
                }
            }
            override fun onFileStatus(message: String) {
                runOnUiThread {
                    chatNotice = message
                    if (message.startsWith("File sent to the nearby phone")) {
                        val savedUri = pendingSentFileUri
                        val savedName = pendingSentFileName
                        if (!savedUri.isNullOrBlank() && !savedName.isNullOrBlank()) {
                            sentFiles.add(0, SentFile(savedName, savedUri, pendingSentFileSize, System.currentTimeMillis()))
                            persistSentFiles()
                        }
                        pendingSentFileUri = null
                        pendingSentFileName = null
                        pendingSentFileSize = 0L
                    }
                    if (message.startsWith("File sent to the nearby phone") ||
                        message.startsWith("Nearby file transfer failed") ||
                        message.startsWith("Nearby file transfer cancelled") ||
                        message.startsWith("Could not start file transfer") ||
                        message.startsWith("Could not send file details") ||
                        message.startsWith("File could not be prepared")) {
                        outgoingTransferId = null
                        outgoingProgress = 0f
            outgoingTransferredBytes = 0L
            outgoingTotalBytes = 0L
            outgoingSpeedBytesPerSecond = 0L
            outgoingRateSampleAt = 0L
            outgoingRateSampleBytes = 0L
                    }
                    if (message.startsWith("Nearby file transfer failed") ||
                        message.startsWith("Nearby file transfer cancelled")) {
                        incomingTransferName = null
                        incomingProgress = 0f
                        incomingTransferredBytes = 0L
                        incomingTotalBytes = 0L
                        incomingSpeedBytesPerSecond = 0L
                        incomingRateSampleAt = 0L
                        incomingRateSampleBytes = 0L
                    }
                    if (message.startsWith("File sent to the nearby phone") ||
                        message.startsWith("Nearby file transfer failed") ||
                        message.startsWith("Nearby file transfer cancelled") ||
                        message.startsWith("Could not start file transfer") ||
                        message.startsWith("File could not be prepared")) {
                        flushPendingChatFiles()
                    }
                }
            }
        })
        if (wifiManager == null) statusMessage = "This phone does not provide Wi-Fi Direct support."
        restoreLocalData()
        restoreReceivedFiles()
        restoreSentFiles()
        displayName = getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getString("display_name", "NeaRer User")?.take(24)?.ifBlank { "NeaRer User" } ?: "NeaRer User"
        restoreCommunityData()
        nearbyTransport?.setDisplayName(displayName)
        restoreRecentlySeen()
        meshRelayEnabled = getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getBoolean("mesh_relay_enabled", false)
        nearbyTransport?.setRelayEnabled(meshRelayEnabled)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Midnight,
                surface = Panel,
                primary = AccentBlue,
                onPrimary = Midnight,
                onBackground = White,
                onSurface = White
            )) {
                NeaRerHome(
                    playerName = displayName,
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
                    sentFiles = sentFiles,
                    onSendMessage = ::sendChatMessage,
                    onSelectConversation = { peerId, peerName ->
                        val connectedPeerId = connectedChatPeerId
                        activeChatPeerId = peerId
                        activeChatPeerName = peerName
                        chatNotice = if (chatReady && peerId != connectedPeerId) "This is a different conversation. Reconnect to ${peerName} before sending; nothing will be sent to the currently connected person." else "Conversation opened: $peerName."
                    },
                    isRecordingVoiceNote = isRecordingVoiceNote,
                    onVoiceNoteAction = ::toggleVoiceNoteRecording,
                    onSendQueuedMessages = ::flushQueuedChatMessages,
                    pendingChatFilesCount = pendingChatFileUris.size,
                    onSendQueuedFiles = ::flushPendingChatFiles,
                    groups = communityGroups.toList(),
                    groupMessages = communityMessages.toList(),
                    selectedGroupCode = selectedCommunityCode,
                    communityNotice = communityNotice,
                    communityLocalMemberId = localCommunityMemberId,
                    incomingGameInvite = incomingGameInvite,
                    activeGame = activeGame,
                    gameChatMessages = gameChatMessages.toList(),
                    onCreateGroup = ::createCommunityGroup,
                    onJoinGroup = ::joinCommunityGroup,
                    onSelectGroup = { selectedCommunityCode = it },
                    onLeaveGroup = ::leaveCommunityGroup,
                    onApproveMember = ::approveCommunityMember,
                    onPromoteMember = ::promoteCommunityMember,
                    onTransferOwnership = ::transferCommunityOwnership,
                    onRemoveMember = ::removeCommunityMember,
                    onSendGroupMessage = ::sendCommunityGroupMessage,
                    onBroadcast = ::sendCommunityBroadcast,
                    onGameInvite = ::inviteToGame,
                    onAcceptGameInvite = ::acceptGameInvite,
                    onRejectGameInvite = { incomingGameInvite = null },
                    onGameMove = ::playGameMove,
                    onGameChat = ::sendGameChat,
                    onEndGame = ::endGame,
                    onSendFile = ::sendFile,
                    onLoadShareableApps = ::getShareableApps,
                    onPrepareAppForSharing = { app, onPrepared ->
                        io.execute {
                            val uri = prepareAppForSharing(app)
                            runOnUiThread { onPrepared(uri) }
                        }
                    },
                    onCancelTransfer = ::cancelOutgoingFileTransfer,
                    outgoingTransferId = outgoingTransferId,
                    outgoingProgress = outgoingProgress,
                    incomingTransferName = incomingTransferName,
                    incomingProgress = incomingProgress,
                    incomingTransferredBytes = incomingTransferredBytes,
                    incomingTotalBytes = incomingTotalBytes,
                    incomingSpeedBytesPerSecond = incomingSpeedBytesPerSecond,
                    outgoingTransferredBytes = outgoingTransferredBytes,
                    outgoingTotalBytes = outgoingTotalBytes,
                    outgoingSpeedBytesPerSecond = outgoingSpeedBytesPerSecond,
                    onOpenFile = ::openReceivedFile,
                    onOpenSentFile = ::openSentFile,
                    onShareReceivedFile = ::shareReceivedFile,
                    onResendSentFile = { sendFile(Uri.parse(it.uri)) },
                    onShareSentFile = { shareFileUri(Uri.parse(it.uri), it.name) },
                    onDisconnect = ::disconnectFromPeer,
                    onAccept = ::approveIncoming,
                    onReject = ::rejectIncoming,
                    hybridPeers = hybridPeers.toMap(),
                    recentlySeenPeers = recentlySeenPeers.toMap(),
                    displayName = displayName,
                    onSaveDisplayName = ::saveDisplayName,
                    meshRelayEnabled = meshRelayEnabled,
                    onMeshRelayChange = ::updateMeshRelayEnabled,
                    onHybridConnect = { id -> nearbyTransport?.request(id) },
                    onFindNearby = ::startDiscovery,
                    onStopSearch = ::stopDiscovery,
                    onConnect = ::connectToDevice,
                    flashRoleReady = flashRoleReady,
                    flashLastSignal = flashLastSignal,
                    onRequestFlashScreeningRole = ::requestFlashScreeningRole
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) beginVoiceNoteRecording()
            else chatNotice = "Microphone permission was not granted. You can still send text and files."
            return
        }
        if (requestCode == REQUEST_DISCOVERY_PERMISSION) {
            if (hasNearbyPermissions()) {
                statusMessage = "Permissions granted. Starting nearby search…"
                startDiscovery()
            } else {
                hybridSearching = false
                scanning = false
                statusMessage = "NeaRer needs Nearby devices and Bluetooth permissions to find other phones. You can enable them in Android app settings."
            }
        }
    }

    private fun importPendingFlashMessages() {
        val flashPrefs = getSharedPreferences(NeaRerFlashScreeningService.PREFS, Context.MODE_PRIVATE)
        val rawEvents = flashPrefs.getString(NeaRerFlashScreeningService.PENDING_EVENTS, "[]") ?: "[]"
        val localPrefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val importedIds = localPrefs.getStringSet("flash_imported_event_ids", emptySet())
            .orEmpty().toMutableSet()
        var changed = false

        try {
            val events = JSONArray(rawEvents)
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                val eventId = event.optString("id").takeIf { it.isNotBlank() } ?: continue

                // A completed multi-call preset can supersede provisional single-call
                // bubbles that were already imported while the sequence was in progress.
                val replacedIds = event.optJSONArray("replaces")
                if (replacedIds != null && replacedIds.length() > 0) {
                    val replacedMessageIds = (0 until replacedIds.length())
                        .mapNotNull { index -> replacedIds.optString(index).takeIf { it.isNotBlank() } }
                        .map { "flash:$it" }
                        .toSet()
                    if (replacedMessageIds.isNotEmpty()) {
                        val removed = chatMessages.removeAll { it.id in replacedMessageIds }
                        if (removed) changed = true
                        replacedMessageIds.forEach { messageId ->
                            receivedMessageIds.remove(messageId)
                            persistedMessageIds.remove(messageId)
                        }
                    }
                }
                if (eventId in importedIds) continue

                val signal = event.optString("signal").takeIf { it.isNotBlank() } ?: continue
                // Keep each trusted sender in a separate chat thread. The service stores
                // only a short fingerprint tag here, never the sender's raw phone number.
                val senderTag = event.optString("senderTag")
                    .filter { it.isLetterOrDigit() }
                    .takeLast(8)
                    .ifBlank { "unknown" }
                val flashConversationId = "nearer-flash:$senderTag"
                val senderName = event.optString("senderName").trim().take(32)
                val flashConversationName = when {
                    senderName.isNotBlank() -> senderName
                    senderTag == "unknown" -> "NeaRer Flash"
                    else -> "Flash • $senderTag"
                }
                val messageId = "flash:$eventId"
                if (chatMessages.none { it.id == messageId }) {
                    chatMessages.add(
                        ChatMessage(
                            id = messageId,
                            body = FlashSignalPattern.toChatMessage(signal),
                            outgoing = false,
                            delivery = "Flash • Received",
                            timestamp = event.optLong("timestamp", System.currentTimeMillis())
                                .takeIf { it > 0L } ?: System.currentTimeMillis(),
                            conversationId = flashConversationId,
                            conversationName = flashConversationName
                        )
                    )
                    receivedMessageIds.add(messageId)
                    persistedMessageIds.add(messageId)
                    changed = true
                }
                importedIds.add(eventId)
            }
        } catch (_: Exception) {
            // Leave malformed or unavailable event data untouched; normal chat remains usable.
        }

        if (changed) persistMessages()
        if (importedIds.isNotEmpty()) {
            localPrefs.edit()
                .putStringSet("flash_imported_event_ids", importedIds.toList().takeLast(150).toSet())
                .apply()
        }
    }

    override fun onResume() {
        super.onResume()
        importPendingFlashMessages()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            flashRoleReady = roles?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            flashRoleReady = false
        }
        flashLastSignal = getSharedPreferences(NeaRerFlashScreeningService.PREFS, MODE_PRIVATE)
            .getString(NeaRerFlashScreeningService.LAST_SIGNAL, null)
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(NeaRerFlashScreeningService.ACTION_FLASH_MESSAGE_RECEIVED)
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
        try { voiceNoteRecorder?.stop() } catch (_: Exception) { }
        try { voiceNoteRecorder?.release() } catch (_: Exception) { }
        voiceNoteRecorder = null
        activeVoiceNoteFile?.delete()
        activeVoiceNoteFile = null
        isRecordingVoiceNote = false
        finishDiscovery()
        nearbyTransport?.stop()
        closePeerTransport()
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onPause() {
        if (isRecordingVoiceNote) finishVoiceNoteRecording(queueOnly = true)
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

    private fun updateMeshRelayEnabled(enabled: Boolean) {
        meshRelayEnabled = enabled
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit().putBoolean("mesh_relay_enabled", enabled).apply()
        nearbyTransport?.setRelayEnabled(enabled)
        statusMessage = if (enabled) "Community relay is ON. Your phone may forward nearby NeaRer text messages while connected." else "Community relay is OFF. Your phone only receives messages from its direct connections."
    }

    private fun saveDisplayName(rawName: String): Boolean {
        val cleanName = rawName.trim().replace(Regex("[\\r\\n|]"), " ").replace(Regex("\\s+"), " ").take(24)
        if (cleanName.isBlank()) {
            statusMessage = "Choose a name so nearby people know what to call you."
            return false
        }
        val preferences = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val saved = preferences.edit().putString("display_name", cleanName).commit()
        val verified = saved && preferences.getString("display_name", null) == cleanName
        if (!verified) {
            statusMessage = "Your name could not be saved. Please try again."
            return false
        }
        displayName = cleanName
        nearbyTransport?.setDisplayName(cleanName)
        statusMessage = "Your nearby name is saved as $cleanName."
        return true
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
        // Android 13+ has NEARBY_WIFI_DEVICES for Wi-Fi Direct operations.
        // Requiring the device-wide Location toggle there would block valid nearby
        // discovery even when the user has granted the correct Nearby devices permission.
        if (DiscoveryReadinessPolicy.requiresLocationServices(Build.VERSION.SDK_INT) && !isLocationEnabled()) {
            finishDiscovery()
            statusMessage = "Bluetooth discovery is active. On Android 12 and older, turn Location ON as well for Wi-Fi Direct discovery."
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
                        manager.stopPeerDiscovery(activeChannel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() = Unit
                            override fun onFailure(reason: Int) = Unit
                        })
                        statusMessage = if (nearbyDevices.isEmpty()) "No Wi-Fi Direct phones found in 20 seconds. Bluetooth discovery can continue; check Wi-Fi on both phones and Location for Wi-Fi Direct." else "${nearbyDevices.size} nearby phone(s) found. Choose one to connect. Bluetooth discovery can continue in the background."
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
        statusMessage = "Sending a connection invitation to ${device.deviceName.takeIf { it.isNotBlank() } ?: "nearby phone"}…"
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
            connectedDeviceName = active?.deviceName?.takeIf { it.isNotBlank() } ?: connectedDeviceName ?: "Nearby device"
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
            connectingAddress = null
            incomingRequesterName = null
            transportStarted = false
            closePeerTransport()
            val nearbyLinkAlive = hybridConnected && !hybridConnectedEndpoint.isNullOrBlank()
            chatReady = nearbyLinkAlive
            if (nearbyLinkAlive) {
                connectionMessage = "Nearby Connections chat link still active"
                statusMessage = "Wi-Fi Direct ended. NeaRer chat remains connected through Nearby Connections."
            } else {
                connectedDeviceName = null
                connectionMessage = "Not connected"
                statusMessage = "The Wi-Fi Direct connection is not active. You can scan and connect again."
            }
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
                if (hello.length > 2048 || !hello.startsWith("NEARER_HELLO|")) {
                    writer.println("REJECT")
                    runOnUiThread { statusMessage = "A device connected, but it did not identify as a secure NeaRer peer." }
                    closePeerTransport()
                    return@execute
                }
                val helloParts = hello.split("|", limit = 4)
                val name = helloParts.getOrNull(1).orEmpty().take(40).ifBlank { "Nearby NeaRer user" }
                val remoteStableId = helloParts.getOrNull(2).orEmpty().ifBlank { "wifi:${name.lowercase(java.util.Locale.ROOT)}" }
                val remotePublicKey = helloParts.getOrNull(3).orEmpty()
                require(remotePublicKey.isNotBlank()) { "The other phone does not support encrypted NeaRer sessions. Update both phones." }
                val localStableId = nearbyTransport?.localMemberId.orEmpty().ifBlank { "wifi:${displayName.lowercase(java.util.Locale.ROOT)}" }
                val localEphemeral = PeerSessionCrypto.createEphemeralKeyPair()
                val identityTranscript = listOf(localStableId, remoteStableId).sorted().joinToString("|")
                val sessionKey = PeerSessionCrypto.deriveSessionKey(
                    localEphemeral.privateKey, remotePublicKey, identityTranscript
                )
                val code = PeerSessionCrypto.authenticationCode(sessionKey, identityTranscript)
                writer.println("PAIR|$code|$localStableId|${localEphemeral.publicKeyBase64}")
                // Do not show an approval prompt or mark the host connected until the
                // connecting phone has independently derived and confirmed the same SAS.
                val pairingConfirmation = reader.readLine()
                if (pairingConfirmation != "PAIR_MATCH|$code") {
                    writer.println("REJECT")
                    runOnUiThread {
                        pairingCode = null
                        incomingRequesterName = null
                        approvalMessage = "Security code could not be confirmed"
                        statusMessage = "The security code was not confirmed on both phones. Connection declined."
                    }
                    closePeerTransport()
                    return@execute
                }
                approvalDecision.clear()
                runOnUiThread {
                    pairingCode = code
                    incomingRequesterName = name
                    approvalMessage = "Compare the six-digit security code on both phones. Accept only if they match."
                    statusMessage = "$name wants to connect on NeaRer."
                }
                val accepted = approvalDecision.poll(60, java.util.concurrent.TimeUnit.SECONDS) ?: false
                writer.println(if (accepted) "ACCEPT" else "REJECT")
                if (accepted) {
                    peerSessionKey = sessionKey
                    runOnUiThread {
                        chatReady = true
                        connectedChatPeerId = remoteStableId
                        connectedChatPeerName = name
                        activeChatPeerId = remoteStableId
                        activeChatPeerName = name
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
                val localName = displayName.trim().replace("|", " ").take(40).ifBlank { "NeaRer User" }
                val localStableId = nearbyTransport?.localMemberId.orEmpty().ifBlank { "wifi:${localName.lowercase(java.util.Locale.ROOT)}" }
                val localEphemeral = PeerSessionCrypto.createEphemeralKeyPair()
                writer.println("NEARER_HELLO|$localName|$localStableId|${localEphemeral.publicKeyBase64}")
                val pairingLine = reader.readLine().orEmpty()
                if (!pairingLine.startsWith("PAIR|")) throw IllegalStateException("The other phone did not provide a pairing code")
                val pairingParts = pairingLine.split("|", limit = 4)
                val remoteCode = pairingParts.getOrNull(1).orEmpty()
                require(Regex("\\d{6}").matches(remoteCode)) { "The other phone provided an invalid security code." }
                val remoteStableId = pairingParts.getOrNull(2).orEmpty()
                require(remoteStableId.matches(Regex("[A-Za-z0-9:_-]{6,64}"))) { "The other phone provided an invalid device identity." }
                val remotePublicKey = pairingParts.getOrNull(3).orEmpty()
                require(remotePublicKey.isNotBlank()) { "The other phone did not provide an encryption key." }
                val identityTranscript = listOf(localStableId, remoteStableId).sorted().joinToString("|")
                val sessionKey = PeerSessionCrypto.deriveSessionKey(
                    localEphemeral.privateKey, remotePublicKey, identityTranscript
                )
                val code = PeerSessionCrypto.authenticationCode(sessionKey, identityTranscript)
                require(remoteCode == code) { "Security code mismatch. Disconnect and decline this connection." }
                // The host waits for this proof before presenting its approval prompt.
                writer.println("PAIR_MATCH|$code")
                runOnUiThread {
                    pairingCode = code
                    statusMessage = "Security code $code. Compare it on both phones; decline if the codes differ."
                }
                when (reader.readLine()) {
                    "ACCEPT" -> {
                        peerSessionKey = sessionKey
                        runOnUiThread {
                            chatReady = true
                            connectedChatPeerId = remoteStableId
                            connectedChatPeerName = connectedDeviceName ?: "Nearby NeaRer user"
                            activeChatPeerId = remoteStableId
                            activeChatPeerName = connectedChatPeerName ?: "Nearby NeaRer user"
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
        peerSessionKey = null
        serverSocket = null
        if (!hybridConnected) {
            connectedChatPeerId = null
            connectedChatPeerName = null
        }
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
            outgoingTransferredBytes = 0L
            outgoingTotalBytes = 0L
            outgoingSpeedBytesPerSecond = 0L
            outgoingRateSampleAt = 0L
            outgoingRateSampleBytes = 0L
            chatReady = false
        }
        if (Looper.myLooper() == Looper.getMainLooper()) resetUi() else runOnUiThread { resetUi() }
    }

    private fun restoreSentFiles() {
        val raw = getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getString("sent_file_history", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val uri = item.optString("uri")
                if (uri.isNotBlank()) sentFiles.add(SentFile(item.optString("name", "Shared file"), uri, item.optLong("size"), item.optLong("time")))
            }
        } catch (_: Exception) { sentFiles.clear() }
    }

    private fun persistSentFiles() {
        val array = JSONArray()
        sentFiles.take(100).forEach { item -> array.put(JSONObject().put("name", item.name).put("uri", item.uri).put("size", item.sizeBytes).put("time", item.timestamp)) }
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit().putString("sent_file_history", array.toString()).apply()
    }

    private fun openSentFile(item: SentFile) {
        try {
            val uri = Uri.parse(item.uri)
            contentResolver.openFileDescriptor(uri, "r")?.close() ?: throw IllegalStateException("Original file is unavailable")
            val mime = contentResolver.getType(uri) ?: "*/*"
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) {
            chatNotice = "The original sent file is no longer available. It may have been moved or deleted."
        }
    }

    private fun shareReceivedFile(item: ReceivedFile) {
        if (!item.file.exists()) { chatNotice = "This received file is no longer available."; return }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.files", item.file)
            shareFileUri(uri, item.displayName)
        } catch (_: Exception) { chatNotice = "NeaRer could not prepare this file for sharing." }
    }

    private fun shareFileUri(uri: Uri, name: String) {
        try {
            val extension = name.substringAfterLast(".", "").lowercase()
            val mime = contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(contentResolver, name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share $name"))
        } catch (_: Exception) { chatNotice = "No compatible app could open the Android share menu." }
    }

    private fun restoreReceivedFiles() {
        val folder = File(filesDir, "received")
        if (!folder.exists() || !folder.isDirectory) return
        val restored = folder.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?.take(30)
            .orEmpty()
        receivedFiles.clear()
        restored.forEach { file ->
            val visibleName = file.name.replaceFirst(Regex("^\\d{10,}-"), "").ifBlank { file.name }
            receivedFiles.add(ReceivedFile(visibleName, file))
        }
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
                        timestamp = item.optLong("timestamp", 0L).takeIf { it > 0L } ?: System.currentTimeMillis(),
                        conversationId = item.optString("conversationId", "legacy"),
                        conversationName = item.optString("conversationName", "Previous chat")
                    )
                    // A process may die after Nearby accepted a packet but before
                    // its ACK arrived. Treat that uncertain state as retryable on reconnect.
                    val restoredMessage = if (message.outgoing &&
                        message.delivery in setOf("Sending", "Awaiting delivery", "Sent")
                    ) message.copy(delivery = "Queued") else message
                    chatMessages.add(restoredMessage)
                    if (!message.outgoing) {
                        receivedMessageIds.add(message.id)
                        persistedMessageIds.add(message.id)
                    }
                }
            } catch (_: Exception) {
                chatMessages.clear()
            }
        }
        val pendingFiles = getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getString("pending_chat_files", null)
        if (!pendingFiles.isNullOrBlank()) {
            try {
                val queued = JSONArray(pendingFiles)
                for (i in 0 until queued.length()) pendingChatFileUris.add(queued.getString(i))
            } catch (_: Exception) { pendingChatFileUris.clear() }
        }
    }

    private fun persistPendingChatFiles() {
        val array = JSONArray()
        pendingChatFileUris.forEach { array.put(it) }
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit().putString("pending_chat_files", array.toString()).apply()
    }

    private fun persistMessages() {
        try {
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
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .edit().putString("messages", array.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun persistReceivedMessageThenAcknowledge(messageId: String, nearbyEndpointId: String? = null, socket: Socket? = null) {
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
        val snapshot = array.toString()
        messagePersistence.execute {
            val saved = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .edit().putString("messages", snapshot).commit()
            if (saved) persistedMessageIds.add(messageId)
            runOnUiThread {
                if (saved) {
                    nearbyEndpointId?.let { nearbyTransport?.sendDeliveryAck(it, messageId) }
                    socket?.let { source ->
                        synchronized(writerLock) {
                            if (peerSocket === source) sendWireLine("ACK|$messageId")
                        }
                    }
                } else {
                    chatNotice = "Message received, but NeaRer could not confirm it was saved. Please keep the connection open and retry."
                }
            }
        }
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
        val key = peerSessionKey ?: return false
        val encryptedLine = try {
            "ENC|" + PeerSessionCrypto.encrypt(line, key)
        } catch (_: Exception) {
            return false
        }
        synchronized(writerLock) {
            writer.println(encryptedLine)
            return !writer.checkError()
        }
    }

    private fun deliverQueuedChatMessage(message: ChatMessage): Boolean {
        if (!chatReady) return false
        val intendedPeer = message.conversationId.ifBlank { "legacy" }
        if (connectedChatPeerId != intendedPeer) {
            chatNotice = "This message belongs to ${message.conversationName}. Reconnect to that person before sending."
            return false
        }
        val endpoint = hybridConnectedEndpoint
        val wifiDirectReady = peerSocket?.isConnected == true && peerSocket?.isClosed == false && peerWriter != null
        val route = chatTransportRouter.send(
            nearbyReady = hybridConnected && !endpoint.isNullOrBlank(),
            wifiDirectReady = wifiDirectReady,
            sendNearby = {
                !endpoint.isNullOrBlank() &&
                    nearbyTransport?.sendChatToEndpoint(endpoint, message.id, message.body) == true
            },
            sendWifiDirect = {
                val encoded = Base64.getEncoder().encodeToString(message.body.toByteArray(Charsets.UTF_8))
                sendWireLine("MSG|${message.id}|$encoded")
            }
        )
        if (route != null) {
            val index = chatMessages.indexOfFirst { it.id == message.id }
            // sendPayload acceptance is not recipient delivery. Keep this message
            // pending until the receiving app saves it and returns chat_ack.
            if (index >= 0) chatMessages[index] = message.copy(delivery = "Awaiting delivery")
            persistMessages()
            chatNotice = when (route) {
                ChatTransportRoute.NEARBY_CONNECTIONS -> "Message sent through Nearby Connections (Bluetooth / nearby Wi-Fi)."
                ChatTransportRoute.WIFI_DIRECT -> "Message sent through the direct Wi-Fi link."
            }
            return true
        }
        return false
    }

    private fun queueUnconfirmedMessagesForPeer(peerId: String) {
        if (peerId.isBlank()) return
        var changed = false
        chatMessages.indices.forEach { index ->
            val message = chatMessages[index]
            if (message.outgoing &&
                message.conversationId.ifBlank { "legacy" } == peerId &&
                message.delivery in setOf("Sending", "Awaiting delivery", "Sent")
            ) {
                chatMessages[index] = message.copy(delivery = "Queued")
                changed = true
            }
        }
        if (changed) persistMessages()
    }

    private fun flushQueuedChatMessages() {
        if (!chatReady) return
        val queuedMessages = chatMessages.filter {
            it.outgoing && it.delivery == "Queued" && it.conversationId.ifBlank { "legacy" } == connectedChatPeerId
        }
        if (queuedMessages.isEmpty()) return
        queuedMessages.forEach { message ->
            if (!deliverQueuedChatMessage(message)) {
                chatNotice = "Your message is saved on this phone. It is still waiting for a working nearby connection."
                return
            }
        }
        if (chatMessages.none { it.outgoing && it.delivery == "Queued" }) chatNotice = "Queued messages sent to the nearby connection."
    }

    private fun flushPendingChatFiles() {
        if (!chatReady || outgoingTransferId != null || pendingChatFileUris.isEmpty()) return
        val next = pendingChatFileUris.removeAt(0)
        persistPendingChatFiles()
        try { sendFile(Uri.parse(next)) } catch (_: Exception) {
            chatNotice = "A queued attachment could not be opened. Please select it again."
        }
    }

    private fun toggleVoiceNoteRecording() {
        if (isRecordingVoiceNote) {
            finishVoiceNoteRecording()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            chatNotice = "Allow microphone access to record a voice note."
            return
        }
        beginVoiceNoteRecording()
    }

    private fun beginVoiceNoteRecording() {
        try {
            val folder = File(filesDir, "voice_notes").apply { if (!exists()) mkdirs() }
            val output = File(folder, "NeaRer-voice-${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION") val recorder = MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()
            voiceNoteRecorder = recorder
            activeVoiceNoteFile = output
            isRecordingVoiceNote = true
            chatNotice = "Recording voice note… Tap the microphone again to finish and prepare it for sharing."
        } catch (error: Exception) {
            try { voiceNoteRecorder?.release() } catch (_: Exception) { }
            voiceNoteRecorder = null
            isRecordingVoiceNote = false
            activeVoiceNoteFile?.delete()
            activeVoiceNoteFile = null
            chatNotice = "Voice recording could not start. Check microphone permission and available storage."
        }
    }

    private fun finishVoiceNoteRecording(queueOnly: Boolean = false) {
        val recorder = voiceNoteRecorder
        val file = activeVoiceNoteFile
        try { recorder?.stop() } catch (_: Exception) {
            file?.delete()
            chatNotice = "That recording was too short or could not be saved. Please try again."
        } finally {
            try { recorder?.reset(); recorder?.release() } catch (_: Exception) { }
            voiceNoteRecorder = null
            activeVoiceNoteFile = null
            isRecordingVoiceNote = false
        }
        if (file != null && file.exists() && file.length() > 0L) {
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.files", file)
                if (queueOnly) {
                    queueChatFile(uri, "Voice note saved safely on this phone • it will wait until you return and choose to send it.")
                } else {
                    chatNotice = "Voice note saved • ${formatTransferSize(file.length())}. Preparing it for nearby sharing."
                    sendFile(uri)
                }
            } catch (_: Exception) {
                chatNotice = "Voice note was recorded but could not be prepared for sharing."
            }
        }
    }

    private fun sendChatMessage(rawText: String) {
        val body = rawText.trim()
        if (body.isEmpty()) return
        val message = ChatMessage(
            UUID.randomUUID().toString(), body, outgoing = true,
            delivery = if (chatReady) "Sending" else "Queued",
            conversationId = activeChatPeerId,
            conversationName = activeChatPeerName
        )
        chatMessages.add(message)
        persistMessages()
        if (!chatReady) {
            chatNotice = "Saved on this phone • waiting for your next nearby connection. Not delivered yet."
            return
        }
        if (!deliverQueuedChatMessage(message)) {
            val index = chatMessages.indexOfFirst { it.id == message.id }
            if (index >= 0) chatMessages[index] = message.copy(delivery = "Queued")
            persistMessages()
            chatNotice = "Message saved here. It will retry when the nearby link is ready."
        } else chatNotice = "Sent over the offline nearby connection."
    }


    private val localCommunityMemberId: String
        get() = nearbyTransport?.localMemberId.orEmpty()

    private fun persistCommunityData() {
        val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val groups = JSONArray()
        communityGroups.forEach { group ->
            val memberIds = JSONObject()
            group.memberIds.forEach { (id, name) -> memberIds.put(id, name) }
            val pending = JSONObject()
            group.pendingMembers.forEach { (id, name) -> pending.put(id, name) }
            groups.put(JSONObject().put("code", group.code).put("name", group.name)
                .put("members", JSONArray(group.members)).put("ownerId", group.ownerId)
                .put("memberIds", memberIds).put("moderators", JSONArray(group.moderators))
                .put("pendingMembers", pending))
        }
        val messages = JSONArray()
        communityMessages.takeLast(300).forEach { messages.put(JSONObject().put("id", it.id).put("code", it.groupCode).put("sender", it.sender).put("body", it.body).put("time", it.time)) }
        prefs.edit().putString("community_groups", groups.toString()).putString("community_messages", messages.toString()).apply()
    }

    private fun restoreCommunityData() {
        val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        try {
            val groups = JSONArray(prefs.getString("community_groups", "[]"))
            for (i in 0 until groups.length()) {
                val item = groups.getJSONObject(i)
                val membersArray = item.optJSONArray("members") ?: JSONArray()
                val members = (0 until membersArray.length()).map { membersArray.optString(it) }.filter { it.isNotBlank() }.distinct()
                val memberIdsJson = item.optJSONObject("memberIds") ?: JSONObject()
                val memberIds = mutableMapOf<String, String>()
                val keys = memberIdsJson.keys()
                while (keys.hasNext()) { val key = keys.next(); val value = memberIdsJson.optString(key); if (key.isNotBlank() && value.isNotBlank()) memberIds[key] = value }
                val pendingJson = item.optJSONObject("pendingMembers") ?: JSONObject()
                val pending = mutableMapOf<String, String>()
                val pendingKeys = pendingJson.keys()
                while (pendingKeys.hasNext()) { val key = pendingKeys.next(); val value = pendingJson.optString(key); if (key.isNotBlank() && value.isNotBlank()) pending[key] = value }
                val moderatorsArray = item.optJSONArray("moderators") ?: JSONArray()
                val moderators = (0 until moderatorsArray.length()).map { moderatorsArray.optString(it) }.filter { it.isNotBlank() }.toSet()
                val ownerId = item.optString("ownerId")
                // Migrate older locally-owned groups once; unknown remote previews remain untrusted.
                if (memberIds.isEmpty() && ownerId.isBlank() && members.firstOrNull() == displayName && localCommunityMemberId.isNotBlank()) {
                    memberIds[localCommunityMemberId] = displayName
                }
                communityGroups.add(CommunityGroup(item.getString("code"), item.optString("name"), members,
                    ownerId.ifBlank { if (memberIds.containsKey(localCommunityMemberId)) localCommunityMemberId else "" },
                    memberIds, moderators, pending))
            }
            val messages = JSONArray(prefs.getString("community_messages", "[]"))
            for (i in 0 until messages.length()) {
                val item = messages.getJSONObject(i)
                communityMessages.add(CommunityChatMessage(item.getString("id"), item.getString("code"), item.optString("sender"), item.getString("body").take(1500), item.optLong("time")))
            }
        } catch (_: Exception) { communityGroups.clear(); communityMessages.clear() }
    }

    private fun sendCommunityPacket(packet: JSONObject): Boolean {
        if (!hybridConnected) { communityNotice = "Connect to nearby NeaRer phones first."; return false }
        return nearbyTransport?.send("NR_COMMUNITY:$packet") == true
    }

    private fun createCommunityGroup(raw: String) {
        val name = raw.trim().take(32)
        if (name.isBlank() || communityGroups.size >= 20) {
            communityNotice = if (communityGroups.size >= 20) "You have reached the 20-community limit." else "Enter a community name first."
            return
        }
        val localId = localCommunityMemberId
        if (localId.isBlank()) { communityNotice = "Your nearby identity is not ready. Reconnect and try again."; return }
        var code: String
        do { code = (100000 + SecureRandom().nextInt(900000)).toString() }
        while (communityGroups.any { it.code == code })
        communityGroups.add(0, CommunityGroup(code, name, listOf(displayName), localId,
            mapOf(localId to displayName)))
        selectedCommunityCode = code
        persistCommunityData()
        val announced = sendCommunityPacket(JSONObject().put("type", "group_created").put("code", code)
            .put("name", name).put("sender", displayName).put("senderId", localId).put("ownerId", localId))
        communityNotice = if (announced) "Community created. You are its owner. Share code " + code + " to invite people."
            else "Community created on this phone. Share code " + code + "; connect nearby to announce it."
    }

    private fun leaveCommunityGroup(rawCode: String) {
        val code = rawCode.filter { it.isDigit() }.take(6)
        val group = communityGroups.firstOrNull { it.code == code } ?: return
        val localId = localCommunityMemberId
        if (group.ownerId == localId && group.members.size > 1) {
            communityNotice = "Transfer ownership or remove members before leaving this community."
            return
        }
        sendCommunityPacket(JSONObject().put("type", "group_leave").put("code", code)
            .put("sender", displayName).put("senderId", localId))
        communityGroups.removeAll { it.code == code }
        if (selectedCommunityCode == code) selectedCommunityCode = communityGroups.firstOrNull()?.code
        persistCommunityData()
        communityNotice = "You left " + group.name + ". Its saved messages remain on this phone."
    }

    private fun joinCommunityGroup(raw: String) {
        val code = raw.filter { it.isDigit() }.take(6)
        val localId = localCommunityMemberId
        if (code.length != 6 || localId.isBlank()) { communityNotice = "Enter a valid six-digit code after connecting nearby."; return }
        val group = communityGroups.firstOrNull { it.code == code }
        if (group?.memberIds?.containsKey(localId) == true) {
            communityNotice = "You are already a member of this community."
            selectedCommunityCode = code
            return
        }
        if (group == null) communityGroups.add(0, CommunityGroup(code, "Nearby community $code", emptyList()))
        selectedCommunityCode = code
        persistCommunityData()
        val sent = sendCommunityPacket(JSONObject().put("type", "group_join_request").put("code", code)
            .put("sender", displayName).put("senderId", localId))
        communityNotice = if (sent) "Join request sent. The community owner or a moderator must approve you."
            else "Community code saved. Connect to a member of this community to request approval."
    }

    private fun sendGroupMembers(code: String) {
        val group = communityGroups.firstOrNull { it.code == code } ?: return
        val memberArray = JSONArray()
        group.memberIds.forEach { (id, name) -> memberArray.put(JSONObject().put("id", id).put("name", name)) }
        val pendingArray = JSONArray()
        group.pendingMembers.forEach { (id, name) -> pendingArray.put(JSONObject().put("id", id).put("name", name)) }
        val packet = JSONObject().put("type", "group_members").put("code", code)
            .put("ownerId", group.ownerId).put("members", memberArray)
            .put("moderators", JSONArray(group.moderators.toList())).put("pendingMembers", pendingArray)
        nearbyTransport?.sendToStableIds("NR_COMMUNITY:$packet", group.memberIds.keys)
    }

    private fun approveCommunityMember(code: String, memberId: String) {
        val i = communityGroups.indexOfFirst { it.code == code }
        if (i < 0) return
        val group = communityGroups[i]
        val localId = localCommunityMemberId
        if (!CommunityAccessPolicy.canManage(group.ownerId, group.moderators, localId)) return
        val name = group.pendingMembers[memberId] ?: return
        val nextIds = group.memberIds + (memberId to name)
        communityGroups[i] = group.copy(members = (group.members + name).distinct(),
            memberIds = nextIds, pendingMembers = group.pendingMembers - memberId)
        persistCommunityData()
        val approval = JSONObject().put("type", "group_join_approved").put("code", code)
            .put("ownerId", group.ownerId).put("sender", displayName).put("senderId", localId)
        nearbyTransport?.sendToStableIds("NR_COMMUNITY:$approval", setOf(memberId))
        sendGroupMembers(code)
        communityNotice = name + " approved to join " + group.name + "."
    }

    private fun promoteCommunityMember(code: String, memberId: String) {
        val i = communityGroups.indexOfFirst { it.code == code }
        if (i < 0) return
        val group = communityGroups[i]
        if (!CommunityAccessPolicy.canPromote(group.ownerId, group.memberIds, group.moderators, localCommunityMemberId, memberId)) return
        communityGroups[i] = group.copy(moderators = group.moderators + memberId)
        persistCommunityData()
        sendGroupMembers(code)
        communityNotice = group.memberIds[memberId].orEmpty() + " is now a moderator."
    }

    private fun transferCommunityOwnership(code: String, memberId: String) {
        val i = communityGroups.indexOfFirst { it.code == code }
        if (i < 0) return
        val group = communityGroups[i]
        val localId = localCommunityMemberId
        if (!CommunityAccessPolicy.canTransferOwnership(group.ownerId, group.memberIds, localId, memberId)) return
        val nextModerators = (group.moderators - memberId) + localId
        communityGroups[i] = group.copy(ownerId = memberId, moderators = nextModerators)
        persistCommunityData()
        val packet = JSONObject().put("type", "ownership_transfer").put("code", code)
            .put("ownerId", memberId).put("sender", displayName).put("senderId", localId)
        nearbyTransport?.sendToStableIds("NR_COMMUNITY:$packet", group.memberIds.keys)
        communityNotice = group.memberIds[memberId].orEmpty() + " is now the community owner. You are a moderator."
    }

    private fun removeCommunityMember(code: String, memberId: String) {
        val i = communityGroups.indexOfFirst { it.code == code }
        if (i < 0) return
        val group = communityGroups[i]
        val localId = localCommunityMemberId
        if (!CommunityAccessPolicy.canRemove(group.ownerId, group.moderators, group.memberIds, localId, memberId)) {
            // Pending join requests are not members yet; owners and moderators may decline them.
            if (!CommunityAccessPolicy.canManage(group.ownerId, group.moderators, localId) || memberId !in group.pendingMembers) return
        }
        val name = group.memberIds[memberId] ?: group.pendingMembers[memberId] ?: return
        communityGroups[i] = group.copy(members = group.members.filterNot { it == name },
            memberIds = group.memberIds - memberId, moderators = group.moderators - memberId,
            pendingMembers = group.pendingMembers - memberId)
        persistCommunityData()
        val removal = JSONObject().put("type", "group_member_removed").put("code", code)
            .put("memberId", memberId).put("ownerId", group.ownerId).put("sender", displayName).put("senderId", localId)
        nearbyTransport?.sendToStableIds("NR_COMMUNITY:$removal", setOf(memberId))
        sendGroupMembers(code)
        communityNotice = name + " removed from " + group.name + "."
    }

    private fun sendCommunityGroupMessage(raw: String) {
        val code = selectedCommunityCode ?: return
        val group = communityGroups.firstOrNull { it.code == code } ?: return
        val localId = localCommunityMemberId
        if (!CommunityAccessPolicy.canPost(group.memberIds, localId)) { communityNotice = "You must be approved as a member before posting."; return }
        val body = raw.trim().take(1500)
        if (body.isBlank()) return
        val id = UUID.randomUUID().toString()
        val packet = JSONObject().put("type", "group_message").put("code", code)
            .put("sender", displayName).put("senderId", localId).put("body", body).put("id", id)
        if (nearbyTransport?.sendToStableIds("NR_COMMUNITY:$packet", group.memberIds.keys - localId) == true) {
            communityMessages.add(CommunityChatMessage(id, code, displayName, body, System.currentTimeMillis()))
            persistCommunityData()
        } else if (group.members.size == 1) {
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
    private fun handleCommunityPacket(sender: String, senderStableId: String, raw: String) {
        try {
            val packet = JSONObject(raw)
            val type = packet.optString("type")
            val code = packet.optString("code")
            val senderId = packet.optString("senderId").ifBlank { senderStableId }
            if (senderId.isBlank() || senderId != senderStableId ||
                !senderStableId.matches(Regex("([A-F0-9]{6}|[A-F0-9]{32})"))) return
            when (type) {
                "group_created" -> {
                    val ownerId = packet.optString("ownerId").ifBlank { senderId }
                    if (code.matches(Regex("[0-9]{6}")) && ownerId == senderId && communityGroups.none { it.code == code } && communityGroups.size < 20) {
                        val groupName = packet.optString("name").trim().take(32).ifBlank { "Nearby community " + code }
                        communityGroups.add(0, CommunityGroup(code, groupName, listOf(sender),
                            ownerId, mapOf(senderId to sender)))
                        persistCommunityData()
                        communityNotice = "New community found: " + groupName + ". Request to join with code " + code + "."
                    }
                }
                "group_join_request" -> {
                    val i = communityGroups.indexOfFirst { it.code == code }
                    if (i >= 0) {
                        val group = communityGroups[i]
                        if ((group.ownerId == localCommunityMemberId || localCommunityMemberId in group.moderators) &&
                            senderId != group.ownerId && senderId !in group.memberIds && senderId !in group.pendingMembers &&
                            group.pendingMembers.size < 100) {
                            communityGroups[i] = group.copy(pendingMembers = group.pendingMembers + (senderId to sender))
                            persistCommunityData()
                            sendGroupMembers(code)
                            communityNotice = sender + " requested to join " + group.name + ". Review the request."
                        }
                    }
                }
                "group_join_approved" -> {
                    val i = communityGroups.indexOfFirst { it.code == code }
                    if (i >= 0) {
                        val group = communityGroups[i]
                        val approverId = senderId
                        val packetOwnerId = packet.optString("ownerId")
                        val approvedByOwner = packetOwnerId.isNotBlank() && approverId == packetOwnerId
                        val approvedByModerator = group.ownerId.isNotBlank() && packetOwnerId == group.ownerId &&
                            approverId in group.moderators
                        if ((group.ownerId.isBlank() && approvedByOwner) ||
                            (packetOwnerId == group.ownerId && (approvedByOwner || approvedByModerator))) {
                            val updatedMembers = group.memberIds + (approverId to sender) +
                                (localCommunityMemberId to displayName)
                            communityGroups[i] = group.copy(members = (group.members + sender + displayName).distinct(),
                                ownerId = packetOwnerId, memberIds = updatedMembers)
                            persistCommunityData()
                            sendGroupMembers(code)
                            communityNotice = "Your request to join " + group.name + " was approved."
                        }
                    }
                }
                "group_leave" -> {
                    val i = communityGroups.indexOfFirst { it.code == code }
                    if (i >= 0) {
                        val group = communityGroups[i]
                        if (senderId in group.memberIds && senderId != group.ownerId) {
                            val name = group.memberIds[senderId]
                            communityGroups[i] = group.copy(members = group.members.filterNot { it == name },
                                memberIds = group.memberIds - senderId, moderators = group.moderators - senderId)
                            persistCommunityData()
                            sendGroupMembers(code)
                        }
                    }
                }
                "group_members" -> {
                    val i = communityGroups.indexOfFirst { it.code == code }
                    if (i >= 0) {
                        val group = communityGroups[i]
                        val incomingOwnerId = packet.optString("ownerId").ifBlank { group.ownerId }
                        if ((senderId == group.ownerId || senderId in group.moderators) && incomingOwnerId == group.ownerId) {
                            val array = packet.optJSONArray("members") ?: JSONArray()
                            val members = mutableMapOf<String, String>()
                            for (n in 0 until array.length()) {
                                val member = array.optJSONObject(n) ?: continue
                                val memberId = member.optString("id")
                                val memberName = member.optString("name")
                                if (memberId.isNotBlank() && memberName.isNotBlank()) members[memberId] = memberName.take(32)
                            }
                            val moderatorsArray = packet.optJSONArray("moderators") ?: JSONArray()
                            val moderators = (0 until moderatorsArray.length()).map { moderatorsArray.optString(it) }
                                .filter { it in members.keys && it != group.ownerId }.toSet()
                            // Moderators may approve/remove ordinary members, but cannot alter ownership
                            // or appoint/demote other moderators. Only the owner can change role assignments.
                            val roleUpdateAllowed = senderId == group.ownerId ||
                                (group.ownerId in members && moderators == group.moderators)
                            if (roleUpdateAllowed) {
                                val pendingArray = packet.optJSONArray("pendingMembers") ?: JSONArray()
                                val pending = mutableMapOf<String, String>()
                                for (n in 0 until pendingArray.length()) {
                                    val request = pendingArray.optJSONObject(n) ?: continue
                                    val requestId = request.optString("id")
                                    val requestName = request.optString("name")
                                    if (requestId.isNotBlank() && requestName.isNotBlank() && requestId !in members) pending[requestId] = requestName.take(32)
                                }
                                communityGroups[i] = group.copy(memberIds = members, members = members.values.distinct(),
                                    moderators = moderators, pendingMembers = pending)
                                persistCommunityData()
                            }
                        }
                    }
                }
                "ownership_transfer" -> {
                    val i = communityGroups.indexOfFirst { it.code == code }
                    if (i >= 0) {
                        val group = communityGroups[i]
                        val newOwnerId = packet.optString("ownerId")
                        if (senderId == group.ownerId && newOwnerId in group.memberIds && newOwnerId != group.ownerId) {
                            communityGroups[i] = group.copy(ownerId = newOwnerId,
                                moderators = (group.moderators - newOwnerId) + group.ownerId)
                            persistCommunityData()
                            communityNotice = "Community ownership was transferred to " + group.memberIds[newOwnerId].orEmpty() + "."
                        }
                    }
                }
                "group_member_removed" -> {
                    val i = communityGroups.indexOfFirst { it.code == code }
                    if (i >= 0 && packet.optString("memberId") == localCommunityMemberId) {
                        val group = communityGroups[i]
                        val packetOwnerId = packet.optString("ownerId")
                        val senderIsOwner = senderId == group.ownerId ||
                            (group.ownerId.isBlank() && packetOwnerId.isNotBlank() && senderId == packetOwnerId)
                        val senderIsModerator = senderId in group.moderators
                        val targetIsAllowed = localCommunityMemberId != group.ownerId &&
                            (senderIsOwner || localCommunityMemberId !in group.moderators)
                        val trustedRemoval = (senderIsOwner || senderIsModerator) && targetIsAllowed
                        if (trustedRemoval) {
                            communityGroups[i] = group.copy(members = group.members - displayName,
                                memberIds = group.memberIds - localCommunityMemberId, moderators = group.moderators - localCommunityMemberId,
                                pendingMembers = group.pendingMembers - localCommunityMemberId)
                            persistCommunityData()
                            communityNotice = "Your request to join " + group.name + " was declined or your membership was removed."
                        }
                    }
                }
                "group_message" -> {
                    val group = communityGroups.firstOrNull { it.code == code }
                    val verifiedName = group?.memberIds?.get(senderId)
                    val messageId = packet.optString("id")
                    val messageBody = packet.optString("body")
                    if (group != null && CommunityAccessPolicy.isValidGroupMessage(group.memberIds, senderId, sender, messageId, messageBody)) {
                        val id = messageId
                        if (communityMessages.none { it.id == id }) {
                            communityMessages.add(CommunityChatMessage(id, code, sender, packet.optString("body").trim().take(1500), System.currentTimeMillis()))
                            while (communityMessages.size > 300) communityMessages.removeAt(0)
                            persistCommunityData()
                        }
                    }
                }
                "broadcast" -> communityNotice = "Broadcast from " + sender + ": " + packet.optString("body").take(500)
                "game_invite" -> if (activeGame == null) incomingGameInvite = GameInvite(packet.optString("matchId"), sender)
                "game_accept" -> activeGame?.let { if (it.matchId == packet.optString("matchId") && it.status == "Waiting for a player") activeGame = it.copy(opponent = sender, status = "Your turn") }
                "game_move" -> {
                    val game = activeGame
                    val symbol = packet.optString("symbol")
                    val index = packet.optInt("index", -1)
                    if (game != null && game.matchId == packet.optString("matchId") && sender == game.opponent && symbol != game.localSymbol && index in 0..8 && game.board[index] == '.' && game.turn == symbol) {
                        val board = game.board.toCharArray()
                        board[index] = symbol.first()
                        val next = String(board)
                        val winner = ticTacToeWinner(next)
                        activeGame = game.copy(board = next, turn = if (symbol == "X") "O" else "X", status = winner?.let { "$it wins" } ?: if (next.none { it == '.' }) "Draw" else "Your turn")
                    }
                }
                "game_chat" -> activeGame?.let { game ->
                    if (game.matchId == packet.optString("matchId")) {
                        if (sender == game.opponent) gameChatMessages.add(sender + ": " + packet.optString("body").take(300))
                        while (gameChatMessages.size > 60) gameChatMessages.removeAt(0)
                    }
                }
                "game_end" -> activeGame?.let { game ->
                    if (game.matchId == packet.optString("matchId") && sender == game.opponent) {
                        activeGame = null
                        gameChatMessages.clear()
                        communityNotice = "$sender ended the match."
                    }
                }
            }
        } catch (_: Exception) { communityNotice = "A community packet was invalid and ignored." }
    }
    private fun inviteToGame() {
        if (!hybridConnected || activeGame != null) return
        val id = UUID.randomUUID().toString()
        activeGame = TicTacToeMatch(id, ".........", "X", "X", "Waiting for a player", "Nearby opponent")
        gameChatMessages.clear()
        if (!sendCommunityPacket(JSONObject().put("type", "game_invite").put("matchId", id).put("sender", displayName))) activeGame = null
    }
    private fun acceptGameInvite() {
        val invite = incomingGameInvite ?: return
        activeGame = TicTacToeMatch(invite.matchId, ".........", "X", "O", "Waiting for X", invite.sender)
        incomingGameInvite = null
        gameChatMessages.clear()
        nearbyTransport?.sendToNames("NR_COMMUNITY:" + JSONObject().put("type", "game_accept").put("matchId", invite.matchId).put("sender", displayName), setOf(invite.sender))
    }
    private fun playGameMove(index: Int) {
        val game = activeGame ?: return
        if (game.status.endsWith("wins") || game.status == "Draw" || game.turn != game.localSymbol || index !in 0..8 || game.board[index] != '.') return
        val board = game.board.toCharArray()
        board[index] = game.localSymbol.first()
        val next = String(board)
        val winner = ticTacToeWinner(next)
        activeGame = game.copy(board = next, turn = if (game.localSymbol == "X") "O" else "X", status = winner?.let { "$it wins" } ?: if (next.none { it == '.' }) "Draw" else "Waiting for opponent")
        nearbyTransport?.sendToNames("NR_COMMUNITY:" + JSONObject().put("type", "game_move").put("matchId", game.matchId).put("index", index).put("symbol", game.localSymbol).put("sender", displayName), setOf(game.opponent))
    }
    private fun endGame() {
        activeGame?.let { game ->
            nearbyTransport?.sendToNames("NR_COMMUNITY:" + JSONObject().put("type", "game_end").put("matchId", game.matchId), setOf(game.opponent))
        }
        activeGame = null
        incomingGameInvite = null
        gameChatMessages.clear()
        communityNotice = "Game closed."
    }

    private fun sendGameChat(raw: String) {
        val game = activeGame ?: return
        val body = raw.trim().take(300)
        if (body.isNotBlank() && nearbyTransport?.sendToNames("NR_COMMUNITY:" + JSONObject().put("type", "game_chat").put("matchId", game.matchId).put("sender", displayName).put("body", body), setOf(game.opponent)) == true) {
            gameChatMessages.add("$displayName: $body")
            while (gameChatMessages.size > 60) gameChatMessages.removeAt(0)
        }
    }

    private fun getShareableApps(): List<ShareableApp> {
        return try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                // Include user-installed and preinstalled system apps whenever Android exposes
                // a readable APK path. The receiver still decides whether Android allows installation.
                .filter { appInfo -> appInfo.packageName != packageName }
                .mapNotNull { appInfo ->
                    try {
                        val base = appInfo.sourceDir?.takeIf { File(it).isFile } ?: return@mapNotNull null
                        val splits = appInfo.splitSourceDirs?.filter { File(it).isFile } ?: emptyList()
                        val drawable = packageManager.getApplicationIcon(appInfo)
                        val iconSize = 96
                        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, iconSize, iconSize)
                        drawable.draw(canvas)
                        val label = packageManager.getApplicationLabel(appInfo).toString()
                        ShareableApp(label, appInfo.packageName, listOf(base) + splits, bitmap.asImageBitmap())
                    } catch (_: Exception) {
                        null
                    }
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()
        } catch (_: Exception) { emptyList() }
    }

    private fun prepareAppForSharing(app: ShareableApp): Uri? {
        return try {
            val outputDir = File(cacheDir, "nearer-apps").apply { mkdirs() }
            val safeName = app.label.replace(Regex("[^A-Za-z0-9._-]"), "_").take(55).ifBlank { "Android-app" }
            val output = if (app.apkPaths.size == 1) File(outputDir, safeName + ".apk") else File(outputDir, safeName + ".nearer-app.zip")
            if (output.exists()) output.delete()
            if (app.apkPaths.size == 1) {
                FileInputStream(app.apkPaths.first()).use { input ->
                    FileOutputStream(output).use { target -> input.copyTo(target) }
                }
            } else {
                ZipOutputStream(FileOutputStream(output)).use { zip ->
                    app.apkPaths.forEachIndexed { index, path ->
                        zip.putNextEntry(ZipEntry(if (index == 0) "base.apk" else "split_" + index + ".apk"))
                        FileInputStream(path).use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("README.txt"))
                    zip.write(("NeaRer app package for " + app.label + " (" + app.packageName + "). Keep base.apk and every split_*.apk together. Installing requires an Android split-package installer.").toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            FileProvider.getUriForFile(this, packageName + ".files", output)
        } catch (_: Exception) {
            null
        }
    }

    private fun openReceivedFile(item: ReceivedFile) {
        val extension = item.file.extension.lowercase()
        if (item.file.name.endsWith(".nearer-app.zip", ignoreCase = true)) {
            chatNotice = "This app package contains multiple APK parts. Keep the ZIP together and use a split-APK installer that supports installing all parts at once."
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.files", item.file)
            if (extension == "apk") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                    chatNotice = "Allow NeaRer to install apps in Android settings, then tap this APK again. Android will still ask you to confirm installation."
                    val settingsIntent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(settingsIntent)
                    return
                }
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(installIntent)
                chatNotice = "Opening Android's app installer. Review the app details and approve installation there."
                return
            }
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            chatNotice = if (extension == "apk") {
                "Android could not open the package installer for this APK."
            } else {
                "No app is available to open this file."
            }
        } catch (_: Exception) {
            chatNotice = "The file could not be opened. Check that it was received completely and try again."
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
            outgoingTransferredBytes = 0L
            outgoingTotalBytes = 0L
            outgoingSpeedBytesPerSecond = 0L
            outgoingRateSampleAt = 0L
            outgoingRateSampleBytes = 0L
            chatNotice = "File transfer cancelled."
            return
        }
        cancelledOutgoingTransfers.add(id)
        sendWireLine("FILE_CANCEL|$id")
        chatNotice = "Cancelling file transfer…"
    }

    private fun queueChatFile(uri: Uri, notice: String) {
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        if (uri.toString() !in pendingChatFileUris) pendingChatFileUris.add(uri.toString())
        persistPendingChatFiles()
        chatNotice = notice
    }

    private fun sendFile(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        if (!chatReady) {
            queueChatFile(uri, "Attachment saved on this phone • it will wait here until you connect to someone nearby.")
            return
        }
        if (outgoingTransferId != null) {
            queueChatFile(uri, "Added to your prepared attachments. It will send after the current transfer finishes.")
            return
        }
        if (hybridConnected) {
            val endpoint = hybridConnectedEndpoint
            if (endpoint == null) {
                chatNotice = "The nearby connection is not ready. Reconnect and try again."
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
            val fileSize = try { contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L } catch (_: Exception) { 0L }
            pendingSentFileName = fileName
            pendingSentFileUri = uri.toString()
            pendingSentFileSize = fileSize
            val payloadId = nearbyTransport?.sendFile(endpoint, uri, fileName)
            if (payloadId != null) {
                outgoingTransferId = "nearby:$payloadId"
                outgoingProgress = 0f
            outgoingTransferredBytes = 0L
            outgoingTotalBytes = 0L
            outgoingSpeedBytesPerSecond = 0L
            outgoingRateSampleAt = 0L
            outgoingRateSampleBytes = 0L
                chatNotice = "Preparing to send $fileName over Nearby Connections…"
            }
            return
        }
        if (!chatReady) return
        val transferId = UUID.randomUUID().toString()
        pendingSentFileUri = uri.toString()
        pendingSentFileName = null
        pendingSentFileSize = 0L
        outgoingTransferId = transferId
        outgoingProgress = 0f
            outgoingTransferredBytes = 0L
            outgoingTotalBytes = 0L
            outgoingSpeedBytesPerSecond = 0L
            outgoingRateSampleAt = 0L
            outgoingRateSampleBytes = 0L
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
                runOnUiThread {
                    pendingSentFileName = fileName
                    pendingSentFileSize = fileSize.coerceAtLeast(0L)
                    outgoingTransferredBytes = 0L
                    outgoingTotalBytes = fileSize.coerceAtLeast(0L)
                }
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
                        digest.update(buffer, 0, count)
                        val chunk = Base64.getEncoder().encodeToString(buffer.copyOf(count))
                        if (!sendWireLine("FILE_CHUNK|$id|0|$chunk")) throw IllegalStateException("Connection lost during transfer")
                        val fraction = if (fileSize > 0L) (sentBytes.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f
                        if (sentBytes % (256 * 1024) < count || fraction >= 1f) {
                            val progress = if (fileSize > 0L) " • ${(fraction * 100).toInt()}%" else " • ${sentBytes / 1024} KB"
                            runOnUiThread {
                                outgoingProgress = fraction
                                outgoingTransferredBytes = sentBytes
                                outgoingTotalBytes = fileSize.coerceAtLeast(0L)
                                updateTransferRate(false, sentBytes)
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
            outgoingTransferredBytes = 0L
            outgoingTotalBytes = 0L
            outgoingSpeedBytesPerSecond = 0L
            outgoingRateSampleAt = 0L
            outgoingRateSampleBytes = 0L
                    chatNotice = if (e.message?.contains("cancel", ignoreCase = true) == true) {
                        "File transfer cancelled."
                    } else {
                        "File transfer failed: ${e.message ?: "connection problem"}"
                    }
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
                    val rawLine = reader.readLine() ?: break
                    if (rawLine.length > 96 * 1024 || !rawLine.startsWith("ENC|")) {
                        throw IllegalStateException("An unencrypted or oversized NeaRer frame was rejected")
                    }
                    val sessionKey = peerSessionKey ?: throw IllegalStateException("Encrypted session is not ready")
                    val line = PeerSessionCrypto.decrypt(rawLine.substringAfter("ENC|"), sessionKey)
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
                                        chatMessages.add(ChatMessage(
                                            id, body, outgoing = false, delivery = "Received",
                                            conversationId = connectedChatPeerId ?: activeChatPeerId,
                                            conversationName = connectedChatPeerName ?: activeChatPeerName
                                        ))
                                        chatNotice = "Saving your received message before confirming delivery…"
                                        persistReceivedMessageThenAcknowledge(id, socket = socket)
                                    }
                                } else if (body.isNotBlank() && persistedMessageIds.contains(id)) {
                                    synchronized(writerLock) {
                                        if (peerSocket === socket) sendWireLine("ACK|$id")
                                    }
                                } else if (body.isNotBlank()) {
                                    persistReceivedMessageThenAcknowledge(id, socket = socket)
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
                                    synchronized(writerLock) { sendWireLine("FILE_DONE|$id|ERROR") }
                                } else {
                                    val folder = File(filesDir, "received").apply { mkdirs() }
                                    val target = File(folder, "${System.currentTimeMillis()}-$safeName")
                                    incomingTransfers[id] = IncomingTransfer(safeName, expected, target, FileOutputStream(target))
                                    runOnUiThread {
                                        incomingTransferName = safeName
                                        incomingProgress = 0f
                                        incomingTransferredBytes = 0L
                                        incomingTotalBytes = expected
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
                                                incomingTransferredBytes = 0L
                                                incomingTotalBytes = 0L
                                                incomingSpeedBytesPerSecond = 0L
                                                incomingRateSampleAt = 0L
                                                incomingRateSampleBytes = 0L
                                                chatNotice = "The received file was larger than its declared size. Transfer stopped."
                                            }
                                            synchronized(writerLock) { sendWireLine("FILE_DONE|${parts[1]}|ERROR") }
                                        } else {
                                            transfer.output.write(bytes)
                                            val fraction = if (transfer.expectedBytes > 0L) {
                                                (transfer.receivedBytes.toFloat() / transfer.expectedBytes.toFloat()).coerceIn(0f, 1f)
                                            } else 0f
                                            runOnUiThread {
                                                incomingProgress = fraction
                                                incomingTransferredBytes = transfer.receivedBytes
                                                incomingTotalBytes = transfer.expectedBytes
                                                updateTransferRate(true, transfer.receivedBytes)
                                                chatNotice = if (transfer.expectedBytes > 0L) {
                                                    "Receiving ${transfer.name} • ${(fraction * 100).toInt()}%"
                                                } else {
                                                    "Receiving ${transfer.name} • ${transfer.receivedBytes / 1024} KB"
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        val failedTransfer = incomingTransfers.remove(parts[1])
                                        failedTransfer?.close()
                                        failedTransfer?.file?.delete()
                                        synchronized(writerLock) { sendWireLine("FILE_DONE|${parts[1]}|ERROR") }
                                        runOnUiThread {
                                            incomingTransferName = null
                                            incomingProgress = 0f
                                            incomingTransferredBytes = 0L
                                            incomingTotalBytes = 0L
                                            incomingSpeedBytesPerSecond = 0L
                                            incomingRateSampleAt = 0L
                                            incomingRateSampleBytes = 0L
                                            chatNotice = "The received file could not be written. Transfer stopped."
                                        }
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
                                            incomingTransferredBytes = 0L
                                            incomingTotalBytes = 0L
                                            incomingSpeedBytesPerSecond = 0L
                                            incomingRateSampleAt = 0L
                                            incomingRateSampleBytes = 0L
                                            receivedFiles.add(0, ReceivedFile(transfer.name, transfer.file))
                                            chatNotice = "Received and verified: ${transfer.name}. Tap it below to open."
                                        }
                                        synchronized(writerLock) { sendWireLine("FILE_DONE|$id|OK") }
                                    } else {
                                        transfer.file.delete()
                                        synchronized(writerLock) { sendWireLine("FILE_DONE|$id|ERROR") }
                                        runOnUiThread {
                                            incomingTransferName = null
                                            incomingProgress = 0f
                                            incomingTransferredBytes = 0L
                                            incomingTotalBytes = 0L
                                            incomingSpeedBytesPerSecond = 0L
                                            incomingRateSampleAt = 0L
                                            incomingRateSampleBytes = 0L
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
            outgoingTransferredBytes = 0L
            outgoingTotalBytes = 0L
            outgoingSpeedBytesPerSecond = 0L
            outgoingRateSampleAt = 0L
            outgoingRateSampleBytes = 0L
                                }
                                if (ok) {
                                    val savedUri = pendingSentFileUri
                                    val savedName = pendingSentFileName
                                    if (!savedUri.isNullOrBlank() && !savedName.isNullOrBlank()) {
                                        sentFiles.add(0, SentFile(savedName, savedUri, pendingSentFileSize, System.currentTimeMillis()))
                                        persistSentFiles()
                                    }
                                    pendingSentFileUri = null
                                    pendingSentFileName = null
                                    pendingSentFileSize = 0L
                                } else {
                                    pendingSentFileUri = null
                                    pendingSentFileName = null
                                    pendingSentFileSize = 0L
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
                                incomingTransferredBytes = 0L
                                incomingTotalBytes = 0L
                                incomingSpeedBytesPerSecond = 0L
                                incomingRateSampleAt = 0L
                                incomingRateSampleBytes = 0L
                                chatNotice = "The other phone cancelled the file transfer."
                            }
                        }
                        line == "PING" -> synchronized(writerLock) { sendWireLine("PONG") }
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
                    if (incomingTransferName != null) {
                        incomingTransferName = null
                        incomingProgress = 0f
                        incomingTransferredBytes = 0L
                        incomingTotalBytes = 0L
                        incomingSpeedBytesPerSecond = 0L
                        incomingRateSampleAt = 0L
                        incomingRateSampleBytes = 0L
                    }
                    if (wasReady) {
                        chatNotice = "The nearby connection ended. Any unfinished incoming file was discarded. Reconnect to continue chatting."
                        connectionMessage = "Connection ended"
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_DISCOVERY_PERMISSION = 410
    private const val REQUEST_RECORD_AUDIO = 411
    }
}

@androidx.compose.runtime.Composable
private fun NeaRerHome(
    playerName: String,
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
    sentFiles: List<SentFile>,
    onSendMessage: (String) -> Unit,
    onSelectConversation: (String, String) -> Unit,
    isRecordingVoiceNote: Boolean,
    onVoiceNoteAction: () -> Unit,
    onSendQueuedMessages: () -> Unit,
    pendingChatFilesCount: Int,
    onSendQueuedFiles: () -> Unit,
    groups: List<CommunityGroup>,
    groupMessages: List<CommunityChatMessage>,
    selectedGroupCode: String?,
    communityNotice: String,
    communityLocalMemberId: String,
    incomingGameInvite: GameInvite?,
    activeGame: TicTacToeMatch?,
    gameChatMessages: List<String>,
    onCreateGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onSelectGroup: (String) -> Unit,
    onLeaveGroup: (String) -> Unit,
    onApproveMember: (String, String) -> Unit,
    onPromoteMember: (String, String) -> Unit,
    onTransferOwnership: (String, String) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onSendGroupMessage: (String) -> Unit,
    onBroadcast: (String) -> Unit,
    onGameInvite: () -> Unit,
    onAcceptGameInvite: () -> Unit,
    onRejectGameInvite: () -> Unit,
    onGameMove: (Int) -> Unit,
    onGameChat: (String) -> Unit,
    onEndGame: () -> Unit,
    onSendFile: (Uri) -> Unit,
    onLoadShareableApps: () -> List<ShareableApp>,
    onPrepareAppForSharing: (ShareableApp, (Uri?) -> Unit) -> Unit,
    onCancelTransfer: () -> Unit,
    outgoingTransferId: String?,
    outgoingProgress: Float,
    incomingTransferName: String?,
    incomingProgress: Float,
    incomingTransferredBytes: Long,
    incomingTotalBytes: Long,
    incomingSpeedBytesPerSecond: Long,
    outgoingTransferredBytes: Long,
    outgoingTotalBytes: Long,
    outgoingSpeedBytesPerSecond: Long,
    onOpenFile: (ReceivedFile) -> Unit,
    onOpenSentFile: (SentFile) -> Unit,
    onShareReceivedFile: (ReceivedFile) -> Unit,
    onResendSentFile: (SentFile) -> Unit,
    onShareSentFile: (SentFile) -> Unit,
    onDisconnect: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    hybridPeers: Map<String, String>,
    recentlySeenPeers: Map<String, NearbyPerson>,
    displayName: String,
    onSaveDisplayName: (String) -> Boolean,
    meshRelayEnabled: Boolean,
    onMeshRelayChange: (Boolean) -> Unit,
    onHybridConnect: (String) -> Unit,
    onFindNearby: () -> Unit,
    onStopSearch: () -> Unit,
    onConnect: (WifiP2pDevice) -> Unit,
    flashRoleReady: Boolean,
    flashLastSignal: String?,
    onRequestFlashScreeningRole: () -> Unit
) {
    val flashContext = androidx.compose.ui.platform.LocalContext.current
    var flashRecipientNumber by remember { mutableStateOf("") }
    var flashCustomMessage by remember { mutableStateOf("") }
    var flashPresetName by remember { mutableStateOf("PING") }
    var flashCallStep by remember { mutableStateOf(0) }
    var flashSenderNotice by remember { mutableStateOf("Choose a signal and enter the recipient's phone number.") }
    var flashTrustedNumber by remember { mutableStateOf("") }
    var flashTrustedName by remember { mutableStateOf("") }
    var flashTrustedCount by remember { mutableStateOf(FlashTrustedSenders.trustedCount(flashContext)) }
    var flashContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(flashContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val flashContactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        flashContactsPermission = granted
        flashSenderNotice = if (granted) {
            "Saved-contact Flash access enabled. NeaRer can now receive screening events for paired numbers saved in your contacts."
        } else {
            "Contacts access was declined. Flash can still work with paired numbers that are not saved in your contacts."
        }
    }
    val flashPresets = remember { FlashSignalPattern.presets() }
    val selectedFlashPreset = flashPresets.firstOrNull { it.name == flashPresetName } ?: flashPresets.first()
    val flashCallCount = selectedFlashPreset.gapsMillis.size + 1
    val flashStepGap = selectedFlashPreset.gapsMillis.getOrNull(flashCallStep - 1)
    val localChatPrefs = flashContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
    var draft by remember { mutableStateOf(localChatPrefs.getString("chat_draft", "") ?: "") }
    var selectedConversationId by remember { mutableStateOf(messages.lastOrNull()?.conversationId ?: "") }
    val conversationRows = messages.groupBy { it.conversationId.ifBlank { "legacy" } }
        .map { (peerId, thread) ->
            val latest = thread.maxByOrNull { it.timestamp }
            Triple(peerId, latest?.conversationName?.ifBlank { "Nearby contact" } ?: "Nearby contact", latest)
        }
        .sortedByDescending { it.third?.timestamp ?: 0L }
    val visibleMessages = if (selectedConversationId.isBlank()) messages else messages.filter {
        (it.conversationId.ifBlank { "legacy" }) == selectedConversationId
    }
    LaunchedEffect(messages.size, selectedConversationId) {
        if (selectedConversationId.isBlank() && conversationRows.isNotEmpty()) {
            selectedConversationId = conversationRows.first().first
        }
        conversationRows.firstOrNull { it.first == selectedConversationId }?.let { row ->
            onSelectConversation(row.first, row.second)
        }
    }
    var showChatAttachmentMenu by remember { mutableStateOf(false) }
    val isConnecting = status.contains("connecting", ignoreCase = true) && !chatReady
    val messageListState = rememberLazyListState()
    LaunchedEffect(visibleMessages.size, selectedConversationId) {
        if (visibleMessages.isNotEmpty()) messageListState.animateScrollToItem(visibleMessages.lastIndex)
    }
    var showLudo by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf("home") }
    var sectionHistory by remember { mutableStateOf(listOf("home")) }
    val navigateToSection: (String) -> Unit = { destination ->
        if (destination != activeSection) {
            sectionHistory = sectionHistory + destination
            activeSection = destination
        }
    }
    var hasChosenName by remember { mutableStateOf(displayName != "NeaRer User") }
    BackHandler(enabled = showLudo || sectionHistory.size > 1) {
        if (showLudo) {
            showLudo = false
        } else {
            sectionHistory = sectionHistory.dropLast(1)
            activeSection = sectionHistory.lastOrNull() ?: "home"
        }
    }
    // Keep each destination's scroll position independent when users move between tabs.
    val pageScrollStates = remember { mutableStateMapOf<String, androidx.compose.foundation.ScrollState>() }
    val pageScrollState = pageScrollStates.getOrPut(activeSection) { androidx.compose.foundation.ScrollState(0) }
    val sectionPositions = remember { mutableStateMapOf<String, Int>() }
    val navigationScope = rememberCoroutineScope()
    var nameDraft by remember(displayName) { mutableStateOf(displayName) }
    var nameSaveFeedback by remember { mutableStateOf<String?>(null) }
    val searchMotion = rememberInfiniteTransition(label = "nearby-search")
    val orbitRotation by searchMotion.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing)), label = "orbit")
    val pulse by searchMotion.animateFloat(0.72f, 1f, infiniteRepeatable(tween(1050), RepeatMode.Reverse), label = "pulse")
    var pendingFileTypes by remember { mutableStateOf<Array<String>?>(null) }
    var receiveMode by remember { mutableStateOf(false) }
    var showInstalledApps by remember { mutableStateOf(false) }
    var showReceivedHistory by remember { mutableStateOf(false) }
    var sharedFilesFilter by remember { mutableStateOf("All") }
    var sharedFilesSearch by remember { mutableStateOf("") }
    var sharedFilesSort by remember { mutableStateOf("Recent") }
    var pendingAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<ShareableApp>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var appShareNotice by remember { mutableStateOf<String?>(null) }
    var isPreparingApp by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(onSendFile)
    }
    if (showChatAttachmentMenu) {
        AlertDialog(
            onDismissRequest = { showChatAttachmentMenu = false },
            containerColor = Color(0xFF102B50),
            title = { Text("SHARE SOMETHING", color = White, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose what you want to share. Files can wait here until someone connects nearby.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    listOf(Triple("Photos & videos", "Images and clips", "media"), Triple("Documents", "PDF, Word, Excel and more", "docs"), Triple("Audio & music", "Sound files and recordings", "audio"), Triple("Any file", "Browse phone storage", "files"), Triple("Installed apps", "Share an app package", "apps")).forEach { item ->
                        Surface(modifier = Modifier.fillMaxWidth().clickable {
                            showChatAttachmentMenu = false
                            when (item.third) {
                                "media" -> filePicker.launch(arrayOf("image/*", "video/*"))
                                "docs" -> filePicker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*"))
                                "audio" -> filePicker.launch(arrayOf("audio/*"))
                                "apps" -> {
                                    showInstalledApps = true
                                    isLoadingApps = true
                                    navigationScope.launch {
                                        installedApps = withContext(Dispatchers.IO) { onLoadShareableApps() }
                                        isLoadingApps = false
                                    }
                                }
                                else -> filePicker.launch(arrayOf("*/*"))
                            }
                        }, color = Color(0xFF173A67), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF315B8D))) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.first, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(item.second, color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChatAttachmentMenu = false }) { Text("CLOSE", color = Gold) } }
        )
    }
    // Keep the exact file-selection intent while nearby discovery establishes a connection.
    LaunchedEffect(chatReady, pendingFileTypes) {
        val requestedTypes = pendingFileTypes
        if (chatReady && requestedTypes != null) {
            pendingFileTypes = null
            filePicker.launch(requestedTypes)
        }
    }
    LaunchedEffect(chatReady, pendingAppPicker) {
        if (chatReady && pendingAppPicker) {
            pendingAppPicker = false
            showInstalledApps = true
            isLoadingApps = true
            installedApps = withContext(Dispatchers.IO) { onLoadShareableApps() }
            isLoadingApps = false
        }
    }
    if (showInstalledApps) {
        AlertDialog(
            onDismissRequest = { showInstalledApps = false },
            containerColor = Color(0xFF102B50),
            title = { Text("SHARE AN APP", color = White, fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Your installed apps", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Choose an app to package and send to the nearby phone.", color = Muted, fontSize = 9.sp)
                        }
                        TextButton(
                            onClick = {
                                if (!isLoadingApps) {
                                    isLoadingApps = true
                                    navigationScope.launch {
                                        installedApps = withContext(Dispatchers.IO) { onLoadShareableApps() }
                                        isLoadingApps = false
                                    }
                                }
                            },
                            enabled = !isLoadingApps
                        ) {
                            Text(if (isLoadingApps) "SCANNING…" else "↻ REFRESH", color = if (isLoadingApps) Muted else Gold, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    if (isLoadingApps) {
                        Text("Scanning installed apps and preparing their icons…", color = Gold, fontSize = 11.sp)
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Gold,
                            trackColor = Color(0xFF24466F)
                        )
                    }
                    var appSearch by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search apps", color = Muted) }
                    )
                    var appFilter by remember { mutableStateOf("All") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All", "Single APK", "Split APK").forEach { filter ->
                            val selected = appFilter == filter
                            Surface(
                                modifier = Modifier.weight(1f).clickable { appFilter = filter },
                                color = if (selected) Color(0xFF1B5EB5) else Color(0xFF0B2342),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (selected) Gold.copy(alpha = 0.8f) else Color(0xFF31547E))
                            ) {
                                Text(
                                    filter,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp),
                                    color = if (selected) White else Muted,
                                    fontSize = 9.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    val visibleApps = installedApps.filter { app ->
                        (appSearch.isBlank() || app.label.contains(appSearch, ignoreCase = true) ||
                            app.packageName.contains(appSearch, ignoreCase = true)) &&
                            when (appFilter) {
                                "Single APK" -> app.apkPaths.size == 1
                                "Split APK" -> app.apkPaths.size > 1
                                else -> true
                            }
                    }
                    Text(
                        if (isLoadingApps) "Scanning this phone's app library…" else
                            "${visibleApps.size} of ${installedApps.size} shareable apps shown • Read on this phone only",
                        color = if (isLoadingApps) Gold else Muted,
                        fontSize = 10.sp
                    )
                    if (isLoadingApps) {
                        Text("Please wait while NeaRer reads the app library on this phone.", color = White, fontSize = 12.sp)
                    } else if (installedApps.isEmpty()) {
                        Text("No shareable apps were found. Check that this phone has apps installed.", color = White, fontSize = 13.sp)
                    } else if (visibleApps.isEmpty()) {
                        val emptyMessage = when {
                            appSearch.isNotBlank() && appFilter != "All" -> "No ${appFilter.lowercase()} apps match “${appSearch.trim()}”."
                            appSearch.isNotBlank() -> "No apps match “${appSearch.trim()}”. Try another name or package ID."
                            appFilter != "All" -> "No ${appFilter.lowercase()} apps were found on this phone."
                            else -> "No shareable apps were found on this phone."
                        }
                        Text(emptyMessage, color = White, fontSize = 13.sp)
                    }
                    if (!isLoadingApps) visibleApps.forEach { app ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                showInstalledApps = false
                                isPreparingApp = true
                                appShareNotice = "Preparing " + app.label + " for sharing…"
                                onPrepareAppForSharing(app) { uri ->
                                    isPreparingApp = false
                                    if (uri != null) {
                                        appShareNotice = app.label + " is prepared. Starting the transfer…"
                                        onSendFile(uri)
                                    } else appShareNotice = "Could not prepare " + app.label + " for sharing."
                                }
                            },
                            color = Color(0xFF173F78), shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF347CEB).copy(alpha = 0.55f))
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(11.dp))
                                )
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(app.label, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (app.apkPaths.size > 1) "Split app • " + app.apkPaths.size + " APK parts" else "App • APK ready", color = Muted, fontSize = 10.sp)
                                    Text(app.packageName, color = Color(0xFF83A5CC), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("›", color = Gold, fontSize = 23.sp)
                            }
                        }
                    }
                    if (installedApps.any { it.apkPaths.size > 1 }) {
                        Text("Some apps contain split APKs. NeaRer sends those together in a ZIP package; the receiver will need a compatible split-APK installer.", color = Color(0xFFFFDFA0), fontSize = 10.sp, lineHeight = 15.sp)
                    }
                    Text(
                        "App availability depends on what Android allows NeaRer to access. Some protected system components may not appear.",
                        color = Color(0xFF91A9C8),
                        fontSize = 9.sp,
                        lineHeight = 14.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showInstalledApps = false }) { Text("CLOSE", color = Gold) } }
        )
    }
    if (showReceivedHistory) {
        AlertDialog(
            onDismissRequest = { showReceivedHistory = false },
            containerColor = Color(0xFF102B50),
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("SHARED FILES", color = White, fontWeight = FontWeight.Black)
                    Text("${receivedFiles.size} received • ${sentFiles.size} sent", color = Muted, fontSize = 10.sp)
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sharedFilesSearch,
                        onValueChange = { sharedFilesSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search files by name", color = Muted, fontSize = 12.sp) }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("All", "Received", "Sent").forEach { filter ->
                                TextButton(onClick = { sharedFilesFilter = filter }) { Text(filter.uppercase(), color = if (sharedFilesFilter == filter) Gold else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        TextButton(onClick = { sharedFilesSort = when (sharedFilesSort) { "Recent" -> "Oldest"; "Oldest" -> "Name"; "Name" -> "Largest"; else -> "Recent" } }) {
                            Text("SORT: $sharedFilesSort", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val searchTerm = sharedFilesSearch.trim()
                    val sortedReceived = receivedFiles.filter { it.displayName.contains(searchTerm, ignoreCase = true) }.let { files ->
                        when (sharedFilesSort) { "Oldest" -> files.sortedBy { it.file.lastModified() }; "Name" -> files.sortedBy { it.displayName.lowercase() }; "Largest" -> files.sortedByDescending { it.file.length() }; else -> files.sortedByDescending { it.file.lastModified() } }
                    }
                    val sortedSent = sentFiles.filter { it.name.contains(searchTerm, ignoreCase = true) }.let { files ->
                        when (sharedFilesSort) { "Oldest" -> files.sortedBy { it.timestamp }; "Name" -> files.sortedBy { it.name.lowercase() }; "Largest" -> files.sortedByDescending { it.sizeBytes }; else -> files.sortedByDescending { it.timestamp } }
                    }
                    val showReceived = sharedFilesFilter != "Sent"
                    val showSent = sharedFilesFilter != "Received"
                    if ((!showReceived || sortedReceived.isEmpty()) && (!showSent || sortedSent.isEmpty())) {
                        Text("Successfully received and sent files appear here. Sent files can be reopened while their original location remains available.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    if (showReceived) sortedReceived.forEach { item ->
                            val exists = item.file.exists()
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF173F78),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF347CEB).copy(alpha = 0.45f))
                            ) {
                                Row(
                                    Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    Text(if (exists) "✓" else "!" , color = if (exists) Color(0xFF8EF0C6) else Color(0xFFFFA5AE), fontSize = 17.sp, fontWeight = FontWeight.Black)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(item.displayName, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (exists) "${formatTransferSize(item.file.length())} • ${formatMessageTime(item.file.lastModified())}" else "File is no longer available", color = Muted, fontSize = 9.sp)
                                    }
                                    TextButton(onClick = { if (exists) onOpenFile(item) }) { Text(if (exists) "OPEN" else "MISSING", color = if (exists) Gold else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                    if (exists) TextButton(onClick = { onShareReceivedFile(item) }) { Text("SHARE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    if (showSent) sortedSent.forEach { item ->
                        Surface(Modifier.fillMaxWidth(), color = Color(0xFF173F78), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Gold.copy(alpha = 0.25f))) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text("↑", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.name, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${formatTransferSize(item.sizeBytes)} • Sent ${formatMessageTime(item.timestamp)}", color = Muted, fontSize = 9.sp)
                                }
                                TextButton(onClick = { onOpenSentFile(item) }) { Text("OPEN", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onResendSentFile(item) }) { Text("SEND AGAIN", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { onShareSentFile(item) }) { Text("SHARE", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReceivedHistory = false }) { Text("DONE", color = Gold) } }
        )
    }
    if (!hasChosenName) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Midnight
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF102D56), Midnight, Color(0xFF061329))))
                    .padding(horizontal = 25.dp, vertical = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(86.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF1554A8),
                        border = BorderStroke(1.dp, Color(0xFF4A91FF))
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(R.drawable.ic_nearer_foreground), contentDescription = "NeaRer connected network N", modifier = Modifier.size(70.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nea", color = White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                            Text("R", color = Gold, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                            Text("er", color = White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                        }
                        Text("YOUR WORLD. YOUR PEOPLE. CLOSER.", color = Color(0xFF7DB5FF), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text("First, what should we call you?", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
                        Text("Choose the name people nearby will see when they find you on NeaRer.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.take(24); nameSaveFeedback = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Your NeaRer name", color = Muted) },
                        shape = RoundedCornerShape(18.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Button(
                        onClick = {
                            if (onSaveDisplayName(nameDraft.trim())) {
                                hasChosenName = true
                                activeSection = "home"
                            } else {
                                nameSaveFeedback = "Please choose a name before continuing."
                            }
                        },
                        enabled = nameDraft.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                    ) {
                        Text("LET'S GO  →", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    nameSaveFeedback?.let { Text(it, color = Color(0xFFFFA7A7), fontSize = 11.sp) }
                    Text("No account. No sign-up. Your name is saved on this phone.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }
        }
        return
    }
    if (showLudo) {
        LudoGameScreen(playerName = playerName, onExit = { showLudo = false })
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Midnight) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF0B2852),
                            0.38f to Midnight,
                            1.0f to Color(0xFF050F22)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(pageScrollState).animateContentSize().padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (activeSection == "home") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_nearer_foreground),
                                contentDescription = "NeaRer connected network symbol",
                                modifier = Modifier.size(34.dp).padding(end = 5.dp)
                            )
                            Text("Nea", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            Text("R", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            Text("er", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("No Data? We Are NeaRer.", color = Muted, fontSize = 12.sp)
                        Text(
                            "Welcome back, ${displayName.trim().ifBlank { "friend" }}",
                            color = Color(0xFFBFD8FF), fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            when (activeSection) {
                                "nearby" -> "Nearby"
                                "chats" -> "Messages"
                                "community" -> "Communities"
                                "games" -> "Game Zone"
                                "flash" -> "NeaRer Flash"
                                else -> "NeaRer"
                            },
                            color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            when (activeSection) {
                                "nearby" -> "Find people around you"
                                "chats" -> "Your private conversations"
                                "community" -> "Your local circles"
                                "games" -> "Play together, stay closer"
                                "flash" -> "Short signals through ordinary phone calls"
                                else -> "No Data? We Are NeaRer."
                            },
                            color = Muted, fontSize = 11.sp
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF123D7A),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.72f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text(
                            displayName.trim().firstOrNull()?.uppercase() ?: "N",
                            color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            AnimatedVisibility(visible = activeSection == "home", enter = slideInVertically(initialOffsetY = { it / 18 }, animationSpec = tween(260)) + fadeIn(tween(220)), exit = slideOutVertically(targetOffsetY = { -it / 24 }, animationSpec = tween(170)) + fadeOut(tween(150))) {
                // File sharing is the primary purpose of NeaRer: make Send and Receive the first actions.
                val smoothIncomingProgress by animateFloatAsState(
                    targetValue = incomingProgress.coerceIn(0f, 1f),
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "home-incoming-progress"
                )
                val smoothOutgoingProgress by animateFloatAsState(
                    targetValue = outgoingProgress.coerceIn(0f, 1f),
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "home-outgoing-progress"
                )
                val homeMotion = rememberInfiniteTransition(label = "home-transfer-motion")
                val homePulse by homeMotion.animateFloat(
                    initialValue = 0.62f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1250, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "home-status-pulse"
                )
                val transferDotOne by homeMotion.animateFloat(0.22f, 1f, infiniteRepeatable(tween(760, delayMillis = 0), RepeatMode.Reverse), label = "transfer-dot-one")
                val transferDotTwo by homeMotion.animateFloat(0.22f, 1f, infiniteRepeatable(tween(760, delayMillis = 180), RepeatMode.Reverse), label = "transfer-dot-two")
                val transferDotThree by homeMotion.animateFloat(0.22f, 1f, infiniteRepeatable(tween(760, delayMillis = 360), RepeatMode.Reverse), label = "transfer-dot-three")
                val homeGlow by homeMotion.animateFloat(
                    initialValue = 0.18f,
                    targetValue = 0.42f,
                    animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "home-fast-send-glow"
                )
                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0D3474),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.dp, Color(0xFF347CEB)),
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF123F87), Color(0xFF0B2854), Color(0xFF102F65))
                                    )
                                )
                                .padding(19.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp)
                        ) {
                            val homeConnectionLabel = when {
                                chatReady -> "CONNECTED"
                                connectingAddress != null -> "CONNECTING"
                                scanning -> "SEARCHING"
                                else -> "OFFLINE READY"
                            }
                            val homeConnectionColor = when {
                                chatReady -> Color(0xFF155B4B)
                                connectingAddress != null -> Color(0xFF765A20)
                                scanning -> Color(0xFF174A58)
                                else -> Color(0xFF173F78)
                            }
                            val homeConnectionTextColor = when {
                                chatReady -> Color(0xFF8EF0C6)
                                connectingAddress != null -> Gold
                                scanning -> Color(0xFF9DEBFF)
                                else -> Color(0xFFD5E6FF)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandBlue.copy(alpha = 0.88f),
                                    border = BorderStroke(1.dp, Color(0xFF8CB8FF).copy(alpha = homePulse * 0.7f)),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Image(painter = painterResource(R.drawable.ic_nearer_foreground), contentDescription = "NeaRer network N", modifier = Modifier.size(32.dp))
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(
                                        "YOUR OFFLINE SHARING SPACE",
                                        color = Color(0xFFFFDFA0), fontSize = 9.sp,
                                        fontWeight = FontWeight.Black, letterSpacing = 1.35.sp
                                    )
                                    Text("Fast. Direct. NeaRer.", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Surface(shape = RoundedCornerShape(30.dp), color = homeConnectionColor) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = homeConnectionTextColor,
                                            modifier = Modifier.size(5.dp).alpha(if (chatReady) 1f else if (scanning || connectingAddress != null) homePulse else 0.8f)
                                        ) {}
                                        Text(homeConnectionLabel, color = homeConnectionTextColor,
                                            fontSize = 8.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                            Text("Share anything.\nStay connected.", color = White, fontSize = 29.sp,
                                lineHeight = 33.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.animateContentSize())
                            Text("Send photos, videos, music, apps and documents directly to people nearby — without mobile data.",
                                color = Color(0xFFD7E5FA), fontSize = 12.sp, lineHeight = 18.sp)
                            appShareNotice?.let { notice ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                                    color = Color(0xFF0B2342),
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("▣", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(notice, color = Color(0xFFFFDFA0), fontSize = 10.sp, lineHeight = 15.sp)
                                    }
                                }
                                if (isPreparingApp) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Gold,
                                        trackColor = Color(0xFF24466F)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        receiveMode = false
                                        if (chatReady) filePicker.launch(arrayOf("*/*"))
                                        else {
                                            pendingFileTypes = arrayOf("*/*")
                                            navigateToSection("nearby")
                                            if (!scanning && connectingAddress == null) onFindNearby()
                                            navigationScope.launch { pageScrollState.animateScrollTo(0) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(15.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = White)
                                ) {
                                    Text(
                                        when {
                                            chatReady -> "↑  SEND NOW"
                                            connectingAddress != null -> "◌  CONNECTING…"
                                            scanning -> "◌  SEARCHING…"
                                            else -> "↑  FIND & SEND"
                                        },
                                        fontSize = 10.sp, fontWeight = FontWeight.Black
                                    )
                                }
                                Button(
                                    onClick = {
                                        receiveMode = true
                                        navigateToSection("nearby")
                                        if (!scanning && connectingAddress == null) onFindNearby()
                                        navigationScope.launch { pageScrollState.animateScrollTo(0) }
                                    },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(15.dp),
                                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.75f)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                                ) {
                                    Text(
                                        when {
                                            connectingAddress != null -> "◌  CONNECTING…"
                                            scanning -> "◌  SEARCHING…"
                                            else -> "↓  RECEIVE"
                                        },
                                        fontSize = 10.sp, fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            val transferGuidance = when {
                                receiveMode && incomingTransferName != null -> "Receiving ${incomingTransferName} from a nearby phone. Keep NeaRer open until it finishes."
                                receiveMode && chatReady -> "You're connected. Ask the sender to select a file; it will arrive here automatically."
                                receiveMode && connectingAddress != null -> "Connecting to the sender. Keep NeaRer open while both phones verify the connection."
                                receiveMode && scanning -> "Searching for nearby senders. Keep this screen open; tap a discovered phone to connect."
                                receiveMode -> "Your phone is ready to receive. Start a nearby search and connect to the sender."
                                connectingAddress != null -> "Connecting to ${connectedDeviceName ?: "the nearby phone"}. Keep both phones close until verification finishes."
                                scanning -> "Searching for nearby phones. Choose a discovered person to connect before selecting files or apps."
                                chatReady -> "Connected to ${connectedDeviceName ?: "a nearby phone"}. Choose any file or app to send."
                                else -> "Find a nearby phone first. Once connected, choose files or apps to share directly."
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().animateContentSize(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (incomingTransferName != null) Color(0xFF123F3A) else Color(0xFF0B2342),
                                border = BorderStroke(
                                    1.dp,
                                    if (incomingTransferName != null) Color(0xFF54D6A5).copy(alpha = 0.55f)
                                    else Color(0xFF4775AD).copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (incomingTransferName != null) Color(0xFF54D6A5) else Gold,
                                        modifier = Modifier.size(7.dp)
                                    ) {}
                                    Text(
                                        transferGuidance,
                                        color = if (incomingTransferName != null) Color(0xFFC8FFEA) else Color(0xFFD7E5FA),
                                        fontSize = 10.sp,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }


                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            receiveMode = false
                            if (chatReady) {
                                filePicker.launch(arrayOf("*/*"))
                            } else {
                                pendingFileTypes = arrayOf("*/*")
                                navigateToSection("nearby")
                                onFindNearby()
                                navigationScope.launch { pageScrollState.animateScrollTo(0) }
                            }
                        },
                        shape = RoundedCornerShape(21.dp),
                        color = Color(0xFF142F54),
                        border = BorderStroke(1.dp, Gold.copy(alpha = homeGlow)),
                        shadowElevation = 7.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.linearGradient(listOf(Color(0xFF24466C), Color(0xFF102847), Color(0xFF18365C))))
                                .padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Gold.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Gold.copy(alpha = 0.42f)),
                                modifier = Modifier.size(48.dp)
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text("ϟ", color = Gold, fontSize = 29.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("FAST SEND", color = White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                                    Text("QUICK ROUTE", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                                Text(
                                    if (chatReady) "Pick anything and send straight away"
                                    else "Connect first — file selection opens automatically.",
                                    color = Color(0xFFD1E0F5), fontSize = 10.sp, lineHeight = 14.sp
                                )
                            }
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(27.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(shape = CircleShape, color = Gold.copy(alpha = 0.10f * homePulse + 0.05f), modifier = Modifier.size(27.dp)) {}
                                Text("›", color = Gold, fontSize = 27.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("BROWSE FILES", color = White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            Text("Choose what to share", color = Muted, fontSize = 10.sp)
                        }
                        val fileCategories = listOf(
                            Triple("▧", "Photos", arrayOf("image/*")),
                            Triple("▶", "Videos", arrayOf("video/*")),
                            Triple("♫", "Music", arrayOf("audio/*")),
                            Triple("▣", "Apps", arrayOf("application/vnd.android.package-archive")),
                            Triple("▤", "Documents", arrayOf("application/pdf", "text/*", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/rtf")),
                            Triple("•••", "All files", arrayOf("*/*"))
                        )
                        fileCategories.chunked(3).forEachIndexed { rowIndex, rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEachIndexed { columnIndex, item ->
                                    val tileBlue = when ((rowIndex * 3) + columnIndex) {
                                        0 -> Color(0xFF1554B8)
                                        1 -> Color(0xFF123F88)
                                        2 -> Color(0xFF174A9B)
                                        3 -> Color(0xFF123D7A)
                                        4 -> Color(0xFF194783)
                                        else -> Color(0xFF102F63)
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f).height(108.dp).animateContentSize().clickable {
                                            receiveMode = false
                                            if (item.second == "Apps") {
                                                if (chatReady) {
                                                    showInstalledApps = true
                                                    isLoadingApps = true
                                                    navigationScope.launch {
                                                        installedApps = withContext(Dispatchers.IO) { onLoadShareableApps() }
                                                        isLoadingApps = false
                                                    }
                                                } else {
                                                    pendingAppPicker = true
                                                    navigateToSection("nearby")
                                                    onFindNearby()
                                                    navigationScope.launch { pageScrollState.animateScrollTo(0) }
                                                }
                                            } else if (chatReady) filePicker.launch(item.third)
                                            else {
                                                pendingFileTypes = item.third
                                                navigateToSection("nearby")
                                                onFindNearby()
                                                navigationScope.launch { pageScrollState.animateScrollTo(0) }
                                            }
                                        },
                                        color = tileBlue,
                                        shape = RoundedCornerShape(19.dp),
                                        border = BorderStroke(1.dp, Color(0xFF5B9BFA).copy(alpha = 0.42f)),
                                        shadowElevation = 5.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize()
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(tileBlue.copy(alpha = 0.98f), Color(0xFF0B2347))
                                                    )
                                                )
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(11.dp),
                                                    color = Gold.copy(alpha = 0.13f),
                                                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.24f))
                                                ) {
                                                    Text(
                                                        item.first,
                                                        color = Gold,
                                                        fontSize = 21.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                                    )
                                                }
                                                Spacer(Modifier.weight(1f))
                                                Text("↗", color = Color(0xFF9FC5FF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(item.second, color = White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                                Text(
                                                    if (chatReady) "Choose files" else "Connect to share",
                                                    color = Color(0xFFB8D2F5), fontSize = 9.sp, fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            if (chatReady) "Choose a category to select files for your connected device."
                            else "Connect to a nearby NeaRer phone to start selecting and sending files.",
                            color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                        )
                    }


                    val hasTransferResult = chatNotice.startsWith("File delivered and verified") ||
                        chatNotice.startsWith("The other phone could not verify") ||
                        chatNotice.startsWith("The received file failed its integrity check") ||
                        chatNotice.startsWith("The other phone cancelled the file transfer") ||
                        chatNotice.startsWith("File transfer failed:") ||
                        chatNotice.startsWith("File transfer cancelled") ||
                        chatNotice.startsWith("File sent to the nearby phone") ||
                        chatNotice.startsWith("Nearby file transfer failed") ||
                        chatNotice.startsWith("Nearby file transfer cancelled") ||
                        chatNotice.startsWith("Could not start file transfer") ||
                        chatNotice.startsWith("Could not send file details") ||
                        chatNotice.startsWith("File could not be prepared") ||
                        chatNotice.startsWith("The received file failed") ||
                        chatNotice.startsWith("The received file was larger than its declared size") ||
                        chatNotice.startsWith("The received file could not be written") ||
                        chatNotice.startsWith("Received and verified:") ||
                        chatNotice.startsWith("Received and saved:")
                    val transferResultCancelled = chatNotice.contains("cancel", ignoreCase = true)
                    val transferResultFailed = !transferResultCancelled && (
                        chatNotice.contains("failed", ignoreCase = true) ||
                            chatNotice.contains("could not", ignoreCase = true) ||
                            chatNotice.contains("stopped", ignoreCase = true) ||
                            chatNotice.contains("larger than its declared size", ignoreCase = true)
                        )
                    val transferResultAccent = when {
                        transferResultCancelled -> Gold
                        transferResultFailed -> Color(0xFFFFA5AE)
                        else -> Color(0xFF8EF0C6)
                    }
                    if (incomingTransferName != null || outgoingTransferId != null || receivedFiles.isNotEmpty() || hasTransferResult) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            color = Color(0xFF102B50),
                            shape = RoundedCornerShape(19.dp),
                            border = BorderStroke(
                                1.dp,
                                if (hasTransferResult && incomingTransferName == null && outgoingTransferId == null)
                                    transferResultAccent.copy(alpha = 0.58f)
                                else Color(0xFF347CEB).copy(alpha = 0.55f)
                            ),
                            shadowElevation = 5.dp
                        ) {
                            Column(
                                Modifier.background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF153A68), Color(0xFF102B50), Color(0xFF0D2442))
                                    )
                                )
                            ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when {
                                            incomingTransferName != null || outgoingTransferId != null -> "TRANSFER ACTIVITY"
                                            hasTransferResult && transferResultCancelled -> "TRANSFER CANCELLED"
                                            hasTransferResult && transferResultFailed -> "TRANSFER NEEDS ATTENTION"
                                            hasTransferResult -> "TRANSFER COMPLETE"
                                            else -> "RECENT TRANSFERS"
                                        },
                                        color = White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (incomingTransferName != null || outgoingTransferId != null) Color(0xFF6BE0B0) else Gold,
                                            modifier = Modifier.size(6.dp).alpha(if (incomingTransferName != null || outgoingTransferId != null) homePulse else 1f)
                                        ) {}
                                        Text(
                                            if (incomingTransferName != null || outgoingTransferId != null) "LIVE TRANSFER" else "RECENT",
                                            color = if (incomingTransferName != null || outgoingTransferId != null) Color(0xFF8EF0C6) else Gold,
                                            fontSize = 9.sp, fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                                incomingTransferName?.let { name ->
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Surface(shape = CircleShape, color = BrandBlue.copy(alpha = homePulse), modifier = Modifier.size(7.dp)) {}
                                            Text("Receiving $name", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                listOf(transferDotOne, transferDotTwo, transferDotThree).forEach { dotAlpha ->
                                                    Surface(shape = CircleShape, color = BrandBlue, modifier = Modifier.size(4.dp).alpha(dotAlpha)) {}
                                                }
                                            }
                                        }
                                        if (incomingProgress <= 0f) {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = BrandBlue,
                                                trackColor = Color(0xFF24436D)
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                progress = { smoothIncomingProgress },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = BrandBlue,
                                                trackColor = Color(0xFF24436D)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${(smoothIncomingProgress * 100).toInt().coerceIn(0, 100)}% received", color = Muted, fontSize = 9.sp)
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                transferProgressDetail(incomingTransferredBytes, incomingTotalBytes, incomingSpeedBytesPerSecond),
                                                color = Color(0xFFB9CBE2),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                                if (outgoingTransferId != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Surface(shape = CircleShape, color = Gold.copy(alpha = homePulse), modifier = Modifier.size(7.dp)) {}
                                            Text(
                                                when {
                                                    chatNotice.startsWith("Sending ") && !chatNotice.startsWith("Sending complete") -> chatNotice.removePrefix("Sending ").substringBefore(" •")
                                                    chatNotice.startsWith("Preparing to send ") -> chatNotice.removePrefix("Preparing to send ").substringBefore(" over ")
                                                    else -> "File transfer in progress"
                                                },
                                                color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                listOf(transferDotOne, transferDotTwo, transferDotThree).forEach { dotAlpha ->
                                                    Surface(shape = CircleShape, color = Gold, modifier = Modifier.size(4.dp).alpha(dotAlpha)) {}
                                                }
                                            }
                                        }
                                        if (outgoingProgress <= 0f) {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Gold,
                                                trackColor = Color(0xFF24436D)
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                progress = { smoothOutgoingProgress },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Gold,
                                                trackColor = Color(0xFF24436D)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${(smoothOutgoingProgress * 100).toInt().coerceIn(0, 100)}% sent", color = Muted, fontSize = 9.sp)
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                transferProgressDetail(outgoingTransferredBytes, outgoingTotalBytes, outgoingSpeedBytesPerSecond),
                                                color = Color(0xFFB9CBE2), fontSize = 9.sp
                                            )
                                            Spacer(Modifier.weight(1f))
                                            TextButton(onClick = onCancelTransfer) {
                                                Text("CANCEL", color = Color(0xFFFFA5AE), fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                                if (receivedFiles.isNotEmpty() || sentFiles.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("RECENTLY RECEIVED", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { showReceivedHistory = true }) {
                                            Text("SHARED FILES · ${receivedFiles.size + sentFiles.size}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                                receivedFiles.take(2).forEach { fileItem ->
                                    val sizeBytes = fileItem.file.length()
                                    val sizeLabel = when {
                                        sizeBytes < 1024L -> "$sizeBytes B"
                                        sizeBytes < 1024L * 1024L -> "${(sizeBytes / 1024.0).toInt()} KB"
                                        sizeBytes < 1024L * 1024L * 1024L -> "${(sizeBytes / (1024.0 * 1024.0) * 10).toInt() / 10.0} MB"
                                        else -> "${(sizeBytes / (1024.0 * 1024.0 * 1024.0) * 10).toInt() / 10.0} GB"
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("✓", color = Color(0xFF8EF0C6), fontSize = 18.sp, fontWeight = FontWeight.Black)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                fileItem.displayName,
                                                color = White,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text("Saved on this phone • $sizeLabel", color = Muted, fontSize = 8.sp)
                                        }
                                        TextButton(onClick = { onOpenFile(fileItem) }) {
                                            Text("OPEN", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (hasTransferResult && incomingTransferName == null && outgoingTransferId == null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .background(transferResultAccent.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            when {
                                                transferResultCancelled -> "↺"
                                                transferResultFailed -> "!"
                                                else -> "✓"
                                            },
                                            color = when {
                                                transferResultCancelled -> Gold
                                                transferResultFailed -> Color(0xFFFFA5AE)
                                                else -> Color(0xFF8EF0C6)
                                            },
                                            fontSize = 15.sp, fontWeight = FontWeight.Black
                                        )
                                        Text(chatNotice, color = Color(0xFFD6E5FA), fontSize = 10.sp,
                                            lineHeight = 14.sp, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0D2545),
                            shape = RoundedCornerShape(19.dp),
                            border = BorderStroke(1.dp, Color(0xFF31547E).copy(alpha = 0.75f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1A3B64),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Text("↗", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("YOUR TRANSFER ACTIVITY", color = White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("Your sent and received files will appear here.", color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
                                }
                                Text("READY", color = Color(0xFF8EF0C6), fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("QUICK ACCESS", color = White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        Text("Everything in one place", color = Muted, fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            listOf("✉", "Chats", "chats", "Keep talking"),
                            listOf("⌕", "Nearby", "nearby", "Find people")
                        ).forEach { item ->
                            Surface(
                                modifier = Modifier.weight(1f).clickable {
                                    navigateToSection(item[2])
                                    if (item[2] == "nearby") onFindNearby()
                                    navigationScope.launch { pageScrollState.animateScrollTo(0) }
                                },
                                color = Color(0xFF12315D),
                                shape = RoundedCornerShape(19.dp),
                                border = BorderStroke(1.dp, Color(0xFF28589A))
                            ) {
                                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(item[0], color = Gold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                    Text(item[1], color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(item[3], color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RECENT CHATS", color = White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            navigateToSection("chats")
                            navigationScope.launch { pageScrollState.animateScrollTo(0) }
                        }) { Text("VIEW ALL  →", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF10294D),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF244A7A))
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {
                            if (messages.isEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = Color(0xFF1B4E91), modifier = Modifier.size(43.dp)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text("✉", color = White, fontSize = 20.sp)
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                        Text("Your conversations start here", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Connect nearby to send your first message.", color = Muted, fontSize = 10.sp)
                                    }
                                    Text("›", color = Gold, fontSize = 24.sp)
                                }
                            } else {
                                messages.takeLast(3).reversed().forEachIndexed { index, message ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = if (message.outgoing) Color(0xFF1A58A7) else Color(0xFF31527D),
                                            modifier = Modifier.size(42.dp)) {
                                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                                Text(if (message.outgoing) "Y" else (connectedDeviceName ?: "N").take(1).uppercase(),
                                                    color = White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                            Text(if (message.outgoing) "You" else (connectedDeviceName ?: "Nearby contact"),
                                                color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(message.body, color = Muted, fontSize = 10.sp, maxLines = 1)
                                        }
                                        Text(message.delivery, color = if (message.outgoing) Color(0xFF8DBAFF) else Gold, fontSize = 9.sp)
                                    }
                                    if (index < minOf(messages.size, 3) - 1) androidx.compose.material3.HorizontalDivider(color = Color(0xFF24436D))
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                navigateToSection("community")
                                navigationScope.launch { pageScrollState.animateScrollTo(0) }
                            },
                            color = Color(0xFF10294D), shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, Color(0xFF244A7A))
                        ) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("♧", color = Gold, fontSize = 22.sp)
                                Text("Communities", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Your local circles", color = Muted, fontSize = 9.sp)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                navigateToSection("games")
                                navigationScope.launch { pageScrollState.animateScrollTo(0) }
                            },
                            color = Color(0xFF10294D), shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, Color(0xFF244A7A))
                        ) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("♟", color = Gold, fontSize = 22.sp)
                                Text("Game Zone", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Play together", color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
            

            AnimatedVisibility(visible = activeSection == "flash", enter = slideInVertically(initialOffsetY = { it / 18 }, animationSpec = tween(260)) + fadeIn(tween(220)), exit = slideOutVertically(targetOffsetY = { -it / 24 }, animationSpec = tween(170)) + fadeOut(tween(150))) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF102B50),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.55f))
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("NEARER FLASH • CALL-TO-CHAT", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                        Text("A quick call. A message in your chat.", color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Send a short Flash call pattern and NeaRer turns the recognised signal into a normal message bubble on the other phone. No SMS and no mobile data are used for the Flash signal. Calls use your carrier network, so any call charges depend on your plan.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        Surface(color = Color(0xFF0A203D), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("CHOOSE A SIGNAL", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    flashPresets.forEach { preset ->
                                        val selected = preset.name == flashPresetName
                                        Surface(
                                            modifier = Modifier.weight(1f).clickable {
                                                flashPresetName = preset.name
                                                flashCallStep = 0
                                                flashSenderNotice = "Selected ${preset.name.replace('_', ' ')}."
                                            },
                                            color = if (selected) AccentBlue else Color(0xFF17375F),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (selected) Gold else Color(0xFF31527A))
                                        ) {
                                            Text(
                                                preset.name.replace('_', ' '),
                                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 3.dp),
                                                color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "${selectedFlashPreset.name.replace('_', ' ')} • $flashCallCount calls • gaps between call starts: " +
                                        (selectedFlashPreset.gapsMillis.joinToString(" → ") { "${it / 1000.0}s" }.ifBlank { "one call" }),
                                    color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                )
                                Text("The sender chooses a signal; the receiver does not need to select anything. NeaRer recognises the incoming-call timing and turns it into a normal chat message, without SMS or mobile data.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                            }
                        }
                        Surface(
                            color = Color(0xFF0A203D),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF31527A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("TYPE ANY MESSAGE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Text(
                                    "Send your own words as a real chat message over NeaRer’s offline nearby connection. This uses Bluetooth / nearby Wi-Fi—not the phone-call Flash signal.",
                                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                                )
                                OutlinedTextField(
                                    value = flashCustomMessage,
                                    onValueChange = { flashCustomMessage = it.take(1000) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    maxLines = 4,
                                    label = { Text("Write your message") },
                                    placeholder = { Text("Type anything you want…") },
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = White, unfocusedTextColor = White,
                                        focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0xFF42618B),
                                        focusedLabelColor = AccentBlue, unfocusedLabelColor = Muted,
                                        cursorColor = Gold
                                    )
                                )
                                Button(
                                    onClick = {
                                        val message = flashCustomMessage.trim()
                                        if (message.isNotEmpty() && chatReady) {
                                            onSendMessage(message)
                                            flashCustomMessage = ""
                                        }
                                    },
                                    enabled = chatReady && flashCustomMessage.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Gold,
                                        contentColor = Midnight,
                                        disabledContainerColor = Color(0xFF33445C),
                                        disabledContentColor = Muted
                                    )
                                ) {
                                    Text(
                                        if (chatReady) "SEND CUSTOM CHAT MESSAGE" else "CONNECT NEARBY TO SEND",
                                        fontSize = 10.sp, fontWeight = FontWeight.Black
                                    )
                                }
                                Text(
                                    if (chatReady) "Connected • ${connectedDeviceName ?: "nearby contact"} • offline delivery ready"
                                    else "Not connected • your text stays in this box until a nearby connection is ready",
                                    color = if (chatReady) Color(0xFF8DDBB0) else Muted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text("SEND A FLASH SIGNAL", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        OutlinedTextField(
                            value = flashRecipientNumber,
                            onValueChange = { flashRecipientNumber = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }.take(20); flashCallStep = 0 },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Recipient phone number") },
                            placeholder = { Text("e.g. 080… or +234…") },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = White, unfocusedTextColor = White,
                                focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0xFF42618B),
                                focusedLabelColor = AccentBlue, unfocusedLabelColor = Muted,
                                cursorColor = Gold
                            )
                        )
                        Text("Call step ${(flashCallStep + 1).coerceAtMost(flashCallCount)} of $flashCallCount", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (flashStepGap != null) {
                            Text("Aim for about ${String.format(java.util.Locale.ROOT, "%.1f", flashStepGap / 1000.0)} seconds between the START of this call and the START of the next one. End the call quickly, return here, and use the interval as a guide. Carrier delays can affect recognition.", color = Muted, fontSize = 10.sp)
                        } else {
                            Text("Start the sequence. Open the dialer, place the call yourself, end it quickly, and return here for the next call. The intervals are deliberately longer to make manual dialling more realistic.", color = Muted, fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                val dialNumber = flashRecipientNumber.trim().filterIndexed { index, ch ->
                                    ch.isDigit() || (ch == '+' && index == 0)
                                }
                                if (dialNumber.count(Char::isDigit) < 7) {
                                    flashSenderNotice = "Enter a valid recipient number first."
                                } else {
                                    try {
                                        flashContext.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", dialNumber, null)))
                                        flashSenderNotice = "Dialer opened. You decide whether to place and end this call; return here for the next step."
                                    } catch (_: Exception) {
                                        flashSenderNotice = "Could not open the phone dialer on this device."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                        ) { Text("OPEN DIALER • CALL ${(flashCallStep + 1).coerceAtMost(flashCallCount)}", fontWeight = FontWeight.Black) }
                        Button(
                            onClick = {
                                if (flashCallStep + 1 >= flashCallCount) {
                                    flashCallStep = 0
                                    flashSenderNotice = "Signal sequence completed. You can send it again."
                                } else {
                                    flashCallStep += 1
                                    flashSenderNotice = "Next call step ready. Follow the displayed interval; network timing may vary."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B416C), contentColor = White)
                        ) { Text(if (flashCallStep + 1 >= flashCallCount) "FINISH SIGNAL SEQUENCE" else "I ENDED THIS CALL • NEXT STEP", fontWeight = FontWeight.Bold) }
                        Text(flashSenderNotice, color = Gold, fontSize = 10.sp, lineHeight = 15.sp)
                        Text("NeaRer does not place calls automatically and does not request permission to call. You must tap Call in the phone app and end each call yourself. Your carrier may charge for calls. The number is used only to open the dialer and is not saved by this screen.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                        Text(if (flashRoleReady) "Call screening access is enabled on this phone." else "To detect incoming Flash patterns, Android asks you to choose NeaRer as your call-screening app. You can decline; normal calling remains available.", color = if (flashRoleReady) Color(0xFF8EE6B2) else Muted, fontSize = 11.sp)
                        Button(
                            onClick = onRequestFlashScreeningRole,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Midnight)
                        ) { Text(if (flashRoleReady) "CALL SCREENING ENABLED" else "ENABLE FLASH ON THIS PHONE", fontWeight = FontWeight.Black) }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0A203D),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, if (flashContactsPermission) Color(0xFF34765C) else Color(0xFF31527A))
                        ) {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(
                                    if (flashContactsPermission) "SAVED-CONTACT FLASH • ENABLED" else "FLASH FROM SAVED CONTACTS",
                                    color = if (flashContactsPermission) Color(0xFF8CE4B2) else Gold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    if (flashContactsPermission) {
                                        "Android can now pass incoming calls from saved contacts to NeaRer Flash. NeaRer does not read or upload your address book."
                                    } else {
                                        "Android normally hides calls from numbers saved in your contacts from call-screening apps. You can grant Contacts permission to allow Flash to recognise paired contacts. NeaRer does not read or upload your address book."
                                    },
                                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                                )
                                if (!flashContactsPermission) {
                                    Button(
                                        onClick = { flashContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B416C), contentColor = White)
                                    ) {
                                        Text("ALLOW FLASH FROM SAVED CONTACTS", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Text("TRUSTED SENDERS • $flashTrustedCount PAIRED", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        OutlinedTextField(
                            value = flashTrustedNumber,
                            onValueChange = { flashTrustedNumber = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }.take(20) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Pair sender's phone number") },
                            placeholder = { Text("Number as it appears on this phone") },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = White, unfocusedTextColor = White,
                                focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0xFF42618B),
                                focusedLabelColor = AccentBlue, unfocusedLabelColor = Muted,
                                cursorColor = Gold
                            )
                        )
                        OutlinedTextField(
                            value = flashTrustedName,
                            onValueChange = { flashTrustedName = it.take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Name to show in your chat (optional)") },
                            placeholder = { Text("e.g. Tunde") },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = White, unfocusedTextColor = White,
                                focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0xFF42618B),
                                focusedLabelColor = AccentBlue, unfocusedLabelColor = Muted,
                                cursorColor = Gold
                            )
                        )
                        Button(
                            onClick = {
                                if (FlashTrustedSenders.addTrusted(flashContext, flashTrustedNumber, flashTrustedName)) {
                                    flashTrustedCount = FlashTrustedSenders.trustedCount(flashContext)
                                    flashTrustedNumber = ""
                                    flashTrustedName = ""
                                    flashSenderNotice = "Sender paired. Their chosen name will label their Flash chat; the raw phone number is not stored."
                                } else {
                                    flashSenderNotice = "Enter a valid phone number with at least 7 digits."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B416C), contentColor = White)
                        ) { Text("ADD TRUSTED SENDER", fontWeight = FontWeight.Bold) }
                        Text("Pair each sender before they send you a Flash. NeaRer stores a salted number fingerprint and your optional chat name, not the raw phone number. Nigerian numbers saved as 080… will also match incoming +234… format. For other countries, use the number format shown on incoming calls.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)

                        if (flashLastSignal != null) {
                            Text("LAST SIGNAL RECEIVED", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(flashLastSignal!!.replace('_', ' '), color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        } else {
                            Text("No Flash signal received on this phone yet.", color = Muted, fontSize = 11.sp)
                        }
                        Text("Android may hide calls from saved contacts from screening apps unless you grant the optional Contacts permission above. NeaRer does not read or upload your address book. Unknown, hidden or unavailable caller IDs may not be delivered to the screening service. Flash remains experimental until tested on real networks; call charges and timing depend on your carrier.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }

            AnimatedVisibility(visible = activeSection == "games", enter = slideInVertically(initialOffsetY = { it / 18 }, animationSpec = tween(260)) + fadeIn(tween(220)), exit = slideOutVertically(targetOffsetY = { -it / 24 }, animationSpec = tween(170)) + fadeOut(tween(150))) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color(0xFF15315A), border = BorderStroke(1.dp, Color(0xFF42618B))) {
                Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("NEARER GAME ZONE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                            Text("Play. Connect. Compete.", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.sp)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF29466B)) {
                            Text("NEW", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                    Text("Start with Ludo: traditional rules, colourful tokens and computer opponents. Nearby multiplayer is the next game milestone; online play will follow.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Button(onClick = { showLudo = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Midnight)) {
                        Text("PLAY LUDO", fontWeight = FontWeight.Black)
                    }
                }
            }

            }
            

            AnimatedVisibility(visible = activeSection == "nearby", enter = slideInVertically(initialOffsetY = { it / 18 }, animationSpec = tween(260)) + fadeIn(tween(220)), exit = slideOutVertically(targetOffsetY = { -it / 24 }, animationSpec = tween(170)) + fadeOut(tween(150))) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF102B50),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFF477FC0).copy(alpha = 0.65f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF163D70), Color(0xFF102B50), Color(0xFF0B203D))))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("YOUR NEARER IDENTITY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.25.sp)
                            Text("The name people recognise", color = White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Surface(shape = CircleShape, color = Color(0xFF244D7A), border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)), modifier = Modifier.size(38.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(nameDraft.trim().firstOrNull()?.uppercase() ?: "N", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.take(24); nameSaveFeedback = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Choose your nearby name", color = Muted) },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = { nameSaveFeedback = if (onSaveDisplayName(nameDraft)) "Your name is saved." else "Could not save your name." },
                        enabled = nameDraft.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                    ) { Text("SAVE MY NAME", fontWeight = FontWeight.Black, letterSpacing = 0.4.sp) }
                    Text(nameSaveFeedback ?: "Your chosen name is shown to people discovering nearby phones.", color = Color(0xFFB8CBE3), fontSize = 10.sp, lineHeight = 15.sp)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                    sectionPositions["nearby"] = it.positionInParent().y.toInt()
                },
                shape = RoundedCornerShape(27.dp),
                color = Color(0xFF102A4A),
                border = BorderStroke(1.dp, Color(0xFF345D8D)),
                shadowElevation = 14.dp
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("NEARBY RADAR", color = Gold, fontSize = 10.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 1.7.sp)
                            Text(when {
                                isConnecting -> "Connecting to a nearby phone"
                                receiveMode && incomingTransferName != null -> "Receiving a nearby file"
                                receiveMode && chatReady -> "Ready for an incoming file"
                                receiveMode && scanning -> "Looking for a nearby sender"
                                receiveMode -> "Ready to receive nearby files"
                                scanning -> "Searching around you"
                                else -> "Your people are closer"
                            },
                                color = White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                        Surface(color = when {
                            isConnecting -> Color(0xFF765A20)
                            scanning -> Color(0xFF1D684F)
                            else -> Color(0xFF1B3B61)
                        }, shape = RoundedCornerShape(50)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(shape = CircleShape, color = when {
                                    isConnecting -> Gold
                                    scanning -> Color(0xFF7CE0B7)
                                    else -> Color(0xFF8FB9F4)
                                }, modifier = Modifier.size(6.dp).alpha(if (isConnecting || scanning) pulse else 1f)) {}
                                Text(when {
                                    isConnecting -> "CONNECTING"
                                    scanning -> "LIVE SCAN"
                                    else -> "READY"
                                },
                                    color = White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().height(156.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color(0xFF315A83).copy(alpha = if (scanning) pulse else 0.55f)),
                            modifier = Modifier.size(148.dp).alpha(if (scanning) pulse else 0.75f)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color(0xFF3C6D9E)),
                            modifier = Modifier.size(112.dp).rotate(if (scanning) orbitRotation else 0f)
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.TopCenter) {
                                Surface(shape = CircleShape, color = Gold, modifier = Modifier.size(8.dp)) {}
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color(0xFF477CB8)),
                            modifier = Modifier.size(76.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = BrandBlue,
                            border = BorderStroke(1.dp, Color(0xFF8DBBFF).copy(alpha = 0.65f)),
                            modifier = Modifier.size(58.dp),
                            shadowElevation = 16.dp
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(
                                    displayName.trim().firstOrNull()?.uppercase() ?: "N",
                                    color = White,
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        if (hybridPeers.isNotEmpty()) {
                            Text("${hybridPeers.size} FOUND", color = Gold, fontSize = 9.sp,
                                fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    }
                    Text(
                        when {
                            receiveMode && incomingTransferName != null -> "A file is arriving from a nearby phone. Keep both phones close until the transfer finishes."
                            receiveMode && chatReady -> "You're connected to ${connectedDeviceName ?: "a nearby phone"}. Ask the sender to select a file and send it to you."
                            receiveMode && scanning -> "NeaRer is listening for nearby senders. Keep this screen open; when someone connects, you can receive their file here."
                            receiveMode -> "Ready to find someone sending you a file. Start discovery and keep NeaRer open. No mobile data is used."
                            scanning -> "NeaRer is looking for nearby phones. Keep the app open on both devices."
                            chatReady -> "Connected to ${connectedDeviceName ?: "a nearby phone"}. You can return Home to choose files to send."
                            else -> "Discover nearby NeaRer users without mobile data. Bluetooth and nearby Wi-Fi work together; hotspot is not required."
                        },
                        color = Color(0xFFD0DDF0), fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onFindNearby,
                            modifier = Modifier.weight(1f).height(49.dp),
                            enabled = !scanning && !chatReady && !isConnecting,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight, disabledContainerColor = Color(0xFF315174), disabledContentColor = Color(0xFFD8E5F5))
                        ) {
                            Text(
                                when {
                                    isConnecting -> "CONNECTING…"
                                    scanning && receiveMode -> "LISTENING FOR SENDER…"
                                    scanning -> "SEARCHING…"
                                    receiveMode && chatReady -> "READY TO RECEIVE"
                                    chatReady -> "CONNECTED"
                                    receiveMode -> "FIND A SENDER"
                                    else -> "START DISCOVERING"
                                },
                                fontSize = 10.sp, fontWeight = FontWeight.Black
                            )
                        }
                        if (scanning) {
                            Button(
                                onClick = onStopSearch,
                                modifier = Modifier.height(49.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF344A66), contentColor = White)
                            ) { Text("STOP", fontWeight = FontWeight.Bold) }
                        }
                    }
                    Surface(color = Color(0xFF0B1D34), shape = RoundedCornerShape(12.dp)) {
                        Text(status, color = Muted, fontSize = 10.sp, lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth().padding(11.dp))
                    }
                    if (incomingRequesterName != null) {
                        Surface(
                            color = Color(0xFF1B3858),
                            shape = RoundedCornerShape(15.dp),
                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.65f))
                        ) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("CONNECTION REQUEST", color = Gold, fontSize = 9.sp,
                                    fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Text("$incomingRequesterName wants to connect.", color = White,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Compare the six-digit code on both phones before accepting.",
                                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onAccept, modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)) {
                                        Text("ACCEPT", fontWeight = FontWeight.Black)
                                    }
                                    Button(onClick = onReject, modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)) {
                                        Text("DECLINE", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val connectionStage = when {
                chatReady -> 2
                pairingCode != null || incomingRequesterName != null || connectingAddress != null -> 1
                else -> 0
            }
            Surface(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp), color = if (chatReady) Color(0xFF173C3A) else Panel) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CONNECTION STATUS", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        if (chatReady) Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFF174B43), modifier = Modifier.alpha(pulse)) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("✓", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Text("CONNECTED", color = AccentBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("DISCOVER", "VERIFY", "READY").forEachIndexed { index, label ->
                            val reached = index <= connectionStage
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (reached) (if (chatReady) Color(0xFF54D6A5) else Gold) else Color(0xFF34506F)
                                ) {}
                                Text(label, color = if (reached) White else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(connectedDeviceName?.let { "You are connected to $it" } ?: connectionMessage, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(connectionMessage, color = Muted, fontSize = 11.sp)
                    Text(approvalMessage, color = if (chatReady) AccentBlue else Muted, fontSize = 12.sp)
                    pairingCode?.let { code ->
                        Text("PAIRING CODE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(code, color = AccentBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
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
            


            AnimatedVisibility(visible = activeSection == "chats", enter = slideInVertically(initialOffsetY = { it / 18 }, animationSpec = tween(260)) + fadeIn(tween(220)), exit = slideOutVertically(targetOffsetY = { -it / 24 }, animationSpec = tween(170)) + fadeOut(tween(150))) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("MESSAGES", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                    Text("Your conversations", color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Private chats and shared files, kept close.", color = Muted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.weight(1f).clickable {
                            navigateToSection("nearby")
                            onFindNearby()
                            navigationScope.launch { pageScrollState.animateScrollTo(0) }
                        },
                        color = Color(0xFF123D7A), shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, Color(0xFF347CEB))
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("+", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Column(Modifier.padding(start = 9.dp)) {
                                Text("New chat", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Find someone nearby", color = Color(0xFFB9D2F5), fontSize = 9.sp)
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).clickable { showChatAttachmentMenu = true },
                        color = Color(0xFF172F52), shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, Color(0xFF28589A))
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("↥", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Column(Modifier.padding(start = 9.dp)) {
                                Text("Share a file", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Photos, videos & more", color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
                }

            if (conversationRows.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0D2545),
                    shape = RoundedCornerShape(19.dp),
                    border = BorderStroke(1.dp, Color(0xFF31547E))
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("YOUR CONVERSATIONS", color = White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            Text("${conversationRows.size} ${if (conversationRows.size == 1) "person" else "people"}", color = Muted, fontSize = 9.sp)
                        }
                        conversationRows.forEach { (peerId, peerName, latest) ->
                            val selected = selectedConversationId == peerId
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedConversationId = peerId
                                    onSelectConversation(peerId, peerName)
                                },
                                color = if (selected) Color(0xFF174A83) else Color(0xFF10294A),
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(1.dp, if (selected) Gold.copy(alpha = 0.75f) else Color(0xFF28496F))
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = if (selected) Color(0xFF285FA0) else Color(0xFF24466D), modifier = Modifier.size(36.dp)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text(peerName.firstOrNull()?.uppercase() ?: "N", color = if (selected) Gold else White, fontWeight = FontWeight.Black)
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(peerName, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(latest?.body ?: "No messages yet", color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(latest?.let { formatMessageTime(it.timestamp) } ?: "", color = Muted, fontSize = 8.sp)
                                        Text(latest?.delivery ?: "CHAT", color = if (latest?.outgoing == true && latest.delivery == "Delivered") Color(0xFF8CE4C8) else Gold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                sectionPositions["chats"] = it.positionInParent().y.toInt()
            }, shape = RoundedCornerShape(23.dp), color = Panel,
                border = BorderStroke(1.dp, if (chatReady) Color(0xFF3A9B7B) else Color(0xFF315B8D)),
                shadowElevation = 9.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(
                            listOf(
                                if (chatReady) Color(0xFF16453F) else Color(0xFF173A67),
                                Color(0xFF10294A),
                                Color(0xFF0C203B)
                            )
                        ))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (chatReady) Color(0xFF145A49) else Color(0xFF244B70),
                            modifier = Modifier.size(44.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(if (chatReady) (connectedDeviceName ?: "N").take(1).uppercase() else "✉",
                                    color = White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text(connectedDeviceName ?: "PRIVATE CHAT", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(if (chatReady) "DIRECT • OFFLINE • PRIVATE" else "OFFLINE • CHAT IS READY",
                                color = if (chatReady) Color(0xFF8CE4C8) else Muted,
                                fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                        }
                        Surface(
                            color = if (chatReady) Color(0xFF174B43) else Color(0xFF25364D),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(if (chatReady) "ACTIVE" else "OFFLINE",
                                color = if (chatReady) Color(0xFF8CE4C8) else Muted,
                                fontSize = 8.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF173A67),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF315F96))
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                                Text("DIRECT LINK", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                Text(
                                    if (!chatReady) "Waiting" else if (usingNearbyConnections) "Bluetooth + Wi-Fi" else "Wi-Fi Direct",
                                    color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = if (meshRelayEnabled) Color(0xFF214B45) else Color(0xFF25364D),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (meshRelayEnabled) Color(0xFF398A70) else Color(0xFF3A526E))
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                                Text("MESH RELAY", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                Text(if (meshRelayEnabled) "ON • Communities" else "OFF", color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF493C27),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF80652E))
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                                Text("NEARER FLASH", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                Text("Experimental", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                    Text(
                        "Chat stays available offline. Messages are saved here first, then sent through an available direct route; private mesh and Flash delivery remain experimental until verified.",
                        color = Muted, fontSize = 9.sp, lineHeight = 13.sp
                    )

                        if (visibleMessages.isEmpty()) {
                            Text("Your private conversation starts here. Write now; messages stay safely on this phone until a route is available.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        } else {
                            LazyColumn(
                                state = messageListState,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                itemsIndexed(visibleMessages, key = { _, message -> message.id }) { _, message ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start
                                    ) {
                                        Surface(
                                            color = if (message.outgoing) Color(0xFF174A9B) else Color(0xFF19375D),
                                            shape = if (message.outgoing) RoundedCornerShape(17.dp, 17.dp, 5.dp, 17.dp)
                                                else RoundedCornerShape(17.dp, 17.dp, 17.dp, 5.dp),
                                            modifier = Modifier.fillMaxWidth(0.86f).animateContentSize()
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                                                Text(message.body, color = White, fontSize = 14.sp)
                                                Text(
                                                    "${if (message.outgoing) message.delivery else "Received"}  •  ${formatMessageTime(message.timestamp)}",
                                                    color = if (message.outgoing) Color(0xFFBBD5FF) else Gold,
                                                    fontSize = 9.sp, fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(chatNotice, color = Muted, fontSize = 10.sp)
                        if (visibleMessages.any { it.outgoing && it.delivery == "Queued" }) {
                            Button(onClick = onSendQueuedMessages, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)) {
                                Text("SEND SAVED MESSAGES TO ${connectedDeviceName ?: "CONNECTED PHONE"}", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        if (pendingChatFilesCount > 0) {
                            Button(onClick = onSendQueuedFiles, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A4B80), contentColor = White)) {
                                Text("SEND $pendingChatFilesCount PREPARED ATTACHMENT(S)", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        if (receivedFiles.isNotEmpty() || sentFiles.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("RECENT FILES", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showReceivedHistory = true }) {
                                    Text("SHARED FILES", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        receivedFiles.take(3).forEach { fileItem ->
                            Button(
                                onClick = { onOpenFile(fileItem) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF19375D), contentColor = White)
                            ) { Text("Open ${fileItem.displayName}", maxLines = 1) }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp).clickable { showChatAttachmentMenu = true },
                                shape = CircleShape,
                                color = Color(0xFF173F78),
                                border = BorderStroke(1.dp, Color(0xFF347CEB))
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text("+", color = Gold, fontSize = 27.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it.take(2000); localChatPrefs.edit().putString("chat_draft", draft).apply() },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Write a message…", color = Muted, fontSize = 12.sp) },
                                shape = RoundedCornerShape(22.dp),
                                minLines = 1,
                                maxLines = 4
                            )
                            Surface(
                                modifier = Modifier.size(48.dp).clickable(enabled = draft.isNotBlank()) {
                                    onSendMessage(draft.trim())
                                    draft = ""
                                    localChatPrefs.edit().remove("chat_draft").apply()
                                },
                                shape = CircleShape,
                                color = if (draft.isNotBlank()) BrandBlue else Color(0xFF24436D)
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text("➤", color = White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        incomingTransferName?.let { incomingName ->
                            Text("Receiving $incomingName", color = Muted, fontSize = 11.sp)
                            LinearProgressIndicator(
                                progress = { incomingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = AccentBlue,
                                trackColor = Color(0xFF19375D)
                            )
                        }
                        if (outgoingTransferId != null) {
                            LinearProgressIndicator(
                                progress = { outgoingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = AccentBlue,
                                trackColor = Color(0xFF19375D)
                            )
                            Button(
                                onClick = onCancelTransfer,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF713D4A), contentColor = White)
                            ) { Text("Cancel file transfer") }
                        }
                        Text("＋ Share photos, videos and files from the attachment button.", color = Muted, fontSize = 9.sp)
                        Text(if (usingNearbyConnections) "DIRECT NEARBY LINK  •  NO MOBILE DATA" else "WI-FI DIRECT LINK  •  NO MOBILE DATA", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.35.sp)

                }
            }

            }
            

            AnimatedVisibility(visible = activeSection == "community", enter = slideInVertically(initialOffsetY = { it / 18 }, animationSpec = tween(260)) + fadeIn(tween(220)), exit = slideOutVertically(targetOffsetY = { -it / 24 }, animationSpec = tween(170)) + fadeOut(tween(150))) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().onGloballyPositioned {
                    sectionPositions["community"] = it.positionInParent().y.toInt()
                }
            ) {
                CommunityHub(groups, groupMessages, selectedGroupCode, communityNotice, communityLocalMemberId, incomingGameInvite,
                    activeGame, gameChatMessages, onCreateGroup, onJoinGroup, onSelectGroup, onLeaveGroup,
                    onApproveMember, onPromoteMember, onTransferOwnership, onRemoveMember, onSendGroupMessage, onBroadcast, onGameInvite, onAcceptGameInvite,
                    onRejectGameInvite, onGameMove, onGameChat, onEndGame)
            }

            }
            

            if (activeSection == "community") {
            if (incomingRequesterName != null) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("NEARER CONNECTION REQUEST", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("$incomingRequesterName wants to connect with you.", color = White, fontWeight = FontWeight.SemiBold)
                        Text("Compare the six-digit code on both phones. Accept only if it matches and you recognise this person.", color = Muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onAccept, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Midnight)) { Text("Accept") }
                            Button(onClick = onReject, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF713D4A), contentColor = White)) { Text("Reject") }
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("COMMUNITY MESH RELAY", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Allow your phone to pass group text messages to other connected NeaRer phones.", color = White, fontSize = 12.sp, lineHeight = 17.sp)
                        Text("Optional • up to 4 hops • off by default. Relaying phones can read message text, so use only with trusted groups.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    androidx.compose.material3.Switch(checked = meshRelayEnabled, onCheckedChange = onMeshRelayChange)
                }
            }

            }

            if (activeSection == "nearby") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("NEARBY DEVICES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text(
                        if (scanning) "Discovery is active" else "Ready when you are",
                        color = if (scanning) Color(0xFF8CE4C8) else Muted,
                        fontSize = 9.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("${devices.size} found", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (!scanning && connectedDeviceName == null) {
                    TextButton(
                        onClick = onFindNearby,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text("SEARCH AGAIN", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp), color = Color(0xFF14243A)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PEOPLE AROUND YOU", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (hybridPeers.isEmpty()) "Your nearby circle" else "${hybridPeers.size} active nearby", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (scanning && hybridPeers.isEmpty()) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(142.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, Color(0xFF31516F)),
                                modifier = Modifier.size(136.dp).alpha(pulse)
                            ) {}
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, Color(0xFF315B7C)),
                                modifier = Modifier.size(104.dp).rotate(orbitRotation)
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.TopCenter) {
                                    Surface(shape = CircleShape, color = AccentBlue, modifier = Modifier.size(10.dp)) {}
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, Color(0xFF31516F)),
                                modifier = Modifier.size(72.dp)
                            ) {}
                            Surface(shape = CircleShape, color = BrandBlue, modifier = Modifier.size(54.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Image(painter = painterResource(R.drawable.ic_nearer_foreground), contentDescription = "NeaRer network N", modifier = Modifier.size(44.dp)) }
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
                                    Text("Active now", color = AccentBlue, fontSize = 9.sp)
                                }
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(78.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = CircleShape, color = Color(0xFF1C3554), modifier = Modifier.size(68.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text("•••", color = AccentBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
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
                    Text("CONNECT WITH A NEARBY PERSON", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    hybridPeers.forEach { (id, name) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(19.dp),
                            color = Color(0xFF17365D),
                            border = BorderStroke(1.dp, Color(0xFF3C72B2).copy(alpha = 0.72f)),
                            shadowElevation = 5.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Brush.linearGradient(listOf(Color(0xFF1B477A), Color(0xFF102747))))
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandBlue,
                                    border = BorderStroke(1.dp, Color(0xFF9BC4FF).copy(alpha = 0.7f)),
                                    modifier = Modifier.size(44.dp),
                                    shadowElevation = 5.dp
                                ) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Text(name.trim().firstOrNull()?.uppercase() ?: "N", color = White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(name, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Surface(shape = CircleShape, color = Color(0xFF6CE0B1), modifier = Modifier.size(6.dp)) {}
                                        Text("ACTIVE NOW", color = Color(0xFF9DE9C9), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                                    }
                                }
                                Button(
                                    onClick = { onHybridConnect(id) },
                                    enabled = !chatReady && !isConnecting,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Gold,
                                        contentColor = Midnight,
                                        disabledContainerColor = Color(0xFF315174),
                                        disabledContentColor = Color(0xFFD8E5F5)
                                    )
                                ) {
                                    Text(
                                        when {
                                            chatReady -> "CONNECTED"
                                            isConnecting -> "CONNECTING…"
                                            else -> "CONNECT"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (devices.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(19.dp),
                    color = Color(0xFF102B50),
                    border = BorderStroke(1.dp, Color(0xFF31547E))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1A4778),
                                modifier = Modifier.size(38.dp)
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(if (scanning) "⌕" else "◎", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (scanning) "Your radar is listening" else "No nearby phone has appeared yet",
                                    color = White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (scanning) "Keep this screen open while the other phone searches too."
                                    else "Start discovery on both phones and bring them closer.",
                                    color = Muted,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Surface(shape = CircleShape, color = if (scanning) Color(0xFF155B4B) else Color(0xFF253E5D)) {
                                Text(
                                    if (scanning) "LIVE" else "READY",
                                    color = if (scanning) Color(0xFF8CE4C8) else Gold,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFF29496F))
                        listOf(
                            "Bluetooth and Wi-Fi should be switched on",
                            "Both phones need NeaRer for chat and app sharing",
                            "Keep NeaRer open and accept the connection on the other phone"
                        ).forEachIndexed { index, tip ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("0${index + 1}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                Text(tip, color = Color(0xFFD1E0F4), fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }
                        if (!scanning && connectedDeviceName == null) {
                            Button(
                                onClick = onFindNearby,
                                modifier = Modifier.fillMaxWidth().height(43.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                            ) {
                                Text("START NEARBY SEARCH", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
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
                                        Image(painter = painterResource(R.drawable.ic_nearer_foreground), contentDescription = "NeaRer network N", modifier = Modifier.size(28.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(device.deviceName.takeIf { it.isNotBlank() } ?: "Nearby Wi-Fi Direct phone",
                                        color = White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("Wi-Fi Direct • " + deviceStatus(device.status), color = Muted, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { onConnect(device) },
                                    enabled = connectingAddress == null && connectedDeviceName == null && device.status != WifiP2pDevice.UNAVAILABLE,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Midnight)
                                ) {
                                    Text(if (connectingAddress == device.deviceAddress) "Requesting…" else "Connect", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))
            }

            Text("NEARER  •  DIRECT DEVICE DISCOVERY", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Surface(
                color = Color(0xFF092653),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 18.dp,
                border = BorderStroke(1.dp, Color(0xFF244A7D).copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFF103365), Color(0xFF071A34))))
                        .padding(horizontal = 7.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(
                        Triple("⇅", "Transfer", "home"),
                        Triple("◎", "Nearby", "nearby"),
                        Triple("✉", "Chats", "chats"),
                        Triple("♧", "Groups", "community"),
                        Triple("♜", "Games", "games"),
                        Triple("⌁", "Flash", "flash")
                    ).forEach { item ->
                        val selected = activeSection == item.third
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).animateContentSize().clickable {
                                navigateToSection(item.third)
                                navigationScope.launch { pageScrollState.animateScrollTo(0) }
                            },
                            color = if (selected) Color(0xFF1554A8) else Color.Transparent,
                            shape = RoundedCornerShape(15.dp),
                            border = if (selected) BorderStroke(1.dp, Gold.copy(alpha = 0.8f)) else null
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    item.first,
                                    color = if (selected) Gold else Muted,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    item.second,
                                    color = if (selected) White else Muted,
                                    fontSize = 9.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                androidx.compose.foundation.layout.Box(
                                    Modifier.padding(top = 1.dp).width(if (selected) 17.dp else 4.dp)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selected) Gold else Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@androidx.compose.runtime.Composable
private fun CommunityHub(
    groups: List<CommunityGroup>, messages: List<CommunityChatMessage>, selectedCode: String?, notice: String, localMemberId: String,
    invite: GameInvite?, game: TicTacToeMatch?, gameChat: List<String>,
    onCreate: (String) -> Unit, onJoin: (String) -> Unit, onSelect: (String) -> Unit,
    onLeaveGroup: (String) -> Unit, onApproveMember: (String, String) -> Unit,
    onPromoteMember: (String, String) -> Unit, onTransferOwnership: (String, String) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onGroupMessage: (String) -> Unit, onBroadcast: (String) -> Unit, onInviteGame: () -> Unit,
    onAccept: () -> Unit, onReject: () -> Unit, onMove: (Int) -> Unit, onGameChat: (String) -> Unit, onEndGame: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var groupText by remember { mutableStateOf("") }
    var broadcastText by remember { mutableStateOf("") }
    var gameText by remember { mutableStateOf("") }
    val selected = groups.firstOrNull { it.code == selectedCode }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NEARER COMMUNITY", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                Button(onClick = { onSelect(group.code) }, Modifier.fillMaxWidth()) {
                    Text(group.name + " • " + group.code + " • " + group.memberIds.size + " members")
                }
            }
            if (selected != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GROUP CHAT • " + selected.name, color = AccentBlue, fontWeight = FontWeight.Bold)
                        Text("Share code " + selected.code + " with connected members.", color = Muted, fontSize = 10.sp)
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NeaRer community code", selected.code))
                    }) { Text("Copy code") }
                }
                val isOwner = selected.ownerId == localMemberId && localMemberId.isNotBlank()
                val canModerate = isOwner || localMemberId in selected.moderators
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.memberIds.size.toString() + " known members", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onLeaveGroup(selected.code) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)) { Text("Leave") }
                }
                Text("MEMBERS", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                selected.memberIds.forEach { (memberId, memberName) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(memberName, color = White, fontSize = 12.sp)
                            Text(when {
                                memberId == selected.ownerId -> "Owner"
                                memberId in selected.moderators -> "Moderator"
                                else -> "Member"
                            }, color = Muted, fontSize = 10.sp)
                        }
                        if (isOwner && memberId != selected.ownerId && memberId !in selected.moderators) {
                            TextButton(onClick = { onPromoteMember(selected.code, memberId) }) { Text("Make moderator") }
                        }
                        if (isOwner && memberId != selected.ownerId) {
                            TextButton(onClick = { onTransferOwnership(selected.code, memberId) }) { Text("Make owner") }
                        }
                        if (canModerate && memberId != selected.ownerId && (isOwner || memberId !in selected.moderators)) {
                            TextButton(onClick = { onRemoveMember(selected.code, memberId) }) { Text("Remove") }
                        }
                    }
                }
                if (canModerate && selected.pendingMembers.isNotEmpty()) {
                    Text("JOIN REQUESTS", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    selected.pendingMembers.forEach { (memberId, memberName) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(memberName, color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onApproveMember(selected.code, memberId) }) { Text("Approve") }
                            TextButton(onClick = { onRemoveMember(selected.code, memberId) }) { Text("Decline") }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().height(130.dp).verticalScroll(rememberScrollState())) {
                    messages.filter { it.groupCode == selected.code }.takeLast(40).forEach { Text(it.sender + ": " + it.body, color = White, fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(groupText, { groupText = it.take(1500) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Group message") })
                    Button(onClick = { onGroupMessage(groupText); groupText = "" }, enabled = groupText.isNotBlank()) { Text("Send") }
                }
            }
            Text("BROADCAST TO CONNECTED PEOPLE", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(broadcastText, { broadcastText = it.take(500) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Announcement") })
                Button(onClick = { onBroadcast(broadcastText); broadcastText = "" }, enabled = broadcastText.isNotBlank()) { Text("Send") }
            }
            Text("Only currently connected NeaRer devices receive these broadcasts.", color = Muted, fontSize = 10.sp)
            Text(notice, color = AccentBlue, fontSize = 10.sp)
        }
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color(0xFF172A42)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("PLAY TOGETHER", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Tic-tac-toe • live nearby match", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Invite a connected player. Board moves and game chat use the offline link.", color = Muted, fontSize = 11.sp)
            if (invite != null) {
                Text(invite.sender + " invited you to play.", color = White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept, Modifier.weight(1f)) { Text("Accept") }
                    Button(onClick = onReject, Modifier.weight(1f)) { Text("Decline") }
                }
            }
            if (game == null) Button(onClick = onInviteGame, Modifier.fillMaxWidth()) { Text("Challenge nearby players") }
            else {
                Text("You are " + game.localSymbol + " • " + game.opponent, color = White, fontWeight = FontWeight.SemiBold)
                Text(game.status, color = AccentBlue)
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { col ->
                            val index = row * 3 + col
                            val cell = game.board[index].takeIf { it != '.' }?.toString() ?: ""
                            Button(onClick = { onMove(index) }, Modifier.weight(1f).height(54.dp),
                                enabled = cell.isEmpty() && game.turn == game.localSymbol && game.status != "Waiting for a player" && !game.status.endsWith("wins") && game.status != "Draw") {
                                Text(cell.ifBlank { " " }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text("IN-GAME CHAT", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().height(80.dp).verticalScroll(rememberScrollState())) {
                    gameChat.takeLast(20).forEach { Text(it, color = White, fontSize = 11.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(gameText, { gameText = it.take(300) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Chat while playing") })
                    Button(onClick = { onGameChat(gameText); gameText = "" }, enabled = gameText.isNotBlank()) { Text("Send") }
                }
                Button(onClick = onEndGame, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B))) { Text("End match") }
            }
        }
    }
}
private fun ticTacToeWinner(board: String): String? {
    val lines = listOf("012", "345", "678", "036", "147", "258", "048", "246")
    for (line in lines) {
        val a = board[line[0].digitToInt()]
        val b = board[line[1].digitToInt()]
        val c = board[line[2].digitToInt()]
        if (a != '.' && a == b && b == c) return a.toString()
    }
    return null
}
private data class CommunityGroup(
    val code: String,
    val name: String,
    val members: List<String>,
    val ownerId: String = "",
    val memberIds: Map<String, String> = emptyMap(),
    val moderators: Set<String> = emptySet(),
    val pendingMembers: Map<String, String> = emptyMap()
)
private data class CommunityChatMessage(val id: String, val groupCode: String, val sender: String, val body: String, val time: Long)
private data class GameInvite(val matchId: String, val sender: String)
private data class TicTacToeMatch(val matchId: String, val board: String, val turn: String, val localSymbol: String, val status: String, val opponent: String)

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
    val delivery: String,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: String = "legacy",
    val conversationName: String = "Previous chat"
)

private fun formatMessageTime(timestamp: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))

private fun transferProgressDetail(transferred: Long, total: Long, speed: Long): String {
    if (total <= 0L) return if (speed > 0L) "${formatTransferSize(speed)}/s" else "Transferring data…"
    if (speed <= 0L) return "${formatTransferSize(transferred)} / ${formatTransferSize(total)}"
    val seconds = ((total - transferred).coerceAtLeast(0L) / speed.toDouble()).toLong()
    val eta = when {
        seconds < 60L -> "~${seconds}s left"
        seconds < 3600L -> "~${seconds / 60}m ${seconds % 60}s left"
        else -> "~${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }
    return "${formatTransferSize(speed)}/s • $eta"
}

private fun formatTransferSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes < 1024L -> "$safeBytes B"
        safeBytes < 1024L * 1024L -> String.format(java.util.Locale.ROOT, "%.1f KB", safeBytes / 1024.0)
        safeBytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.ROOT, "%.1f MB", safeBytes / (1024.0 * 1024.0))
        else -> String.format(java.util.Locale.ROOT, "%.2f GB", safeBytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private data class ReceivedFile(val displayName: String, val file: File)
private data class SentFile(val name: String, val uri: String, val sizeBytes: Long, val timestamp: Long)
private data class ShareableApp(val label: String, val packageName: String, val apkPaths: List<String>, val icon: androidx.compose.ui.graphics.ImageBitmap)

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
