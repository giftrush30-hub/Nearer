package com.nearer.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PremiumChatsScreen(
    chatReady: Boolean,
    connectedDeviceName: String?,
    connectionMessage: String,
    usingNearbyConnections: Boolean,
    messages: List<ChatMessage>,
    chatNotice: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onFindNearby: () -> Unit,
    onChooseFiles: () -> Unit,
    receivedFiles: List<ReceivedFile>,
    onOpenFile: (ReceivedFile) -> Unit,
    transferHistory: List<NearbyTransferRecord>,
    outgoingTransferId: String?,
    outgoingProgress: Float,
    incomingTransferName: String?,
    incomingProgress: Float,
    onCancelTransfer: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("YOUR CONVERSATIONS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Chats", color = White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
            Text("Private conversations, closer than ever.", color = Muted, fontSize = 13.sp)
        }

        Surface(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF172B49)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = Color(0xFF2455A5)
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text(
                            connectedDeviceName?.take(1)?.uppercase() ?: "N",
                            color = White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        connectedDeviceName ?: "Your nearby circle",
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier.size(7.dp),
                            shape = CircleShape,
                            color = if (chatReady) Color(0xFF43D7A0) else Gold
                        ) {}
                        Text(
                            if (chatReady) "Connected · Private chat ready" else "Not connected yet",
                            color = if (chatReady) Color(0xFF9DE8C9) else Muted,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
                if (!chatReady) {
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onFindNearby),
                        shape = RoundedCornerShape(12.dp),
                        color = BlueAccent
                    ) {
                        Text("Find", color = Midnight, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                } else {
                    Text("●", color = Color(0xFF43D7A0), fontSize = 12.sp)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Panel
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("PRIVATE CHAT", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text(connectedDeviceName ?: "Start a nearby conversation", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(30.dp), color = if (chatReady) Color(0xFF174B43) else Color(0xFF3C3540)) {
                        Text(
                            if (chatReady) "SECURE LINK" else "OFFLINE",
                            color = if (chatReady) Color(0xFF9DE8C9) else Gold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                if (chatReady) {
                    if (messages.isEmpty()) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF142238)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("✦", color = Gold, fontSize = 25.sp)
                                Text("A fresh conversation", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Say hello. Your messages travel over the direct nearby phone connection.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(230.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            messages.forEach { message ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start
                                ) {
                                    Surface(
                                        color = if (message.outgoing) Color(0xFF174B72) else Color(0xFF22334B),
                                        shape = RoundedCornerShape(
                                            topStart = 17.dp,
                                            topEnd = 17.dp,
                                            bottomStart = if (message.outgoing) 17.dp else 5.dp,
                                            bottomEnd = if (message.outgoing) 5.dp else 17.dp
                                        ),
                                        modifier = Modifier.fillMaxWidth(0.88f)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(message.body, color = White, fontSize = 14.sp, lineHeight = 20.sp)
                                            Text(if (message.outgoing) message.delivery else "Received", color = if (message.outgoing) Color(0xFF9BD8FF) else Muted, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (chatNotice.isNotBlank()) {
                        Text(chatNotice, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Write a message…", color = Muted, fontSize = 13.sp) },
                            shape = RoundedCornerShape(18.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = { if (draft.isNotBlank()) onSendMessage(draft) },
                            enabled = draft.isNotBlank(),
                            modifier = Modifier.height(54.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                        ) { Text("Send", fontWeight = FontWeight.Bold) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Button(
                            onClick = onChooseFiles,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263B59), contentColor = White)
                        ) { Text("＋  Share files", fontSize = 12.sp) }
                        Text(
                            if (usingNearbyConnections) "Nearby link" else "Wi-Fi Direct link",
                            color = Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }

                    incomingTransferName?.let { incomingName ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Receiving $incomingName", color = White, fontSize = 11.sp)
                            LinearProgressIndicator(
                                progress = { incomingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = BlueAccent,
                                trackColor = Color(0xFF31435B)
                            )
                        }
                    }
                    if (outgoingTransferId != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Sending file…", color = White, fontSize = 11.sp)
                            LinearProgressIndicator(
                                progress = { outgoingProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Gold,
                                trackColor = Color(0xFF31435B)
                            )
                            Button(
                                onClick = onCancelTransfer,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)
                            ) { Text("Cancel transfer") }
                        }
                    }

                    receivedFiles.take(3).forEach { fileItem ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable { onOpenFile(fileItem) },
                            shape = RoundedCornerShape(13.dp),
                            color = Color(0xFF223650)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("↓", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(fileItem.displayName, color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 9.dp))
                                Text("Open", color = BlueAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (transferHistory.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("RECENT FILE ACTIVITY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            transferHistory.take(3).forEach { record ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (record.direction == "Sent") "↑" else "↓", color = BlueAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Column(modifier = Modifier.weight(1f).padding(start = 9.dp)) {
                                        Text(record.fileName, color = White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatTransferBytes(record.bytes)} · ${record.status}", color = Muted, fontSize = 9.sp)
                                    }
                                    Text(record.timeLabel, color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Connect with someone nearby to start a private conversation. NeaRer uses supported local phone connections; mobile data isn't needed for this nearby chat.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                        Button(
                            onClick = onFindNearby,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                        ) { Text("Find people nearby", fontWeight = FontWeight.Bold) }
                        Text(connectionMessage, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
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

private data class ReceivedFile(val displayName: String, val file: File)

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

private fun deviceStatus(status: Int): String = when (status) {
    WifiP2pDevice.AVAILABLE -> "Available"
    WifiP2pDevice.INVITED -> "Invitation pending"
    WifiP2pDevice.CONNECTED -> "Connected"
    WifiP2pDevice.FAILED -> "Connection failed"
    WifiP2pDevice.UNAVAILABLE -> "Temporarily unavailable"
    else -> "Nearby Wi-Fi Direct device"
}
