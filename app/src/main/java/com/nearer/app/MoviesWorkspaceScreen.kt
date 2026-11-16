package com.nearer.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.widget.ImageView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
internal fun MoviesWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) {
        context.getSharedPreferences("nearer_movies_local_library", Context.MODE_PRIVATE)
    }
    val rawMovies = remember(context) { prefs.getString("movies", "[]") }
    var libraryReadOnly by remember(context) { mutableStateOf(!MoviesLibraryCodec.canDecode(rawMovies)) }
    val restoredMovies = remember(context) { MoviesLibraryCodec.decode(rawMovies) }
    val persistedUris = remember(context) {
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
    }
    val unavailableCount = remember(context) { restoredMovies.count { it.uri !in persistedUris } }
    val movies = remember(context) {
        mutableStateListOf<NearerMovie>().apply {
            addAll(restoredMovies.filter { it.uri in persistedUris }.map { movie ->
                val legacyResume = runCatching { prefs.getLong("resume_${movie.uri}", 0L) }.getOrDefault(0L)
                movie.copy(resumePositionMs = maxOf(movie.resumePositionMs, legacyResume).coerceIn(0L, MoviesLibraryCodec.MAX_RESUME_POSITION_MS))
            })
        }
    }
    var query by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") }
    var selected by remember { mutableStateOf<NearerMovie?>(null) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var playerIsPlaying by remember { mutableStateOf(false) }
    var statusMessage by remember {
        mutableStateOf(
            when {
                libraryReadOnly -> "This movie library was saved by an unsupported version. It is protected from edits."
                unavailableCount > 0 -> "$unavailableCount saved video(s) no longer have an available document permission. Re-add the original files to restore them."
                else -> ""
            }
        )
    }

    fun saveMovies() {
        if (!libraryReadOnly) {
            prefs.edit().putString("movies", MoviesLibraryCodec.encode(movies)).apply()
        }
    }

    fun persistPosition(uri: String, positionMs: Long) {
        val index = movies.indexOfFirst { it.uri == uri }
        if (index < 0 || libraryReadOnly) return
        val safePosition = positionMs.coerceIn(0L, MoviesLibraryCodec.MAX_RESUME_POSITION_MS)
        movies[index] = movies[index].copy(
            resumePositionMs = safePosition,
            lastWatchedAt = System.currentTimeMillis()
        )
        prefs.edit().putLong("resume_$uri", safePosition).apply()
        saveMovies()
    }

    val player = remember(context) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!libraryReadOnly) {
            var added = 0
            var rejected = 0
            uris.forEach { uri ->
                if (movies.any { it.uri == uri.toString() }) return@forEach
                if (movies.size >= MoviesLibraryCodec.MAX_MOVIES) {
                    rejected++
                    return@forEach
                }
                val alreadyPersisted = context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
                val permissionGranted = alreadyPersisted || runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.isSuccess
                if (!permissionGranted) {
                    rejected++
                    return@forEach
                }
                val displayName = runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled video"
                movies.add(
                    0,
                    NearerMovie(
                        uri = uri.toString(),
                        title = displayName.substringBeforeLast('.', displayName).trim().take(MoviesLibraryCodec.MAX_TITLE_LENGTH),
                        favourite = false,
                        addedAt = System.currentTimeMillis()
                    )
                )
                added++
            }
            saveMovies()
            statusMessage = when {
                added > 0 && rejected > 0 -> "Added $added video(s). $rejected could not be added because of the library limit or missing file access."
                added > 0 -> "Added $added video(s) to your offline library."
                rejected > 0 -> "Some videos could not be added. Check the file access and library limit."
                else -> "Those videos are already in your library."
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val movie = selected ?: return
                if (playbackState == Player.STATE_ENDED) persistPosition(movie.uri, 0L)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerIsPlaying = isPlaying
                if (!isPlaying && player.playbackState == Player.STATE_READY) {
                    selected?.let { persistPosition(it.uri, player.currentPosition) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                statusMessage = "This video could not be played. The file may have moved, its permission may have expired, or its format may not be supported."
            }
        }
        player.addListener(listener)
        onDispose {
            selected?.let { persistPosition(it.uri, player.currentPosition) }
            playerIsPlaying = false
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                selected?.let { persistPosition(it.uri, player.currentPosition) }
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selected?.uri) {
        val movie = selected
        if (movie == null) {
            player.pause()
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }
        statusMessage = ""
        val legacyResume = runCatching { prefs.getLong("resume_${movie.uri}", 0L) }.getOrDefault(0L)
        val resumeAt = maxOf(movie.resumePositionMs, legacyResume).coerceIn(0L, MoviesLibraryCodec.MAX_RESUME_POSITION_MS)
        player.setMediaItem(MediaItem.fromUri(Uri.parse(movie.uri)), resumeAt)
        player.setPlaybackSpeed(playbackSpeed)
        player.prepare()
        player.playWhenReady = true
        try {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) persistPosition(movie.uri, player.currentPosition)
            }
        } finally {
            persistPosition(movie.uri, player.currentPosition)
        }
    }

    val filtered = movies
        .filter { movie ->
            (filterMode != "FAVOURITES" || movie.favourite) &&
                (filterMode != "CONTINUE" || movie.resumePositionMs > 0L) &&
                (query.isBlank() || movie.title.contains(query.trim(), ignoreCase = true))
        }
        .sortedByDescending { it.addedAt }

    Column(
        modifier = Modifier.fillMaxSize().background(Midnight).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (selected == null) "‹  MORE" else "‹  MOVIES",
                color = Mint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    if (selected != null) {
                        selected?.let { persistPosition(it.uri, player.currentPosition) }
                        player.pause()
                        selected = null
                    } else onBack()
                }.padding(vertical = 8.dp)
            )
            Text(if (selected == null) "OFFLINE FIRST" else "LOCAL PLAYBACK", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        if (selected != null) {
            Text(selected!!.title, color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        keepScreenOn = playerIsPlaying
                    }
                },
                update = {
                    it.player = player
                    it.keepScreenOn = playerIsPlaying
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            Text("Playing from your phone · no data required", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SPEED", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                listOf(1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    val active = playbackSpeed == speed
                    Surface(
                        color = if (active) Mint.copy(alpha = 0.18f) else Panel,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.clickable {
                            playbackSpeed = speed
                            player.setPlaybackSpeed(speed)
                        }
                    ) {
                        Text(
                            if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×",
                            color = if (active) Mint else Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Text(
                if (selected!!.resumePositionMs > 0L) "Continue position saved · ${formatMoviePosition(selected!!.resumePositionMs)}" else "Your playback position is saved automatically while you watch.",
                color = Muted,
                fontSize = 11.sp
            )
            if (statusMessage.isNotBlank()) Text(statusMessage, color = Gold, fontSize = 10.sp, lineHeight = 15.sp)
        } else {
            Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("NEARER MOVIES", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your stories. Your screen. Your library.", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add videos already on your phone and watch them here, even when your data is off. NeaRer stores the selected file reference and asks Android to keep permission to open only the videos you choose.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = Midnight, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(11.dp)) {
                                Text(movies.size.toString(), color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                                Text("IN YOUR LIBRARY", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(color = Midnight, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(11.dp)) {
                                Text(movies.count { it.favourite }.toString(), color = Gold, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                                Text("FAVOURITES", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(color = Midnight, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(11.dp)) {
                                Text(movies.count { it.resumePositionMs > 0L }.toString(), color = Mint, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                                Text("CONTINUE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(
                        onClick = { picker.launch(arrayOf("video/*")) },
                        enabled = !libraryReadOnly && movies.size < MoviesLibraryCodec.MAX_MOVIES,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) {
                        Text("＋  Add videos from phone", fontWeight = FontWeight.Bold)
                    }
                    Text("${movies.size}/${MoviesLibraryCodec.MAX_MOVIES} local video references", color = Muted, fontSize = 9.sp)
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search your movies", color = Muted) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Panel,
                    unfocusedContainerColor = Panel,
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    cursorColor = Mint,
                    focusedIndicatorColor = Mint,
                    unfocusedIndicatorColor = Panel
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("ALL", "FAVOURITES", "CONTINUE").forEach { mode ->
                    val active = filterMode == mode
                    Surface(
                        color = if (active) Mint.copy(alpha = 0.18f) else Panel,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).clickable { filterMode = mode }
                    ) {
                        Text(
                            mode,
                            color = if (active) Mint else Muted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp)
                        )
                    }
                }
                Text("${filtered.size}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            if (libraryReadOnly) {
                Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Library protected", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Update NeaRer to a compatible version before changing these saved video entries.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(if (filterMode == "CONTINUE") "Nothing to continue yet" else "Your movie shelf is waiting", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Add a video to start your private offline library.", color = Muted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.uri }) { movie ->
                        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(Modifier.size(88.dp, 62.dp).clip(RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                                    MovieThumbnail(uri = movie.uri, modifier = Modifier.fillMaxSize())
                                    Text("▶", color = White, fontSize = 18.sp, modifier = Modifier.background(Midnight.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(movie.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(
                                        if (movie.resumePositionMs > 0L) "CONTINUE · ${formatMoviePosition(movie.resumePositionMs)}" else "LOCAL VIDEO",
                                        color = if (movie.resumePositionMs > 0L) Mint else Muted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    if (movie.favourite) "★" else "☆",
                                    color = Gold,
                                    fontSize = 20.sp,
                                    modifier = Modifier.clickable(enabled = !libraryReadOnly) {
                                        val index = movies.indexOfFirst { it.uri == movie.uri }
                                        if (index >= 0) movies[index] = movies[index].copy(favourite = !movie.favourite)
                                        saveMovies()
                                    }.padding(5.dp)
                                )
                                Text(
                                    "PLAY",
                                    color = Mint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { selected = movie }.padding(6.dp)
                                )
                                Text(
                                    "×",
                                    color = Muted,
                                    fontSize = 18.sp,
                                    modifier = Modifier.clickable(enabled = !libraryReadOnly) {
                                        if (selected?.uri == movie.uri) {
                                            persistPosition(movie.uri, player.currentPosition)
                                            player.pause()
                                            selected = null
                                        }
                                        movies.remove(movie)
                                        saveMovies()
                                        prefs.edit().remove("resume_${movie.uri}").apply()
                                        MediaUriGrantRegistry.releaseIfUnreferenced(context, Uri.parse(movie.uri))
                                        statusMessage = "Removed from your Movies library."
                                    }.padding(5.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (statusMessage.isNotBlank()) Text(statusMessage, color = Gold, fontSize = 10.sp, lineHeight = 15.sp)
            Text(
                "Online creator publishing, licensed movie discovery and streaming need proper hosting, rights, moderation and bandwidth. They are not simulated by this offline library.",
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
    }
}

private object MovieThumbnailCache : LruCache<String, Bitmap>(4 * 1_024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1_024).coerceAtLeast(1)
}

@Composable
private fun MovieThumbnail(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri, context) {
        value = withContext(Dispatchers.IO) {
            MovieThumbnailCache.get(uri) ?: run {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(uri))
                    val frame = if (Build.VERSION.SDK_INT >= 27) {
                        retriever.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
                            ?: retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
                    } else {
                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                    if (frame == null) {
                        null
                    } else {
                        val scale = minOf(320f / frame.width.coerceAtLeast(1), 180f / frame.height.coerceAtLeast(1), 1f)
                        val width = (frame.width * scale).toInt().coerceAtLeast(1)
                        val height = (frame.height * scale).toInt().coerceAtLeast(1)
                        val thumbnail = Bitmap.createScaledBitmap(frame, width, height, true)
                        if (thumbnail !== frame) frame.recycle()
                        MovieThumbnailCache.put(uri, thumbnail)
                        thumbnail
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: OutOfMemoryError) {
                    null
                } catch (_: Exception) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }
    }
    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(android.graphics.Color.DKGRAY)
            }
        },
        update = { it.setImageBitmap(bitmap) },
        modifier = modifier
    )
}

private fun formatMoviePosition(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
