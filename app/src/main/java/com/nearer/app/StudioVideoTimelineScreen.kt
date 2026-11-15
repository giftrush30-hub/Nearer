package com.nearer.app

import android.content.Intent
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File

private data class StudioVideoClip(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val inMs: Long = 0L,
    val outMs: Long = durationMs
)

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun StudioVideoTimelineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val projectPreferences = remember(context) { context.getSharedPreferences("nearer_studio_video_timeline", android.content.Context.MODE_PRIVATE) }
    val restoredProject = remember(context) { StudioVideoProjectCodec.decode(projectPreferences.getString("project_v1", null)) }
    val clips = remember(context) {
        mutableStateListOf<StudioVideoClip>().apply {
            addAll(restoredProject.clips.map { saved -> StudioVideoClip(Uri.parse(saved.uri), saved.name, saved.durationMs, saved.inMs, saved.outMs) })
        }
    }
    var backgroundAudio by remember(context) { mutableStateOf(restoredProject.backgroundAudioUri?.let { Uri.parse(it) }) }
    var backgroundName by remember(context) { mutableStateOf(restoredProject.backgroundAudioName) }
    LaunchedEffect(clips.map { StudioVideoClipState(it.uri.toString(), it.name, it.durationMs, it.inMs, it.outMs) }, backgroundAudio?.toString(), backgroundName) {
        val project = StudioVideoProjectState(
            clips = clips.map { StudioVideoClipState(it.uri.toString(), it.name, it.durationMs, it.inMs, it.outMs) },
            backgroundAudioUri = backgroundAudio?.toString(),
            backgroundAudioName = backgroundName
        )
        projectPreferences.edit().putString("project_v1", StudioVideoProjectCodec.encode(project)).apply()
    }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Add video clips, arrange their order, trim each clip and optionally lay music underneath.") }
    var exported by remember { mutableStateOf<File?>(null) }
    val player = remember(context) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(clips.toList()) {
        if (clips.isNotEmpty()) {
            val previewItems = clips.map { clip ->
                MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.inMs).setEndPositionMs(clip.outMs).build()
                ).build()
            }
            player.setMediaItems(previewItems)
            player.prepare()
            player.playWhenReady = false
        } else player.clearMediaItems()
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        selected.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (clips.none { it.uri == uri } && clips.size < 12) {
                    val retriever = MediaMetadataRetriever()
                    val duration = try {
                        retriever.setDataSource(context, uri)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally { runCatching { retriever.release() } }
                    if (duration > 500L) {
                        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "Video clip ${clips.size + 1}"
                        clips.add(StudioVideoClip(uri, name, duration))
                    }
                }
            }.onFailure { message = "A selected video could not be read: ${it.message ?: "unsupported video"}" }
        }
        exported = null
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            backgroundAudio = uri
            backgroundName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Background audio"
            exported = null
        }.onFailure { message = "Could not access background audio: ${it.message}" }
    }

    Column(Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("VIDEO TIMELINE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("Shape the story.", color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Arrange up to 12 clips in sequence. Trim each one, preview the ordered reel and optionally add a looping music bed.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Text("● TIMELINE AUTO-SAVED ON THIS PHONE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (clips.isNotEmpty()) {
            AndroidView(factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true } },
                update = { it.player = player }, modifier = Modifier.fillMaxWidth().height(190.dp))
        } else Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Text("Your preview will appear here after you add video clips.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(18.dp))
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("EDIT SEQUENCE", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${clips.size}/12 CLIPS", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (clips.isEmpty()) Text("No clips yet. Choose two or more videos to begin a sequence.", color = Muted, fontSize = 12.sp)
                clips.forEachIndexed { index, clip ->
                    Surface(color = Midnight, shape = RoundedCornerShape(13.dp)) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("${index + 1}. ${clip.name}", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text("${formatStudioTimelineTime(clip.outMs - clip.inMs)} selected / ${formatStudioTimelineTime(clip.durationMs)} source", color = Mint, fontSize = 9.sp)
                                }
                                Text("↑", color = Mint, modifier = Modifier.clickable(enabled = index > 0) {
                                    val item = clips.removeAt(index); clips.add(index - 1, item); exported = null
                                }.padding(5.dp))
                                Text("↓", color = Mint, modifier = Modifier.clickable(enabled = index < clips.lastIndex) {
                                    val item = clips.removeAt(index); clips.add(index + 1, item); exported = null
                                }.padding(5.dp))
                                Text("×", color = Gold, modifier = Modifier.clickable { clips.removeAt(index); exported = null }.padding(5.dp))
                            }
                            Text("IN  ·  ${formatStudioTimelineTime(clip.inMs)}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = clip.inMs.toFloat(), onValueChange = { value ->
                                clips[index] = clip.copy(inMs = value.toLong().coerceAtMost(clip.outMs - 500L)); exported = null
                            }, valueRange = 0f..(clip.durationMs - 500L).coerceAtLeast(0L).toFloat())
                            Text("OUT  ·  ${formatStudioTimelineTime(clip.outMs)}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = clip.outMs.toFloat(), onValueChange = { value ->
                                clips[index] = clip.copy(outMs = value.toLong().coerceAtLeast(clip.inMs + 500L).coerceAtMost(clip.durationMs)); exported = null
                            }, valueRange = (clip.inMs + 500L).coerceAtMost(clip.durationMs).toFloat()..clip.durationMs.toFloat())
                        }
                    }
                }
                Button(onClick = { videoPicker.launch(arrayOf("video/*")) }, enabled = clips.size < 12, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                    Text("＋  ADD VIDEO CLIPS", fontWeight = FontWeight.Bold)
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BACKGROUND MUSIC", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(backgroundName.ifBlank { "Optional · no music selected" }, color = if (backgroundAudio == null) Muted else Mint, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) { Text("ADD MUSIC") }
                    if (backgroundAudio != null) Button(onClick = { backgroundAudio = null; backgroundName = ""; exported = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) { Text("REMOVE") }
                }
            }
        }
        Button(onClick = {
            if (clips.isEmpty()) message = "Add at least one video clip before exporting."
            else {
                exporting = true
                exported = null
                message = "Rendering ${clips.size} clip(s)… Keep NeaRer open until export finishes."
                val videoItems = clips.map { clip ->
                    val item = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.inMs).setEndPositionMs(clip.outMs).build()
                    ).build()
                    EditedMediaItem.Builder(item).build()
                }
                val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()
                val sequences = mutableListOf(videoSequence)
                if (backgroundAudio != null) {
                    val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(backgroundAudio!!)).build()
                    sequences.add(EditedMediaItemSequence.Builder(listOf(musicItem)).setIsLooping(true).build())
                }
                val composition = Composition.Builder(sequences).build()
                val output = File(File(context.filesDir, "studio-exports").apply { mkdirs() }, "NeaRer_Film_${System.currentTimeMillis()}.mp4")
                val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        exporting = false
                        exported = output
                        message = "Film export complete · ${output.length() / (1024L * 1024L)} MB. Your source clips remain unchanged."
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        exporting = false
                        message = "Film export failed: ${exportException.message ?: "unsupported source combination or device encoder error"}"
                    }
                }).build()
                runCatching { transformer.start(composition, output.absolutePath) }.onFailure {
                    exporting = false
                    message = "Could not start film export: ${it.message ?: "unknown error"}"
                }
            }
        }, enabled = !exporting && clips.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight), shape = RoundedCornerShape(14.dp)) {
            Text(if (exporting) "RENDERING…" else "EXPORT VIDEO SEQUENCE", fontWeight = FontWeight.ExtraBold)
        }
        Text(message, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        exported?.let { file ->
            Button(onClick = {
                runCatching {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share NeaRer film"))
                }.onFailure { message = "Could not share video: ${it.message}" }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) {
                Text("SHARE EXPORTED VIDEO")
            }
        }
        Text("The timeline and trim points are saved locally so you can return to your edit. This first timeline joins clips in sequence and can mix a looping music bed. It does not yet include crossfades, captions, stickers, speed ramps or frame-accurate waveform editing.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
    }
}


private fun formatStudioTimelineTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return String.format("%d:%02d", seconds / 60L, seconds % 60L)
}
