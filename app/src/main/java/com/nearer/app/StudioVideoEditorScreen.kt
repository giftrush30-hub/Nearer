package com.nearer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
internal fun StudioVideoEditorScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    var durationMs by remember { mutableStateOf(0L) }
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(0L) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("Choose your in and out points, then export a new MP4.") }
    var exportedFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration > 0L) {
                    durationMs = player.duration
                    if (endMs == 0L || endMs > player.duration) endMs = player.duration
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
    }

    val durationSeconds = (durationMs / 1000L).toFloat().coerceAtLeast(1f)
    val startSeconds = (startMs / 1000L).toFloat().coerceIn(0f, durationSeconds)
    val endSeconds = (endMs / 1000L).toFloat().coerceIn(startSeconds + 0.1f, durationSeconds.coerceAtLeast(startSeconds + 0.1f))

    Column(Modifier.fillMaxSize().background(Midnight).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("VIDEO CUT", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text(title, color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
        AndroidView(factory = { viewContext ->
            PlayerView(viewContext).apply { this.player = player; useController = true }
        }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().height(220.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("EDIT RANGE", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("IN  ·  ${formatStudioTime(startMs)}", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(value = startSeconds, onValueChange = { value ->
                    startMs = (value * 1000f).toLong().coerceAtMost((endMs - 500L).coerceAtLeast(0L))
                }, valueRange = 0f..(durationSeconds - 0.5f).coerceAtLeast(0f), enabled = durationMs > 0L)
                Text("OUT  ·  ${formatStudioTime(endMs)}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(value = endSeconds, onValueChange = { value ->
                    endMs = (value * 1000f).toLong().coerceAtLeast(startMs + 500L).coerceAtMost(durationMs)
                }, valueRange = (startSeconds + 0.5f).coerceAtMost(durationSeconds)..durationSeconds, enabled = durationMs > 0L)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startMs = player.currentPosition.coerceAtMost((endMs - 500L).coerceAtLeast(0L)) },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                        Text("SET IN")
                    }
                    Button(onClick = { endMs = player.currentPosition.coerceAtLeast(startMs + 500L).coerceAtMost(durationMs) },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) {
                        Text("SET OUT")
                    }
                }
                Text("Original video remains untouched. Export creates a separate trimmed MP4.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Button(onClick = {
            if (durationMs <= 0L || endMs <= startMs) {
                exportMessage = "Wait for the video to load and choose a valid trim range."
            } else {
                exportBusy = true
                exportedFile = null
                exportMessage = "Preparing your trimmed video…"
                val folder = File(context.filesDir, "studio-exports").apply { mkdirs() }
                val output = File(folder, "NeaRer_Cut_${System.currentTimeMillis()}.mp4")
                val clip = MediaItem.Builder().setUri(uri).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()
                ).build()
                val edited = EditedMediaItem.Builder(clip).build()
                val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        exportBusy = false
                        exportedFile = output
                        exportMessage = "Export complete · ${output.length() / 1024L} KB saved on this device."
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        exportBusy = false
                        exportMessage = "Export failed: ${exportException.message ?: "unsupported media or device encoder error"}"
                    }
                }).build()
                runCatching { transformer.start(edited, output.absolutePath) }.onFailure {
                    exportBusy = false
                    exportMessage = "Could not start export: ${it.message ?: "unknown error"}"
                }
            }
        }, enabled = !exportBusy && durationMs > 0L, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight), shape = RoundedCornerShape(14.dp)) {
            Text(if (exportBusy) "EXPORTING…" else "EXPORT TRIMMED VIDEO", fontWeight = FontWeight.Bold)
        }
        Text(exportMessage, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        exportedFile?.let { file ->
            Button(onClick = {
                runCatching {
                    val uriOut = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uriOut)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, "Share edited video"))
                }.onFailure { exportMessage = "Could not share this export: ${it.message}" }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) {
                Text("SHARE EXPORTED VIDEO")
            }
        }
    }
}

private fun formatStudioTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return String.format("%d:%02d", seconds / 60L, seconds % 60L)
}
