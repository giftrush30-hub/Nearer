package com.nearer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.AudioManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun StudioAudioRecorderScreen(onBack: () -> Unit, onRecorded: (Uri, String) -> Unit) {
    val context = LocalContext.current
    val supportsUnprocessedInput = remember(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)?.equals("true", ignoreCase = true) == true
    }
    var showPitchMonitor by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("Record voice, narration, music ideas or sound effects. Everything stays on this phone.") }
    var output by remember { mutableStateOf<File?>(null) }
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) message = "Microphone permission is needed to record. You can still import and edit existing audio."
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            elapsedSeconds++
        }
    }
    val latestRecorder = rememberUpdatedState(recorder)
    val latestRecording = rememberUpdatedState(recording)
    DisposableEffect(Unit) {
        onDispose {
            val active = latestRecorder.value
            if (latestRecording.value) runCatching { active?.stop() }
            runCatching { active?.release() }
        }
    }

    if (showPitchMonitor) {
        StudioPitchMonitorScreen(onBack = { showPitchMonitor = false })
    } else {
    Column(Modifier.fillMaxSize().background(Midnight).padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (recording) "● RECORDING" else "‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !recording, onClick = onBack).padding(vertical = 8.dp))
            Text("VOICE RECORDER", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("Capture the moment.", color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Offline recording · AAC audio · Original take preserved", color = Mint, fontSize = 12.sp)
        Text(
            if (supportsUnprocessedInput) "STUDIO INPUT  ·  UNPROCESSED MIC" else "STUDIO INPUT  ·  VOICE-RECOGNITION FALLBACK",
            color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp
        )
        Button(onClick = {
            if (permissionGranted) showPitchMonitor = true
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = !recording, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)) {
            Text("OPEN LIVE PITCH & KEY MONITOR")
        }
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (recording) "● RECORDING" else "READY TO RECORD", color = if (recording) Gold else Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60), color = White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (recording) "Keep the phone steady and speak clearly." else "Use your phone microphone or a compatible connected mic.", color = Muted, fontSize = 12.sp)
                Button(onClick = {
                    if (!recording) {
                        if (!permissionGranted) permission.launch(Manifest.permission.RECORD_AUDIO)
                        else {
                            val file = File(File(context.filesDir, "studio-recordings").apply { mkdirs() }, "NeaRer_Take_${System.currentTimeMillis()}.m4a")
                            runCatching {
                                @Suppress("DEPRECATION")
                                val next = MediaRecorder()
                                recorder = next
                                next.setAudioSource(if (supportsUnprocessedInput) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION)
                                next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                next.setAudioEncodingBitRate(192000)
                                next.setAudioSamplingRate(44100)
                                next.setOutputFile(file.absolutePath)
                                next.prepare()
                                next.start()
                                recorder = next
                                output = file
                                elapsedSeconds = 0
                                recording = true
                                message = if (supportsUnprocessedInput) {
                                    "Recording with the device's unprocessed microphone path. Tap stop when finished."
                                } else {
                                    "Recording with Android's voice-recognition input fallback. Tap stop when finished."
                                }
                            }.onFailure { error ->
                                runCatching { recorder?.release() }
                                recorder = null
                                message = "Could not start recording: ${error.message ?: "microphone unavailable"}"
                            }
                        }
                    } else {
                        val file = output
                        val active = recorder
                        runCatching { active?.stop() }.onSuccess {
                            runCatching { active?.release() }
                            recorder = null
                            recording = false
                            if (file != null && file.exists() && file.length() > 1024L) {
                                message = "Take saved safely. Open it in the audio editor to trim and export."
                                onRecorded(Uri.fromFile(file), file.name)
                            } else message = "The take was too short or empty. Please record again."
                        }.onFailure {
                            runCatching { active?.release() }
                            recorder = null
                            recording = false
                            message = "Recording could not be finalised. Please try again."
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (recording) Gold else Mint, contentColor = Midnight)) {
                    Text(if (recording) "STOP & SAVE TAKE" else "●  START RECORDING", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Text(message, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        if (!recording) Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) { Text("BACK TO STUDIO") }
    }
    }
}

@Composable
internal fun StudioAudioEditorScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    var durationMs by remember { mutableStateOf(0L) }
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(0L) }
    var exportBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Preview your recording, set the cut points and export a new audio file.") }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var playhead by remember { mutableStateOf(0L) }
    val vocalPreferences = remember(context) { context.getSharedPreferences("nearer_studio_vocal_profile", android.content.Context.MODE_PRIVATE) }
    var selectedVocalProfile by remember(context) {
        mutableStateOf(StudioVocalProfile.values().firstOrNull { it.name == vocalPreferences.getString("profile", StudioVocalProfile.WARM_CONDENSER.name) } ?: StudioVocalProfile.WARM_CONDENSER)
    }
    LaunchedEffect(selectedVocalProfile) { vocalPreferences.edit().putString("profile", selectedVocalProfile.name).apply() }
    val vocalControlPreferences = remember(context) { context.getSharedPreferences("nearer_studio_vocal_controls", android.content.Context.MODE_PRIVATE) }
    var vocalControls by remember(context) {
        mutableStateOf(StudioVocalControlsCodec.decode(vocalControlPreferences.getString("controls_v1", null)))
    }
    LaunchedEffect(vocalControls) {
        vocalControlPreferences.edit().putString("controls_v1", StudioVocalControlsCodec.encode(vocalControls)).apply()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration > 0L) {
                    durationMs = player.duration
                    if (endMs == 0L || endMs > player.duration) endMs = player.duration
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) playhead = player.currentPosition }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
    }
    LaunchedEffect(player, durationMs) {
        while (true) {
            if (player.isPlaying) playhead = player.currentPosition
            delay(250)
        }
    }

    val durationSeconds = (durationMs / 1000L).toFloat().coerceAtLeast(1f)
    val startSeconds = (startMs / 1000L).toFloat().coerceIn(0f, durationSeconds)
    val endSeconds = (endMs / 1000L).toFloat().coerceIn(startSeconds + 0.25f, durationSeconds.coerceAtLeast(startSeconds + 0.25f))

    Column(Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("AUDIO EDIT", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
        AndroidView(factory = { viewContext -> PlayerView(viewContext).apply { this.player = player; useController = true } },
            update = { it.player = player }, modifier = Modifier.fillMaxWidth().height(150.dp))
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TRIM YOUR AUDIO", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("PLAYHEAD  ·  ${formatStudioAudioTime(playhead)} / ${formatStudioAudioTime(durationMs)}", color = Muted, fontSize = 10.sp)
                Slider(value = playhead.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)), onValueChange = { player.seekTo(it.toLong()); playhead = it.toLong() }, valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f), enabled = durationMs > 0L)
                Text("IN  ·  ${formatStudioAudioTime(startMs)}", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(value = startSeconds, onValueChange = { startMs = (it * 1000f).toLong().coerceAtMost((endMs - 250L).coerceAtLeast(0L)); exportedFile = null },
                    valueRange = 0f..(durationSeconds - 0.25f).coerceAtLeast(0f), enabled = durationMs > 0L)
                Text("OUT  ·  ${formatStudioAudioTime(endMs)}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(value = endSeconds, onValueChange = { endMs = (it * 1000f).toLong().coerceAtLeast(startMs + 250L).coerceAtMost(durationMs); exportedFile = null },
                    valueRange = (startSeconds + 0.25f).coerceAtMost(durationSeconds)..durationSeconds, enabled = durationMs > 0L)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startMs = player.currentPosition.coerceAtMost((endMs - 250L).coerceAtLeast(0L)) }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) { Text("SET IN") }
                    Button(onClick = { endMs = player.currentPosition.coerceAtLeast(startMs + 250L).coerceAtMost(durationMs) }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) { Text("SET OUT") }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("VOCAL PRO  ·  MANUAL STUDIO CONTROLS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Fine-tune the selected vocal profile. These settings are applied to the rendered copy; the original take stays untouched.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                Text("WARMTH  ·  " + "${(vocalControls.warmth * 100).toInt()}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = vocalControls.warmth, onValueChange = { vocalControls = vocalControls.copy(warmth = it); exportedFile = null }, valueRange = -1f..1f)
                Text("PRESENCE  ·  " + "${(vocalControls.presence * 100).toInt()}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = vocalControls.presence, onValueChange = { vocalControls = vocalControls.copy(presence = it); exportedFile = null }, valueRange = -0.2f..0.25f)
                Text("DE-ESSER  ·  " + "${(vocalControls.deEss * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = vocalControls.deEss, onValueChange = { vocalControls = vocalControls.copy(deEss = it); exportedFile = null }, valueRange = 0f..0.5f)
                Text("COMPRESSION  ·  " + "${vocalControls.compression.toInt()}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = vocalControls.compression, onValueChange = { vocalControls = vocalControls.copy(compression = it); exportedFile = null }, valueRange = 0f..4f)
                Text("ROOM AMBIENCE  ·  " + "${(vocalControls.ambience * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = vocalControls.ambience, onValueChange = { vocalControls = vocalControls.copy(ambience = it); exportedFile = null }, valueRange = 0f..0.15f)
                Text("OUTPUT LEVEL  ·  " + "${(vocalControls.outputGain * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = vocalControls.outputGain, onValueChange = { vocalControls = vocalControls.copy(outputGain = it); exportedFile = null }, valueRange = 0.6f..1.4f)
                Button(onClick = { vocalControls = StudioVocalControls(); exportedFile = null }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) { Text("RESET MANUAL CONTROLS") }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("CONDENSER STUDIO  ·  VOCAL POLISH", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Choose a vocal finish for the exported copy. The original take stays untouched.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                StudioVocalProfile.values().toList().chunked(2).forEach { rowProfiles ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowProfiles.forEach { profile ->
                            Button(onClick = { selectedVocalProfile = profile; exportedFile = null }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedVocalProfile == profile) Mint else Midnight,
                                    contentColor = if (selectedVocalProfile == profile) Midnight else White
                                )) {
                                Text(profile.title, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (rowProfiles.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
                Text("This offline chain adds a controlled low cut, presence, de-essing, gentle noise gate, compression, soft saturation and subtle room ambience. The live preview above remains the untouched source; the selected polish is applied during export.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                Text("PITCH CORRECTION / AUTO-TUNE IS A SEPARATE ENGINE MILESTONE — NOT SIMULATED BY THIS PRESET.", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Button(onClick = {
            if (durationMs <= 0L || endMs <= startMs) message = "Wait for the audio to load and choose a valid range."
            else {
                exportBusy = true
                exportedFile = null
                message = "Exporting a clean trimmed audio copy…"
                val output = File(File(context.filesDir, "studio-exports").apply { mkdirs() }, "NeaRer_Audio_${System.currentTimeMillis()}.m4a")
                val clip = MediaItem.Builder().setUri(uri).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()
                ).build()
                val vocalEffects = Effects(
                    listOf(ToInt16PcmAudioProcessor(), StudioVocalPolishProcessor(selectedVocalProfile, vocalControls)),
                    emptyList()
                )
                val edited = EditedMediaItem.Builder(clip).setEffects(vocalEffects).build()
                val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        exportBusy = false
                        exportedFile = output
                        message = "Audio export complete · ${output.length() / 1024L} KB saved on this device."
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        exportBusy = false
                        message = "Audio export failed: ${exportException.message ?: "unsupported format or device encoder error"}"
                    }
                }).build()
                runCatching { transformer.start(edited, output.absolutePath) }.onFailure {
                    exportBusy = false
                    message = "Could not start audio export: ${it.message ?: "unknown error"}"
                }
            }
        }, enabled = !exportBusy && durationMs > 0L, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight), shape = RoundedCornerShape(14.dp)) {
            Text(if (exportBusy) "EXPORTING…" else "EXPORT TRIMMED AUDIO", fontWeight = FontWeight.Bold)
        }
        Text(message, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        exportedFile?.let { file ->
            Button(onClick = {
                runCatching {
                    val outUri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/mp4")
                        .putExtra(Intent.EXTRA_STREAM, outUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share edited audio"))
                }.onFailure { message = "Could not share audio: ${it.message}" }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) {
                Text("SHARE EDITED AUDIO")
            }
        }
    }
}


private fun formatStudioAudioTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return String.format("%d:%02d", seconds / 60L, seconds % 60L)
}
