package com.nearer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

private data class NearerMovie(
    val uri: Uri,
    val title: String,
    val favourite: Boolean,
    val addedAt: Long
)

@Composable
internal fun MoviesWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("nearer_movies_local_library", Context.MODE_PRIVATE)
    }
    val movies = remember(context) {
        mutableStateListOf<NearerMovie>().apply {
            val stored = runCatching { JSONArray(prefs.getString("movies", "[]")) }.getOrNull()
            if (stored != null) for (i in 0 until stored.length()) {
                val item = stored.optJSONObject(i) ?: continue
                val uri = runCatching { Uri.parse(item.optString("uri")) }.getOrNull() ?: continue
                val permitted = context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
                if (permitted) add(NearerMovie(uri, item.optString("title", "Untitled video"), item.optBoolean("favourite"), item.optLong("added")))
            }
        }
    }
    var query by remember { mutableStateOf("") }
    var favouritesOnly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<NearerMovie?>(null) }
    var resumePosition by remember { mutableStateOf(0L) }

    fun saveMovies() {
        val array = JSONArray()
        movies.forEach { movie ->
            array.put(JSONObject().put("uri", movie.uri.toString()).put("title", movie.title)
                .put("favourite", movie.favourite).put("added", movie.addedAt))
        }
        prefs.edit().putString("movies", array.toString()).apply()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (movies.none { it.uri == uri }) {
                    val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled video"
                    movies.add(0, NearerMovie(uri, title.substringBeforeLast('.', title), false, System.currentTimeMillis()))
                }
            }
        }
        saveMovies()
    }

    val player = remember(context) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (selected != null) prefs.edit().putLong("resume_" + selected!!.uri.toString(), player.currentPosition).apply()
            }
        }
        player.addListener(listener)
        onDispose {
            selected?.let { prefs.edit().putLong("resume_" + it.uri.toString(), player.currentPosition).apply() }
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(selected?.uri) {
        val movie = selected ?: return@LaunchedEffect
        resumePosition = prefs.getLong("resume_" + movie.uri.toString(), 0L)
        player.setMediaItem(MediaItem.fromUri(movie.uri), resumePosition)
        player.prepare()
        player.playWhenReady = true
    }

    val filtered = movies.filter {
        (!favouritesOnly || it.favourite) && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true))
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Midnight).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (selected == null) "‹  MORE" else "‹  MOVIES", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { if (selected != null) { prefs.edit().putLong("resume_" + selected!!.uri.toString(), player.currentPosition).apply(); player.pause(); selected = null } else onBack() }.padding(vertical = 8.dp))
            Text("OFFLINE FIRST", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        if (selected != null) {
            Text(selected!!.title, color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            AndroidView(
                factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().height(245.dp)
            )
            Text("Playing from your phone · no data required", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("Playback position is saved on this device so you can continue later.", color = Muted, fontSize = 12.sp)
        } else {
            Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("NEARER MOVIES", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your stories. Your screen. Your library.", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Add videos already on your phone and watch them here, even when your data is off. Your selected files stay on this device; NeaRer keeps permission to open only the videos you choose.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
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
                    }
                    Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                        Text("＋  Add videos from phone", fontWeight = FontWeight.Bold)
                    }
                }
            }
            TextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search your movies", color = Muted) },
                colors = TextFieldDefaults.colors(focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                    focusedTextColor = White, unfocusedTextColor = White, cursorColor = Mint,
                    focusedIndicatorColor = Mint, unfocusedIndicatorColor = Panel))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "FAVOURITES").forEach { mode ->
                    val active = (mode == "FAVOURITES") == favouritesOnly
                    Surface(color = if (active) Mint.copy(alpha = 0.18f) else Panel, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { favouritesOnly = mode == "FAVOURITES" }) {
                        Text(mode, color = if (active) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
                    }
                }
                Text("${filtered.size} VIDEOS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Your movie shelf is waiting", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Add a video to start your private offline library.", color = Muted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.uri.toString() }) { movie ->
                        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(color = Midnight, shape = RoundedCornerShape(10.dp)) {
                                    Text("▶", color = Mint, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(movie.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text("LOCAL VIDEO", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(if (movie.favourite) "★" else "☆", color = Gold, fontSize = 20.sp,
                                    modifier = Modifier.clickable {
                                        val index = movies.indexOfFirst { it.uri == movie.uri }
                                        if (index >= 0) movies[index] = movies[index].copy(favourite = !movie.favourite)
                                        saveMovies()
                                    }.padding(5.dp))
                                Text("PLAY", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { selected = movie }.padding(7.dp))
                                Text("×", color = Muted, fontSize = 18.sp, modifier = Modifier.clickable {
                                    runCatching { context.contentResolver.releasePersistableUriPermission(movie.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                    movies.remove(movie)
                                    if (selected?.uri == movie.uri) { player.stop(); selected = null }
                                    saveMovies()
                                }.padding(5.dp))
                            }
                        }
                    }
                }
            }
            Text("Online creator publishing, licensed movie discovery and streaming will be added only with proper hosting, rights and bandwidth support.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}
