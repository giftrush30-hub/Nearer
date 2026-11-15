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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private data class StudioMixTrack(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val muted: Boolean = false,
    val gain: Float = 1f,
    val pan: Float = 0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {
    fun toProjectState() = StudioAudioProjectTrackState(
        uri = uri.toString(), name = name, durationMs = durationMs, muted = muted,
        gain = gain, pan = pan, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs
    )
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun StudioAudioMixerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val projectPreferences = remember(context) { context.getSharedPreferences("nearer_studio_audio_project", android.content.Context.MODE_PRIVATE) }
    val tracks = remember(context) {
        mutableStateListOf<StudioMixTrack>().apply {
            addAll(StudioAudioProjectCodec.decode(projectPreferences.getString("tracks_v1", null)).mapNotNull { saved ->
                runCatching { StudioMixTrack(Uri.parse(saved.uri), saved.name, saved.durationMs, saved.muted, saved.gain, saved.pan, saved.fadeInMs, saved.fadeOutMs) }.getOrNull()
            })
        }
    }
    LaunchedEffect(tracks.map { it.toProjectState() }) {
        projectPreferences.edit().putString("tracks_v1", StudioAudioProjectCodec.encode(tracks.map { it.toProjectState() })).apply()
    }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Build a layered mix from voice, music and sound effects stored on your phone.") }
    var exported by remember { mutableStateOf<File?>(null) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    val previewPlayer = remember(context) { ExoPlayer.Builder(context).build() }

    DisposableEffect(previewPlayer) { onDispose { previewPlayer.release() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        selected.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (tracks.none { it.uri == uri } && tracks.size < 8) {
                    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Audio track"
                    val duration = readStudioAudioDuration(context, uri)
                    tracks.add(StudioMixTrack(uri, name, duration))
                }
            }.onFailure { message = "Could not add one of the selected files: ${it.message ?: "access unavailable"}" }
        }
        if (tracks.size >= 8) message = "Eight tracks added. Each track now has its own mix controls."
        exported = null
    }

    Column(
        Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("NEARER SOUND DESK", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("Mix every layer.", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("A phone-friendly multitrack desk. Balance each layer, shape its entrance and ending, reorder the arrangement, then render one local mix.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Text("● PROJECT AUTO-SAVED ON THIS PHONE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("PROJECT TRACKS", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${tracks.size}/8", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (tracks.isEmpty()) {
                    Text("Start with a vocal take, beat, instrument, ambience or sound effect.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                } else tracks.forEachIndexed { index, track ->
                    Surface(color = Midnight, shape = RoundedCornerShape(15.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.name, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text("TRACK ${index + 1}  ·  ${if (track.durationMs > 0) formatMixTime(track.durationMs) else "DURATION UNKNOWN"}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(if (previewUri == track.uri && previewPlayer.isPlaying) "PAUSE" else "PREVIEW", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                    if (previewUri == track.uri && previewPlayer.isPlaying) previewPlayer.pause()
                                    else {
                                        previewUri = track.uri
                                        previewPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                                        previewPlayer.volume = track.gain.coerceIn(0f, 1f)
                                        previewPlayer.prepare()
                                        previewPlayer.play()
                                    }
                                }.padding(5.dp))
                                Text("↑", color = if (index > 0) Mint else Muted, modifier = Modifier.clickable(enabled = index > 0) {
                                    val item = tracks.removeAt(index); tracks.add(index - 1, item); exported = null
                                }.padding(4.dp))
                                Text("↓", color = if (index < tracks.lastIndex) Mint else Muted, modifier = Modifier.clickable(enabled = index < tracks.lastIndex) {
                                    val item = tracks.removeAt(index); tracks.add(index + 1, item); exported = null
                                }.padding(4.dp))
                                Text("×", color = Muted, fontSize = 18.sp, modifier = Modifier.clickable {
                                    tracks.removeAt(index)
                                    if (previewUri == track.uri) { previewPlayer.stop(); previewUri = null }
                                    exported = null
                                }.padding(4.dp))
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Mute layer", color = Muted, fontSize = 11.sp)
                                Switch(checked = track.muted, onCheckedChange = {
                                    tracks[index] = track.copy(muted = it); exported = null
                                })
                            }
                            Text("VOLUME  ·  ${(track.gain * 100).toInt()}%", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = track.gain, onValueChange = {
                                tracks[index] = track.copy(gain = it); exported = null
                                if (previewUri == track.uri) previewPlayer.volume = it
                            }, valueRange = 0f..1f)
                            val maxFade = (track.durationMs / 1000f).coerceAtMost(8f)
                            Text("STEREO PAN  ·  ${if (track.pan < -0.01f) "L ${(-track.pan * 100).toInt()}%" else if (track.pan > 0.01f) "R ${(track.pan * 100).toInt()}%" else "CENTRE"}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = track.pan, onValueChange = {
                                tracks[index] = track.copy(pan = it); exported = null
                            }, valueRange = -1f..1f)
                            Text("PAN IS APPLIED TO THE EXPORTED MIX", color = Muted, fontSize = 8.sp)
                            Text("FADE IN  ·  ${String.format(Locale.US, "%.1f", track.fadeInMs / 1000f)}s", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = (track.fadeInMs / 1000f).coerceIn(0f, maxFade), onValueChange = {
                                tracks[index] = track.copy(fadeInMs = (it * 1000f).toLong()); exported = null
                            }, valueRange = 0f..maxFade.coerceAtLeast(0.1f), enabled = track.durationMs > 0L)
                            Text("FADE OUT  ·  ${String.format(Locale.US, "%.1f", track.fadeOutMs / 1000f)}s", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = (track.fadeOutMs / 1000f).coerceIn(0f, maxFade), onValueChange = {
                                tracks[index] = track.copy(fadeOutMs = (it * 1000f).toLong()); exported = null
                            }, valueRange = 0f..maxFade.coerceAtLeast(0.1f), enabled = track.durationMs > 0L)
                            if (track.durationMs <= 0L) Text("Fade controls need readable duration metadata for this file.", color = Muted, fontSize = 9.sp)
                        }
                    }
                }
                Button(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = tracks.size < 8 && !exporting, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight), shape = RoundedCornerShape(13.dp)) {
                    Text("＋  ADD AUDIO LAYER", fontWeight = FontWeight.Bold)
                }
            }
        }
        Button(onClick = {
            val activeTracks = tracks.toList().filterNot { it.muted }
            if (activeTracks.isEmpty()) message = "Unmute at least one track before exporting."
            else {
                exporting = true; exported = null
                message = "Rendering ${activeTracks.size} track(s) with their own volume and fade envelopes…"
                val sequences = activeTracks.map { track ->
                    val effects = Effects(
                        listOf(
                            ToInt16PcmAudioProcessor(),
                            StudioTrackGainProcessor(track.gain, track.pan, track.durationMs, track.fadeInMs, track.fadeOutMs)
                        ),
                        emptyList()
                    )
                    val item = EditedMediaItem.Builder(MediaItem.fromUri(track.uri)).setEffects(effects).build()
                    EditedMediaItemSequence.Builder(listOf(item)).build()
                }
                val composition = Composition.Builder(sequences).build()
                val output = File(File(context.filesDir, "studio-exports").apply { mkdirs() }, "NeaRer_Mix_${System.currentTimeMillis()}.mp4")
                val transformer = Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            exporting = false
                            exported = output.takeIf { it.exists() && it.length() > 0L }
                            message = if (exported != null) "Mix rendered · ${output.length() / 1024L} KB. Original recordings and source tracks remain untouched."
                                else "The render finished but no output file was found. Please try again."
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            exporting = false
                            output.delete()
                            message = "This mix could not be rendered on this device: ${exportException.message ?: "encoder or source format not supported"}"
                        }
                    }).build()
                runCatching { transformer.start(composition, output.absolutePath) }.onFailure {
                    exporting = false; output.delete()
                    message = "Could not start the mix: ${it.message ?: "unknown error"}"
                }
            }
        }, enabled = !exporting && tracks.any { !it.muted }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight), shape = RoundedCornerShape(14.dp)) {
            Text(if (exporting) "RENDERING MIX…" else "RENDER FINAL MIX", fontWeight = FontWeight.ExtraBold)
        }
        Text(message, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        exported?.let { file ->
            Button(onClick = {
                runCatching {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/mp4")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share NeaRer mix"))
                }.onFailure { message = "Could not share the mix: ${it.message}" }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) {
                Text("SHARE FINISHED MIX")
            }
        }
        Text("Track volume, stereo balance, mute, reorder, and non-destructive fade-in/fade-out are applied during the offline render. Mono tracks are normalized to stereo so they can be mixed with stereo sources. Live simultaneous monitoring, EQ, reverb, tempo automation and multiple named projects are planned for the next audio-engine milestone.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
    }
}

private fun readStudioAudioDuration(context: android.content.Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatMixTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L)
}
