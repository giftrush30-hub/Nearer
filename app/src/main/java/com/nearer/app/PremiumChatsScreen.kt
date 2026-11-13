package com.nearer.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import java.util.Locale

@Composable
internal fun PremiumChatsScreen(
    meshRecipients: List<MeshRecipient>,
    selectedMeshRecipientId: String?,
    onSelectMeshRecipient: (String?) -> Unit,
    connectedPeerStableId: String?,
    chatReady: Boolean,
    connectedDeviceName: String?,
    connectionMessage: String,
    usingNearbyConnections: Boolean,
    messages: List<ChatMessage>,
    chatNotice: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSendMessage: (String, ChatMessage?) -> Unit,
    onDeleteMessage: (String) -> Boolean,
    onEditMessage: (String, String) -> Boolean,
    onReactToMessage: (String, String) -> Unit,
    onFindNearby: () -> Unit,
    onBackToInbox: () -> Unit,
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
    if (selectedMeshRecipientId == null && !chatReady) {
        PremiumChatsInboxScreen(
            meshRecipients = meshRecipients,
            messages = messages,
            onSelectConversation = onSelectMeshRecipient,
            onFindNearby = onFindNearby
        )
        return
    }

    var recipientMenuExpanded by remember { mutableStateOf(false) }
    var replyingTo by remember(selectedMeshRecipientId) { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember(selectedMeshRecipientId) { mutableStateOf<ChatMessage?>(null) }
    var expandedMessageActions by remember { mutableStateOf<String?>(null) }
    var reactingToMessageId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val context = LocalContext.current
    pendingDeleteMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMessage = null },
            title = { Text("Delete this message?") },
            text = { Text("This removes the message from this phone only. It will still remain on the other person's phone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (onDeleteMessage(target.id) && replyingTo?.id == target.id) replyingTo = null
                    if (editingMessage?.id == target.id) {
                        editingMessage = null
                        onDraftChange("")
                    }
                    pendingDeleteMessage = null
                }) { Text("Delete for me", color = Color(0xFFE6A0A0)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMessage = null }) { Text("Cancel") }
            }
        )
    }

    val selectedRecipient = meshRecipients.firstOrNull { it.peerId == selectedMeshRecipientId }
    val selectedRecipientUnavailable = selectedMeshRecipientId != null && selectedRecipient == null
    val chatAvailable = selectedRecipient != null || (selectedMeshRecipientId == null && chatReady)
    val activeName = selectedRecipient?.displayName
        ?: selectedMeshRecipientId?.let { "NeaRer ${it.takeLast(4)}" }
        ?: connectedDeviceName
    val visibleMessages = selectedMeshRecipientId?.let { conversationId ->
        messages.filter { it.conversationId == conversationId }
    } ?: messages
    // File transfer still uses the single active direct endpoint. Never offer it
    // when another direct peer is selected or when the selected peer is remote.
    val directConversation = chatReady && (
        selectedRecipient == null || selectedRecipient.peerId == connectedPeerStableId
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("‹  All chats", color = BlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBackToInbox).padding(vertical = 3.dp))
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
                            activeName?.take(1)?.uppercase() ?: "N",
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
                                selectedRecipient?.isDirect == false -> "Reaching them through ${selectedRecipient.hopCount} nearby helper(s)"
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
                            else if (selectedRecipient?.isDirect == false) "VIA OTHERS"
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
                                            else -> "Through ${selectedRecipient.hopCount} trusted nearby helper(s)"
                                        },
                                        color = Muted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text("⌄", color = BlueAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "Messages can travel through nearby helpers only for people you have already securely paired with.",
                            color = Muted,
                            fontSize = 9.sp,
                            lineHeight = 13.sp
                        )
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
                                                if (recipient.isDirect) "Direct nearby" else "Via ${recipient.hopCount} nearby helper(s)",
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

                if (chatAvailable || selectedRecipientUnavailable) {
                    if (visibleMessages.isEmpty()) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF142238)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("✦", color = Gold, fontSize = 25.sp)
                                Text(if (selectedRecipientUnavailable) "Conversation saved on this phone" else "A fresh conversation", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        selectedRecipientUnavailable -> "This person is offline. Your previous messages remain available here; reconnect when they are nearby to continue."
                                        selectedRecipient?.isDirect == false -> "Send a private message through trusted nearby helpers. They cannot read the message; only the person you chose can."
                                        else -> "Say hello. Your messages travel over the secure nearby phone connection."
                                    },
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
                                            if (!message.replyToPreview.isNullOrBlank()) {
                                                Surface(color = Color(0xFF10243A), shape = RoundedCornerShape(9.dp)) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(message.replyToSender ?: "Reply", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Text(message.replyToPreview.orEmpty(), color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                            Text(message.body, color = White, fontSize = 14.sp, lineHeight = 20.sp)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(if (message.outgoing) ChatDeliveryLabelPolicy.label(message.delivery) else "Received", color = if (message.outgoing) Color(0xFF9BD8FF) else Muted, fontSize = 9.sp)
                                                    if (message.editVersion > 0) Text("Edited", color = Gold, fontSize = 9.sp)
                                                }
                                                Box {
                                                    Text("More", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.clickable { expandedMessageActions = message.id }.padding(start = 12.dp))
                                                    DropdownMenu(
                                                        expanded = expandedMessageActions == message.id,
                                                        onDismissRequest = { expandedMessageActions = null }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("React") },
                                                            onClick = {
                                                                reactingToMessageId = if (reactingToMessageId == message.id) null else message.id
                                                                expandedMessageActions = null
                                                            }
                                                        )
                                                        if (message.outgoing) {
                                                            DropdownMenuItem(
                                                                text = { Text("Edit") },
                                                                onClick = {
                                                                    editingMessage = message
                                                                    replyingTo = null
                                                                    onDraftChange(message.body)
                                                                    expandedMessageActions = null
                                                                }
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text("Reply") },
                                                            onClick = { replyingTo = message; expandedMessageActions = null }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Copy message") },
                                                            onClick = {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                clipboard.setPrimaryClip(ClipData.newPlainText("NeaRer message", message.body))
                                                                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                                                                expandedMessageActions = null
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Delete for me") },
                                                            onClick = {
                                                                expandedMessageActions = null
                                                                pendingDeleteMessage = message
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (message.reactions.isNotEmpty()) {
                                        val counts = message.reactions.values.groupingBy { it }.eachCount()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(0.88f),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            counts.forEach { (emoji, count) ->
                                                Surface(
                                                    color = Color(0xFF263B59),
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.clickable {
                                                        onReactToMessage(message.id, emoji)
                                                    }
                                                ) {
                                                    Text("$emoji $count", color = White, fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (reactingToMessageId == message.id) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(0.88f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                                Surface(
                                                    color = Color(0xFF263B59),
                                                    shape = CircleShape,
                                                    modifier = Modifier.clickable {
                                                        onReactToMessage(message.id, emoji)
                                                        reactingToMessageId = null
                                                    }
                                                ) {
                                                    Text(emoji, fontSize = 18.sp, modifier = Modifier.padding(7.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (chatNotice.isNotBlank()) {
                        Text(chatNotice, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }

                    if (chatAvailable) {
                        editingMessage?.let { target ->
                            Surface(color = Color(0xFF142238), shape = RoundedCornerShape(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Editing your message", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(target.body, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("Cancel", color = BlueAccent, fontSize = 11.sp,
                                        modifier = Modifier.clickable { editingMessage = null; onDraftChange("") }.padding(6.dp))
                                }
                            }
                        }
                        replyingTo?.let { target ->
                            Surface(color = Color(0xFF142238), shape = RoundedCornerShape(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Replying to " + if (target.outgoing) "your message" else target.conversationName, color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(target.body, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("Cancel", color = BlueAccent, fontSize = 11.sp, modifier = Modifier.clickable { replyingTo = null }.padding(6.dp))
                                }
                            }
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
                                onClick = {
                                    if (draft.isNotBlank()) {
                                        val target = editingMessage
                                        if (target != null) {
                                            if (onEditMessage(target.id, draft)) {
                                                editingMessage = null
                                                onDraftChange("")
                                            }
                                        } else {
                                            onSendMessage(draft, replyingTo)
                                            replyingTo = null
                                        }
                                    }
                                },
                                enabled = draft.isNotBlank() && (editingMessage == null || draft.trim() != editingMessage?.body),
                                modifier = Modifier.height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                            ) { Text(if (editingMessage != null) "Save" else "Send", fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        Surface(color = Color(0xFF142238), shape = RoundedCornerShape(14.dp)) {
                            Text("Offline · reconnect to send a new message", color = Muted, fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth().padding(13.dp))
                        }
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
                            if (selectedRecipient?.isDirect == false) "Via nearby helpers" else if (usingNearbyConnections) "Nearby link" else "Wi-Fi Direct link",
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
                                meshRecipients.isNotEmpty() -> "Choose one of your trusted contacts above to send through nearby helpers."
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

@Composable
private fun PremiumChatsInboxScreen(
    meshRecipients: List<MeshRecipient>,
    messages: List<ChatMessage>,
    onSelectConversation: (String) -> Unit,
    onFindNearby: () -> Unit
) {
    val latestMessages = messages
        .filter { it.conversationId.isNotBlank() }
        .groupBy { it.conversationId }
        .mapValues { (_, conversation) -> conversation.maxByOrNull { it.timestamp }!! }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("nearer_chat_inbox", android.content.Context.MODE_PRIVATE) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var pinnedIds by remember { mutableStateOf(preferences.getStringSet("pinned_conversations", emptySet()).orEmpty()) }
    var archivedIds by remember { mutableStateOf(preferences.getStringSet("archived_conversations", emptySet()).orEmpty()) }
    var expandedConversationActions by remember { mutableStateOf<String?>(null) }

    val conversationIds = ChatInboxPolicy.conversationIds(
        messages = messages,
        recipients = meshRecipients,
        query = searchQuery,
        filter = if (selectedFilter == "Archived" || selectedFilter == "Unread") "All" else selectedFilter,
        pinnedIds = pinnedIds
    ).filter { id ->
        val isArchived = id in archivedIds
        val unread = ChatInboxStatePolicy.unreadCount(
            messages.filter { it.conversationId == id },
            preferences.getLong("read_through_$id", 0L)
        )
        when (selectedFilter) {
            "Archived" -> isArchived
            "Unread" -> !isArchived && unread > 0
            else -> !isArchived
        }
    }

    fun togglePinned(id: String) {
        pinnedIds = if (id in pinnedIds) pinnedIds - id else pinnedIds + id
        preferences.edit().putStringSet("pinned_conversations", pinnedIds).apply()
    }

    fun setArchived(id: String, archived: Boolean) {
        archivedIds = if (archived) archivedIds + id else archivedIds - id
        preferences.edit().putStringSet("archived_conversations", archivedIds).apply()
        expandedConversationActions = null
    }

    fun markConversationRead(id: String) {
        val newestTimestamp = messages.filter { it.conversationId == id }.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        preferences.edit().putLong("read_through_$id", newestTimestamp).apply()
    }

    fun markConversationUnread(id: String) {
        preferences.edit().putLong("read_through_$id", 0L).apply()
        expandedConversationActions = null
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("YOUR CONVERSATIONS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Chats", color = White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
            Text("Your messages and trusted nearby people, together.", color = Muted, fontSize = 13.sp)
        }

        Button(
            onClick = onFindNearby,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
        ) {
            Text("＋  Find people / Start a chat", fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search chats and messages") },
            placeholder = { Text("Name, NeaRer ID or message") },
            shape = RoundedCornerShape(15.dp)
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All", "Unread", "Nearby", "Via others", "Offline", "Archived").forEach { filter ->
                val selected = selectedFilter == filter
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { selectedFilter = filter },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) BlueAccent else Color(0xFF20334D)
                ) {
                    Text(
                        filter,
                        color = if (selected) Midnight else Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                    )
                }
            }
        }

        if (conversationIds.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = Color(0xFF1D3C61), shape = CircleShape, modifier = Modifier.size(58.dp)) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Text("✦", color = Gold, fontSize = 27.sp)
                        }
                    }
                    Text(
                        if (selectedFilter == "Archived") "No archived chats" else if (selectedFilter == "Unread") "You're all caught up" else if (searchQuery.isNotBlank() || selectedFilter != "All") "No matching conversations" else "Your conversations start here",
                        color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (selectedFilter == "Archived")
                            "Chats you archive will be kept here, separate from your main inbox."
                        else if (selectedFilter == "Unread")
                            "New messages will appear here until you open their conversation."
                        else if (searchQuery.isNotBlank() || selectedFilter != "All")
                            "Try another name or message, or clear the current search and filter."
                        else
                            "Find someone nearby or add a trusted NeaRer ID. Your conversations will stay here when you disconnect.",
                        color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    if (searchQuery.isNotBlank() || selectedFilter != "All") {
                        Text(
                            "Clear search and filters",
                            color = BlueAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                searchQuery = ""
                                selectedFilter = "All"
                            }.padding(6.dp)
                        )
                    }
                }
            }
        } else {
            conversationIds.forEach { id ->
                val recipient = meshRecipients.firstOrNull { it.peerId == id }
                val latest = latestMessages[id]
                val displayName = recipient?.displayName
                    ?: latest?.conversationName?.takeIf { it.isNotBlank() }
                    ?: "NeaRer ${id.takeLast(4)}"
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        markConversationRead(id)
                        onSelectConversation(id)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Surface(color = Color(0xFF2455A5), shape = CircleShape, modifier = Modifier.size(46.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(displayName.take(1).uppercase(), color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(displayName, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                latest?.body?.take(62) ?: if (recipient?.isDirect == true) "Nearby and ready to chat" else if (recipient != null) "Reachable through nearby helpers" else "Previous conversation · reconnect when nearby",
                                color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                latest?.timestamp?.let { java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it)) }.orEmpty(),
                                color = Muted,
                                fontSize = 9.sp
                            )
                            Text(
                                when {
                                    recipient?.isDirect == true -> "ONLINE"
                                    recipient != null -> "VIA OTHERS"
                                    else -> "OFFLINE"
                                },
                                color = if (recipient != null) Color(0xFF8FE0B9) else Muted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val unreadCount = ChatInboxStatePolicy.unreadCount(
                                messages.filter { it.conversationId == id },
                                preferences.getLong("read_through_$id", 0L)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (unreadCount > 0) {
                                    Surface(color = Gold, shape = CircleShape, modifier = Modifier.size(21.dp)) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text(if (unreadCount > 99) "99+" else unreadCount.toString(), color = Midnight, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                                Text(
                                    if (id in pinnedIds) "★" else "☆",
                                    color = if (id in pinnedIds) Gold else Muted,
                                    fontSize = 17.sp,
                                    modifier = Modifier.clip(CircleShape).clickable { togglePinned(id) }.padding(5.dp)
                                )
                                Box {
                                    Text("⋮", color = BlueAccent, fontSize = 20.sp,
                                        modifier = Modifier.clickable { expandedConversationActions = id }.padding(horizontal = 5.dp))
                                    DropdownMenu(
                                        expanded = expandedConversationActions == id,
                                        onDismissRequest = { expandedConversationActions = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (id in archivedIds) "Unarchive chat" else "Archive chat") },
                                            onClick = { setArchived(id, id !in archivedIds) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mark as unread") },
                                            onClick = { markConversationUnread(id) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (id in pinnedIds) "Unpin chat" else "Pin chat") },
                                            onClick = { togglePinned(id); expandedConversationActions = null }
                                        )
                                    }
                                }
                            }
                            Text("›", color = BlueAccent, fontSize = 21.sp)
                        }
                    }
                }
            }
        }
        Text("NeaRer reconnects to people you trust when they are nearby and your phone is ready.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}
