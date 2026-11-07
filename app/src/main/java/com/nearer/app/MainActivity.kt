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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
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
import com.nearer.app.games.ludo.LudoRoomSession
import com.nearer.app.games.ludo.LudoRoomRole
import com.nearer.app.games.ludo.LudoRoomAction
import com.nearer.app.games.ludo.LudoRoomActionType
import com.nearer.app.games.ludo.LudoRoomActionRequest
import com.nearer.app.games.ludo.LudoRoomActionCodec
import com.nearer.app.games.ludo.LudoRoomEnvelopeCodec
import com.nearer.app.games.ludo.LudoDoubleDiceState

// NeaRer Royal Neutral palette: blue is the signature accent, not the page background.
private val Midnight = Color(0xFF111827)       // graphite / ink canvas
private val Panel = Color(0xFFF8F7F3)           // warm ivory content surfaces
private val AccentBlue = Color(0xFF3478C8)      // restrained brand blue
private val Muted = Color(0xFFB8C3D1)           // readable secondary text
private val White = Color(0xFFFFFFFF)
private val BrandBlue = Color(0xFF285F9E)
private val Gold = Color(0xFFD9AE4B)

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
    private var anonymousDiscovery by mutableStateOf(false)
    private val discoveryHandler = Handler(Looper.getMainLooper())
    private var discoveryTimeout: Runnable? = null
    private var discoveryGeneration = 0
    private var nearbySearchTimeout: Runnable? = null
    private var nearbySearchGeneration = 0
    private var connectingAddress by mutableStateOf<String?>(null)
    private var connectedDeviceName by mutableStateOf<String?>(null)
    private var connectionMessage by mutableStateOf("Not connected")
    private var incomingRequesterName by mutableStateOf<String?>(null)
    private var pendingHybridStableId: String = ""
    private val blockedPeerIds = ConcurrentHashMap.newKeySet<String>()
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
    // A queued outgoing message must finish its disk write before any transport can send it.
    private val pendingOutgoingMessageSaves = ConcurrentHashMap.newKeySet<String>()
    private val messagePersistence = Executors.newSingleThreadExecutor()
    private val io = Executors.newCachedThreadPool()
    private val approvalDecision = LinkedBlockingQueue<Boolean>()
    private val chatMessages = mutableStateListOf<ChatMessage>()
    private val pendingChatFileUris = mutableStateListOf<String>()
    // The head item stays in the durable queue while in flight. It is removed
    // only after the receiver confirms that the verified file was saved.
    private var activeQueuedFileUri: String? = null
    private val communityGroups = mutableStateListOf<CommunityGroup>()
    private val communityMessages = mutableStateListOf<CommunityChatMessage>()
    private var selectedCommunityCode by mutableStateOf<String?>(null)
    private var communityNotice by mutableStateOf("Create or join a nearby group.")
    private var incomingGameInvite by mutableStateOf<GameInvite?>(null)
    private var activeGame by mutableStateOf<TicTacToeMatch?>(null)
    private val gameChatMessages = mutableStateListOf<String>()
    private data class LudoPendingInvite(val roomId: String, val senderName: String, val senderStableId: String)
    private var ludoRoomId by mutableStateOf<String?>(null)
    private var ludoRoomPeerName by mutableStateOf<String?>(null)
    private var ludoRoomPeerStableId: String = ""
    private var ludoRoomReady by mutableStateOf(false)
    private var ludoRoomStatus by mutableStateOf("Connect to a nearby player to invite them.")
    private var incomingLudoInvite by mutableStateOf<LudoPendingInvite?>(null)
    private val ludoRoomMessages = mutableStateListOf<String>()
    private var ludoRoomSession: LudoRoomSession? = null
    private var ludoRoomGame by mutableStateOf<LudoDoubleDiceState?>(null)
    private var ludoRoomIsHost by mutableStateOf(false)
    private var ludoRoomCanAct by mutableStateOf(false)
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
        window.statusBarColor = android.graphics.Color.rgb(17, 24, 39)
        window.navigationBarColor = android.graphics.Color.rgb(17, 24, 39)
        window.decorView.systemUiVisibility = 0
        wifiManager = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = wifiManager?.initialize(this, mainLooper, null)
        nearbyTransport = NearbyConnectionsTransport(this, object : NearbyConnectionsTransport.Listener {
            override fun onStatus(message: String) { runOnUiThread { statusMessage = message } }
            override fun onFound(id: String, name: String, stableId: String) { runOnUiThread {
                val normalizedId = BlockedPeerPolicy.normalize(stableId)
                if (normalizedId != null && normalizedId in blockedPeerIds) {
                    hybridPeers.remove(id)
                } else {
                    hybridPeers[id] = name
                    if (stableId.isNotBlank()) recentlySeenPeers.remove(stableId)
                }
            } }
            override fun onLost(id: String, stableId: String) { runOnUiThread {
                val name = hybridPeers.remove(id)
                val normalizedId = BlockedPeerPolicy.normalize(stableId)
                if (name != null && (normalizedId == null || normalizedId !in blockedPeerIds)) recordRecentlySeen(stableId.ifBlank { id }, name)
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
            override fun onRequest(id: String, name: String, token: String, stableId: String) {
                runOnUiThread {
                    val normalizedId = BlockedPeerPolicy.normalize(stableId)
                    if (normalizedId != null && normalizedId in blockedPeerIds) {
                        nearbyTransport?.reject(id)
                        statusMessage = "A connection request from a blocked nearby device was automatically declined."
                        return@runOnUiThread
                    }
                    pendingHybridEndpoint = id
                    pendingHybridStableId = normalizedId.orEmpty()
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
                    pendingHybridStableId = ""
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
                    // Resume the durable attachment queue only after this peer's
                    // connection has been accepted again.
                    flushPendingChatFiles()
                }
            }
            override fun onDisconnected(id: String, stableId: String, name: String) {
                runOnUiThread {
                    val peerId = stableId.ifBlank { "nearby:$id" }
                    if ((ludoRoomPeerStableId.isNotBlank() && ludoRoomPeerStableId == stableId) || (ludoRoomPeerStableId.isBlank() && ludoRoomPeerName == name)) {
                        ludoRoomStatus = "Nearby player disconnected. Your match room has ended."
                        ludoRoomId = null
                        ludoRoomPeerName = null
                        ludoRoomPeerStableId = ""
                        ludoRoomReady = false
                    }
                    val lastName = hybridPeers.remove(id) ?: name
                    recordRecentlySeen(peerId, lastName)
                    // A second nearby endpoint can disconnect while the selected chat
                    // remains connected. Queue only that peer's unconfirmed messages.
                    if (hybridConnectedEndpoint != id) {
                        queueUnconfirmedMessagesForPeer(peerId)
                        return@runOnUiThread
                    }
                    hybridConnected = false
                    hybridConnectedEndpoint = null
                    if (connectedChatPeerId == stableId || connectedChatPeerId == "nearby:$id") {
                        connectedChatPeerId = null
                        connectedChatPeerName = null
                    }
                    pendingHybridEndpoint = null
                    val legacyLinkAlive = peerSocket?.isConnected == true && peerSocket?.isClosed == false
                    chatReady = legacyLinkAlive
                    if (!legacyLinkAlive) queueUnconfirmedMessagesForPeer(peerId)
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
                    if (body.startsWith("NR_LUDO:")) handleLudoRoomPacket(senderName, senderStableId, body.removePrefix("NR_LUDO:"))
                    else if (body.startsWith("NR_COMMUNITY:")) handleCommunityPacket(senderName, senderStableId, body.removePrefix("NR_COMMUNITY:"))
                    else {
                        chatMessages.add(ChatMessage("legacy-" + System.currentTimeMillis(), body, outgoing = false, delivery = "Received"))
                        persistMessages()
                        chatNotice = "New offline message received from $senderName."
                    }
                }
            }
            override fun onMessageWithId(id: String, senderName: String, senderStableId: String, messageId: String, body: String) {
                runOnUiThread {
                    if (body.startsWith("NR_LUDO:")) {
                        handleLudoRoomPacket(senderName, senderStableId, body.removePrefix("NR_LUDO:"))
                    } else if (body.startsWith("NR_COMMUNITY:")) {
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
            override fun onDeliveryAck(id: String, messageId: String, senderStableId: String) {
                runOnUiThread {
                    val expectedPeerId = senderStableId.ifBlank { "nearby:$id" }
                    val index = chatMessages.indexOfFirst {
                        it.id == messageId && it.outgoing &&
                            it.conversationId.ifBlank { "legacy" } == expectedPeerId
                    }
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
                    if (message.startsWith("File sent to the nearby phone")) {
                        stopNearbyTransferKeepAlive()
                        activeQueuedFileUri?.let { completedUri ->
                            pendingChatFileUris.remove(completedUri)
                            if (!persistPendingChatFiles()) {
                                // The receiver has saved the file, but keep its queue
                                // record if local cleanup cannot be made durable. Do not
                                // silently lose the user's transfer history.
                                pendingChatFileUris.add(0, completedUri)
                                chatNotice = "File delivered, but NeaRer could not update the local queue. Remove the delivered item from prepared files to avoid resending it."
                            }
                        }
                        activeQueuedFileUri = null
                        if (chatNotice != "File delivered, but NeaRer could not update the local queue. Remove the delivered item from prepared files to avoid resending it.") {
                            flushPendingChatFiles()
                        }
                    }
                    if (message.startsWith("Nearby file transfer failed") ||
                        message.startsWith("Nearby file transfer cancelled") ||
                        message.startsWith("Could not start file transfer") ||
                        message.startsWith("Could not send file details") ||
                        message.startsWith("File could not be prepared") ||
                        message.startsWith("Connection lost before the other phone confirmed saving") ||
                        message.startsWith("file transfers lost their connection before recipient confirmation")) {
                        stopNearbyTransferKeepAlive()
                        // A disconnected recipient cannot ACK this transfer. Clear the
                        // spinner but keep the prepared item; reconnecting will retry it.
                        outgoingTransferId = null
                        outgoingProgress = 0f
                        outgoingTransferredBytes = 0L
                        outgoingTotalBytes = 0L
                        outgoingSpeedBytesPerSecond = 0L
                        outgoingRateSampleAt = 0L
                        outgoingRateSampleBytes = 0L
                        activeQueuedFileUri = null
                    }
                }
            }
        })
        if (wifiManager == null) statusMessage = "This phone does not provide Wi-Fi Direct support."
        restoreLocalData()
        restoreReceivedFiles()
        restoreSentFiles()
        displayName = getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getString("display_name", "NeaRer User")?.take(24)?.ifBlank { "NeaRer User" } ?: "NeaRer User"
        anonymousDiscovery = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            .getBoolean("anonymous_discovery_enabled", false)
        getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            .getStringSet("blocked_nearby_peer_ids", emptySet())
            ?.mapNotNull(BlockedPeerPolicy::normalize)
            ?.forEach { blockedPeerIds.add(it) }
        restoreCommunityData()
        nearbyTransport?.setDisplayName(displayName)
        nearbyTransport?.setAnonymousDiscovery(anonymousDiscovery)
        restoreRecentlySeen()
        meshRelayEnabled = getSharedPreferences("nearer_local", Context.MODE_PRIVATE).getBoolean("mesh_relay_enabled", false)
        nearbyTransport?.setRelayEnabled(meshRelayEnabled)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Midnight,
                surface = Panel,
                surfaceVariant = Color(0xFFECEAE4),
                primary = AccentBlue,
                secondary = Color(0xFF16877A),
                tertiary = Color(0xFFC17A52),
                onPrimary = White,
                onBackground = Color(0xFFF7F8FA),
                onSurface = Color(0xFF202B3C),
                onSurfaceVariant = Color(0xFF667085)
            )) {
                NeaRerHome(
                    playerName = displayName,
                    status = statusMessage,
                    scanning = scanning || hybridSearching,
                    devices = nearbyDevices,
                    connectingAddress = connectingAddress,
                    connectedDeviceName = connectedDeviceName,
                    connectedNearbyEndpoint = hybridConnectedEndpoint,
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
                    onSendMessage = { message, onSaved -> sendChatMessage(message, onSaved) },
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
                    pendingChatFiles = pendingChatFileUris.toList(),
                    onRemoveQueuedFile = { queuedUri ->
                        val index = pendingChatFileUris.indexOf(queuedUri)
                        if (index >= 0) {
                            pendingChatFileUris.removeAt(index)
                            if (persistPendingChatFiles()) {
                                chatNotice = "Removed from prepared files."
                            } else {
                                pendingChatFileUris.add(index.coerceAtMost(pendingChatFileUris.size), queuedUri)
                                chatNotice = "NeaRer could not update the prepared queue. The file is still listed."
                            }
                        }
                    },
                    onSendQueuedFiles = ::flushPendingChatFiles,
                    groups = communityGroups.toList(),
                    groupMessages = communityMessages.toList(),
                    selectedGroupCode = selectedCommunityCode,
                    communityNotice = communityNotice,
                    communityLocalMemberId = localCommunityMemberId,
                    incomingGameInvite = incomingGameInvite,
                    activeGame = activeGame,
                    gameChatMessages = gameChatMessages.toList(),
                    ludoRoomStatus = ludoRoomStatus,
                    incomingLudoInviteName = incomingLudoInvite?.senderName,
                    ludoRoomPeerName = ludoRoomPeerName,
                    ludoRoomReady = ludoRoomReady,
                    ludoRoomMessages = ludoRoomMessages.toList(),
                    ludoRoomGame = ludoRoomGame,
                    ludoRoomIsHost = ludoRoomIsHost,
                    ludoRoomCanAct = ludoRoomCanAct,
                    onLudoRoomAction = ::submitLudoRoomAction,
                    onInviteLudoPeer = ::inviteLudoPeer,
                    onAcceptLudoInvite = ::acceptLudoInvite,
                    onDeclineLudoInvite = ::declineLudoInvite,
                    onSendLudoRoomChat = ::sendLudoRoomChat,
                    onLeaveLudoRoom = ::leaveLudoRoom,
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
                    onBlockIncoming = ::blockIncomingPeer,
                    blockedPeerIds = blockedPeerIds.toList().sorted(),
                    onUnblockPeer = ::unblockPeer,
                    hybridPeers = hybridPeers.toMap(),
                    recentlySeenPeers = recentlySeenPeers.toMap(),
                    displayName = displayName,
                    onSaveDisplayName = ::saveDisplayName,
                    anonymousDiscovery = anonymousDiscovery,
                    onAnonymousDiscoveryChange = ::updateAnonymousDiscovery,
                    meshRelayEnabled = meshRelayEnabled,
                    onMeshRelayChange = ::updateMeshRelayEnabled,
                    onHybridConnect = { id ->
                        val stableId = BlockedPeerPolicy.normalize(nearbyTransport?.stableIdForEndpoint(id))
                        if (stableId != null && stableId in blockedPeerIds) {
                            hybridPeers.remove(id)
                            statusMessage = "This nearby device is blocked on your phone."
                        } else {
                            nearbyTransport?.request(id)
                        }
                    },
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
        var importedIdsChanged = false
        var flashMessagesNeedCommit = false
        val flashMessageIdsToMarkPersisted = mutableSetOf<String>()
        val flashEventIdsToMarkImported = mutableSetOf<String>()

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
                // Even if the bubble is already in memory, an earlier disk write may
                // have failed. Retry the full message snapshot before marking this
                // event imported so a process restart cannot lose the Flash message.
                flashMessagesNeedCommit = true
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
                val numberVerification = event.optString("numberVerification", "NOT_VERIFIED")
                val deliveryLabel = if (numberVerification == "NETWORK_VERIFIED") {
                    "Flash • Received • Network verified"
                } else {
                    "Flash • Received • Caller ID unverified"
                }
                if (chatMessages.none { it.id == messageId }) {
                    chatMessages.add(
                        ChatMessage(
                            id = messageId,
                            body = FlashSignalPattern.toChatMessage(signal),
                            outgoing = false,
                            delivery = deliveryLabel,
                            timestamp = event.optLong("timestamp", System.currentTimeMillis())
                                .takeIf { it > 0L } ?: System.currentTimeMillis(),
                            conversationId = flashConversationId,
                            conversationName = flashConversationName
                        )
                    )
                    receivedMessageIds.add(messageId)
                    changed = true
                }
                flashMessageIdsToMarkPersisted.add(messageId)
                if (eventId !in importedIds) {
                    flashEventIdsToMarkImported.add(eventId)
                    importedIdsChanged = true
                }
            }
        } catch (_: Exception) {
            // Leave malformed or unavailable event data untouched; normal chat remains usable.
        }

        if (changed || flashMessagesNeedCommit || importedIdsChanged) {
            // Save the imported chat bubbles before recording their event IDs. If the
            // message write fails or the app is interrupted, the same Flash event can
            // be imported again on the next launch instead of silently disappearing.
            val messageSnapshot = if (changed || flashMessagesNeedCommit) {
                try { serializeChatMessages() } catch (_: Exception) { null }
            } else null
            val canCommit = !(changed || flashMessagesNeedCommit) || messageSnapshot != null
            val importedIdSnapshot = (importedIds + flashEventIdsToMarkImported)
                .toList().takeLast(150).toSet()
            messagePersistence.execute {
                val messagesSaved = canCommit && (
                    messageSnapshot == null ||
                        localPrefs.edit().putString("messages", messageSnapshot).commit()
                    )
                if (messagesSaved) flashMessageIdsToMarkPersisted.forEach { persistedMessageIds.add(it) }
                if (messagesSaved && importedIdsChanged) {
                    val idsSaved = localPrefs.edit()
                        .putStringSet("flash_imported_event_ids", importedIdSnapshot)
                        .commit()
                    if (idsSaved) importedIds.addAll(flashEventIdsToMarkImported)
                }
            }
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

    override fun onStop() {
        // Discovery is user-initiated. Stop advertising/scanning when the app
        // leaves the foreground to avoid wasting battery; keep an established
        // peer session alive for an active user-visible transfer.
        if (NearbyDiscoveryBudgetPolicy.shouldStopWhenActivityStops(scanning || hybridSearching)) stopDiscovery()
        super.onStop()
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
        nearbySearchGeneration += 1
        nearbySearchTimeout?.let(discoveryHandler::removeCallbacks)
        nearbySearchTimeout = null
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

    private fun updateAnonymousDiscovery(enabled: Boolean) {
        val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        if (!prefs.edit().putBoolean("anonymous_discovery_enabled", enabled).commit()) {
            statusMessage = "Your privacy setting could not be saved. Please try again."
            return
        }
        anonymousDiscovery = enabled
        nearbyTransport?.setAnonymousDiscovery(enabled)
        statusMessage = if (enabled) {
            "Anonymous-name discovery is on. Nearby people see “Nearby NeaRer user” instead of your chosen name. Your persistent device ID is still visible."
        } else {
            "Your chosen nearby name is visible again."
        }
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
        val wifiOn = isWifiEnabled()
        if (!NearbyRadioReadinessPolicy.canStartNearby(bluetoothOn, wifiOn)) {
            finishDiscovery()
            statusMessage = "Turn Bluetooth or Wi-Fi ON in Quick Settings on both phones, then search again. Hotspot and mobile data are not required."
            return
        }
        nearbySearchTimeout?.let(discoveryHandler::removeCallbacks)
        val searchGeneration = ++nearbySearchGeneration
        nearbyTransport?.start()
        hybridSearching = true
        val nearbyTimeout = Runnable {
            if (searchGeneration == nearbySearchGeneration && hybridSearching) {
                nearbyTransport?.stopSearching()
                hybridSearching = false
                nearbySearchTimeout = null
                statusMessage = "Nearby search paused after 60 seconds to save battery. Tap Find people nearby to search again; connected chats and transfers stay active."
            }
        }
        nearbySearchTimeout = nearbyTimeout
        discoveryHandler.postDelayed(nearbyTimeout, NearbyDiscoveryBudgetPolicy.FOREGROUND_SEARCH_TIMEOUT_MS)
        if (!NearbyRadioReadinessPolicy.shouldStartWifiDirect(wifiOn)) {
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
                        statusMessage = if (nearbyDevices.isEmpty()) "No Wi-Fi Direct phones found in 20 seconds. Bluetooth discovery can continue; check Wi-Fi on both phones and Location for Wi-Fi Direct." else "${nearbyDevices.size} nearby phone(s) found. Choose one to connect. Bluetooth discovery can continue while NeaRer stays open."
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
                    approvalMessage = "Compare the verification code on both phones. Accept only if they match."
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
        stopNearbyTransferKeepAlive()
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

    private fun blockIncomingPeer() {
        val stableId = BlockedPeerPolicy.normalize(pendingHybridStableId)
        val endpoint = pendingHybridEndpoint
        if (stableId == null) {
            statusMessage = "NeaRer cannot safely block this device because it did not provide a valid nearby identity."
            rejectIncoming()
            return
        }
        val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet("blocked_nearby_peer_ids", emptySet()) ?: emptySet()).toMutableSet()
        updated.add(stableId)
        if (!prefs.edit().putStringSet("blocked_nearby_peer_ids", updated).commit()) {
            statusMessage = "The block could not be saved. Please try again."
            return
        }
        blockedPeerIds.add(stableId)
        hybridPeers.keys.filter { nearbyTransport?.stableIdForEndpoint(it) == stableId }.forEach { hybridPeers.remove(it) }
        endpoint?.let { nearbyTransport?.reject(it) }
        pendingHybridEndpoint = null
        pendingHybridStableId = ""
        pairingCode = null
        incomingRequesterName = null
        approvalMessage = "Nearby device blocked"
        statusMessage = "This device is blocked. Future nearby requests using this device ID will be declined."
    }

    private fun unblockPeer(stableId: String) {
        val normalized = BlockedPeerPolicy.normalize(stableId) ?: return
        val prefs = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet("blocked_nearby_peer_ids", emptySet()) ?: emptySet())
            .mapNotNull(BlockedPeerPolicy::normalize).toMutableSet()
        updated.remove(normalized)
        if (prefs.edit().putStringSet("blocked_nearby_peer_ids", updated).commit()) {
            blockedPeerIds.remove(normalized)
            statusMessage = "Nearby device unblocked. It can appear in discovery again."
        } else {
            statusMessage = "Could not update the blocked-device list. Please try again."
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
            pendingHybridStableId = ""
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
            stopNearbyTransferKeepAlive()
            activeQueuedFileUri = null
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
                    val restoredMessage = message.copy(
                        delivery = ChatDeliveryRecoveryPolicy.restoreDelivery(message.outgoing, message.delivery)
                    )
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

    private fun persistPendingChatFiles(): Boolean {
        val array = JSONArray()
        pendingChatFileUris.forEach { array.put(it) }
        // Prepared files are user work: report failure rather than silently
        // presenting an attachment as safely queued when disk persistence failed.
        return getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            .edit().putString("pending_chat_files", array.toString()).commit()
    }

    // Capture message state on the UI thread, then serialize every write through one
    // executor. This prevents an older asynchronous apply() from overwriting a newer
    // durable snapshot after a delivery receipt has already been sent.
    private fun serializeChatMessages(): String {
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
        return array.toString()
    }

    private fun persistMessages() {
        val snapshot = try { serializeChatMessages() } catch (_: Exception) { return }
        messagePersistence.execute {
            getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .edit().putString("messages", snapshot).commit()
        }
    }

    private fun persistReceivedMessageThenAcknowledge(messageId: String, nearbyEndpointId: String? = null, socket: Socket? = null) {
        val snapshot = try { serializeChatMessages() } catch (_: Exception) {
            chatNotice = "NeaRer could not prepare this message for saving. Please retry."
            return
        }
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
        if (pendingOutgoingMessageSaves.contains(message.id)) return false
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
            if (ChatDeliveryRecoveryPolicy.shouldQueueAfterDisconnect(
                    outgoing = message.outgoing,
                    delivery = message.delivery,
                    conversationId = message.conversationId,
                    disconnectedPeerId = peerId
                )
            ) {
                chatMessages[index] = message.copy(delivery = "Queued")
                changed = true
            }
        }
        if (changed) persistMessages()
    }

    private fun flushQueuedChatMessages() {
        if (!chatReady) return
        val queuedMessages = chatMessages.filter { message ->
            ChatDeliveryRecoveryPolicy.canRetryOnReconnect(
                outgoing = message.outgoing,
                delivery = message.delivery,
                conversationId = message.conversationId,
                connectedPeerId = connectedChatPeerId.orEmpty(),
                persistenceInProgress = pendingOutgoingMessageSaves.contains(message.id)
            )
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
        if (!chatReady || outgoingTransferId != null || activeQueuedFileUri != null || pendingChatFileUris.isEmpty()) return
        // Keep the head item persisted while it is in flight. A Nearby SUCCESS
        // only means bytes reached the peer's Nearby stack; the receiver's
        // file_ack is the event that proves the file was actually saved.
        val next = pendingChatFileUris.first()
        activeQueuedFileUri = next
        try {
            sendFile(Uri.parse(next))
            if (outgoingTransferId == null) {
                // sendFile may have had to re-queue the item because the peer
                // disappeared between the readiness check and payload creation.
                activeQueuedFileUri = null
            }
        } catch (_: Exception) {
            activeQueuedFileUri = null
            chatNotice = "This prepared attachment could not be opened. It remains in your queue; choose it again if Android revoked access."
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

    private fun persistOutgoingMessageThenDeliver(message: ChatMessage, onSaved: (Boolean) -> Unit) {
        val snapshot = try { serializeChatMessages() } catch (_: Exception) {
            chatNotice = "NeaRer could not prepare your message for saving. It has not been sent."
            onSaved(false)
            return
        }
        pendingOutgoingMessageSaves.add(message.id)
        messagePersistence.execute {
            val saved = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .edit().putString("messages", snapshot).commit()
            runOnUiThread {
                // Keep the pending marker until this main-thread callback runs so a
                // reconnect callback cannot flush this message in the meantime.
                pendingOutgoingMessageSaves.remove(message.id)
                if (!saved) {
                    val failedIndex = chatMessages.indexOfFirst { it.id == message.id && it.outgoing }
                    if (failedIndex >= 0) chatMessages[failedIndex] = chatMessages[failedIndex].copy(delivery = "Save failed")
                    chatNotice = "NeaRer could not save this message, so it was not sent. Please copy it and try again."
                    onSaved(false)
                    return@runOnUiThread
                }
                val index = chatMessages.indexOfFirst { it.id == message.id && it.outgoing }
                if (index < 0) return@runOnUiThread
                val current = chatMessages[index]
                onSaved(true)
                if (!chatReady) {
                    chatNotice = "Saved on this phone • waiting for your next nearby connection. Not delivered yet."
                    return@runOnUiThread
                }
                if (!deliverQueuedChatMessage(current)) {
                    val currentIndex = chatMessages.indexOfFirst { it.id == message.id }
                    if (currentIndex >= 0) chatMessages[currentIndex] = current.copy(delivery = "Queued")
                    persistMessages()
                    chatNotice = "Message saved here. It will retry when the nearby link is ready."
                }
            }
        }
    }

    private fun sendChatMessage(rawText: String, onSaved: (Boolean) -> Unit = {}) {
        val body = rawText.trim()
        if (body.isEmpty()) return
        val message = ChatMessage(
            UUID.randomUUID().toString(), body, outgoing = true,
            // Do not expose a sending state until the local copy is safely committed.
            delivery = "Queued",
            conversationId = activeChatPeerId,
            conversationName = activeChatPeerName
        )
        chatMessages.add(message)
        persistOutgoingMessageThenDeliver(message, onSaved)
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
        val message = CommunityChatMessage(id, code, displayName, body, System.currentTimeMillis())
        communityMessages.add(message)
        while (communityMessages.size > 300) communityMessages.removeAt(0)
        persistCommunityData()

        val packet = JSONObject().put("type", "group_message").put("code", code)
            .put("sender", displayName).put("senderId", localId).put("body", body).put("id", id)
        val submitted = nearbyTransport?.sendToStableIds("NR_COMMUNITY:$packet", group.memberIds.keys - localId) == true
        communityNotice = when {
            submitted -> "Message saved here and submitted to connected group devices. Offline members have not received it."
            group.members.size == 1 -> "Message saved in this community on this phone."
            else -> "Message saved on this phone. No group members are connected, so it has not been sent."
        }
    }

    private fun sendCommunityBroadcast(raw: String) {
        val body = raw.trim().take(500)
        if (body.isNotBlank() && sendCommunityPacket(JSONObject().put("type", "broadcast").put("sender", displayName).put("body", body))) {
            communityNotice = "Broadcast sent to connected nearby phones."
        }
    }
    private fun handleCommunityPacket(sender: String, senderStableId: String, raw: String) {
        try {
            if (raw.length > 8_192 || !CommunityPacketPolicy.isValidStableId(senderStableId)) {
                communityNotice = "A community packet was invalid and ignored."
                return
            }
            val packet = JSONObject(raw)
            val type = packet.optString("type")
            val code = packet.optString("code")
            val senderId = packet.optString("senderId").ifBlank { senderStableId }
            val memberArray = packet.optJSONArray("members")
            val pendingArray = packet.optJSONArray("pendingMembers")
            val moderatorsArray = packet.optJSONArray("moderators")
            val descriptor = CommunityPacketDescriptor(
                rawLength = raw.length,
                type = type,
                senderId = senderId,
                authenticatedSenderId = senderStableId,
                code = code,
                senderName = packet.optString("sender"),
                ownerId = packet.optString("ownerId"),
                name = packet.optString("name"),
                messageId = packet.optString("id"),
                body = packet.optString("body"),
                memberCount = memberArray?.length() ?: 0,
                pendingMemberCount = pendingArray?.length() ?: 0,
                moderatorCount = moderatorsArray?.length() ?: 0,
                memberId = packet.optString("memberId"),
                matchId = packet.optString("matchId"),
                symbol = packet.optString("symbol"),
                moveIndex = packet.optInt("index", -1)
            )
            if (!CommunityPacketPolicy.isValid(descriptor)) {
                communityNotice = "A community packet was invalid and ignored."
                return
            }
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

    private fun sendLudoRoomPacket(peerName: String, type: String, roomId: String, body: String = "", sequence: Long = 0L): Boolean {
        val encoded = LudoRoomProtocol.encode(type, roomId, body, sequence) ?: return false
        if (!hybridConnected || connectedDeviceName != peerName) return false
        val endpoint = hybridConnectedEndpoint ?: return false
        return nearbyTransport?.sendToEndpoint(endpoint, "NR_LUDO:" + encoded) == true
    }

    private fun inviteLudoPeer() {
        val peer = connectedDeviceName?.takeIf { hybridConnected && !it.isBlank() }
        if (peer == null) {
            ludoRoomStatus = "Connect to a nearby NeaRer player first."
            return
        }
        if (ludoRoomId != null) {
            ludoRoomStatus = "Leave your current room before inviting another player."
            return
        }
        val roomId = UUID.randomUUID().toString()
        ludoRoomPeerStableId = connectedChatPeerId.orEmpty()
        if (ludoRoomPeerStableId.isBlank()) {
            ludoRoomStatus = "The nearby connection has no verified device identity. Reconnect and try again."
            return
        }
        if (sendLudoRoomPacket(peer, "invite", roomId, displayName)) {
            ludoRoomId = roomId
            ludoRoomPeerName = peer
            ludoRoomReady = false
            ludoRoomStatus = "Invitation sent to " + peer + ". Waiting for acceptance…"
            ludoRoomMessages.clear()
        } else {
            ludoRoomStatus = "Could not send the invitation. Check that " + peer + " is still connected."
        }
    }

    private fun acceptLudoInvite() {
        val invite = incomingLudoInvite ?: return
        if (invite.senderStableId != connectedChatPeerId) {
            ludoRoomStatus = "Reconnect to the same player who sent this invitation before accepting."
            return
        }
        if (!sendLudoRoomPacket(invite.senderName, "accept", invite.roomId, displayName)) {
            ludoRoomStatus = "Could not accept. Reconnect to " + invite.senderName + " and try again."
            return
        }
        ludoRoomId = invite.roomId
        ludoRoomPeerName = invite.senderName
        ludoRoomPeerStableId = invite.senderStableId
        ludoRoomReady = true
        ludoRoomSession = null
        ludoRoomGame = null
        ludoRoomIsHost = false
        ludoRoomCanAct = false
        incomingLudoInvite = null
        ludoRoomStatus = "Accepted. Waiting for the host's official Ludo board…"
        ludoRoomMessages.clear()
    }

    private fun declineLudoInvite() {
        val invite = incomingLudoInvite
        if (invite != null && invite.senderStableId == connectedChatPeerId) {
            sendLudoRoomPacket(invite.senderName, "decline", invite.roomId, displayName)
        }
        incomingLudoInvite = null
        ludoRoomStatus = "Invitation declined."
    }

    private fun leaveLudoRoom() {
        val room = ludoRoomId
        val peer = ludoRoomPeerName
        if (room != null && peer != null) sendLudoRoomPacket(peer, "leave", room)
        ludoRoomId = null
        ludoRoomPeerName = null
        ludoRoomPeerStableId = ""
        ludoRoomReady = false
        ludoRoomSession = null
        ludoRoomGame = null
        ludoRoomIsHost = false
        ludoRoomCanAct = false
        ludoRoomMessages.clear()
        ludoRoomStatus = "You left the Ludo room."
    }

    private fun sendLudoRoomChat(raw: String) {
        if (!ludoRoomReady) return
        val room = ludoRoomId ?: return
        val peer = ludoRoomPeerName ?: return
        val body = raw.trim().take(300)
        if (body.isBlank()) return
        if (sendLudoRoomPacket(peer, "chat", room, body)) {
            ludoRoomMessages.add("You: " + body)
            while (ludoRoomMessages.size > 40) ludoRoomMessages.removeAt(0)
        } else {
            ludoRoomStatus = "Message not sent. Check your nearby connection."
        }
    }

    private fun sendLudoRoomSnapshot() {
        val session = ludoRoomSession ?: return
        if (session.role != LudoRoomRole.HOST || !ludoRoomReady) return
        val snapshot = session.snapshot
        val body = LudoRoomEnvelopeCodec.encode(session.envelope()) ?: run {
            ludoRoomStatus = "The official Ludo board could not be prepared."
            return
        }
        ludoRoomGame = snapshot.game
        ludoRoomCanAct = snapshot.game.game.activePlayer.id == session.localPeerId
        val peer = ludoRoomPeerName ?: return
        if (!sendLudoRoomPacket(peer, "state", snapshot.roomId, body, snapshot.sequence)) {
            ludoRoomStatus = "Board update not sent. Check the nearby connection."
        }
    }

    private fun submitLudoRoomAction(action: LudoRoomAction) {
        if (!ludoRoomReady) return
        val session = ludoRoomSession ?: return
        if (session.role == LudoRoomRole.HOST) {
            val updated = session.applyLocalHostAction(action) ?: return
            ludoRoomGame = updated.game
            ludoRoomCanAct = updated.game.game.activePlayer.id == session.localPeerId
            sendLudoRoomSnapshot()
        } else {
            val request = session.createGuestRequest(action) ?: return
            val body = LudoRoomActionCodec.encode(request.action) ?: return
            val peer = ludoRoomPeerName ?: return
            if (!sendLudoRoomPacket(peer, "intent", request.roomId, body, request.expectedSequence)) {
                ludoRoomStatus = "Move request not sent. Check the nearby connection."
            } else ludoRoomStatus = "Move request sent to the room host…"
        }
    }

    private fun handleLudoRoomPacket(sender: String, senderStableId: String, raw: String) {
        val packet = LudoRoomProtocol.parse(raw)
        if (packet == null || senderStableId.isBlank()) {
            ludoRoomStatus = "An invalid Ludo room message was ignored."
            return
        }
        when (packet.type) {
            "invite" -> {
                if (ludoRoomId != null || incomingLudoInvite != null) return
                incomingLudoInvite = LudoPendingInvite(packet.roomId, sender, senderStableId)
                ludoRoomStatus = sender + " invited you to a Ludo match."
            }
            "accept" -> {
                if (packet.roomId == ludoRoomId && ludoRoomPeerName == sender &&
                    (ludoRoomPeerStableId.isBlank() || ludoRoomPeerStableId == senderStableId)) {
                    ludoRoomPeerStableId = senderStableId
                    val localId = nearbyTransport?.localMemberId.orEmpty()
                    val hostSession = LudoRoomSession.host(packet.roomId, localId, senderStableId, displayName, sender)
                    if (hostSession == null) {
                        ludoRoomStatus = "Could not create a verified Ludo board. Reconnect and invite again."
                        return
                    }
                    ludoRoomSession = hostSession
                    ludoRoomGame = hostSession.snapshot.game
                    ludoRoomIsHost = true
                    ludoRoomCanAct = hostSession.snapshot.game.game.activePlayer.id == localId
                    ludoRoomReady = true
                    ludoRoomStatus = sender + " joined. Sending the official Ludo board…"
                    sendLudoRoomSnapshot()
                }
            }
            "state" -> {
                if (packet.roomId != ludoRoomId || ludoRoomPeerName != sender || ludoRoomPeerStableId != senderStableId || ludoRoomIsHost) return
                val envelope = LudoRoomEnvelopeCodec.decode(packet.body) ?: return
                val localId = nearbyTransport?.localMemberId.orEmpty()
                val session = ludoRoomSession
                val accepted = if (session == null) {
                    val created = LudoRoomSession.guest(envelope, localId)
                    if (created != null) ludoRoomSession = created
                    created != null
                } else session.acceptHostSnapshot(envelope)
                if (accepted) {
                    val synced = ludoRoomSession?.snapshot?.game
                    ludoRoomGame = synced
                    ludoRoomCanAct = synced?.game?.activePlayer?.id == localId
                    ludoRoomReady = true
                    ludoRoomStatus = "Official board synced with " + sender + "."
                }
            }
            "intent" -> {
                if (packet.roomId != ludoRoomId || ludoRoomPeerName != sender || ludoRoomPeerStableId != senderStableId || !ludoRoomIsHost) return
                val action = LudoRoomActionCodec.decode(packet.body) ?: return
                val session = ludoRoomSession ?: return
                val updated = session.applyGuestRequest(LudoRoomActionRequest(packet.roomId, senderStableId, packet.sequence, action)) ?: return
                ludoRoomGame = updated.game
                ludoRoomCanAct = updated.game.game.activePlayer.id == session.localPeerId
                sendLudoRoomSnapshot()
            }
            "chat" -> {
                if (packet.roomId == ludoRoomId && ludoRoomPeerName == sender &&
                    ludoRoomPeerStableId == senderStableId) {
                    ludoRoomMessages.add(sender + ": " + packet.body)
                    while (ludoRoomMessages.size > 40) ludoRoomMessages.removeAt(0)
                }
            }
            "decline" -> {
                if (packet.roomId == ludoRoomId && ludoRoomPeerName == sender &&
                    ludoRoomPeerStableId == senderStableId && !ludoRoomReady) {
                    ludoRoomId = null
                    ludoRoomPeerName = null
                    ludoRoomPeerStableId = ""
                    ludoRoomReady = false
                    ludoRoomStatus = sender + " declined the Ludo invitation."
                    ludoRoomSession = null
                    ludoRoomGame = null
                    ludoRoomIsHost = false
                    ludoRoomCanAct = false
                }
            }
            "leave" -> {
                if (packet.roomId == ludoRoomId && ludoRoomPeerName == sender &&
                    ludoRoomPeerStableId == senderStableId) {
                    ludoRoomId = null
                    ludoRoomPeerName = null
                    ludoRoomPeerStableId = ""
                    ludoRoomReady = false
                    ludoRoomMessages.clear()
                    ludoRoomSession = null
                    ludoRoomGame = null
                    ludoRoomIsHost = false
                    ludoRoomCanAct = false
                    ludoRoomStatus = sender + " left the Ludo room."
                }
            }
            else -> ludoRoomStatus = "This Ludo room action is not available yet."
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
                    zip.write(("NeaRer app package for " + app.label + " (" + app.packageName + "). Keep base.apk and every split_*.apk together. NeaRer can hand this package to Android's built-in split-app installer. Android will ask the recipient to approve installation.").toByteArray(Charsets.UTF_8))
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                chatNotice = "Allow NeaRer to install apps from this source in Android settings, then tap this app package again."
                val settingsIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
                try {
                    startActivity(settingsIntent)
                } catch (_: android.content.ActivityNotFoundException) {
                    chatNotice = "Android could not open the install-permission settings. Open Settings manually, find NeaRer under Install unknown apps, and allow it."
                } catch (_: SecurityException) {
                    chatNotice = "Android blocked the install-permission settings. Open Settings manually and allow NeaRer to install apps from this source."
                }
                return
            }
            try {
                SplitApkInstaller.install(this, item.file)
                chatNotice = "The split app package has been handed to Android. Review and approve the installation prompt."
            } catch (error: Exception) {
                chatNotice = error.message ?: "Android could not prepare this split app for installation."
            }
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
            activeQueuedFileUri = null
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
        activeQueuedFileUri = null
        sendWireLine("FILE_CANCEL|$id")
        stopNearbyTransferKeepAlive()
        chatNotice = "Cancelling file transfer…"
        // The prepared item remains in the queue for a later manual retry.
    }

    private fun queueChatFile(uri: Uri, notice: String) {
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val value = uri.toString()
        val alreadyQueued = value in pendingChatFileUris
        if (!alreadyQueued) pendingChatFileUris.add(value)
        if (!persistPendingChatFiles()) {
            if (!alreadyQueued) pendingChatFileUris.remove(value)
            chatNotice = "NeaRer could not save this attachment to your prepared queue. Check phone storage and try again."
            return
        }
        chatNotice = notice
    }

    private fun startNearbyTransferKeepAlive() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, NearbyTransferForegroundService::class.java))
        } catch (_: Exception) {
            // The durable prepared-file queue remains the recovery path if Android
            // refuses a foreground-service start on this device or OS version.
            chatNotice = "Transfer is running, but Android did not allow background protection. Keep NeaRer open until it finishes."
        }
    }

    private fun stopNearbyTransferKeepAlive() {
        try { stopService(Intent(this, NearbyTransferForegroundService::class.java)) } catch (_: Exception) { }
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
                queueChatFile(uri, "The nearby connection is not ready. This file remains prepared; reconnect and try again.")
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
                startNearbyTransferKeepAlive()
                outgoingProgress = 0f
                outgoingTransferredBytes = 0L
                outgoingTotalBytes = 0L
                outgoingSpeedBytesPerSecond = 0L
                outgoingRateSampleAt = 0L
                outgoingRateSampleBytes = 0L
                chatNotice = "Preparing to send $fileName over Nearby Connections…"
            } else {
                queueChatFile(uri, "Could not start this transfer. Your file remains prepared; reconnect and try again.")
            }
            return
        }
        if (!chatReady) return
        val transferId = UUID.randomUUID().toString()
        pendingSentFileUri = uri.toString()
        pendingSentFileName = null
        pendingSentFileSize = 0L
        outgoingTransferId = transferId
        startNearbyTransferKeepAlive()
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
                    val wasCancelled = e.message?.contains("cancel", ignoreCase = true) == true
                    chatNotice = if (wasCancelled) {
                        "File transfer cancelled."
                    } else {
                        queueChatFile(uri, "Transfer interrupted. This file has been returned to your prepared files.")
                        "File transfer failed: ${e.message ?: "connection problem"} • file kept in your prepared files"
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
    connectedNearbyEndpoint: String?,
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
    onSendMessage: (String, (Boolean) -> Unit) -> Unit,
    onSelectConversation: (String, String) -> Unit,
    isRecordingVoiceNote: Boolean,
    onVoiceNoteAction: () -> Unit,
    onSendQueuedMessages: () -> Unit,
    pendingChatFilesCount: Int,
    pendingChatFiles: List<String>,
    onRemoveQueuedFile: (String) -> Unit,
    onSendQueuedFiles: () -> Unit,
    groups: List<CommunityGroup>,
    groupMessages: List<CommunityChatMessage>,
    selectedGroupCode: String?,
    communityNotice: String,
    communityLocalMemberId: String,
    incomingGameInvite: GameInvite?,
    activeGame: TicTacToeMatch?,
    gameChatMessages: List<String>,
    ludoRoomStatus: String,
    incomingLudoInviteName: String?,
    ludoRoomPeerName: String?,
    ludoRoomReady: Boolean,
    ludoRoomMessages: List<String>,
    ludoRoomGame: LudoDoubleDiceState?,
    ludoRoomIsHost: Boolean,
    ludoRoomCanAct: Boolean,
    onLudoRoomAction: (LudoRoomAction) -> Unit,
    onInviteLudoPeer: () -> Unit,
    onAcceptLudoInvite: () -> Unit,
    onDeclineLudoInvite: () -> Unit,
    onSendLudoRoomChat: (String) -> Unit,
    onLeaveLudoRoom: () -> Unit,
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
    onBlockIncoming: () -> Unit,
    blockedPeerIds: List<String>,
    onUnblockPeer: (String) -> Unit,
    hybridPeers: Map<String, String>,
    recentlySeenPeers: Map<String, NearbyPerson>,
    displayName: String,
    onSaveDisplayName: (String) -> Boolean,
    anonymousDiscovery: Boolean,
    onAnonymousDiscoveryChange: (Boolean) -> Unit,
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
    val welcomeEnergy = remember {
        localChatPrefs.getString("welcome_answers_v1", "").orEmpty()
            .split("|").firstOrNull { it.startsWith("0=") }?.substringAfter("=")
    }
    val welcomePurpose = remember {
        localChatPrefs.getString("welcome_answers_v1", "").orEmpty()
            .split("|").firstOrNull { it.startsWith("1=") }?.substringAfter("=")
    }
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
    var receiveMode by remember { mutableStateOf(false) }
    var showInstalledApps by remember { mutableStateOf(false) }
    var showReceivedHistory by remember { mutableStateOf(false) }
    var sharedFilesFilter by remember { mutableStateOf("All") }
    var sharedFilesSearch by remember { mutableStateOf("") }
    var sharedFilesSort by remember { mutableStateOf("Recent") }
    var installedApps by remember { mutableStateOf<List<ShareableApp>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var appShareNotice by remember { mutableStateOf<String?>(null) }
    var isPreparingApp by remember { mutableStateOf(false) }
    var phoneFolderTreeUri by remember {
        mutableStateOf(flashContext.getSharedPreferences("nearer_folder_browser", Context.MODE_PRIVATE)
            .getString("tree_uri", null)?.let(Uri::parse))
    }
    var showPhoneFolderBrowser by remember { mutableStateOf(false) }
    val phoneFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                flashContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                phoneFolderTreeUri = uri
                flashContext.getSharedPreferences("nearer_folder_browser", Context.MODE_PRIVATE)
                    .edit().putString("tree_uri", uri.toString()).apply()
                showPhoneFolderBrowser = true
            } catch (e: SecurityException) {
                appShareNotice = "NeaRer could not keep access to that folder. Please choose it again."
            }
        }
    }
    val openPhoneFolderBrowser = {
        val savedGrantIsValid = phoneFolderTreeUri != null &&
            flashContext.contentResolver.persistedUriPermissions.any {
                it.uri == phoneFolderTreeUri && it.isReadPermission
            }
        if (savedGrantIsValid) {
            showPhoneFolderBrowser = true
        } else {
            phoneFolderTreeUri = null
            flashContext.getSharedPreferences("nearer_folder_browser", Context.MODE_PRIVATE)
                .edit().remove("tree_uri").apply()
            phoneFolderPicker.launch(null)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach(onSendFile)
    }
    if (showPhoneFolderBrowser && phoneFolderTreeUri != null) {
        PhoneFolderBrowser(
            treeUri = phoneFolderTreeUri!!,
            onSelectFiles = { uris -> uris.forEach(onSendFile) },
            onClose = { showPhoneFolderBrowser = false }
        )
    }
    if (showChatAttachmentMenu) {
        AlertDialog(
            onDismissRequest = { showChatAttachmentMenu = false },
            containerColor = Color(0xFF263449),
            title = { Text("SHARE SOMETHING", color = White, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose what you want to share. Files can wait here until someone connects nearby.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    listOf(Triple("Photos & videos", "Images and clips", "media"), Triple("Documents", "PDF, Word, Excel and more", "docs"), Triple("Audio & music", "Sound files and recordings", "audio"), Triple("Browse phone folders", "Explore a folder inside NeaRer", "folders"), Triple("Any file", "Browse phone storage", "files"), Triple("Installed apps", "Share an app package", "apps")).forEach { item ->
                        Surface(modifier = Modifier.fillMaxWidth().clickable {
                            showChatAttachmentMenu = false
                            when (item.third) {
                                "media" -> filePicker.launch(arrayOf("image/*", "video/*"))
                                "docs" -> filePicker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*"))
                                "audio" -> filePicker.launch(arrayOf("audio/*"))
                                "folders" -> openPhoneFolderBrowser()
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
                        }, color = Color(0xFF263449), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF63A7FF))) {
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
    if (showInstalledApps) {
        AlertDialog(
            onDismissRequest = { showInstalledApps = false },
            containerColor = Color(0xFFF8F7F3),
            title = { Text("Share an app", color = Color(0xFF202B3C), fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Your installed apps", color = Color(0xFF202B3C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Choose an app to package and send to the nearby phone.", color = Color(0xFF667085), fontSize = 11.sp)
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
                            Text(if (isLoadingApps) "SCANNING…" else "↻ REFRESH", color = if (isLoadingApps) Muted else Color(0xFF285F9E), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    if (isLoadingApps) {
                        Text("Scanning installed apps and preparing their icons…", color = Color(0xFF285F9E), fontSize = 11.sp)
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Gold,
                            trackColor = Color(0xFFE3E7ED)
                        )
                    }
                    var appSearch by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search apps", color = Color(0xFF667085)) }
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
                                color = if (selected) Color(0xFFE4EDF8) else Color(0xFFECEAE4),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (selected) Gold.copy(alpha = 0.8f) else Color(0xFFD5D9E0))
                            ) {
                                Text(
                                    filter,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp),
                                    color = Color(0xFF202B3C),
                                    fontSize = 10.sp,
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
                        color = if (isLoadingApps) Color(0xFF285F9E) else Color(0xFF667085),
                        fontSize = 10.sp
                    )
                    if (isLoadingApps) {
                        Text("Please wait while NeaRer reads the app library on this phone.", color = Color(0xFF202B3C), fontSize = 12.sp)
                    } else if (installedApps.isEmpty()) {
                        Text("No shareable apps were found. Check that this phone has apps installed.", color = Color(0xFF202B3C), fontSize = 13.sp)
                    } else if (visibleApps.isEmpty()) {
                        val emptyMessage = when {
                            appSearch.isNotBlank() && appFilter != "All" -> "No ${appFilter.lowercase()} apps match “${appSearch.trim()}”."
                            appSearch.isNotBlank() -> "No apps match “${appSearch.trim()}”. Try another name or package ID."
                            appFilter != "All" -> "No ${appFilter.lowercase()} apps were found on this phone."
                            else -> "No shareable apps were found on this phone."
                        }
                        Text(emptyMessage, color = Color(0xFF202B3C), fontSize = 13.sp)
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
                            color = Color.White, shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFE1E5EB))
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(11.dp))
                                )
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(app.label, color = Color(0xFF202B3C), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (app.apkPaths.size > 1) "Split app • " + app.apkPaths.size + " APK parts" else "App • APK ready", color = Color(0xFF667085), fontSize = 10.sp)
                                    Text(app.packageName, color = Color(0xFF7A8798), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("›", color = Color(0xFF285F9E), fontSize = 23.sp)
                            }
                        }
                    }
                    if (installedApps.any { it.apkPaths.size > 1 }) {
                        Text("Some apps contain split APKs. NeaRer sends those together in a ZIP package; the receiver will need a compatible split-APK installer.", color = Color(0xFF8B6B25), fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Text(
                        "App availability depends on what Android allows NeaRer to access. Some protected system components may not appear.",
                        color = Color(0xFF667085),
                        fontSize = 9.sp,
                        lineHeight = 14.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showInstalledApps = false }) { Text("Close", color = Color(0xFF285F9E)) } }
        )
    }
    if (showReceivedHistory) {
        AlertDialog(
            onDismissRequest = { showReceivedHistory = false },
            containerColor = Color(0xFF263449),
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
                                color = Color(0xFF1769FF),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF78B8FF).copy(alpha = 0.45f))
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
                        Surface(Modifier.fillMaxWidth(), color = Color(0xFF1769FF), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Gold.copy(alpha = 0.25f))) {
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
    var hasCompletedWelcome by remember {
        mutableStateOf(localChatPrefs.getBoolean("welcome_completed_v1", false))
    }
    if (!hasChosenName || !hasCompletedWelcome) {
        NeaRerWelcomeFlow(
            initialName = displayName.takeIf { it != "NeaRer User" }.orEmpty(),
            onSaveName = onSaveDisplayName,
            onComplete = { finalName, answers ->
                onSaveDisplayName(finalName)
                localChatPrefs.edit()
                    .putBoolean("welcome_completed_v1", true)
                    .putString("welcome_answers_v1", answers.entries.joinToString("|") { "${it.key}=${it.value}" })
                    .apply()
                hasChosenName = true
                hasCompletedWelcome = true
                activeSection = "home"
            }
        )
        return
    }
    if (showLudo) {
        LudoGameScreen(
            playerName = playerName,
            onExit = { showLudo = false },
            connectedPeerName = connectedDeviceName.takeIf { usingNearbyConnections },
            roomStatus = ludoRoomStatus,
            incomingInviteName = incomingLudoInviteName,
            roomPeerName = ludoRoomPeerName,
            roomReady = ludoRoomReady,
            roomMessages = ludoRoomMessages,
            roomGameState = ludoRoomGame,
            roomIsHost = ludoRoomIsHost,
            roomCanAct = ludoRoomCanAct,
            onRoomAction = onLudoRoomAction,
            onInvitePeer = onInviteLudoPeer,
            onAcceptInvite = onAcceptLudoInvite,
            onDeclineInvite = onDeclineLudoInvite,
            onSendRoomChat = onSendLudoRoomChat,
            onLeaveRoom = onLeaveLudoRoom
        )
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Midnight) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF172338),
                            0.42f to Color(0xFF111827),
                            1.0f to Color(0xFF202B3C)
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
                                painter = painterResource(R.drawable.ic_nearer),
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
                            color = Color(0xFFE4EAF2), fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when (welcomeEnergy) {
                                "Electric" -> "Big energy. Let's make things happen."
                                "Positive" -> "Good vibes look good on you."
                                "Creative" -> "Your next great idea starts here."
                                "Chill" -> "Your space. Your pace."
                                else -> when (welcomePurpose) {
                                    "Sharing files" -> "Everything you want to share, closer."
                                    "Chatting with people" -> "Your conversations are always welcome."
                                    "Playing games" -> "Ready when your people are."
                                    else -> "Your NeaRer space is ready."
                                }
                            },
                            color = Color(0xFFFFDFA0), fontSize = 10.sp, fontWeight = FontWeight.Medium
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
                    color = Color(0xFF285F9E),
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
                        color = Color(0xFF347CEB),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.dp, Color(0xFF78B8FF)),
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1769FF), Color(0xFF1769FF), Color(0xFF263449))
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
                                scanning -> Color(0xFF263449)
                                else -> Color(0xFF1769FF)
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
                                        Image(painter = painterResource(R.drawable.ic_nearer), contentDescription = "NeaRer network N", modifier = Modifier.size(32.dp))
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
                                    color = Color(0xFF1769FF),
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
                                        trackColor = Color(0xFF4D8FEA)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        receiveMode = false
                                        filePicker.launch(arrayOf("*/*"))
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
                                scanning -> "Searching for nearby phones. You can browse and prepare files while discovery runs."
                                chatReady -> "Connected to ${connectedDeviceName ?: "a nearby phone"}. Choose any file or app to send."
                                else -> "Browse your phone now. Selected files stay queued safely until you connect to a nearby person."
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().animateContentSize(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (incomingTransferName != null) Color(0xFF123F3A) else Color(0xFF1769FF),
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
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        shape = RoundedCornerShape(25.dp),
                        color = Color(0xFFF7FAFF),
                        border = BorderStroke(1.dp, Color(0xFFBBD4F5)),
                        shadowElevation = 8.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("YOUR NEARBY ORBIT", color = Color(0xFF1769FF), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                                    Text(when {
                                        hybridPeers.isNotEmpty() -> "${hybridPeers.size} NeaRer ${if (hybridPeers.size == 1) "person" else "people"} found"
                                        scanning -> "Your circle is taking shape…"
                                        else -> "Your people are closer than you think."
                                    }, color = Color(0xFF193653), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Surface(shape = CircleShape, color = Color(0xFFE7F0FF), modifier = Modifier.size(44.dp)) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Image(painter = painterResource(R.drawable.ic_nearer), contentDescription = "NeaRer nearby network", modifier = Modifier.size(35.dp))
                                    }
                                }
                            }
                            if (hybridPeers.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    hybridPeers.entries.take(4).forEach { peer ->
                                        Surface(modifier = Modifier.weight(1f).clickable { onHybridConnect(peer.key) },
                                            shape = RoundedCornerShape(17.dp), color = Color.White,
                                            border = BorderStroke(1.dp, Color(0xFFDCE8F7))) {
                                            Column(Modifier.padding(vertical = 11.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                Surface(shape = CircleShape, color = Color(0xFF1769FF), border = BorderStroke(2.dp, Color(0xFFFFD36B)), modifier = Modifier.size(43.dp)) {
                                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                                        Text(peer.value.trim().firstOrNull()?.uppercase() ?: "N", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                                    }
                                                }
                                                Text(peer.value, color = Color(0xFF193653), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("ACTIVE NOW", color = Color(0xFF16866B), fontSize = 8.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                                Text("Tap a person to start connecting. Only phones actually discovered nearby appear here.", color = Color(0xFF7187A0), fontSize = 10.sp, lineHeight = 15.sp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    androidx.compose.foundation.layout.Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                                        Surface(Modifier.size(62.dp), shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, Color(0xFFBBD4F5))) {}
                                        Surface(Modifier.size(43.dp), shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, Color(0xFF8BB8F4))) {}
                                        Surface(Modifier.size(27.dp), shape = CircleShape, color = Color(0xFF1769FF)) {
                                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                                Image(painter = painterResource(R.drawable.ic_nearer), contentDescription = null, modifier = Modifier.size(23.dp))
                                            }
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(if (scanning) "Scanning for real nearby phones" else "Start discovering your circle", color = Color(0xFF193653), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(if (scanning) "Keep NeaRer open while discovery is active." else "Find people around you and connect directly — even without mobile data.", color = Color(0xFF7187A0), fontSize = 10.sp, lineHeight = 15.sp)
                                    }
                                }
                                Button(onClick = {
                                    navigateToSection("nearby")
                                    if (!scanning && connectingAddress == null) onFindNearby()
                                    navigationScope.launch { pageScrollState.animateScrollTo(0) }
                                }, modifier = Modifier.fillMaxWidth().height(47.dp), shape = RoundedCornerShape(15.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769FF), contentColor = Color.White)) {
                                    Text(if (scanning) "VIEW NEARBY DISCOVERY  →" else "FIND PEOPLE NEAR ME  →", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                }
                            }
                            val recentCount = recentlySeenPeers.size
                            if (recentCount > 0) Text("$recentCount previously seen on this phone · not currently confirmed nearby", color = Color(0xFF8799AD), fontSize = 9.sp)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("YOUR NEARER SHORTCUTS", color = Color(0xFFFFDFA0), fontSize = 10.sp,
                                    fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                                Text("Jump straight into what you love.", color = Color(0xFFBFD8FF), fontSize = 11.sp)
                            }
                            Text("✦", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HomeFeatureTile("Messages", "Talk nearby", "✉", Color(0xFF1769FF), Modifier.weight(1f)) {
                                navigateToSection("chats")
                            }
                            HomeFeatureTile("Flash", "Quick signals", "ϟ", Color(0xFFE8B33E), Modifier.weight(1f)) {
                                navigateToSection("flash")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HomeFeatureTile("Communities", "Your circles", "◎", Color(0xFF22A88C), Modifier.weight(1f)) {
                                navigateToSection("community")
                            }
                            HomeFeatureTile("Game Zone", "Play together", "♟", Color(0xFF8A6BDA), Modifier.weight(1f)) {
                                navigateToSection("games")
                            }
                        }
                    }

                    if (pendingChatFilesCount > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF1769FF),
                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.8f)),
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                    Surface(shape = RoundedCornerShape(13.dp), color = Gold, modifier = Modifier.size(43.dp)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text("$pendingChatFilesCount", color = Color(0xFF1769FF), fontSize = 18.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("YOUR PREPARED FILES", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                                        Text(
                                            if (chatReady) "Ready to send to ${connectedDeviceName ?: "your nearby contact"}"
                                            else "Saved on this phone • connect whenever you're ready",
                                            color = White, fontSize = 10.sp, lineHeight = 14.sp
                                        )
                                    }
                                    Button(
                                        onClick = onSendQueuedFiles,
                                        enabled = chatReady && outgoingTransferId == null,
                                        shape = RoundedCornerShape(13.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Gold,
                                            contentColor = Color(0xFF1769FF),
                                            disabledContainerColor = Color(0xFFB7CFFF),
                                            disabledContentColor = Color(0xFF347CEB)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)
                                    ) {
                                        Text(if (chatReady) "SEND" else "WAIT", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                pendingChatFiles.take(5).forEach { queuedUri ->
                                    val queuedLabel = Uri.decode(Uri.parse(queuedUri).lastPathSegment.orEmpty())
                                        .substringAfterLast(':').substringAfterLast('/').ifBlank { "Selected file" }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color(0xFF263449), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("▣", color = Gold, fontSize = 13.sp)
                                        Text(queuedLabel, color = White, fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { onRemoveQueuedFile(queuedUri) }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)) {
                                            Text("REMOVE", color = Color(0xFFFFD0D0), fontSize = 8.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                                if (pendingChatFiles.size > 5) {
                                    Text("+ ${pendingChatFiles.size - 5} more prepared files", color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            receiveMode = false
                            filePicker.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(21.dp),
                        color = Color(0xFF263449),
                        border = BorderStroke(1.dp, Gold.copy(alpha = homeGlow)),
                        shadowElevation = 7.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.linearGradient(listOf(Color(0xFF263449), Color(0xFF263449), Color(0xFF263449))))
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
                                    "Browse and prepare files now. They'll send when a nearby connection is ready.",
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
                            Triple("📁", "Phone folders", emptyArray()),
                            Triple("•••", "All files", arrayOf("*/*"))
                        )
                        fileCategories.chunked(3).forEachIndexed { rowIndex, rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEachIndexed { columnIndex, item ->
                                    val tileBlue = when ((rowIndex * 3) + columnIndex) {
                                        0 -> Color(0xFF1554B8)
                                        1 -> Color(0xFF347CEB)
                                        2 -> Color(0xFF347CEB)
                                        3 -> Color(0xFF347CEB)
                                        4 -> Color(0xFF347CEB)
                                        else -> Color(0xFF263449)
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f).height(108.dp).animateContentSize().clickable {
                                            receiveMode = false
                                            if (item.second == "Phone folders") {
                                                openPhoneFolderBrowser()
                                            } else if (item.second == "Apps") {
                                                showInstalledApps = true
                                                isLoadingApps = true
                                                navigationScope.launch {
                                                    installedApps = withContext(Dispatchers.IO) { onLoadShareableApps() }
                                                    isLoadingApps = false
                                                }
                                            } else {
                                                filePicker.launch(item.third)
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
                                                        listOf(tileBlue.copy(alpha = 0.98f), Color(0xFF1769FF))
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
                            color = Color(0xFF263449),
                            shape = RoundedCornerShape(19.dp),
                            border = BorderStroke(
                                1.dp,
                                if (hasTransferResult && incomingTransferName == null && outgoingTransferId == null)
                                    transferResultAccent.copy(alpha = 0.58f)
                                else Color(0xFF78B8FF).copy(alpha = 0.55f)
                            ),
                            shadowElevation = 5.dp
                        ) {
                            Column(
                                Modifier.background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF263449), Color(0xFF263449), Color(0xFF1769FF))
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
                                                trackColor = Color(0xFF347CEB)
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                progress = { smoothIncomingProgress },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = BrandBlue,
                                                trackColor = Color(0xFF347CEB)
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
                                                trackColor = Color(0xFF347CEB)
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                progress = { smoothOutgoingProgress },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Gold,
                                                trackColor = Color(0xFF347CEB)
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
                            color = Color(0xFF1769FF),
                            shape = RoundedCornerShape(19.dp),
                            border = BorderStroke(1.dp, Color(0xFF63A7FF).copy(alpha = 0.75f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF263449),
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
                                color = Color(0xFF263449),
                                shape = RoundedCornerShape(19.dp),
                                border = BorderStroke(1.dp, Color(0xFF347CEB))
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
                        color = Color(0xFF263449),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF1769FF))
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {
                            if (messages.isEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = Color(0xFF347CEB), modifier = Modifier.size(43.dp)) {
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
                                        Surface(shape = CircleShape, color = if (message.outgoing) Color(0xFF347CEB) else Color(0xFF347CEB),
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
                                    if (index < minOf(messages.size, 3) - 1) androidx.compose.material3.HorizontalDivider(color = Color(0xFF347CEB))
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
                            color = Color(0xFF263449), shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, Color(0xFF1769FF))
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
                            color = Color(0xFF263449), shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, Color(0xFF1769FF))
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
                    color = Color(0xFF263449),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.55f))
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("NEARER FLASH • CALL-TO-CHAT", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                        Text("A quick call. A message in your chat.", color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Send a short Flash call pattern and NeaRer turns the recognised signal into a normal message bubble on the other phone. No SMS and no mobile data are used for the Flash signal. Calls use your carrier network, so any call charges depend on your plan.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        Surface(color = Color(0xFF1769FF), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
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
                                            color = if (selected) AccentBlue else Color(0xFF263449),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (selected) Gold else Color(0xFF347CEB))
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
                            color = Color(0xFF1769FF),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF347CEB)),
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
                                        focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0xFF347CEB),
                                        focusedLabelColor = AccentBlue, unfocusedLabelColor = Muted,
                                        cursorColor = Gold
                                    )
                                )
                                Button(
                                    onClick = {
                                        val message = flashCustomMessage.trim()
                                        if (message.isNotEmpty()) {
                                            onSendMessage(message) { saved ->
                                                if (saved && flashCustomMessage.trim() == message) flashCustomMessage = ""
                                            }
                                        }
                                    },
                                    enabled = flashCustomMessage.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Gold,
                                        contentColor = Midnight,
                                        disabledContainerColor = Color(0xFF263449),
                                        disabledContentColor = Muted
                                    )
                                ) {
                                    Text(
                                        if (chatReady) "SEND CUSTOM CHAT MESSAGE" else "SAVE UNTIL NEARBY CONNECTION",
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
                                focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0xFF347CEB),
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF347CEB), contentColor = White)
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
                            color = Color(0xFF1769FF),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, if (flashContactsPermission) Color(0xFF34765C) else Color(0xFF347CEB))
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
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF347CEB), contentColor = White)
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
                                focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0xFF347CEB),
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
                                focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0xFF347CEB),
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF347CEB), contentColor = White)
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
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color(0xFF263449), border = BorderStroke(1.dp, Color(0xFF347CEB))) {
                Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("NEARER GAME ZONE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                            Text("Play. Connect. Compete.", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.sp)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF263449)) {
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
                color = Color(0xFF263449),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFF477FC0).copy(alpha = 0.65f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF172B35), Color(0xFF24433F))))
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
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF123D70), RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("HIDE MY NAME IN DISCOVERY", color = White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text(
                                "Shows “Nearby NeaRer user” instead. Your persistent device ID remains visible, so this is not full anonymity.",
                                color = Color(0xFFB8CBE3), fontSize = 9.sp, lineHeight = 13.sp
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = anonymousDiscovery,
                            onCheckedChange = onAnonymousDiscoveryChange
                        )
                    }
                    if (blockedPeerIds.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("BLOCKED NEARBY DEVICES", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            blockedPeerIds.forEach { blockedId ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFF123D70), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Device " + blockedId.take(8) + "…", color = White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { onUnblockPeer(blockedId) }) {
                                        Text("UNBLOCK", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                    sectionPositions["nearby"] = it.positionInParent().y.toInt()
                },
                shape = RoundedCornerShape(27.dp),
                color = Color(0xFF263449),
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
                                chatReady -> "Connected to ${connectedDeviceName ?: "nearby phone"}"
                                scanning -> "Searching around you"
                                else -> "Your people are closer"
                            },
                                color = White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                        Surface(color = when {
                            isConnecting -> Color(0xFF765A20)
                            scanning -> Color(0xFF1D684F)
                            else -> Color(0xFF263449)
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
                                    chatReady -> "CONNECTED"
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
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight, disabledContainerColor = Color(0xFF1769FF), disabledContentColor = Color(0xFFD8E5F5))
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769FF), contentColor = White)
                            ) { Text("STOP", fontWeight = FontWeight.Bold) }
                        }
                    }
                    Surface(color = Color(0xFF1769FF), shape = RoundedCornerShape(12.dp)) {
                        Text(status, color = Muted, fontSize = 10.sp, lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth().padding(11.dp))
                    }
                    if (incomingRequesterName != null) {
                        Surface(
                            color = Color(0xFF263449),
                            shape = RoundedCornerShape(15.dp),
                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.65f))
                        ) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("CONNECTION REQUEST", color = Gold, fontSize = 9.sp,
                                    fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Text("$incomingRequesterName wants to connect.", color = White,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Compare the verification code on both phones before accepting.",
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
                                    Button(onClick = onBlockIncoming, modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2635), contentColor = White)) {
                                        Text("BLOCK", fontWeight = FontWeight.Black)
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
                                    color = if (reached) (if (chatReady) Color(0xFF54D6A5) else Gold) else Color(0xFF1769FF)
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
                        color = Color(0xFF347CEB), shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, Color(0xFF78B8FF))
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
                        color = Color(0xFF263449), shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, Color(0xFF347CEB))
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
                    color = Color(0xFFF7F5EF),
                    shape = RoundedCornerShape(19.dp),
                    border = BorderStroke(1.dp, Color(0xFFD8DDE5))
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("YOUR CONVERSATIONS", color = Color(0xFF202B3C), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            Text("${conversationRows.size} ${if (conversationRows.size == 1) "person" else "people"}", color = Color(0xFF667085), fontSize = 10.sp)
                        }
                        conversationRows.forEach { (peerId, peerName, latest) ->
                            val selected = selectedConversationId == peerId
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedConversationId = peerId
                                    onSelectConversation(peerId, peerName)
                                },
                                color = if (selected) Color(0xFFE6EEF8) else Color.White,
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(1.dp, if (selected) Gold.copy(alpha = 0.75f) else Color(0xFFE5E8ED))
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = if (selected) Color(0xFFD5E4F5) else Color(0xFFECEFF3), modifier = Modifier.size(36.dp)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text(peerName.firstOrNull()?.uppercase() ?: "N", color = Color(0xFF285F9E), fontWeight = FontWeight.Black)
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(peerName, color = Color(0xFF202B3C), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(latest?.body ?: "No messages yet", color = Color(0xFF667085), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(latest?.let { formatMessageTime(it.timestamp) } ?: "", color = Color(0xFF667085), fontSize = 9.sp)
                                        Text(latest?.delivery ?: "CHAT", color = if (latest?.outgoing == true && latest.delivery == "Delivered") Color(0xFF8CE4C8) else Color(0xFF8B6B25), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                sectionPositions["chats"] = it.positionInParent().y.toInt()
            }, shape = RoundedCornerShape(23.dp), color = Color(0xFFF7F5EF),
                border = BorderStroke(1.dp, if (chatReady) Color(0xFF16877A) else Color(0xFFD8DDE5)),
                shadowElevation = 9.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(
                            listOf(
                                if (chatReady) Color(0xFFEAF4EF) else Color(0xFFF7F5EF),
                                Color(0xFFF7F5EF),
                                Color(0xFFE8EDF3)
                            )
                        ))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (chatReady) Color(0xFF145A49) else Color(0xFF263449),
                            modifier = Modifier.size(44.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(if (chatReady) (connectedDeviceName ?: "N").take(1).uppercase() else "✉",
                                    color = White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text(connectedDeviceName ?: "PRIVATE CHAT", color = Color(0xFF202B3C), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(if (chatReady) "DIRECT • OFFLINE • PRIVATE" else "OFFLINE • CHAT IS READY",
                                color = if (chatReady) Color(0xFF16877A) else Color(0xFF667085),
                                fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                        }
                        Surface(
                            color = if (chatReady) Color(0xFFE0F3EC) else Color(0xFFE8EBF0),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(if (chatReady) "ACTIVE" else "OFFLINE",
                                color = if (chatReady) Color(0xFF16877A) else Color(0xFF667085),
                                fontSize = 8.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E4EA))
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                                Text("DIRECT LINK", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                Text(
                                    if (!chatReady) "Waiting" else if (usingNearbyConnections) "Bluetooth + Wi-Fi" else "Wi-Fi Direct",
                                    color = Color(0xFF202B3C), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = if (meshRelayEnabled) Color(0xFF214B45) else Color.White,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (meshRelayEnabled) Color(0xFF398A70) else Color(0xFFE0E4EA))
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                                Text("MESH RELAY", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
                                Text(if (meshRelayEnabled) "ON • Communities" else "OFF", color = Color(0xFF202B3C), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                            Text("Your private conversation starts here. Write now; messages stay safely on this phone until a route is available.", color = Color(0xFF596579), fontSize = 12.sp, lineHeight = 18.sp)
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
                                            color = if (message.outgoing) Color(0xFF285F9E) else Color.White,
                                            shape = if (message.outgoing) RoundedCornerShape(17.dp, 17.dp, 5.dp, 17.dp)
                                                else RoundedCornerShape(17.dp, 17.dp, 17.dp, 5.dp),
                                            modifier = Modifier.fillMaxWidth(0.86f).animateContentSize()
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                                                Text(message.body, color = if (message.outgoing) Color.White else Color(0xFF202B3C), fontSize = 14.sp)
                                                Text(
                                                    "${if (message.outgoing) message.delivery else "Received"}  •  ${formatMessageTime(message.timestamp)}",
                                                    color = if (message.outgoing) Color(0xFFDCE8F7) else Color(0xFF667085),
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769FF), contentColor = White)
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
                                color = Color(0xFF1769FF),
                                border = BorderStroke(1.dp, Color(0xFF78B8FF))
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
                                    val submittedDraft = draft.trim()
                                    onSendMessage(submittedDraft) { saved ->
                                        if (saved && draft.trim() == submittedDraft) {
                                            draft = ""
                                            localChatPrefs.edit().remove("chat_draft").apply()
                                        }
                                    }
                                },
                                shape = CircleShape,
                                color = if (draft.isNotBlank()) BrandBlue else Color(0xFF347CEB)
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
                                trackColor = Color(0xFF1769FF)
                            )
                        }
                        if (outgoingTransferId != null) {
                            LinearProgressIndicator(
                                progress = { outgoingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = AccentBlue,
                                trackColor = Color(0xFF1769FF)
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
                        Text("Compare the verification code on both phones. Accept only if it matches and you recognise this person.", color = Muted, fontSize = 12.sp)
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

            Surface(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp), color = Color(0xFF202B3C)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PEOPLE AROUND YOU", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (hybridPeers.isEmpty()) "Your nearby circle" else "${hybridPeers.size} active nearby", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (scanning && hybridPeers.isEmpty()) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(142.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, Color(0xFF1769FF)),
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
                                border = BorderStroke(1.dp, Color(0xFF1769FF)),
                                modifier = Modifier.size(72.dp)
                            ) {}
                            Surface(shape = CircleShape, color = BrandBlue, modifier = Modifier.size(54.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Image(painter = painterResource(R.drawable.ic_nearer), contentDescription = "NeaRer network N", modifier = Modifier.size(44.dp)) }
                            }
                        }
                        Text("Looking for NeaRer people…", color = White, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("Your chosen name is visible to nearby NeaRer phones.", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (hybridPeers.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            hybridPeers.entries.take(5).forEachIndexed { index, entry ->
                                val drift by searchMotion.animateFloat(0f, if (index % 2 == 0) 5f else -5f, infiniteRepeatable(tween(1300 + index * 170), RepeatMode.Reverse), label = "bubble-$index")
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Surface(shape = CircleShape, color = Color(0xFF263449), modifier = Modifier.size(54.dp).rotate(drift)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(entry.value.take(1).uppercase(), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                                    }
                                    Text(entry.value, color = White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("Active now", color = AccentBlue, fontSize = 9.sp)
                                }
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(78.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = CircleShape, color = Color(0xFF263449), modifier = Modifier.size(68.dp)) {
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
                                    Surface(shape = CircleShape, color = Color(0xFF263449), modifier = Modifier.size(32.dp)) {
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
                            color = Color(0xFF263449),
                            border = BorderStroke(1.dp, Color(0xFF3C72B2).copy(alpha = 0.72f)),
                            shadowElevation = 5.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Brush.linearGradient(listOf(Color(0xFF1B477A), Color(0xFF263449))))
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
                                        disabledContainerColor = Color(0xFF1769FF),
                                        disabledContentColor = Color(0xFFD8E5F5)
                                    )
                                ) {
                                    Text(
                                        when {
                                            connectedNearbyEndpoint == id -> "CONNECTED"
                                            chatReady -> "IN ANOTHER CHAT"
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
                    color = Color(0xFFF7F5EF),
                    border = BorderStroke(1.dp, Color(0xFFD8DDE5))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE5EDF7),
                                modifier = Modifier.size(38.dp)
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(if (scanning) "⌕" else "◎", color = Color(0xFF285F9E), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (scanning) "Searching for nearby people" else "Ready when you are",
                                    color = Color(0xFF202B3C),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (scanning) "NeaRer is actively looking. Keep this screen open on both phones."
                                    else "Tap Find people nearby to start live discovery. No mobile data is needed.",
                                    color = Color(0xFF596579),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                            Surface(shape = CircleShape, color = if (scanning) Color(0xFFE0F3EC) else Color(0xFFE8EBF0)) {
                                Text(
                                    if (scanning) "LIVE" else "STANDBY",
                                    color = if (scanning) Color(0xFF126B56) else Color(0xFF596579),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE0E4EA))
                        listOf(
                            "Bluetooth and Wi-Fi should be switched on",
                            "Both phones need NeaRer for chat and app sharing",
                            "Keep NeaRer open and accept the connection on the other phone"
                        ).forEachIndexed { index, tip ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("0${index + 1}", color = Color(0xFF285F9E), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                Text(tip, color = Color(0xFF596579), fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                        if (!scanning && connectedDeviceName == null) {
                            Button(
                                onClick = onFindNearby,
                                modifier = Modifier.fillMaxWidth().height(43.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF285F9E), contentColor = Color.White)
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
                                        Image(painter = painterResource(R.drawable.ic_nearer), contentDescription = "NeaRer network N", modifier = Modifier.size(28.dp))
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
                color = Color(0xFF263449),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 18.dp,
                border = BorderStroke(1.dp, Color(0xFF244A7D).copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFF263449), Color(0xFF1769FF))))
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
                            modifier = Modifier.weight(1f).heightIn(min = 60.dp).animateContentSize().semantics(mergeDescendants = true) {
                                    contentDescription = item.second + " tab"
                                    this.selected = selected
                                    role = Role.Tab
                                }.clickable {
                                navigateToSection(item.third)
                                navigationScope.launch { pageScrollState.animateScrollTo(0) }
                            },
                            color = if (selected) Color(0xFF1769FF) else Color.Transparent,
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
    val ink = Color(0xFF202B3C)
    val mutedInk = Color(0xFF667085)
    val line = Color(0xFFE5E7EB)
    val blue = Color(0xFF285F9E)
    val teal = Color(0xFF16877A)
    val ivory = Color(0xFFF8F7F3)
    val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = blue,
        focusedBorderColor = blue, unfocusedBorderColor = Color(0xFFD2D7DF),
        focusedLabelColor = blue, unfocusedLabelColor = mutedInk
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = ivory) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("YOUR PEOPLE, YOUR SPACE", color = teal, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                        Text("Communities", color = ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("Create a group for the people you want to stay close to.", color = mutedInk, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Surface(shape = CircleShape, color = Color(0xFFE4F1EC), modifier = Modifier.size(48.dp)) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Text("N", color = teal, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), color = White, border = BorderStroke(1.dp, line)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("START A COMMUNITY", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = name, onValueChange = { name = it.take(32) }, modifier = Modifier.weight(1f),
                                singleLine = true, label = { Text("Community name") }, colors = fieldColors,
                                shape = RoundedCornerShape(13.dp)
                            )
                            Button(
                                onClick = { onCreate(name); name = "" }, enabled = name.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = blue, contentColor = White),
                                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp)
                            ) { Text("Create", fontWeight = FontWeight.Bold) }
                        }
                        Text("HAVE AN INVITE CODE?", color = mutedInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                                modifier = Modifier.weight(1f), singleLine = true, label = { Text("6-digit code") },
                                colors = fieldColors, shape = RoundedCornerShape(13.dp)
                            )
                            Button(
                                onClick = { onJoin(code); code = "" }, enabled = code.length == 6,
                                colors = ButtonDefaults.buttonColors(containerColor = teal, contentColor = White),
                                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) { Text("Join", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Text("YOUR COMMUNITIES  ·  ${groups.size}", color = mutedInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                if (groups.isEmpty()) {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFFECEAE4)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Your community list is ready", color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Create a group or enter a six-digit invite code. Groups appear here when they are saved on this phone.", color = mutedInk, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                } else {
                    groups.forEachIndexed { index, group ->
                        val chosen = group.code == selectedCode
                        Surface(
                            Modifier.fillMaxWidth().clickable { onSelect(group.code) },
                            shape = RoundedCornerShape(15.dp),
                            color = if (chosen) Color(0xFFE8F0F8) else White,
                            border = BorderStroke(1.dp, if (chosen) blue.copy(alpha = 0.55f) else line)
                        ) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                Surface(shape = RoundedCornerShape(12.dp), color = if (index % 2 == 0) Color(0xFFE8F2EF) else Color(0xFFF4EBDD), modifier = Modifier.size(42.dp)) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Text(group.name.firstOrNull()?.uppercase() ?: "N", color = if (index % 2 == 0) teal else Color(0xFF986B35), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(group.name, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${group.memberIds.size} members  ·  Code ${group.code}", color = mutedInk, fontSize = 10.sp)
                                }
                                Text(if (chosen) "OPEN" else "VIEW", color = blue, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
                Text(notice, color = if (notice.contains("error", true) || notice.contains("failed", true)) Color(0xFF9C4545) else mutedInk, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        if (selected != null) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = White, border = BorderStroke(1.dp, line)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("COMMUNITY SPACE", color = teal, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            Text(selected.name, color = ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("Invite code ${selected.code}", color = mutedInk, fontSize = 10.sp)
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NeaRer community code", selected.code))
                        }) { Text("COPY CODE", color = blue, fontWeight = FontWeight.Bold) }
                    }
                    val isOwner = selected.ownerId == localMemberId && localMemberId.isNotBlank()
                    val canModerate = isOwner || localMemberId in selected.moderators
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${selected.memberIds.size} known members", color = mutedInk, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onLeaveGroup(selected.code) }) { Text("Leave group", color = Color(0xFF9C4545)) }
                    }
                    Text("MEMBERS", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    selected.memberIds.forEach { (memberId, memberName) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Surface(shape = CircleShape, color = Color(0xFFEAF0F5), modifier = Modifier.size(34.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(memberName.firstOrNull()?.uppercase() ?: "?", color = blue, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(memberName, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(when { memberId == selected.ownerId -> "Owner"; memberId in selected.moderators -> "Moderator"; else -> "Member" }, color = mutedInk, fontSize = 10.sp)
                            }
                            if (isOwner && memberId != selected.ownerId && memberId !in selected.moderators) TextButton(onClick = { onPromoteMember(selected.code, memberId) }) { Text("MOD", color = blue, fontSize = 9.sp) }
                            if (isOwner && memberId != selected.ownerId) TextButton(onClick = { onTransferOwnership(selected.code, memberId) }) { Text("OWNER", color = teal, fontSize = 9.sp) }
                            if (canModerate && memberId != selected.ownerId && (isOwner || memberId !in selected.moderators)) TextButton(onClick = { onRemoveMember(selected.code, memberId) }) { Text("REMOVE", color = Color(0xFF9C4545), fontSize = 9.sp) }
                        }
                    }
                    if (canModerate && selected.pendingMembers.isNotEmpty()) {
                        Text("JOIN REQUESTS", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        selected.pendingMembers.forEach { (memberId, memberName) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(memberName, color = ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onApproveMember(selected.code, memberId) }) { Text("Approve", color = teal) }
                                TextButton(onClick = { onRemoveMember(selected.code, memberId) }) { Text("Decline", color = Color(0xFF9C4545)) }
                            }
                        }
                    }
                    Text("CONVERSATION", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Column(Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 190.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        val thread = messages.filter { it.groupCode == selected.code }.takeLast(40)
                        if (thread.isEmpty()) Text("No messages yet. Start the conversation with your group.", color = mutedInk, fontSize = 11.sp)
                        thread.forEach { message ->
                            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0xFFF3F4F6)) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(message.sender, color = blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(message.body, color = ink, fontSize = 12.sp, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(groupText, { groupText = it.take(1500) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Write a group message") }, colors = fieldColors)
                        Button(onClick = { onGroupMessage(groupText); groupText = "" }, enabled = groupText.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = blue)) { Text("Send") }
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color(0xFF202B3C)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("NEARBY BROADCAST", color = Color(0xFF82C8B9), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text("Share an announcement", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Only currently connected NeaRer devices receive broadcasts. This does not send through the internet.", color = Color(0xFFD0D7E2), fontSize = 11.sp, lineHeight = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        broadcastText, { broadcastText = it.take(500) }, Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("Write an announcement") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White, unfocusedTextColor = White, cursorColor = Color(0xFF82C8B9),
                            focusedBorderColor = Color(0xFF82C8B9), unfocusedBorderColor = Color(0xFF687587),
                            focusedPlaceholderColor = Color(0xFFCAD2DD), unfocusedPlaceholderColor = Color(0xFFCAD2DD)
                        )
                    )
                    Button(onClick = { onBroadcast(broadcastText); broadcastText = "" }, enabled = broadcastText.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16877A), contentColor = White)) { Text("Send") }
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color(0xFFF4EBDD)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PLAY TOGETHER", color = Color(0xFF986B35), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text("A little friendly competition", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Invite a connected person to a live nearby Tic-tac-toe match.", color = mutedInk, fontSize = 11.sp, lineHeight = 16.sp)
                if (invite != null) {
                    Text(invite.sender + " invited you to play.", color = ink, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAccept, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = teal)) { Text("Accept") }
                        Button(onClick = onReject, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8D4545))) { Text("Decline") }
                    }
                } else {
                    Button(onClick = onInviteGame, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF986B35), contentColor = White)) { Text("Invite nearby player") }
                }
                if (game != null) {
                    Text("MATCH IN PROGRESS", color = Color(0xFF986B35), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text("Game state: " + game.toString(), color = ink, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..8).forEach { cell ->
                            TextButton(onClick = { onMove(cell) }) { Text((cell + 1).toString(), color = blue) }
                        }
                    }
                    gameChat.takeLast(8).forEach { Text(it, color = ink, fontSize = 11.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(gameText, { gameText = it.take(300) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Game chat") }, colors = fieldColors)
                        Button(onClick = { onGameChat(gameText); gameText = "" }, enabled = gameText.isNotBlank()) { Text("Send") }
                    }
                    TextButton(onClick = onEndGame) { Text("End match", color = Color(0xFF9C4545)) }
                }
            }
        }
    }
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


@androidx.compose.runtime.Composable
private fun NeaRerWelcomeFlow(
    initialName: String,
    onSaveName: (String) -> Boolean,
    onComplete: (String, Map<Int, String>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var step by remember { mutableStateOf(0) }
    var revealFinished by remember { mutableStateOf(false) }
    val answers = remember { androidx.compose.runtime.mutableStateMapOf<Int, String>() }
    val multi = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val questions = remember {
        listOf(
            "What kind of energy do you bring?",
            "What brings you to NeaRer?",
            "What do you enjoy most?",
            "What matters most when you connect?",
            "Your perfect free day looks like…",
            "How should NeaRer welcome you?"
        )
    }
    val options = remember {
        listOf(
            listOf("Electric", "Positive", "Creative", "Chill", "A little of everything"),
            listOf("Sharing files", "Chatting with people", "Discovering communities", "Playing games", "Everything"),
            listOf("Music & movies", "Games & entertainment", "Technology & gadgets", "Learning new things", "Creating things", "Sports & adventures"),
            listOf("Speed", "Privacy", "Beautiful design", "Works without internet", "Everything working smoothly"),
            listOf("Explore somewhere new", "Relax and recharge", "Spend time with friends", "Learn or create", "Games and movies", "Something unpredictable"),
            listOf("Hey, good to see you!", "Ready for something exciting?", "Your space is ready.", "Let's connect!")
        )
    }
    val captions = listOf(
        "No labels needed. Pick the vibe that feels most like you.",
        "We'll put the things you care about closer to the front.",
        "Choose as many as you like. This is your space.",
        "Tell us what makes a connection feel right.",
        "Just for fun. There are no wrong answers.",
        "A little personality makes every visit warmer."
    )
    val transition = rememberInfiniteTransition(label = "welcome-orbit")
    val orbit by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing)), label = "welcome-orbit-angle")
    val glow by transition.animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "welcome-glow")
    val currentIndex = step - 1
    val isMulti = currentIndex == 2
    val selected = if (isMulti) multi.isNotEmpty() else answers.containsKey(currentIndex)
    val progress = (step.coerceIn(0, 6) / 6f)
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF8FBFF), Color(0xFFEAF3FF), Color(0xFFFFFFFF))))
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.ic_nearer), contentDescription = "NeaRer", modifier = Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nea", color = Color(0xFF1769FF), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("R", color = Color(0xFFE5A900), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("er", color = Color(0xFF1769FF), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("NEARER YOU. NEARER EVERYTHING.", color = Color(0xFF66809F), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                if (step in 1..6) Text("SKIP", color = Color(0xFF1769FF), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { step = 7 })
            }
            if (step <= 6) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(6) { index ->
                        androidx.compose.foundation.layout.Box(
                            Modifier.weight(1f).height(5.dp).clip(CircleShape)
                                .background(if (index < step) Color(0xFF1769FF) else Color(0xFFD9E6F7))
                        )
                    }
                }
            }
            if (step == 0) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                    repeat(3) { index ->
                        Surface(
                            modifier = Modifier.size((116 + index * 42).dp).rotate(orbit * (if (index % 2 == 0) 1f else -1f)).alpha(glow),
                            shape = CircleShape, color = Color.Transparent,
                            border = BorderStroke(1.dp, if (index == 1) Color(0xFF8CB9FF) else Color(0xFFBED7FF))
                        ) {}
                    }
                    Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFF1769FF), shadowElevation = 16.dp,
                        modifier = Modifier.size(100.dp)) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(R.drawable.ic_nearer), contentDescription = "NeaRer connection symbol", modifier = Modifier.size(82.dp))
                        }
                    }
                }
                Text("A little about you.\nA lot closer to everything.", color = Color(0xFF142C4B), fontSize = 28.sp, lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Let's make your NeaRer space feel like yours from the very first hello.", color = Color(0xFF647B98), fontSize = 14.sp,
                    lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("WHAT SHOULD WE CALL YOU?", color = Color(0xFF55718F), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, placeholder = { Text("Your name") }, shape = RoundedCornerShape(18.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF183452), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                    Text("This is the name nearby people can see. You can change it later.", color = Color(0xFF7890AA), fontSize = 11.sp)
                }
                Button(onClick = { if (name.trim().isNotBlank() && onSaveName(name.trim())) step = 1 },
                    enabled = name.trim().isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769FF), contentColor = Color.White)) {
                    Text("LET'S MAKE IT YOURS  →", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
            } else if (step in 1..6) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFDCE9F8)), shadowElevation = 8.dp) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("GET TO KNOW YOU  •  0\${step}/06".replace("0\${step}", step.toString().padStart(2, '0')),
                            color = Color(0xFF1769FF), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(questions[currentIndex], color = Color(0xFF142C4B), fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
                        Text(captions[currentIndex], color = Color(0xFF7288A1), fontSize = 12.sp, lineHeight = 18.sp)
                        options[currentIndex].forEachIndexed { index, option ->
                            val active = if (isMulti) option in multi else answers[currentIndex] == option
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (isMulti) { if (active) multi.remove(option) else multi.add(option) }
                                    else answers[currentIndex] = option
                                },
                                shape = RoundedCornerShape(17.dp),
                                color = if (active) Color(0xFFEAF2FF) else Color(0xFFF7FAFE),
                                border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Color(0xFF1769FF) else Color(0xFFE2EBF5))
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(12.dp), color = if (active) Color(0xFF1769FF) else Color(0xFFE6EEF8),
                                        modifier = Modifier.size(38.dp)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text(listOf("✦", "◎", "⌁", "◈", "☼", "♡")[index % 6],
                                                color = if (active) Color.White else Color(0xFF5B7CA2), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(option, color = Color(0xFF193653), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    if (active) Text("✓", color = Color(0xFF1769FF), fontSize = 18.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        if (isMulti) Text("Select all that feel like you.", color = Color(0xFF7890AA), fontSize = 10.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { step = (step - 1).coerceAtLeast(0) },
                        modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(17.dp)) {
                        Text("←  BACK", color = Color(0xFF1769FF), fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = {
                        if (isMulti) answers[currentIndex] = multi.joinToString(", ")
                        step++
                    }, enabled = selected, modifier = Modifier.weight(1.25f).height(54.dp), shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769FF), contentColor = Color.White)) {
                        Text(if (step == 6) "FINISH  ✦" else "CONTINUE  →", fontWeight = FontWeight.Black)
                    }
                }
            } else {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2300)
                    revealFinished = true
                    onComplete(name.trim().ifBlank { initialName.ifBlank { "NeaRer User" } }, answers.toMap())
                }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    repeat(4) { index ->
                        Surface(Modifier.size((92 + index * 48).dp).rotate(orbit * (if (index % 2 == 0) 1f else -1f)).alpha(glow),
                            shape = CircleShape, color = Color.Transparent,
                            border = BorderStroke(1.dp, if (index % 2 == 0) Color(0xFF83B5FF) else Color(0xFFFFD66B))) {}
                    }
                    Surface(Modifier.size(110.dp), shape = RoundedCornerShape(32.dp), color = Color(0xFF1769FF), shadowElevation = 18.dp) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(R.drawable.ic_nearer), contentDescription = "NeaRer", modifier = Modifier.size(92.dp))
                        }
                    }
                }
                Text("Here's something\njust for you.", color = Color(0xFF142C4B), fontSize = 31.sp, lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Bringing your NeaRer space together, ${name.trim().ifBlank { "friend" }}…", color = Color(0xFF647B98),
                    fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF1769FF), trackColor = Color(0xFFDCE9F8))
            }
            Text("PRIVATE BY DESIGN  •  NO ACCOUNT REQUIRED", color = Color(0xFF8AA0B8), fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun HomeFeatureTile(
    title: String,
    subtitle: String,
    symbol: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(118.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF8FBFF),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        shadowElevation = 5.dp
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.linearGradient(listOf(Color.White, accent.copy(alpha = 0.075f))))
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(43.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = accent.copy(alpha = 0.13f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text(symbol, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("OPEN  ›", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.55.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color(0xFF193653), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color(0xFF7187A0), fontSize = 9.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
