package com.nearer.app

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.os.Build
import android.provider.MediaStore
import android.Manifest
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal enum class FileCentreCategory(val label: String) {
    PICTURES("Pictures"), VIDEOS("Videos"), MUSIC("Music"), APPS("Apps"),
    DOCUMENTS("Documents"), DOWNLOADS("Downloads"), OTHER("Other files")
}

internal data class FileCentreItem(
    val id: String,
    val name: String,
    val category: FileCentreCategory,
    val uri: Uri? = null,
    val sizeBytes: Long = 0L,
    val modifiedSeconds: Long = 0L,
    val mimeType: String? = null,
    val packageName: String? = null
)

internal object AndroidFileCatalog {
    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasMediaAccess(context: Context, category: FileCentreCategory): Boolean {
        if (category == FileCentreCategory.APPS || category == FileCentreCategory.DOCUMENTS || category == FileCentreCategory.DOWNLOADS || category == FileCentreCategory.OTHER) return true
        if (Build.VERSION.SDK_INT < 33) {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val required = when (category) {
            FileCentreCategory.PICTURES -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            FileCentreCategory.VIDEOS -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
            FileCentreCategory.MUSIC -> listOf(Manifest.permission.READ_MEDIA_AUDIO)
            else -> emptyList()
        }
        val granted = required.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val selectedVisualAccess = Build.VERSION.SDK_INT >= 34 &&
            category in listOf(FileCentreCategory.PICTURES, FileCentreCategory.VIDEOS) &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        return granted || selectedVisualAccess
    }

    fun scanMedia(context: Context): List<FileCentreItem> {
        val resolver = context.contentResolver
        val result = ArrayList<FileCentreItem>()
        val collections = listOf(
            Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileCentreCategory.PICTURES, MediaStore.Images.Media._ID),
            Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileCentreCategory.VIDEOS, MediaStore.Video.Media._ID),
            Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, FileCentreCategory.MUSIC, MediaStore.Audio.Media._ID)
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        collections.forEach { (collection, category, _) ->
            try {
                resolver.query(
                    collection, projection, null, null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unnamed file"
                        val uri = ContentUris.withAppendedId(collection, mediaId)
                        result += FileCentreItem(
                            id = uri.toString(),
                            name = name,
                            category = category,
                            uri = uri,
                            sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L),
                            modifiedSeconds = cursor.getLong(dateColumn).coerceAtLeast(0L),
                            mimeType = if (mimeColumn >= 0) cursor.getString(mimeColumn) else null
                        )
                    }
                }
            } catch (_: SecurityException) {
                // Partial media access is valid on newer Android versions.
            } catch (_: RuntimeException) {
                // A provider may be unavailable; keep the other categories usable.
            }
        }
        return result.sortedByDescending { it.modifiedSeconds }
    }

    fun installedApps(context: Context): List<FileCentreItem> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            context.packageManager.queryIntentActivities(launcher, 0)
                .asSequence()
                .mapNotNull { resolve ->
                    val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                    val label = runCatching { context.packageManager.getApplicationLabel(info).toString() }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: info.packageName
                    FileCentreItem(
                        id = "app:${info.packageName}",
                        name = label,
                        category = FileCentreCategory.APPS,
                        packageName = info.packageName
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun appIcon(context: Context, packageName: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "Size unavailable"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return String.format(Locale.getDefault(), if (unit == 0) "%.0f %s" else "%.1f %s", value, units[unit])
    }
}

@Composable
internal fun FileCentreScreen(
    connected: Boolean,
    onSendFiles: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<FileCentreItem>() }
    val selected = remember { mutableStateListOf<String>() }
    var category by remember { mutableStateOf(FileCentreCategory.PICTURES) }
    var search by remember { mutableStateOf("") }
    var gridMode by remember { mutableStateOf(true) }
    var sortMode by remember { mutableStateOf("Recent") }
    var loading by remember { mutableStateOf(false) }
    val hasAccess = AndroidFileCatalog.hasMediaAccess(context, category)
    var notice by remember { mutableStateOf("Your phone's accessible media and apps will appear here.") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (AndroidFileCatalog.hasMediaAccess(context, category)) {
            loading = true
            scope.launch {
                val media = withContext(Dispatchers.IO) { AndroidFileCatalog.scanMedia(context) }
                items.removeAll { it.category != FileCentreCategory.APPS }
                items.addAll(media)
                loading = false
                notice = "Your accessible pictures, videos and music are ready."
            }
        } else {
            notice = "Media access was not granted. You can still browse installed apps and choose documents manually."
        }
    }
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val pickedCategory = category
            val pickedItems = uris.map { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
                var sizeBytes = 0L
                runCatching {
                    context.contentResolver.query(uri, arrayOf(
                        android.provider.OpenableColumns.DISPLAY_NAME,
                        android.provider.OpenableColumns.SIZE
                    ), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                        }
                    }
                }
                FileCentreItem(
                    id = uri.toString(),
                    name = displayName,
                    category = pickedCategory,
                    uri = uri,
                    sizeBytes = sizeBytes,
                    mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                )
            }
            pickedItems.forEach { picked ->
                if (items.none { it.id == picked.id }) items.add(picked)
                if (picked.id !in selected) selected.add(picked.id)
            }
            notice = "${pickedItems.size} file(s) added to your selection basket."
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        val apps = withContext(Dispatchers.IO) { AndroidFileCatalog.installedApps(context) }
        items.removeAll { it.category == FileCentreCategory.APPS }
        items.addAll(apps)
        if (AndroidFileCatalog.hasMediaAccess(context, category)) {
            val media = withContext(Dispatchers.IO) { AndroidFileCatalog.scanMedia(context) }
            items.removeAll { it.category != FileCentreCategory.APPS }
            items.addAll(media)
            notice = "Showing accessible files and installed apps on this phone."
        } else {
            notice = "Allow media access to show pictures, videos and music. Your installed apps are already listed."
        }
        loading = false
    }

    val visible = items.filter { item ->
        item.category == category &&
            (search.isBlank() || item.name.contains(search.trim(), ignoreCase = true))
    }.let { matches ->
        when (sortMode) {
            "Name" -> matches.sortedBy { it.name.lowercase(Locale.getDefault()) }
            "Largest" -> matches.sortedByDescending { it.sizeBytes }
            else -> matches.sortedByDescending { it.modifiedSeconds }
        }
    }
    val selectedItems = items.filter { it.id in selected }
    val selectedUris = selectedItems.mapNotNull { it.uri }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR FILE CENTRE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text("Everything, organised.", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("${items.size} discoverable items", color = Muted, fontSize = 11.sp)
            }
            Button(
                onClick = { gridMode = !gridMode },
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (gridMode) "List" else "Grid") }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search this category…", color = Muted) },
            shape = RoundedCornerShape(14.dp)
        )
        Row(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FileCentreCategory.entries.forEach { entry ->
                FilterChip(
                    selected = category == entry,
                    onClick = { category = entry },
                    label = { Text(entry.label, fontSize = 10.sp) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sort:", color = Muted, fontSize = 10.sp)
            listOf("Recent", "Name", "Largest").forEach { mode ->
                FilterChip(selected = sortMode == mode, onClick = { sortMode = mode }, label = { Text(mode, fontSize = 10.sp) })
            }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Mint)
        if (!hasAccess && category in listOf(FileCentreCategory.PICTURES, FileCentreCategory.VIDEOS, FileCentreCategory.MUSIC)) {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Let NeaRer organise your media", color = White, fontWeight = FontWeight.Bold)
                    Text("Android asks permission before NeaRer can list your photos, videos and music. You stay in control.", color = Muted, fontSize = 12.sp)
                    Button(
                        onClick = { permissionLauncher.launch(AndroidFileCatalog.mediaPermissions()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) { Text("Allow media access") }
                }
            }
        }
        if (category == FileCentreCategory.DOCUMENTS || category == FileCentreCategory.OTHER) {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when (category) { FileCentreCategory.DOCUMENTS -> "Browse documents"; FileCentreCategory.DOWNLOADS -> "Browse downloads"; else -> "Browse other files" }, color = White, fontWeight = FontWeight.Bold)
                    Text(if (category == FileCentreCategory.DOWNLOADS) "Choose files from your Downloads folder. Android controls access, so NeaRer only lists files you select." else "Android protects documents and many folders. Choose files using the system file picker; NeaRer can then send them without scanning private folders.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Button(
                        onClick = { documentPicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) { Text("Choose files") }
                }
            }
        }
        if (category == FileCentreCategory.APPS) {
            Text("Installed apps are listed using Android's launcher-app visibility. APK extraction and split-app packaging are the next stage.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Text(notice, color = Muted, fontSize = 10.sp)
        if (visible.isEmpty() && !loading && (category == FileCentreCategory.APPS || hasAccess)) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (search.isBlank()) "Nothing found in this category yet." else "No matches. Try another search.", color = Muted)
            }
        } else if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(visible, key = { it.id }) { item ->
                    FileCentreTile(
                        item = item,
                        selected = item.id in selected,
                        context = context,
                        onClick = {
                            if (item.category == FileCentreCategory.APPS) {
                                notice = "App installer sharing is being built next. Split APK apps need all components packaged together."
                            } else if (item.uri != null) {
                                if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                            }
                        }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(visible, key = { it.id }) { item ->
                    FileCentreRow(
                        item = item,
                        selected = item.id in selected,
                        context = context,
                        onClick = {
                            if (item.category == FileCentreCategory.APPS) {
                                notice = "App installer sharing is being built next. Split APK apps need all components packaged together."
                            } else if (item.uri != null) {
                                if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                            }
                        }
                    )
                }
            }
        }
        if (selectedItems.isNotEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${selectedItems.size} selected • ${AndroidFileCatalog.formatSize(selectedItems.sumOf { it.sizeBytes })}", color = White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selected.clear() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Muted)
                        ) { Text("Clear") }
                        Button(
                            onClick = {
                                if (!connected) notice = "Connect to a nearby NeaRer phone before sending."
                                else if (selectedUris.isNotEmpty()) {
                                    onSendFiles(selectedUris)
                                    // Keep the basket intact until the user clears it.
                                    // The sender callback reports failures through the chat/transfer status,
                                    // so clearing here could silently discard a selection that was not queued.
                                    notice = "Send request handed to NeaRer. Check the transfer status before clearing your basket."
                                }
                            },
                            enabled = selectedUris.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                        ) { Text("Send selected") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileCentreTile(
    item: FileCentreItem,
    selected: Boolean,
    context: Context,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFF244D4A) else Panel,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.packageName != null) {
                val icon = remember(item.packageName) { AndroidFileCatalog.appIcon(context, item.packageName) }
                if (icon != null) {
                    val bitmap = remember(icon) {
                        val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        icon.setBounds(0, 0, 96, 96)
                        icon.draw(canvas)
                        bmp.asImageBitmap()
                    }
                    Image(bitmap, contentDescription = item.name, modifier = Modifier.size(52.dp))
                } else {
                    Text("APP", color = Gold, fontWeight = FontWeight.Bold)
                }
            } else {
                val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, item.uri) {
                    value = withContext(Dispatchers.IO) {
                        if (Build.VERSION.SDK_INT >= 29 && item.uri != null) runCatching {
                            context.contentResolver.loadThumbnail(item.uri, Size(240, 240), null).asImageBitmap()
                        }.getOrNull() else null
                    }
                }
                if (thumbnail != null) {
                    Image(thumbnail!!, contentDescription = item.name, modifier = Modifier.fillMaxWidth().height(86.dp))
                } else Text(
                    when (item.category) {
                        FileCentreCategory.PICTURES -> "PHOTO"
                        FileCentreCategory.VIDEOS -> "VIDEO"
                        FileCentreCategory.MUSIC -> "AUDIO"
                        else -> "FILE"
                    },
                    color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
            Text(item.name, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(if (item.packageName != null) "Installed app" else AndroidFileCatalog.formatSize(item.sizeBytes), color = Muted, fontSize = 9.sp)
            if (selected) Text("✓ Selected", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FileCentreRow(
    item: FileCentreItem,
    selected: Boolean,
    context: Context,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFF244D4A) else Panel,
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (item.category) {
                    FileCentreCategory.PICTURES -> "▧"
                    FileCentreCategory.VIDEOS -> "▶"
                    FileCentreCategory.MUSIC -> "♫"
                    FileCentreCategory.APPS -> "APP"
                    else -> "FILE"
                },
                color = if (selected) Gold else Mint,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(item.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                Text(if (item.packageName != null) item.packageName else AndroidFileCatalog.formatSize(item.sizeBytes), color = Muted, fontSize = 10.sp, maxLines = 1)
            }
            Text(if (selected) "✓" else "＋", color = if (selected) Gold else Muted, fontWeight = FontWeight.Bold)
        }
    }
}
