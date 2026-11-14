package com.nearer.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val MAX_PHONE_FOLDER_ITEMS = 2_000

private data class PhoneDocumentEntry(
    val name: String,
    val mime: String,
    val uri: Uri,
    val size: Long
)

/**
 * A read-only browser for a folder the user explicitly selected through Android's
 * Storage Access Framework. Only individually selected document URIs are returned.
 */
@Composable
fun PhoneFolderBrowser(
    treeUri: Uri,
    onSelectFiles: (List<Uri>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val rootUri = remember(treeUri) {
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
    }
    val folderStack = remember(treeUri) { mutableStateListOf(rootUri) }
    val folderUri = folderStack.last()
    var rows by remember(treeUri) { mutableStateOf<List<PhoneDocumentEntry>>(emptyList()) }
    var search by remember(treeUri) { mutableStateOf("") }
    var selected by remember(treeUri) { mutableStateOf<Set<Uri>>(emptySet()) }
    var loading by remember(treeUri) { mutableStateOf(true) }
    var error by remember(treeUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(treeUri, folderUri) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            queryPhoneFolder(context, treeUri, folderUri)
        }
        rows = result.first
        error = result.second
        loading = false
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = NeaRerPalette.Panel,
        title = {
            Column {
                Text(
                    "PHONE FILES",
                    color = NeaRerPalette.Gold,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
                Text(
                    "Choose files to send",
                    color = NeaRerPalette.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (folderStack.size > 1) {
                                folderStack.removeAt(folderStack.lastIndex)
                                search = ""
                            }
                        },
                        enabled = folderStack.size > 1,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeaRerPalette.Gold,
                            contentColor = NeaRerPalette.Midnight
                        )
                    ) { Text("‹ BACK") }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "${rows.size} items • ${selected.size} selected",
                        color = NeaRerPalette.TextPrimary
                    )
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search this folder") }
                )
                if (loading) {
                    Text("Reading this folder…", color = NeaRerPalette.TextMuted)
                }
                error?.let {
                    Text(it, color = NeaRerPalette.Gold, fontSize = 11.sp)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(
                        rows.filter { it.name.contains(search, ignoreCase = true) },
                        key = { it.uri.toString() }
                    ) { item ->
                        val isDirectory = item.mime == DocumentsContract.Document.MIME_TYPE_DIR
                        val isSelected = item.uri in selected
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (isDirectory) {
                                    folderStack.add(item.uri)
                                    search = ""
                                } else {
                                    selected = if (isSelected) selected - item.uri else selected + item.uri
                                }
                            },
                            color = if (isSelected) NeaRerPalette.RaisedPanel else NeaRerPalette.Midnight,
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isDirectory &&
                                    (item.mime.startsWith("image/") || item.mime.startsWith("video/"))
                                ) {
                                    PhoneMediaThumbnail(item.uri, item.mime)
                                } else {
                                    Text(
                                        if (isDirectory) "DIR" else documentGlyph(item.mime),
                                        color = NeaRerPalette.Mint,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Column(
                                    Modifier.weight(1f).padding(start = 10.dp)
                                ) {
                                    Text(
                                        item.name,
                                        color = NeaRerPalette.TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        if (isDirectory) "FOLDER • OPEN"
                                        else "TAP TO SELECT • ${readableSize(item.size)}",
                                        color = NeaRerPalette.TextMuted,
                                        fontSize = 8.sp
                                    )
                                }
                                if (!isDirectory) {
                                    Text(
                                        if (isSelected) "✓" else "＋",
                                        color = NeaRerPalette.Mint,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
                if (!loading && rows.isEmpty() && error == null) {
                    Text("This folder is empty.", color = NeaRerPalette.TextMuted)
                }
                if (!loading && error == null && selected.isNotEmpty()) {
                    Text(
                        "Selected files remain selected as you browse other folders.",
                        color = NeaRerPalette.TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose) {
                    Text("CLOSE", color = NeaRerPalette.TextPrimary)
                }
                Button(
                    onClick = {
                        val chosen = selected.toList()
                        if (chosen.isNotEmpty()) onSelectFiles(chosen)
                        selected = emptySet()
                        onClose()
                    },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeaRerPalette.Gold,
                        contentColor = NeaRerPalette.Midnight
                    )
                ) {
                    Text("PREPARE ${selected.size}", fontWeight = FontWeight.Black)
                }
            }
        }
    )
}

@Composable
private fun PhoneMediaThumbnail(uri: Uri, mime: String) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    context.contentResolver.loadThumbnail(
                        uri,
                        Size(192, 192),
                        CancellationSignal()
                    )
                }.getOrNull()
            } else null
        }
    }
    Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(9.dp),
        color = NeaRerPalette.RaisedPanel
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = if (mime.startsWith("video/")) "Video thumbnail" else "Image thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (mime.startsWith("video/")) "▶" else "▧",
                    color = NeaRerPalette.Mint,
                    fontSize = 23.sp
                )
            }
        }
    }
}

private fun queryPhoneFolder(
    context: Context,
    treeUri: Uri,
    folderUri: Uri
): Pair<List<PhoneDocumentEntry>, String?> = try {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri,
        DocumentsContract.getDocumentId(folderUri)
    )
    val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE
    )
    val result = mutableListOf<PhoneDocumentEntry>()
    var truncated = false
    val cursor = context.contentResolver.query(children, columns, null, null, null)
        ?: throw IllegalStateException("This storage provider could not open the selected folder.")
    cursor.use {
        val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
            throw IllegalStateException("This storage provider did not return the required file details.")
        }
        while (cursor.moveToNext()) {
            if (result.size >= MAX_PHONE_FOLDER_ITEMS) {
                truncated = true
                break
            }
            val documentId = cursor.getString(idColumn) ?: continue
            val name = cursor.getString(nameColumn) ?: "Unnamed file"
            val mime = cursor.getString(mimeColumn) ?: "application/octet-stream"
            val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                cursor.getLong(sizeColumn)
            } else -1L
            result.add(
                PhoneDocumentEntry(
                    name = name,
                    mime = mime,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                    size = size
                )
            )
        }
    }
    Pair(
        result.sortedWith(
            compareBy<PhoneDocumentEntry> {
                it.mime != DocumentsContract.Document.MIME_TYPE_DIR
            }.thenBy { it.name.lowercase(Locale.ROOT) }
        ),
        if (truncated) {
            "Showing the first $MAX_PHONE_FOLDER_ITEMS items. Search or open a smaller folder to find the rest."
        } else null
    )
} catch (error: Exception) {
    Pair(emptyList(), error.message ?: "This folder could not be read.")
}

private fun documentGlyph(mime: String): String = when {
    mime.startsWith("image/") -> "IMG"
    mime.startsWith("video/") -> "VID"
    mime.startsWith("audio/") -> "AUD"
    mime.contains("pdf") -> "PDF"
    mime.contains("zip") || mime.contains("compressed") -> "ZIP"
    mime.contains("spreadsheet") || mime.contains("excel") -> "XLS"
    mime.contains("presentation") || mime.contains("powerpoint") -> "PPT"
    mime.contains("word") || mime.contains("document") -> "DOC"
    else -> "FILE"
}

private fun readableSize(size: Long): String = when {
    size < 0L -> "Size unavailable"
    size < 1024L -> "$size B"
    size < 1_048_576L -> "${size / 1024L} KB"
    else -> String.format(Locale.ROOT, "%.1f MB", size / 1_048_576.0)
}
