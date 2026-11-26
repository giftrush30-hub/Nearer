package com.nearer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import com.nearer.app.bible.BibleWorkspaceScreen
import org.json.JSONArray
import org.json.JSONObject

private data class LibraryEntry(
    val uri: Uri,
    val name: String,
    val mime: String,
    val kind: String,
    val sizeBytes: Long,
    val favourite: Boolean,
    val addedAt: Long
)

private fun libraryKind(mime: String, name: String): String {
    val lower = name.lowercase()
    return when {
        mime == "application/pdf" || lower.endsWith(".pdf") -> "PDF"
        mime.contains("epub") || lower.endsWith(".epub") -> "BOOK"
        mime.startsWith("image/") -> "IMAGE"
        mime.startsWith("audio/") -> "AUDIO"
        mime.startsWith("video/") -> "VIDEO"
        mime.contains("word") || lower.endsWith(".doc") || lower.endsWith(".docx") -> "WORD"
        mime.contains("sheet") || lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv") -> "SHEET"
        mime.contains("presentation") || lower.endsWith(".ppt") || lower.endsWith(".pptx") -> "SLIDES"
        mime.startsWith("text/") || lower.endsWith(".rtf") || lower.endsWith(".txt") -> "TEXT"
        else -> "FILE"
    }
}

