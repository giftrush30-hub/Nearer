package com.nearer.app

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.Manifest
import android.graphics.drawable.Drawable
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.TextButton
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal enum class FileCentreCategory(val label: String) {
    PICTURES("Pictures"), VIDEOS("Videos"), MUSIC("Music"), APPS("Apps"),
    DOCUMENTS("Documents"), DOWNLOADS("Downloads"), OTHER("Other files"), FAVOURITES("Favourites"), RECENT("Recent files")
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

    /** Traverse only a folder tree explicitly approved by the user. */
    fun scanFolderTree(context: Context, treeUri: Uri): List<FileCentreItem> {
        val resolver = context.contentResolver
        val results = ArrayList<FileCentreItem>()
        val pending = ArrayDeque<Pair<String, Int>>()
        val visited = HashSet<String>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
        pending.add(rootId to 0)
        while (pending.isNotEmpty() && results.size < 5000) {
            val (parentId, depth) = pending.removeFirst()
            if (depth > 12 || !visited.add(parentId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            try {
                resolver.query(childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ), null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val dateCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext() && results.size < 5000) {
                        if (idCol < 0 || nameCol < 0 || mimeCol < 0) continue
                        val documentId = cursor.getString(idCol) ?: continue
                        val name = cursor.getString(nameCol) ?: "Unnamed file"
                        val mime = cursor.getString(mimeCol)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            pending.add(documentId to depth + 1)
                            continue
                        }
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        val category = when {
                            mime?.startsWith("image/") == true -> FileCentreCategory.PICTURES
                            mime?.startsWith("video/") == true -> FileCentreCategory.VIDEOS
                            mime?.startsWith("audio/") == true -> FileCentreCategory.MUSIC
                            mime == "application/vnd.android.package-archive" -> FileCentreCategory.APPS
                            mime?.contains("pdf") == true || mime?.contains("word") == true ||
                                mime?.contains("sheet") == true || mime?.startsWith("text/") == true -> FileCentreCategory.DOCUMENTS
                            else -> FileCentreCategory.OTHER
                        }
                        results += FileCentreItem(
                            id = uri.toString(), name = name, category = category, uri = uri,
                            sizeBytes = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L,
                            modifiedSeconds = if (dateCol >= 0 && !cursor.isNull(dateCol)) (cursor.getLong(dateCol) / 1000L).coerceAtLeast(0L) else 0L,
                            mimeType = mime
                        )
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: RuntimeException) {
            }
        }
        return results
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

    
    fun exportAppBundle(context: Context, item: FileCentreItem): FileCentreItem {
        val packageName = requireNotNull(item.packageName) { "This item is not an installed app." }
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val baseApk = java.io.File(appInfo.sourceDir)
        require(baseApk.isFile) { "The app's base APK is unavailable." }
        val splitApks = appInfo.splitSourceDirs.orEmpty().map { java.io.File(it) }.filter { it.isFile }
        val outputDir = java.io.File(context.cacheDir, "nearer-share").apply { mkdirs() }
        val safePackage = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        // Keep each export separate so a later export cannot overwrite a bundle being shared.
        val output = java.io.File.createTempFile("$safePackage-", "-app-bundle.zip", outputDir)
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            // APK entries are already compressed; avoid wasting CPU recompressing them.
            // APK files are already compressed; avoid spending CPU recompressing them.
            zip.setLevel(0)
            fun addApk(file: java.io.File, entryName: String) {
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { input -> input.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }
            addApk(baseApk, "base.apk")
            splitApks.forEachIndexed { index, file ->
                val safeName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                addApk(file, "splits/${index + 1}-$safeName")
            }
            zip.putNextEntry(ZipEntry("INSTALLATION.txt"))
            zip.write(("NeaRer app bundle for $packageName\n" +
                "Contains base.apk and ${splitApks.size} split APK file(s).\n" +
                "Install with a compatible split-APK installer. Do not install only base.apk when split APKs are present.\n" +
                "Private app data, accounts and settings are not included.\n").toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", output)
        return item.copy(
            id = uri.toString(),
            name = item.name + " • App bundle",
            uri = uri,
            sizeBytes = output.length(),
            modifiedSeconds = System.currentTimeMillis() / 1000L,
            mimeType = "application/zip"
        )
    }

    fun openPreview(context: Context, item: FileCentreItem): Boolean {
        val uri = item.uri ?: return false
        val mime = item.mimeType?.takeIf { it.isNotBlank() } ?: when (item.category) {
            FileCentreCategory.PICTURES -> "image/*"
            FileCentreCategory.VIDEOS -> "video/*"
            FileCentreCategory.MUSIC -> "audio/*"
            else -> "*/*"
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open ${item.name}"))
            true
        }.getOrDefault(false)
    }

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


    fun formatModified(seconds: Long): String {
        if (seconds <= 0L) return "Date unavailable"
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault())
            .format(java.util.Date(seconds * 1000L))
    }

    fun formatType(item: FileCentreItem): String {
        val subtype = item.mimeType?.substringAfter("/", "")?.substringBefore(";")
        if (!subtype.isNullOrBlank() && subtype != "*") return subtype.uppercase(Locale.getDefault())
        return when (item.category) {
            FileCentreCategory.PICTURES -> "IMAGE"
            FileCentreCategory.VIDEOS -> "VIDEO"
            FileCentreCategory.MUSIC -> "AUDIO"
            FileCentreCategory.APPS -> "APP"
            FileCentreCategory.DOCUMENTS -> "DOCUMENT"
            FileCentreCategory.DOWNLOADS -> "DOWNLOAD"
            FileCentreCategory.OTHER -> "FILE"
            FileCentreCategory.FAVOURITES -> "FAVOURITE"
            FileCentreCategory.RECENT -> "RECENT"
        }
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
    val favouriteIds = remember { mutableStateListOf<String>() }
    val recentIds = remember { mutableStateListOf<String>() }
    var category by remember { mutableStateOf(FileCentreCategory.PICTURES) }
    var search by remember { mutableStateOf("") }
    var gridMode by remember { mutableStateOf(true) }
    var sortMode by remember { mutableStateOf("Recent") }
    var loading by remember { mutableStateOf(false) }
    val hasAccess = AndroidFileCatalog.hasMediaAccess(context, category)
    var notice by remember { mutableStateOf("Your phone's accessible media and apps will appear here.") }
    LaunchedEffect(Unit) {
        val saved = context.getSharedPreferences("nearer_file_centre", Context.MODE_PRIVATE)
            .getStringSet("favourites", emptySet()).orEmpty()
        favouriteIds.addAll(saved)
        val savedRecent = context.getSharedPreferences("nearer_file_centre", Context.MODE_PRIVATE)
            .getString("recent_files_order", "").orEmpty()
        recentIds.addAll(savedRecent.split("\n").filter { it.isNotBlank() }.take(100))

        // SAF tree grants survive process restarts. Re-scan only roots NeaRer
        // previously received a persistable read grant for; stale grants are pruned.
        val preferences = context.getSharedPreferences("nearer_file_centre", Context.MODE_PRIVATE)
        val savedRoots = preferences.getStringSet("folder_roots", emptySet()).orEmpty().toSet()
        val validRoots = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
        val rootsToKeep = savedRoots.intersect(validRoots)
        if (rootsToKeep != savedRoots) {
            preferences.edit().putStringSet("folder_roots", rootsToKeep).apply()
        }
        if (rootsToKeep.isNotEmpty()) {
            loading = true
            val restored = withContext(Dispatchers.IO) {
                rootsToKeep.flatMap { root ->
                    runCatching { AndroidFileCatalog.scanFolderTree(context, Uri.parse(root)) }
                        .getOrDefault(emptyList())
                }.distinctBy { it.id }
            }
            restored.forEach { found ->
                if (items.none { it.id == found.id }) items.add(found)
            }
            loading = false
            if (restored.isNotEmpty()) {
                notice = "${restored.size} file(s) from your saved folders are available again."
            }
        }
    }
    val markRecent: (String) -> Unit = { id ->
        recentIds.remove(id)
        recentIds.add(0, id)
        while (recentIds.size > 100) recentIds.removeAt(recentIds.lastIndex)
        context.getSharedPreferences("nearer_file_centre", Context.MODE_PRIVATE)
            .edit().putString("recent_files_order", recentIds.joinToString("\n")).apply()
    }

    val toggleFavourite: (String) -> Unit = { id ->
        if (id in favouriteIds) favouriteIds.remove(id) else favouriteIds.add(id)
        context.getSharedPreferences("nearer_file_centre", Context.MODE_PRIVATE)
            .edit().putStringSet("favourites", favouriteIds.toSet()).apply()
    }

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
                val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                val resolvedCategory = if (pickedCategory == FileCentreCategory.DOCUMENTS || pickedCategory == FileCentreCategory.OTHER) {
                    when {
                        mimeType?.startsWith("image/") == true -> FileCentreCategory.PICTURES
                        mimeType?.startsWith("video/") == true -> FileCentreCategory.VIDEOS
                        mimeType?.startsWith("audio/") == true -> FileCentreCategory.MUSIC
                        mimeType == "application/vnd.android.package-archive" -> FileCentreCategory.APPS
                        mimeType?.contains("pdf") == true || mimeType?.contains("word") == true ||
                            mimeType?.contains("sheet") == true || mimeType?.contains("text") == true -> FileCentreCategory.DOCUMENTS
                        else -> pickedCategory
                    }
                } else pickedCategory
                FileCentreItem(
                    id = uri.toString(),
                    name = displayName,
                    category = resolvedCategory,
                    uri = uri,
                    sizeBytes = sizeBytes,
                    mimeType = mimeType
                )
            }
            pickedItems.forEach { picked ->
                if (items.none { it.id == picked.id }) items.add(picked)
                if (picked.id !in selected) selected.add(picked.id)
                markRecent(picked.id)
            }
            notice = "${pickedItems.size} file(s) added to your selection basket."
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val grantSaved = runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == treeUri && it.isReadPermission
                }
            }.getOrDefault(false)
            if (grantSaved) {
                val preferences = context.getSharedPreferences("nearer_file_centre", Context.MODE_PRIVATE)
                val roots = preferences.getStringSet("folder_roots", emptySet()).orEmpty().toMutableSet()
                roots.add(treeUri.toString())
                preferences.edit().putStringSet("folder_roots", roots).apply()
            }
            loading = true
            notice = "Reading the folder you approved…"
            scope.launch {
                val folderItems = withContext(Dispatchers.IO) {
                    AndroidFileCatalog.scanFolderTree(context, treeUri)
                }
                folderItems.forEach { found ->
                    if (items.none { it.id == found.id }) items.add(found)
                    if (found.id !in selected) selected.add(found.id)
                    markRecent(found.id)
                }
                loading = false
                notice = if (folderItems.isEmpty()) "No shareable files were found in that folder."
                    else "${folderItems.size} file(s) from the selected folder added to your send basket."
            }
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
        (when (category) { FileCentreCategory.FAVOURITES -> item.id in favouriteIds; FileCentreCategory.RECENT -> item.id in recentIds; else -> item.category == category }) &&
            (search.isBlank() || item.name.contains(search.trim(), ignoreCase = true))
    }.let { matches ->
        when (sortMode) {
            "Name" -> matches.sortedBy { it.name.lowercase(Locale.getDefault()) }
            "Largest" -> matches.sortedByDescending { it.sizeBytes }
            else -> if (category == FileCentreCategory.RECENT) matches.sortedBy { recentIds.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } } else matches.sortedByDescending { it.modifiedSeconds }
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
        val selectableVisibleIds = visible.filter { it.uri != null }.map { it.id }
        val allVisibleSelected = selectableVisibleIds.isNotEmpty() && selectableVisibleIds.all { it in selected }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${selectableVisibleIds.size} files in view", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (allVisibleSelected) selected.removeAll(selectableVisibleIds.toSet())
                    else selectableVisibleIds.filter { it !in selected }.forEach { selected.add(it) }
                    notice = if (allVisibleSelected) "Visible files removed from your basket." else "${selectableVisibleIds.size} visible file(s) selected."
                },
                enabled = selectableVisibleIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold),
                shape = RoundedCornerShape(11.dp)
            ) { Text(if (allVisibleSelected) "Deselect visible" else "Select visible", fontSize = 10.sp) }
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
        if (category == FileCentreCategory.DOCUMENTS || category == FileCentreCategory.DOWNLOADS || category == FileCentreCategory.OTHER) {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when (category) { FileCentreCategory.DOCUMENTS -> "Browse documents"; FileCentreCategory.DOWNLOADS -> "Browse downloads"; else -> "Browse other files" }, color = White, fontWeight = FontWeight.Bold)
                    Text(if (category == FileCentreCategory.DOWNLOADS) "Choose files from your Downloads folder. Android controls access, so NeaRer only lists files you select." else "Android protects documents and many folders. Choose files using the system file picker; NeaRer can then send them without scanning private folders.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Button(
                        onClick = { documentPicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) { Text("Choose files") }
                    Button(
                        onClick = { folderPicker.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)
                    ) { Text("Browse a folder") }
                }
            }
        }
        if (category == FileCentreCategory.APPS) {
            Text("Tap an app to package its base APK and any split APK parts into one shareable bundle. Private app data and accounts are not included.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
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
                        favourite = item.id in favouriteIds,
                        onToggleFavourite = { toggleFavourite(item.id) },
                        context = context,
                        onClick = {
                            if (item.category == FileCentreCategory.APPS && item.packageName != null && item.uri == null) {
                                if (item.id in selected) selected.remove(item.id)
                                else {
                                    notice = "Packaging ${item.name}, including any split APK parts…"
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) { AndroidFileCatalog.exportAppBundle(context, item) } }
                                            .onSuccess { bundle ->
                                                items.removeAll { it.id == bundle.id }
                                                items.add(bundle)
                                                if (bundle.id !in selected) selected.add(bundle.id)
                                                notice = "${item.name} bundle is ready and added to your selection."
                                            }
                                            .onFailure { error ->
                                                notice = "Could not package ${item.name}: ${error.message ?: "storage or package access error"}"
                                            }
                                    }
                                }
                            } else if (item.uri != null) {
                                markRecent(item.id)
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
                        favourite = item.id in favouriteIds,
                        onToggleFavourite = { toggleFavourite(item.id) },
                        context = context,
                        onClick = {
                            if (item.category == FileCentreCategory.APPS && item.packageName != null && item.uri == null) {
                                if (item.id in selected) selected.remove(item.id)
                                else {
                                    notice = "Packaging ${item.name}, including any split APK parts…"
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) { AndroidFileCatalog.exportAppBundle(context, item) } }
                                            .onSuccess { bundle ->
                                                items.removeAll { it.id == bundle.id }
                                                items.add(bundle)
                                                if (bundle.id !in selected) selected.add(bundle.id)
                                                notice = "${item.name} bundle is ready and added to your selection."
                                            }
                                            .onFailure { error ->
                                                notice = "Could not package ${item.name}: ${error.message ?: "storage or package access error"}"
                                            }
                                    }
                                }
                            } else if (item.uri != null) {
                                markRecent(item.id)
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
                    Text("YOUR SEND BASKET", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        selectedItems.forEach { selectedItem ->
                            Surface(color = Midnight, shape = RoundedCornerShape(12.dp)) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Column {
                                        Text(selectedItem.name, color = White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(AndroidFileCatalog.formatSize(selectedItem.sizeBytes), color = Muted, fontSize = 8.sp)
                                    }
                                    TextButton(onClick = {
                                        selected.remove(selectedItem.id)
                                        notice = "${selectedItem.name} removed from your basket."
                                    }) {
                                        Text("Remove", color = Gold, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
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
    favourite: Boolean,
    onToggleFavourite: () -> Unit,
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
            Text(if (item.packageName != null) "Installed app" else AndroidFileCatalog.formatSize(item.sizeBytes) + " • " + AndroidFileCatalog.formatType(item), color = Muted, fontSize = 9.sp)
            if (item.modifiedSeconds > 0L) Text(AndroidFileCatalog.formatModified(item.modifiedSeconds), color = Muted, fontSize = 8.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) Text("✓ Selected", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleFavourite) { Text(if (favourite) "★" else "☆", color = Gold, fontSize = 17.sp) }
                if (item.uri != null) {
                    TextButton(onClick = { AndroidFileCatalog.openPreview(context, item) }) {
                        Text("Preview", color = Mint, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileCentreRow(
    item: FileCentreItem,
    selected: Boolean,
    favourite: Boolean,
    onToggleFavourite: () -> Unit,
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
                Text(if (item.packageName != null) item.packageName else AndroidFileCatalog.formatSize(item.sizeBytes) + " • " + AndroidFileCatalog.formatType(item) + " • " + AndroidFileCatalog.formatModified(item.modifiedSeconds), color = Muted, fontSize = 9.sp, maxLines = 1)
            }
            TextButton(onClick = onToggleFavourite) { Text(if (favourite) "★" else "☆", color = Gold, fontSize = 17.sp) }
            if (item.uri != null) {
                TextButton(onClick = { AndroidFileCatalog.openPreview(context, item) }) {
                    Text("Preview", color = Mint, fontSize = 10.sp)
                }
            }
            Text(if (selected) "✓" else "＋", color = if (selected) Gold else Muted, fontWeight = FontWeight.Bold)
        }
    }
}
