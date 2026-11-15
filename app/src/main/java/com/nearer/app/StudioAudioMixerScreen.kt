package com.nearer.app

import android.content.Intent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.delay
import java.io.File

private data class StudioMixTrack(
    val uri: Uri,
    val name: String,
    val muted: Boolean = false
)

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun StudioAudioMixerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tracks = remember { mutableStateListOf<StudioMixTrack>() }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Build a layered mix from voice, music and sound effects stored on your phone.") }
    var exported by remember { mutableStateOf<File?>(null) }
    var previewName by remember { mutableStateOf("") }
    val previewPlayer = remember(context) { ExoPlayer.Builder(context).build() }

    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        selected.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (tracks.none { it.uri == uri } && tracks.size < 8) {
                    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Audio track"
                    tracks.add(StudioMixTrack(uri, name))
                }
            }.onFailure { message = "Could not add one of the selected files: ${it.message ?: "access unavailable"}" }
        }
        if (tracks.size >= 8) message = "Eight tracks added. This first mixer keeps projects phone-friendly."
        exported = null
    }

    Column(
        Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("MULTITRACK MIX", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("Build your sound.", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("Layer up to eight local audio tracks. Layer local audio files together, mute tracks you don't want, and export one combined mix.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("YOUR TRACKS", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${tracks.size}/8", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (tracks.isEmpty()) {
                    Text("No tracks yet. Add a voice recording, background music or sound effect.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                } else tracks.forEachIndexed { index, track ->
                    Surface(color = Midnight, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.name, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text("TRACK ${index + 1}  ·  STARTS WITH THE MIX", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(if (previewName == track.name && previewPlayer.isPlaying) "PAUSE" else "PREVIEW", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                    if (previewName == track.name && previewPlayer.isPlaying) previewPlayer.pause()
                                    else {
                                        previewName = track.name
                                        previewPlayer.setMediaItem(MediaItem.fromUri(track.uri))
                                        previewPlayer.prepare()
                                        previewPlayer.play()
                                    }
                                }.padding(6.dp))
                                Text("×", color = Muted, fontSize = 18.sp, modifier = Modifier.clickable {
                                    tracks.removeAt(index)
                                    if (previewName == track.name) previewPlayer.stop()
                                    exported = null
                                }.padding(5.dp))
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Mute this track", color = Muted, fontSize = 11.sp)
                                Switch(checked = track.muted, onCheckedChange = {
                                    tracks[index] = track.copy(muted = it)
                                    exported = null
                                })
                            }
                        }
                    }
                }
                Button(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = tracks.size < 8, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight), shape = RoundedCornerShape(13.dp)) {
                    Text("＋  ADD AUDIO TRACK", fontWeight = FontWeight.Bold)
                }
            }
        }
        Button(onClick = {
            val activeTracks = tracks.filterNot { it.muted }
            if (activeTracks.isEmpty()) {
                message = "Unmute at least one track before exporting."
            } else {
                exporting = true
                exported = null
                message = "Mixing ${activeTracks.size} track(s)… Keep NeaRer open until export finishes."
                val sequences = activeTracks.map { track ->
                    val item = EditedMediaItem.Builder(MediaItem.fromUri(track.uri)).build()
                    EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).addItem(item).build()
                }
                val composition = Composition.Builder(sequences).build()
                val output = File(File(context.filesDir, "studio-exports").apply { mkdirs() }, "NeaRer_Mix_${System.currentTimeMillis()}.mp4")
                val transformer = Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            exporting = false
                            exported = output
                            message = "Mix exported · ${output.length() / 1024L} KB. Your source tracks are unchanged."
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            exporting = false
                            message = "This mix could not be exported on this device: ${exportException.message ?: "encoder or source format not supported"}"
                        }
                    }).build()
                runCatching { transformer.start(composition, output.absolutePath) }.onFailure {
                    exporting = false
                    message = "Could not start the mix: ${it.message ?: "unknown error"}"
                }
            }
        }, enabled = !exporting && tracks.any { !it.muted }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight), shape = RoundedCornerShape(14.dp)) {
            Text(if (exporting) "MIXING…" else "EXPORT COMBINED MIX", fontWeight = FontWeight.ExtraBold)
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
        Text("This first mixer supports layered start positions and mute/enable. Individual track gain, fades, panning, waveform editing and live multitrack playback are the next audio-engine steps.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
    }
}
