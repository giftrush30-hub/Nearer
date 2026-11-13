package com.nearer.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocalPhoneContact(val id: String, val name: String, val number: String)

@Composable
internal fun PeopleScreen(
    meshRecipients: List<MeshRecipient>,
    onSelectMeshRecipient: (String) -> Unit,
    onOpenChat: () -> Unit,
    onFindNearby: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts = remember { mutableStateListOf<LocalPhoneContact>() }
    val savedIds = remember { mutableStateListOf<String>() }
    var idDraft by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var loadingContacts by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("Your address book stays on this phone. NeaRer IDs can be linked when someone shares theirs with you.") }
    val hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun loadContacts() {
        loadingContacts = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                val result = LinkedHashMap<String, LocalPhoneContact>()
                runCatching {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        null, null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (cursor.moveToNext()) {
                            if (idCol < 0 || nameCol < 0 || numberCol < 0) continue
                            val id = cursor.getString(idCol) ?: continue
                            val name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: "Unnamed contact"
                            val number = cursor.getString(numberCol).orEmpty()
                            if (result[id] == null) result[id] = LocalPhoneContact(id, name, number)
                        }
                    }
                }
                result.values.toList()
            }
            contacts.clear()
            contacts.addAll(found)
            loadingContacts = false
            notice = if (found.isEmpty()) "No phone contacts were found. You can still connect using a NeaRer ID or Nearby." else "${found.size} phone contacts loaded locally."
        }
    }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadContacts() else notice = "Contacts access was declined. You can still use NeaRer IDs and Nearby discovery."
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("nearer_people", Context.MODE_PRIVATE)
        savedIds.addAll(prefs.getStringSet("saved_nearer_ids", emptySet()).orEmpty().sorted())
        if (hasContactsPermission) loadContacts()
    }

    fun saveId() {
        val normalized = NeaRerIdentity.normalize(idDraft.trim().removePrefix("NR-").removePrefix("nr-"))
        if (normalized == null) {
            notice = "That ID format is not recognised. Check the ID and try again."
            return
        }
        if (normalized.isNotBlank()) {
            if (normalized !in savedIds) savedIds.add(normalized)
            context.getSharedPreferences("nearer_people", Context.MODE_PRIVATE)
                .edit().putStringSet("saved_nearer_ids", savedIds.toSet()).apply()
            idDraft = ""
            notice = "NeaRer ID saved. NeaRer will recognise it when that identity is discovered nearby."
        }
    }

    val filteredContacts = contacts.filter {
        search.isBlank() || it.name.contains(search, true) || it.number.contains(search, true)
    }.take(100)

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("YOUR PEOPLE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Find your people", color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Connect by NeaRer ID, trusted nearby devices or your phone contacts.", color = Muted, fontSize = 13.sp)
        }

        Surface(color = Color(0xFF172B49), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("FIND SOMEONE NEARBY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Discover NeaRer users around you. Previously trusted devices can reconnect when they are nearby and available.", color = White, fontSize = 13.sp, lineHeight = 19.sp)
                Button(onClick = onFindNearby, modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight),
                    shape = RoundedCornerShape(14.dp)) {
                    Text("Find people nearby", fontWeight = FontWeight.Bold)
                }
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ADD BY NEARER ID", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = idDraft,
                    onValueChange = { idDraft = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(15) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("NeaRer ID") },
                    placeholder = { Text("NR-XXXXXXXXXXXX") },
                    shape = RoundedCornerShape(14.dp)
                )
                Button(onClick = ::saveId, enabled = idDraft.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight),
                    shape = RoundedCornerShape(14.dp)) { Text("Save NeaRer ID", fontWeight = FontWeight.Bold) }
                Text("Saving an ID does not create an internet lookup or force a connection. It lets you keep the identity ready for nearby discovery.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }

        if (savedIds.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SAVED NEARER IDS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                savedIds.toList().forEach { id ->
                    val recipient = meshRecipients.firstOrNull { it.peerId.equals(id, true) || NeaRerIdentity.normalize(it.peerId) == id }
                    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Surface(color = Color(0xFF2455A5), shape = CircleShape, modifier = Modifier.size(38.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(id.takeLast(2), color = White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("NeaRer ${id.takeLast(4)}", color = White, fontWeight = FontWeight.Bold)
                                Text(if (recipient != null) "Trusted · available nearby" else "Saved · waiting to be discovered", color = Muted, fontSize = 10.sp)
                            }
                            if (recipient != null) {
                                Text("Chat", color = BlueAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onSelectMeshRecipient(recipient.peerId); onOpenChat() }.padding(8.dp))
                            } else {
                                Text("×", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable {
                                    savedIds.remove(id)
                                    context.getSharedPreferences("nearer_people", Context.MODE_PRIVATE).edit()
                                        .putStringSet("saved_nearer_ids", savedIds.toSet()).apply()
                                }.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PHONE CONTACTS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Your address book", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(if (hasContactsPermission) "Refresh" else "Allow access", color = BlueAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        if (hasContactsPermission) loadContacts() else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }.padding(8.dp))
            }
            if (hasContactsPermission && contacts.isNotEmpty()) {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Search phone contacts") }, shape = RoundedCornerShape(14.dp))
                filteredContacts.forEach { contact ->
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = Color(0xFF263F61), shape = CircleShape, modifier = Modifier.size(38.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(contact.name.take(1).uppercase(), color = White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(contact.name, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(contact.number, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("ID needed", color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            } else if (!hasContactsPermission) {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Allow access to browse your phone contacts. NeaRer will not upload your address book.", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp))
                }
            } else {
                Text(if (loadingContacts) "Loading contacts…" else "No contacts to show.", color = Muted, fontSize = 12.sp)
            }
        }

        if (meshRecipients.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TRUSTED NEARBY PEOPLE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                meshRecipients.forEach { person ->
                    Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()
                        .clickable { onSelectMeshRecipient(person.peerId); onOpenChat() }) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = Color(0xFF2455A5), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(person.displayName.take(1).uppercase(), color = White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(person.displayName, color = White, fontWeight = FontWeight.Bold)
                                Text(if (person.isDirect) "Direct connection" else "Mesh · ${person.hopCount} relay(s)", color = Muted, fontSize = 10.sp)
                            }
                            Text("Message  ›", color = BlueAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text(notice, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(8.dp))
    }
}
