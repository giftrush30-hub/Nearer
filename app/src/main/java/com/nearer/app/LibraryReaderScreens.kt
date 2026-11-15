package com.nearer.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@Composable
internal fun LibraryPdfReaderScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val readingPrefs = remember(context) { context.getSharedPreferences("nearer_library_reading", android.content.Context.MODE_PRIVATE) }
    val lastPageKey = "last-page:$uri"
    val bookmarkKey = "bookmark-page:$uri"
    var pageIndex by remember(uri) { mutableStateOf(readingPrefs.getInt(lastPageKey, 0).coerceAtLeast(0)) }
    var bookmarkedPage by remember(uri) { mutableStateOf(readingPrefs.getInt(bookmarkKey, -1)) }
    var pageCount by remember(uri) { mutableStateOf(0) }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(uri) { mutableStateOf(true) }
    var error by remember(uri) { mutableStateOf("") }

    LaunchedEffect(uri, pageIndex) {
        readingPrefs.edit().putInt(lastPageKey, pageIndex).apply()
        loading = true
        error = ""
        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("Android could not open this PDF.")
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        require(renderer.pageCount > 0) { "This PDF has no readable pages." }
                        val safePage = pageIndex.coerceIn(0, renderer.pageCount - 1)
                        val page = renderer.openPage(safePage)
                        val scale = minOf(2.0, 1600.0 / page.width.toDouble())
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        output to renderer.pageCount
                    }
                }
            }
        }
        rendered.onSuccess { (image, count) ->
            bitmap = image
            pageCount = count
            pageIndex = pageIndex.coerceIn(0, count - 1)
        }.onFailure { error = it.message ?: "Unable to render this PDF." }
        loading = false
    }
    DisposableEffect(bitmap) {
        val displayed = bitmap
        onDispose { displayed?.recycle() }
    }

    Column(Modifier.fillMaxSize().background(Midnight).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹  LIBRARY", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("PDF READER", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    loading -> Text("Rendering page…", color = Muted, modifier = Modifier.padding(30.dp))
                    error.isNotBlank() -> Text(error, color = Gold, modifier = Modifier.padding(24.dp))
                    bitmap != null -> Image(bitmap!!.asImageBitmap(), contentDescription = "PDF page ${pageIndex + 1}", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0 && !loading, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) { Text("PREVIOUS") }
            Text(if (pageCount == 0) "— / —" else "${pageIndex + 1} / $pageCount", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, enabled = pageCount > 0 && pageIndex < pageCount - 1 && !loading, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) { Text("NEXT") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                bookmarkedPage = pageIndex
                readingPrefs.edit().putInt(bookmarkKey, pageIndex).apply()
            }, enabled = pageCount > 0 && !loading, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)) {
                Text(if (bookmarkedPage == pageIndex) "BOOKMARKED" else "BOOKMARK PAGE")
            }
            Button(onClick = { pageIndex = bookmarkedPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) },
                enabled = bookmarkedPage >= 0 && pageCount > 0 && !loading, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) {
                Text(if (bookmarkedPage >= 0) "GO TO PAGE ${bookmarkedPage + 1}" else "NO BOOKMARK")
            }
        }
        Text("Your last page and one bookmark are saved on this phone. Pages are rendered locally; no PDF upload or internet connection is required.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
internal fun LibraryTextReaderScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var content by remember(uri) { mutableStateOf<String?>(null) }
    var error by remember(uri) { mutableStateOf("") }
    LaunchedEffect(uri) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText().take(1_000_000)
                } ?: throw IllegalStateException("Android could not open this text file.")
            }
        }
        result.onSuccess { content = it }.onFailure { error = it.message ?: "Unable to read this file." }
    }
    Column(Modifier.fillMaxSize().background(Midnight).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("‹  LIBRARY", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(15.dp)) {
                Text(content ?: if (error.isNotBlank()) error else "Opening text…", color = if (error.isNotBlank()) Gold else White, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        Text("Local text reader · up to 1 MB displayed per file", color = Muted, fontSize = 10.sp)
    }
}
