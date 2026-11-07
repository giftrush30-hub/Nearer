package com.nearer.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PhoneDocumentEntry(val name: String, val mime: String, val uri: Uri, val size: Long)

@Composable
fun PhoneFolderBrowser(treeUri: Uri, onSelectFiles: (List<Uri>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stack = remember(treeUri) { mutableListOf(DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))) }
    var folder by remember(treeUri) { mutableStateOf(stack.last()) }
    var rows by remember { mutableStateOf<List<PhoneDocumentEntry>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(folder) {
        loading = true
        val result = withContext(Dispatchers.IO) { queryPhoneFolder(context, treeUri, folder) }
        rows = result.first
        error = result.second
        loading = false
    }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0B54DC),
        title = { Column { Text("PHONE FILES", color = Color(0xFFFFD84D), fontWeight = FontWeight.Black, fontSize = 11.sp); Text("Browse folders before connecting", color = Color.White, fontWeight = FontWeight.Bold) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (stack.size > 1) { stack.removeAt(stack.lastIndex); folder = stack.last() } }, enabled = stack.size > 1, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD84D), contentColor = Color(0xFF0647C9))) { Text("‹ BACK") }
                    Spacer(Modifier.width(8.dp))
                    Text(rows.size.toString() + " items • " + selected.size + " selected", color = Color.White)
                }
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search this folder") })
                if (loading) Text("Reading this folder…", color = Color.White)
                if (error != null) Text(error ?: "Could not read folder", color = Color(0xFFFFD84D))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(rows.filter { it.name.contains(search, true) }, key = { it.uri.toString() }) { item ->
                        val directory = item.mime == DocumentsContract.Document.MIME_TYPE_DIR
                        Surface(modifier = Modifier.fillMaxWidth().clickable { if (directory) { stack.add(item.uri); folder = item.uri; search = "" } else selected = if (item.uri in selected) selected - item.uri else selected + item.uri }, color = if (item.uri in selected) Color(0xFF087FCE) else Color(0xFF0647C9), shape = RoundedCornerShape(11.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (directory) "📁" else "📄", fontSize = 20.sp)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    Text(if (directory) "FOLDER • OPEN" else "TAP TO PREPARE • " + readableSize(item.size), color = Color(0xFFFFD84D), fontSize = 8.sp)
                                }
                                if (!directory) Text(if (item.uri in selected) "✓" else "＋", color = Color(0xFFFFD84D), fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
                if (!loading && rows.isEmpty() && error == null) Text("This folder is empty.", color = Color.White)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose) { Text("CLOSE", color = Color.White) }
                Button(onClick = { onSelectFiles(selected.toList()); selected = emptySet(); onClose() }, enabled = selected.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD84D), contentColor = Color(0xFF0647C9))) { Text("PREPARE ${selected.size}", fontWeight = FontWeight.Black) }
            }
        }
    )
}

private fun queryPhoneFolder(context: Context, treeUri: Uri, folderUri: Uri): Pair<List<PhoneDocumentEntry>, String?> = try {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(folderUri))
    val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE)
    val result = mutableListOf<PhoneDocumentEntry>()
    context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getString(idCol) ?: continue
            val name = cursor.getString(nameCol) ?: "Unnamed file"
            val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
            val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
            result.add(PhoneDocumentEntry(name, mime, DocumentsContract.buildDocumentUriUsingTree(treeUri, id), size))
        }
    }
    Pair(result.sortedWith(compareBy<PhoneDocumentEntry> { it.mime != DocumentsContract.Document.MIME_TYPE_DIR }.thenBy { it.name.lowercase() }), null)
} catch (e: Exception) { Pair(emptyList(), e.message ?: "This folder could not be read.") }

private fun readableSize(size: Long): String = when { size <= 0L -> "Size unavailable"; size < 1024L -> size.toString() + " B"; size < 1048576L -> (size / 1024L).toString() + " KB"; else -> String.format("%.1f MB", size / 1048576.0) }