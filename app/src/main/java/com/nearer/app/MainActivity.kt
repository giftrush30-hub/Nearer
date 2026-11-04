package com.nearer.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Bundle
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private val Midnight = Color(0xFF101A2B)
private val Panel = Color(0xFF1A2940)
private val Mint = Color(0xFF69E0C0)
private val Muted = Color(0xFFA8B6C9)
private val White = Color(0xFFF6F8FC)

class MainActivity : ComponentActivity() {
    private lateinit var wifiManager: WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var scanning by mutableStateOf(false)
    private var connectingAddress by mutableStateOf<String?>(null)
    private var connectedDeviceName by mutableStateOf<String?>(null)
    private var connectionMessage by mutableStateOf("Not connected")
    private var incomingRequesterName by mutableStateOf<String?>(null)
    private var approvalMessage by mutableStateOf("Waiting for app approval")
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
    private val receivedFiles = mutableStateListOf<String>()
    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()
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
                        wifiManager.requestPeers(channel) { peers ->
                            nearbyDevices.clear()
                            nearbyDevices.addAll(peers.deviceList)
                            scanning = false
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
                    scanning = state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                    if (scanning) statusMessage = "Searching nearby…"
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (hasDiscoveryPermission()) {
                        wifiManager.requestConnectionInfo(channel) { info ->
                            updateConnectionState(info)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiManager.initialize(this, mainLooper, null)
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
                    scanning = scanning,
                    devices = nearbyDevices,
                    connectingAddress = connectingAddress,
                    connectedDeviceName = connectedDeviceName,
                    connectionMessage = connectionMessage,
                    incomingRequesterName = incomingRequesterName,
                    approvalMessage = approvalMessage,
                    chatReady = chatReady,
                    messages = chatMessages,
                    chatNotice = chatNotice,
                    onSendMessage = ::sendChatMessage,
                    onSendFile = ::sendFile,
                    onAccept = ::approveIncoming,
                    onReject = ::rejectIncoming,
                    onFindNearby = ::startDiscovery,
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

    private fun startDiscovery() {
        if (!hasDiscoveryPermission()) {
            val permissions = if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            ActivityCompat.requestPermissions(this, permissions, REQUEST_DISCOVERY_PERMISSION)
            statusMessage = "Allow nearby-device permission, then tap Find people nearby again."
            return
        }

        val activeChannel = channel ?: run {
            statusMessage = "The nearby connection service is not ready. Please reopen NeaRer."
            return
        }

        scanning = true
        statusMessage = "Starting nearby search…"
        wifiManager.discoverPeers(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                statusMessage = "Search started. Looking for nearby Wi-Fi Direct devices…"
            }

            override fun onFailure(reason: Int) {
                scanning = false
                statusMessage = when (reason) {
                    WifiP2pManager.BUSY -> "Wi-Fi is busy. Wait a moment and try again."
                    WifiP2pManager.ERROR -> "Wi-Fi Direct reported an error. Check that Wi-Fi is on."
                    WifiP2pManager.P2P_UNSUPPORTED -> "This phone does not support Wi-Fi Direct."
                    else -> "Could not start discovery (code $reason). Check Wi-Fi and try again."
                }
            }
        })
    }

    private fun connectToDevice(device: WifiP2pDevice) {
        if (!hasDiscoveryPermission()) {
            statusMessage = "Nearby-device permission is needed before connecting."
            startDiscovery()
            return
        }
        val activeChannel = channel ?: return
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
        wifiManager.connect(activeChannel, config, object : WifiP2pManager.ActionListener {
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
                if (info.isGroupOwner) startApprovalServer() else connectToApprovalServer(info)
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

    private fun startApprovalServer() {
        io.execute {
            try {
                val server = ServerSocket(8988)
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
                runOnUiThread {
                    incomingRequesterName = name
                    approvalMessage = "Review this connection request"
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
                        approvalMessage = "Request declined or expired"
                        statusMessage = "The NeaRer connection request was declined or expired."
                    }
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
                val socket = Socket()
                peerSocket = socket
                socket.connect(InetSocketAddress(info.groupOwnerAddress, 8988), 12_000)
                socket.soTimeout = 65_000
                val writer = PrintWriter(socket.getOutputStream(), true)
                peerWriter = writer
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val localName = Build.MODEL.orEmpty().replace("|", " ").take(40).ifBlank { "Android phone" }
                writer.println("NEARER_HELLO|$localName")
                runOnUiThread { statusMessage = "NeaRer identity sent. Waiting for the other person to approve…" }
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
                    else -> runOnUiThread {
                        chatReady = false
                        approvalMessage = "Request declined or expired"
                        statusMessage = "The other person declined the request or it expired."
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

    private fun approveIncoming() {
        approvalDecision.offer(true)
        incomingRequesterName = null
        approvalMessage = "Approving connection…"
    }

    private fun rejectIncoming() {
        approvalDecision.offer(false)
        incomingRequesterName = null
        approvalMessage = "Declining connection…"
    }

    private fun closePeerTransport() {
        try { peerSocket?.close() } catch (_: Exception) { }
        try { serverSocket?.close() } catch (_: Exception) { }
        peerSocket = null
        peerWriter = null
        serverSocket = null
        chatReady = false
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
        val encoded = Base64.getEncoder().encodeToString(body.toByteArray(Charsets.UTF_8))
        if (!sendWireLine("MSG|$id|$encoded")) {
            chatNotice = "Message could not be sent. Check the nearby connection."
            return
        }
        chatMessages.add(ChatMessage(id, body, outgoing = true, delivery = "Sent"))
        chatNotice = "Sent directly • waiting for delivery confirmation"
    }

    private fun sendFile(uri: Uri) {
        if (!chatReady) return
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
                    val id = UUID.randomUUID().toString()
                    val encodedName = Base64.getEncoder().encodeToString(fileName.toByteArray(Charsets.UTF_8))
                    if (!sendWireLine("FILE_START|$id|$encodedName|$fileSize")) throw IllegalStateException("Connection unavailable")
                    runOnUiThread { chatNotice = "Sending $fileName…" }
                    val buffer = ByteArray(12 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        val chunk = Base64.getEncoder().encodeToString(buffer.copyOf(count))
                        if (!sendWireLine("FILE_CHUNK|$id|0|$chunk")) throw IllegalStateException("Connection lost during transfer")
                    }
                    if (!sendWireLine("FILE_END|$id")) throw IllegalStateException("Could not finish file transfer")
                    runOnUiThread { chatNotice = "Sending complete. Waiting for the other phone to confirm…" }
                }
            } catch (e: Exception) {
                runOnUiThread { chatNotice = "File transfer failed: ${e.message ?: "connection problem"}" }
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
                                if (index >= 0) chatMessages[index] = chatMessages[index].copy(delivery = "Delivered")
                            }
                        }
                        line.startsWith("FILE_START|") -> {
                            val parts = line.split("|", limit = 4)
                            if (parts.size == 4) {
                                val id = parts[1]
                                val name = try { String(Base64.getDecoder().decode(parts[2]), Charsets.UTF_8) } catch (_: Exception) { "shared-file" }
                                val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "shared-file" }
                                val expected = parts[3].toLongOrNull() ?: 0L
                                val folder = File(filesDir, "received").apply { mkdirs() }
                                val target = File(folder, "${System.currentTimeMillis()}-$safeName")
                                incomingTransfers[id] = IncomingTransfer(safeName, expected, target, FileOutputStream(target))
                            }
                        }
                        line.startsWith("FILE_CHUNK|") -> {
                            val parts = line.split("|", limit = 4)
                            if (parts.size == 4) {
                                val transfer = incomingTransfers[parts[1]]
                                if (transfer != null) {
                                    try {
                                        val bytes = Base64.getDecoder().decode(parts[3])
                                        transfer.output.write(bytes)
                                        transfer.receivedBytes += bytes.size
                                    } catch (_: Exception) {
                                        incomingTransfers.remove(parts[1])?.close()
                                    }
                                }
                            }
                        }
                        line.startsWith("FILE_END|") -> {
                            val id = line.substringAfter("FILE_END|")
                            val transfer = incomingTransfers.remove(id)
                            if (transfer != null) {
                                transfer.close()
                                runOnUiThread {
                                    receivedFiles.add(0, transfer.name)
                                    chatNotice = "Received file: ${transfer.name}. Saved inside NeaRer."
                                }
                                synchronized(writerLock) { peerWriter?.println("FILE_DONE|$id") }
                            }
                        }
                        line.startsWith("FILE_DONE|") -> {
                            runOnUiThread { chatNotice = "File delivered successfully to the nearby phone." }
                        }
                        line == "PING" -> synchronized(writerLock) { peerWriter?.println("PONG") }
                    }
                }
            } catch (_: Exception) {
                // Socket closure is handled below; no message body is logged.
            } finally {
                runOnUiThread {
                    if (chatReady) {
                        chatReady = false
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
    chatReady: Boolean,
    messages: List<ChatMessage>,
    chatNotice: String,
    receivedFiles: List<String>,
    onSendMessage: (String) -> Unit,
    onSendFile: (Uri) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onFindNearby: () -> Unit,
    onConnect: (WifiP2pDevice) -> Unit
) {
    var draft by remember { mutableStateOf("") }
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
                    Text("NeaRer", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Your people. Nearby. Offline.", color = Muted, fontSize = 13.sp)
                }
                Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(46.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text("N", color = Mint, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Panel) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("OFFLINE CONNECTION", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Bring nearby people closer.", color = White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Find nearby Android phones using Wi-Fi Direct. Discovery and the direct link work locally without mobile data. Both phones need Wi-Fi turned on.",
                        color = Muted, fontSize = 14.sp, lineHeight = 21.sp
                    )
                    Button(
                        onClick = onFindNearby,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !scanning,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) {
                        Text(if (scanning) "Searching nearby…" else "Find people nearby", fontWeight = FontWeight.Bold)
                    }
                    Text(status, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("CONNECTION STATUS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(connectedDeviceName?.let { "Connected to $it" } ?: connectionMessage, color = White, fontWeight = FontWeight.SemiBold)
                    Text(connectionMessage, color = Muted, fontSize = 11.sp)
                    Text(approvalMessage, color = if (chatReady) Mint else Muted, fontSize = 12.sp)
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
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                items(messages, key = { it.id }) { message ->
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
                        receivedFiles.take(3).forEach { fileName ->
                            Text("Received: $fileName", color = Mint, fontSize = 10.sp)
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
                        Button(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26364D), contentColor = White)
                        ) { Text("Share a photo or file") }
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
                        Text("Accept only if you recognise this person. Approval is required before NeaRer marks the link ready.", color = Muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onAccept, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) { Text("Accept") }
                            Button(onClick = onReject, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)) { Text("Reject") }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEARBY DEVICES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("${devices.size} found", color = Mint, fontSize = 11.sp)
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

private data class ChatMessage(
    val id: String,
    val body: String,
    val outgoing: Boolean,
    val delivery: String
)

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