private fun librarySizeLabel(bytes: Long): String = when {
    bytes <= 0L -> "Size unavailable"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
internal fun LibraryWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("nearer_library_collection", Context.MODE_PRIVATE) }
    val entries = remember(context) {
        mutableStateListOf<LibraryEntry>().apply {
            val saved = runCatching { JSONArray(preferences.getString("entries", "[]")) }.getOrNull()
            if (saved != null) for (index in 0 until saved.length()) {
                val item = saved.optJSONObject(index) ?: continue
                val uri = runCatching { Uri.parse(item.optString("uri")) }.getOrNull() ?: continue
                val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                if (granted) add(LibraryEntry(uri, item.optString("name", "Untitled"), item.optString("mime", "*/*"), item.optString("kind", "FILE"), item.optLong("size", 0L), item.optBoolean("favourite"), item.optLong("added")))
            }
        }
    }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var showFavourites by remember { mutableStateOf(false) }
    var readingEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var showBible by remember { mutableStateOf(false) }
    BackHandler(enabled = showBible) { showBible = false }

    fun saveCollection() {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(JSONObject().put("uri", entry.uri.toString()).put("name", entry.name).put("mime", entry.mime).put("kind", entry.kind).put("size", entry.sizeBytes).put("favourite", entry.favourite).put("added", entry.addedAt))
        }
        preferences.edit().putString("entries", json.toString()).apply()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        selected.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (entries.none { it.uri == uri }) {
                    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled file"
                    var size = 0L
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                        }
                    }
                    val mime = context.contentResolver.getType(uri) ?: "*/*"
                    entries.add(0, LibraryEntry(uri, name, mime, libraryKind(mime, name), size, false, System.currentTimeMillis()))
                }
            }
        }
        saveCollection()
    }

    val filtered = entries.filter { entry ->
        (filter == "ALL" || entry.kind == filter) &&
            (!showFavourites || entry.favourite) &&
            (query.isBlank() || entry.name.contains(query.trim(), ignoreCase = true))
    }

    if (showBible) {
        BibleWorkspaceScreen(onBack = { showBible = false })
        return
    }

    if (readingEntry != null) {
        val entry = readingEntry!!
        if (entry.kind == "PDF") LibraryPdfReaderScreen(uri = entry.uri, title = entry.name, onBack = { readingEntry = null })
        else LibraryTextReaderScreen(uri = entry.uri, title = entry.name, onBack = { readingEntry = null })
    } else Column(
        modifier = Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  MORE", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("YOUR PRIVATE COLLECTION", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("NEARER LIBRARY", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Everything worth keeping. All in one place.", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Books, study notes, PDFs, documents, images and media you choose. Your collection stays on this phone and is available without internet.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Midnight, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(entries.size.toString(), color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            Text("SAVED ITEMS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(color = Midnight, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(entries.count { it.favourite }.toString(), color = Gold, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            Text("FAVOURITES", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                    Text("＋  Add books & files", fontWeight = FontWeight.Bold)
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BIBLE & SCRIPTURE STUDY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Read offline, compare translations and explore original-language word studies.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Button(onClick = { showBible = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                    Text("Open NeaRer Word", fontWeight = FontWeight.Bold)
                }
            }
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search your collection", color = Muted) },
            leadingIcon = { Text("⌕", color = Mint, fontSize = 21.sp) },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Panel, unfocusedContainerColor = Panel, focusedTextColor = White, unfocusedTextColor = White, cursorColor = Mint, focusedIndicatorColor = Mint, unfocusedIndicatorColor = Panel, focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL", "FAVOURITES").forEach { item ->
                val active = if (item == "ALL") !showFavourites else showFavourites
                Surface(color = if (active) Mint.copy(alpha = 0.18f) else Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable { showFavourites = item == "FAVOURITES" }) {
                    Text(item, color = if (active) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("ALL", "PDF", "BOOK", "WORD", "SHEET", "SLIDES", "TEXT", "IMAGE", "AUDIO", "VIDEO", "FILE").forEach { item ->
                val active = filter == item
                Surface(color = if (active) Mint else Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { filter = item }) {
                    Text(item, color = if (active) Midnight else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (showFavourites) "YOUR FAVOURITES" else "YOUR COLLECTION", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("${filtered.size} ITEMS", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        if (filtered.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (entries.isEmpty()) "Your library starts here" else "Nothing found", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (entries.isEmpty()) "Bring in a book, PDF, document or any file you want close at hand." else "Try another search or choose a different collection filter.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        } else {
            filtered.forEach { entry ->
                Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Surface(color = Midnight, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(when (entry.kind) {
                                    "PDF" -> "PDF"
                                    "BOOK" -> "EP"
                                    "WORD" -> "W"
                                    "SHEET" -> "X"
                                    "SLIDES" -> "P"
                                    "IMAGE" -> "◈"
                                    "AUDIO" -> "♫"
                                    "VIDEO" -> "▶"
                                    else -> "▤"
                                }, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.name, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(entry.kind + "  •  " + librarySizeLabel(entry.sizeBytes), color = Muted, fontSize = 9.sp)
                        }
                        Text(if (entry.favourite) "★" else "☆", color = Gold, fontSize = 19.sp, modifier = Modifier.clickable {
                            val index = entries.indexOfFirst { it.uri == entry.uri }
                            if (index >= 0) {
                                entries[index] = entries[index].copy(favourite = !entries[index].favourite)
                                saveCollection()
                            }
                        }.padding(5.dp))
                        Text(if (entry.kind == "PDF" || (entry.kind == "TEXT" && (entry.name.endsWith(".txt", true) || entry.mime == "text/plain"))) "READ" else "OPEN", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            if (entry.kind == "PDF") readingEntry = entry
                            else if (entry.kind == "TEXT" && (entry.name.endsWith(".txt", true) || entry.mime == "text/plain")) readingEntry = entry
                            else runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(entry.uri, entry.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }
                        }.padding(5.dp))
                        Text("×", color = Muted, fontSize = 17.sp, modifier = Modifier.clickable {
                            entries.removeAll { it.uri == entry.uri }
                            saveCollection()
                            MediaUriGrantRegistry.releaseIfUnreferenced(context, entry.uri)
                        }.padding(5.dp))
                    }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("THE NEARER DIFFERENCE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Your files are not uploaded. NeaRer remembers only the files you select and the access Android grants. Opening a file uses an app already installed on your phone.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
