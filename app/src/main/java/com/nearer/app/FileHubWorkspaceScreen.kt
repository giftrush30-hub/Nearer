package com.nearer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile

private data class HubFileAction(val file: DocumentFile, val action: String)

@Composable
internal fun FileHubWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("nearer_file_hub", Context.MODE_PRIVATE) }
    val stack = remember { mutableStateListOf<DocumentFile>() }
    var query by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<HubFileAction?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showCreateFolder by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    fun openTree(uri: Uri) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return
        stack.clear()
        stack.add(root)
        prefs.edit().putString("tree_uri", uri.toString()).apply()
        query = ""
        refreshKey++
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("tree_uri", null)?.let(Uri::parse)
        if (saved != null && context.contentResolver.persistedUriPermissions.any { it.uri == saved && it.isReadPermission }) {
            openTree(saved)
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            openTree(uri)
        }
    }

    val current = stack.lastOrNull()
    val entries = remember(current, query, refreshKey) {
        current?.listFiles()?.sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase().orEmpty() })
            ?.filter { query.isBlank() || it.name.orEmpty().contains(query.trim(), ignoreCase = true) }
            .orEmpty()
    }

    fun openFile(file: DocumentFile) {
        runCatching {
            val mime = file.type ?: "*/*"
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    fun shareFile(file: DocumentFile) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).setType(file.type ?: "*/*").putExtra(Intent.EXTRA_STREAM, file.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Share with NeaRer"))
        }
    }

    Column(
        Modifier.fillMaxSize().background(Midnight).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  MORE", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("LOCAL FILE DESK", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("NEARER FILE HUB", color = White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your files. Your folders. One organised place.", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Browse a folder you choose, open files with compatible apps, create folders, rename items and share files. Access stays limited to the folder you select.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Button(onClick = { treePicker.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                    Text(if (current == null) "＋  Choose a folder to browse" else "CHANGE ROOT FOLDER", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (current != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(current.name ?: "Selected folder", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${entries.size} ITEMS · USER-GRANTED ACCESS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text("＋ FOLDER", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { newFolderName = ""; showCreateFolder = true }.padding(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (stack.size > 1) {
                    Text("‹ UP", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                        stack.removeAt(stack.lastIndex); query = ""; refreshKey++
                    }.padding(vertical = 9.dp, horizontal = 6.dp))
                }
                TextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search this folder", color = Muted) },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                        focusedTextColor = White, unfocusedTextColor = White, cursorColor = Mint,
                        focusedIndicatorColor = Mint, unfocusedIndicatorColor = Panel))
            }
            if (entries.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (query.isBlank()) "This folder is empty" else "No matching files", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Choose another folder or create a folder to organise your files.", color = Muted, fontSize = 11.sp)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(entries, key = { it.uri.toString() }) { file ->
                        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Surface(color = Midnight, shape = RoundedCornerShape(10.dp)) {
                                    Text(if (file.isDirectory) "▣" else "▤", color = Mint, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                                }
                                Column(Modifier.weight(1f).clickable {
                                    if (file.isDirectory) { stack.add(file); query = ""; refreshKey++ } else openFile(file)
                                }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(file.name ?: "Unnamed item", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(if (file.isDirectory) "FOLDER" else "${file.type ?: "FILE"} · ${formatHubBytes(file.length())}", color = Muted, fontSize = 9.sp)
                                }
                                Text("↗", color = Mint, fontSize = 16.sp, modifier = Modifier.clickable { if (file.isDirectory) { stack.add(file); query = ""; refreshKey++ } else openFile(file) }.padding(4.dp))
                                Text("⋯", color = Gold, fontSize = 19.sp, modifier = Modifier.clickable {
                                    pendingAction = HubFileAction(file, "menu")
                                }.padding(4.dp))
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Start with a folder", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Select internal storage, an SD card or another folder provider. NeaRer won't scan storage without your permission.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Text("Share Hub remains your nearby transfer tool. File Hub is for browsing and organising local files.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }

    if (showCreateFolder) {
        AlertDialog(onDismissRequest = { showCreateFolder = false },
            title = { Text("Create folder", color = White) },
            text = { TextField(value = newFolderName, onValueChange = { newFolderName = it }, singleLine = true, placeholder = { Text("Folder name") }) },
            confirmButton = { TextButton(onClick = {
                val name = newFolderName.trim()
                if (name.isNotEmpty()) current?.createDirectory(name)
                showCreateFolder = false; refreshKey++
            }) { Text("CREATE", color = Mint) } },
            dismissButton = { TextButton(onClick = { showCreateFolder = false }) { Text("CANCEL", color = Muted) } },
            containerColor = Panel
        )
    }

    if (pendingAction != null) {
        val file = pendingAction!!.file
        AlertDialog(onDismissRequest = { pendingAction = null },
            title = { Text(file.name ?: "File options", color = White) },
            text = { Text(if (file.isDirectory) "Folder · last changed ${file.lastModified()}" else "${file.type ?: "File"} · ${formatHubBytes(file.length())} · modified ${file.lastModified()}", color = Muted) },
            confirmButton = {
                Row {
                    TextButton(onClick = { shareFile(file); pendingAction = null }) { Text("SHARE", color = Mint) }
                    TextButton(onClick = { renameValue = file.name.orEmpty(); pendingAction = HubFileAction(file, "rename") }) { Text("RENAME", color = Gold) }
                    TextButton(onClick = { file.delete(); pendingAction = null; refreshKey++ }) { Text("DELETE", color = Gold) }
                }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("CLOSE", color = Muted) } },
            containerColor = Panel
        )
    }

    if (pendingAction?.action == "rename") {
        val file = pendingAction!!.file
        AlertDialog(onDismissRequest = { pendingAction = null },
            title = { Text("Rename item", color = White) },
            text = { TextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val name = renameValue.trim()
                if (name.isNotEmpty()) file.renameTo(name)
                pendingAction = null; refreshKey++
            }) { Text("SAVE", color = Mint) } },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("CANCEL", color = Muted) } },
            containerColor = Panel
        )
    }
}

private fun formatHubBytes(bytes: Long): String = when {
    bytes <= 0L -> "Size unavailable"
    bytes < 1024L -> "${bytes} B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
