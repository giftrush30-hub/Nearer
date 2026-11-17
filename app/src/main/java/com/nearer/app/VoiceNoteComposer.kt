package com.nearer.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.io.File

@Composable
internal fun VoiceNoteComposer(
    enabled: Boolean,
    onSend: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else notice = "Microphone permission is required for voice notes."
    }

    fun discardRecording() {
        val active = recorder
        val file = recordingFile
        recorder = null
        recordingFile = null
        recording = false
        runCatching { active?.stop() }
        runCatching { active?.release() }
        file?.delete()
    }

    fun stopAndSend() {
        val active = recorder
        val file = recordingFile
        recorder = null
        recordingFile = null
        recording = false
        val stopped = runCatching { active?.stop(); active != null }.getOrDefault(false)
        runCatching { active?.release() }
        if (!stopped || file == null || !file.exists()) {
            file?.delete()
            notice = "The recording could not be finalised. Please try again."
            return
        }
        val descriptor = VoiceNotePolicy.create(
            fileName = file.name,
            mimeType = "audio/mp4",
            durationMillis = elapsedSeconds.coerceAtLeast(1) * 1_000L,
            byteCount = file.length()
        )
        if (descriptor == null) {
            file.delete()
            notice = "That voice note was empty or too long. Please record again."
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
        if (uri == null) {
            file.delete()
            notice = "NeaRer could not prepare the voice note for sharing."
            return
        }
        onSend(uri)
        notice = "Voice note added to the nearby file transfer."
    }

    fun startRecording() {
        if (!enabled || recording) return
        val folder = File(context.filesDir, "voice-notes")
        if (!folder.exists() && !folder.mkdirs()) {
            notice = "NeaRer could not create the voice-note folder."
            return
        }
        val file = File(folder, "NeaRer_Voice_${System.currentTimeMillis()}.m4a")
        var created: MediaRecorder? = null
        runCatching {
            created = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            created?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(96_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = created
            recordingFile = file
            elapsedSeconds = 0
            recording = true
            notice = "Recording… tap Stop & send when ready."
        }.onFailure {
            runCatching { created?.release() }
            file.delete()
            recorder = null
            recordingFile = null
            recording = false
            notice = "Could not start recording. Check microphone access and try again."
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1_000)
            if (elapsedSeconds + 1 >= (VoiceNotePolicy.MAX_DURATION_MILLIS / 1_000L).toInt()) {
                stopAndSend()
            } else {
                elapsedSeconds += 1
            }
        }
    }

    val latestRecorder = androidx.compose.runtime.rememberUpdatedState(recorder)
    val latestFile = androidx.compose.runtime.rememberUpdatedState(recordingFile)
    val latestRecording = androidx.compose.runtime.rememberUpdatedState(recording)
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && latestRecording.value) {
                runCatching { latestRecorder.value?.stop() }
                runCatching { latestRecorder.value?.release() }
                latestFile.value?.delete()
                recorder = null
                recordingFile = null
                recording = false
                notice = "Recording cancelled when NeaRer went into the background."
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            if (latestRecording.value) {
                runCatching { latestRecorder.value?.stop() }
                runCatching { latestRecorder.value?.release() }
                latestFile.value?.delete()
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = {
                    if (recording) stopAndSend()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recording) Gold else NeaRerPalette.AccentBlue,
                    contentColor = if (recording) Midnight else White
                )
            ) {
                Text(
                    if (recording) "■ Stop · ${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}" else "● Voice",
                    fontSize = 11.sp
                )
            }
            if (recording) {
                Button(
                    onClick = { discardRecording(); notice = "Voice recording cancelled." },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = White)
                ) { Text("×", fontSize = 12.sp) }
            }
        }
        if (notice.isNotBlank()) {
            Text(notice, color = if (recording) Gold else Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
internal fun InlineVoiceNotePlayer(file: File) {
    var player by remember(file.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file.absolutePath) { mutableStateOf(false) }
    DisposableEffect(file.absolutePath) {
        onDispose { runCatching { player?.release() }; player = null }
    }
    Button(
        onClick = {
            if (playing) {
                runCatching { player?.pause() }
                playing = false
            } else {
                runCatching {
                    if (player == null) {
                        player = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnCompletionListener {
                                playing = false
                                runCatching { it.seekTo(0) }
                            }
                            prepare()
                        }
                    }
                    player?.start()
                    playing = true
                }.onFailure { playing = false }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NeaRerPalette.RaisedPanel, contentColor = NeaRerPalette.Mint)
    ) { Text(if (playing) "Pause" else "Play", fontSize = 10.sp) }
}
