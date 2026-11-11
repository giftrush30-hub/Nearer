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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PremiumChatsScreen(
    meshRecipients: List<MeshRecipient>,
    selectedMeshRecipientId: String?,
    onSelectMeshRecipient: (String) -> Unit,
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
    var recipientMenuExpanded by remember { mutableStateOf(false) }
    val selectedRecipient = meshRecipients.firstOrNull { it.peerId == selectedMeshRecipientId }
    val selectedRecipientUnavailable = selectedMeshRecipientId != null && selectedRecipient == null
    val chatAvailable = selectedRecipient != null || (selectedMeshRecipientId == null && chatReady)
    val activeName = selectedRecipient?.displayName
        ?: selectedMeshRecipientId?.let { "NeaRer ${it.takeLast(4)}" }
        ?: connectedDeviceName
    val visibleMessages = if (selectedRecipient != null) {
        messages.filter { it.conversationId == selectedRecipient.peerId }
    } else messages
    val directConversation = selectedRecipient?.isDirect ?: (selectedMeshRecipientId == null && chatReady)

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
                        activeName ?: "Your nearby circle",
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
                            color = if (chatAvailable) Color(0xFF43D7A0) else Gold
                        ) {}
                        Text(
                            when {
                                selectedRecipientUnavailable -> "Selected recipient is currently unreachable"
                                selectedRecipient?.isDirect == false -> "Mesh route · ${selectedRecipient.hopCount} relay(s) away"
                                chatAvailable -> "Direct nearby · Private chat ready"
                                else -> "Not connected yet"
                            },
                            color = if (chatAvailable) Color(0xFF9DE8C9) else Muted,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
                if (!chatAvailable) {
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
                        Text(activeName ?: "Choose a nearby recipient", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(30.dp), color = if (chatAvailable) Color(0xFF174B43) else Color(0xFF3C3540)) {
                        Text(
                            if (selectedRecipientUnavailable) "ROUTE LOST"
                            else if (selectedRecipient?.isDirect == false) "MESH ROUTE"
                            else if (chatAvailable) "SECURE LINK" else "OFFLINE",
                            color = if (chatAvailable) Color(0xFF9DE8C9) else Gold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                if (meshRecipients.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("SEND TO A TRUSTED RECIPIENT", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                recipientMenuExpanded = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF172A43)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(activeName ?: "Choose a person", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        when {
                                            selectedRecipientUnavailable -> "Route unavailable · waiting for a path"
                                            selectedRecipient == null -> "Select someone you have securely paired with"
                                            selectedRecipient.isDirect -> "Direct nearby connection"
                                            else -> "Through ${selectedRecipient.hopCount} trusted relay(s)"
                                        },
                                        color = Muted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text("⌄", color = BlueAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        DropdownMenu(
                            expanded = recipientMenuExpanded,
                            onDismissRequest = { recipientMenuExpanded = false }
                        ) {
                            meshRecipients.forEach { recipient ->
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(recipient.displayName, color = White, fontWeight = FontWeight.Bold)
                                            Text(
                                                if (recipient.isDirect) "Direct nearby" else "Mesh · ${recipient.hopCount} relay(s)",
                                                color = Muted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectMeshRecipient(recipient.peerId)
                                        recipientMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (chatAvailable) {
                    if (visibleMessages.isEmpty()) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF142238)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("✦", color = Gold, fontSize = 25.sp)
                                Text("A fresh conversation", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (selectedRecipient?.isDirect == false)
                                        "Send a private message through the nearby mesh. Relays carry encrypted packets; only the selected recipient can read them."
                                    else
                                        "Say hello. Your messages travel over the secure nearby phone connection.",
                                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(230.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            visibleMessages.forEach { message ->
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
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (chatReady && directConversation) {
                            Button(
                                onClick = onChooseFiles,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263B59), contentColor = White)
                            ) { Text("＋  Share files", fontSize = 12.sp) }
                        } else {
                            Text("File sharing needs a direct nearby connection.", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        }
                        Text(
                            if (selectedRecipient?.isDirect == false) "Mesh chat" else if (usingNearbyConnections) "Nearby link" else "Wi-Fi Direct link",
                            color = Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }

                    if (chatReady && directConversation) incomingTransferName?.let { incomingName ->
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
                    if (chatReady && directConversation && outgoingTransferId != null) {
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

                    if (chatReady && directConversation) receivedFiles.take(3).forEach { fileItem ->
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

                    if (chatReady && directConversation && transferHistory.isNotEmpty()) {
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
                        Text(
                            when {
                                selectedRecipientUnavailable -> "Your selected person is not reachable right now. NeaRer will not send this message to a different phone."
                                meshRecipients.isNotEmpty() -> "Choose one of your trusted contacts above to send through an available mesh route."
                                else -> "Connect with someone nearby to start a private conversation. NeaRer uses local phone connections; mobile data isn't needed."
                            },
                            color = Muted, fontSize = 12.sp, lineHeight = 19.sp
                        )
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
