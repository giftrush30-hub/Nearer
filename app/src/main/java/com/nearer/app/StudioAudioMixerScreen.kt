package com.nearer.app

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import kotlinx.coroutines.CancellationException
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
    val fadeOutMs: Long = 0L,
    val lowGainDb: Float = 0f,
    val midGainDb: Float = 0f,
    val highGainDb: Float = 0f,
    val reverbMix: Float = 0f,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = durationMs,
    val startOffsetMs: Long = 0L
) {
    fun toProjectState() = StudioAudioProjectTrackState(
        uri = uri.toString(), name = name, durationMs = durationMs, muted = muted,
        gain = gain, pan = pan, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs,
        lowGainDb = lowGainDb, midGainDb = midGainDb, highGainDb = highGainDb, reverbMix = reverbMix,
        clipStartMs = clipStartMs, clipEndMs = clipEndMs, startOffsetMs = startOffsetMs
    )
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun StudioAudioMixerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val projectPreferences = remember(context) { context.getSharedPreferences("nearer_studio_audio_project", android.content.Context.MODE_PRIVATE) }
    val projects = remember(context) {
        mutableStateListOf<StudioAudioProjectLibraryEntry>().apply {
            addAll(StudioAudioProjectLibraryCodec.decode(projectPreferences.getString("projects_v1", null)))
            if (isEmpty()) add(StudioAudioProjectLibraryEntry(
                id = "studio-default", name = "My First Mix", updatedAtMs = System.currentTimeMillis(),
                tracks = StudioAudioProjectCodec.decode(projectPreferences.getString("tracks_v1", null))
            ))
        }
    }
    var currentProjectId by remember(context) { mutableStateOf(projects.first().id) }
    val tracks = remember(context) {
        mutableStateListOf<StudioMixTrack>().apply {
            addAll(projects.first().tracks.map { saved -> StudioMixTrack(
                Uri.parse(saved.uri), saved.name, saved.durationMs, saved.muted, saved.gain, saved.pan,
                saved.fadeInMs, saved.fadeOutMs, saved.lowGainDb, saved.midGainDb, saved.highGainDb, saved.reverbMix,
                saved.clipStartMs, saved.clipEndMs, saved.startOffsetMs
            ) })
        }
    }
    LaunchedEffect(currentProjectId, tracks.map { it.toProjectState() }) {
        val index = projects.indexOfFirst { it.id == currentProjectId }
        if (index >= 0) {
            projects[index] = projects[index].copy(tracks = tracks.map { it.toProjectState() }, updatedAtMs = System.currentTimeMillis())
            projectPreferences.edit().putString("projects_v1", StudioAudioProjectLibraryCodec.encode(projects.toList())).remove("tracks_v1").apply()
        }
    }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showProjectNameDialog by remember { mutableStateOf(false) }
    var showDeleteProjectDialog by remember { mutableStateOf(false) }
    var projectNameDraft by remember { mutableStateOf("") }
    var creatingProject by remember { mutableStateOf(true) }
    val currentProject = projects.firstOrNull { it.id == currentProjectId } ?: projects.first()
    var exported by remember { mutableStateOf<File?>(null) }
    fun switchProject(project: StudioAudioProjectLibraryEntry) {
        val oldIndex = projects.indexOfFirst { it.id == currentProjectId }
        if (oldIndex >= 0) projects[oldIndex] = projects[oldIndex].copy(tracks = tracks.map { it.toProjectState() }, updatedAtMs = System.currentTimeMillis())
        tracks.clear()
        tracks.addAll(project.tracks.map { saved -> StudioMixTrack(
            Uri.parse(saved.uri), saved.name, saved.durationMs, saved.muted, saved.gain, saved.pan,
            saved.fadeInMs, saved.fadeOutMs, saved.lowGainDb, saved.midGainDb, saved.highGainDb, saved.reverbMix,
            saved.clipStartMs, saved.clipEndMs, saved.startOffsetMs
        ) })
        currentProjectId = project.id
        exported = null
    }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Build a layered mix from voice, music and sound effects stored on your phone.") }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var finalMixSelected by remember { mutableStateOf(false) }
    var finalMixPlaying by remember { mutableStateOf(false) }
    var expandedTrackEffects by remember { mutableStateOf(setOf<String>()) }
    val waveformByUri = remember { mutableStateMapOf<String, List<Float>>() }
    var waveformRequestUri by remember { mutableStateOf<String?>(null) }
    var waveformLoadingUri by remember { mutableStateOf<String?>(null) }
    val previewPlayer = remember(context) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(waveformRequestUri) {
        val key = waveformRequestUri ?: return@LaunchedEffect
        val target = tracks.firstOrNull { it.uri.toString() == key }
        if (target != null && waveformByUri[key] == null) {
            waveformLoadingUri = key
            try {
                val waveform = StudioAudioWaveformExtractor.extract(context, target.uri, target.durationMs)
                waveformByUri[key] = waveform.peaks
                message = "Real audio waveform generated · ${waveform.peaks.size} amplitude windows."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = "Waveform unavailable: ${error.message ?: "this audio format could not be decoded"}"
            } finally {
                if (waveformLoadingUri == key) waveformLoadingUri = null
                if (waveformRequestUri == key) waveformRequestUri = null
            }
        }
    }

    DisposableEffect(previewPlayer) {
        val playbackListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                finalMixPlaying = isPlaying
            }
        }
        previewPlayer.addListener(playbackListener)
        onDispose {
            previewPlayer.removeListener(playbackListener)
            previewPlayer.release()
        }
    }

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

    if (showProjectPicker) AlertDialog(
        onDismissRequest = { showProjectPicker = false },
        title = { Text("Your Studio projects") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            projects.sortedByDescending { it.updatedAtMs }.forEach { project ->
                Button(onClick = { switchProject(project); showProjectPicker = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (project.id == currentProjectId) Mint else Panel,
                        contentColor = if (project.id == currentProjectId) Midnight else White
                    )) { Text("${if (project.id == currentProjectId) "✓  " else ""}${project.name} · ${project.tracks.size} tracks") }
            }
        } },
        confirmButton = { Button(onClick = { showProjectPicker = false }) { Text("CLOSE") } }
    )
    if (showProjectNameDialog) AlertDialog(
        onDismissRequest = { showProjectNameDialog = false },
        title = { Text(if (creatingProject) "Create a project" else "Rename project") },
        text = { OutlinedTextField(value = projectNameDraft, onValueChange = { projectNameDraft = it.take(48) },
            label = { Text("Project name") }, singleLine = true) },
        confirmButton = { Button(onClick = {
            val safeName = StudioAudioProjectLibraryCodec.normalizeName(projectNameDraft)
            if (creatingProject) {
                val previousIndex = projects.indexOfFirst { it.id == currentProjectId }
                if (previousIndex >= 0) projects[previousIndex] = projects[previousIndex].copy(
                    tracks = tracks.map { it.toProjectState() },
                    updatedAtMs = System.currentTimeMillis()
                )
                val created = StudioAudioProjectLibraryEntry(java.util.UUID.randomUUID().toString(), safeName, System.currentTimeMillis(), emptyList())
                projects.add(created)
                tracks.clear()
                currentProjectId = created.id
                exported = null
            } else {
                val index = projects.indexOfFirst { it.id == currentProjectId }
                if (index >= 0) projects[index] = projects[index].copy(name = safeName, updatedAtMs = System.currentTimeMillis())
            }
            showProjectNameDialog = false
        }) { Text(if (creatingProject) "CREATE" else "SAVE") } },
        dismissButton = { Button(onClick = { showProjectNameDialog = false }) { Text("CANCEL") } }
    )
    if (showDeleteProjectDialog) AlertDialog(
        onDismissRequest = { showDeleteProjectDialog = false },
        title = { Text("Delete this project?") },
        text = { Text("Only the saved arrangement is removed. Your original audio files are never deleted.") },
        confirmButton = { Button(onClick = {
            projects.removeAll { it.id == currentProjectId }
            val replacement = projects.first()
            tracks.clear()
            tracks.addAll(replacement.tracks.map { saved -> StudioMixTrack(
                Uri.parse(saved.uri), saved.name, saved.durationMs, saved.muted, saved.gain, saved.pan,
                saved.fadeInMs, saved.fadeOutMs, saved.lowGainDb, saved.midGainDb, saved.highGainDb, saved.reverbMix,
                saved.clipStartMs, saved.clipEndMs, saved.startOffsetMs
            ) })
            currentProjectId = replacement.id
            exported = null
            showDeleteProjectDialog = false
        }) { Text("DELETE") } },
        dismissButton = { Button(onClick = { showDeleteProjectDialog = false }) { Text("CANCEL") } }
    )
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
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("PROJECT LIBRARY  ·  ${projects.size}/20", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(currentProject.name, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${tracks.size} audio layers · Saved locally on this phone", color = Muted, fontSize = 10.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = { showProjectPicker = true }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) { Text("OPEN", fontSize = 9.sp) }
                    Button(onClick = { creatingProject = true; projectNameDraft = ""; showProjectNameDialog = true },
                        enabled = projects.size < 20, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) { Text("＋ NEW", fontSize = 9.sp) }
                    Button(onClick = { creatingProject = false; projectNameDraft = currentProject.name; showProjectNameDialog = true },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) { Text("RENAME", fontSize = 9.sp) }
                    Button(onClick = { showDeleteProjectDialog = true }, enabled = projects.size > 1,
                        colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Muted)) { Text("×") }
                }
            }
        }
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
                                Text(if (previewUri == track.uri && previewPlayer.isPlaying) "PAUSE" else "CLIP PREVIEW", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                    if (previewUri == track.uri && previewPlayer.isPlaying) previewPlayer.pause()
                                    else {
                                        finalMixSelected = false
                                        previewUri = track.uri
                                        val clipPreview = MediaItem.Builder().setUri(track.uri).apply {
                                            if (track.durationMs > 0L && track.clipEndMs > track.clipStartMs) {
                                                setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                                                    .setStartPositionMs(track.clipStartMs).setEndPositionMs(track.clipEndMs).build())
                                            }
                                        }.build()
                                        previewPlayer.setMediaItem(clipPreview)
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
                            val waveformKey = track.uri.toString()
                            val waveformPeaks = waveformByUri[waveformKey]
                            if (waveformPeaks == null) {
                                Button(
                                    onClick = { waveformRequestUri = waveformKey },
                                    enabled = waveformLoadingUri == null,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)
                                ) {
                                    Text(if (waveformLoadingUri == waveformKey) "DECODING AUDIO WAVEFORM…" else "GENERATE REAL WAVEFORM")
                                }
                            } else {
                                Text("REAL AUDIO WAVEFORM · SELECTED CLIP HIGHLIGHTED", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Canvas(
                                    modifier = Modifier.fillMaxWidth().height(54.dp)
                                        .background(Panel, RoundedCornerShape(8.dp))
                                ) {
                                    val gap = 2.dp.toPx()
                                    val barWidth = ((size.width - gap * (waveformPeaks.size - 1)) / waveformPeaks.size).coerceAtLeast(1f)
                                    val startFraction = if (track.durationMs > 0L) track.clipStartMs.toFloat() / track.durationMs else 0f
                                    val endFraction = if (track.durationMs > 0L) track.clipEndMs.toFloat() / track.durationMs else 1f
                                    waveformPeaks.forEachIndexed { bar, amplitude ->
                                        val fraction = (bar + 0.5f) / waveformPeaks.size
                                        val selected = fraction >= startFraction && fraction <= endFraction
                                        val x = bar * (barWidth + gap) + barWidth / 2f
                                        val halfHeight = (amplitude.coerceIn(0f, 1f) * size.height * 0.46f).coerceAtLeast(1.2f)
                                        drawLine(
                                            color = if (selected) Color(0xFF35D6B0) else Color(0xFF52636D),
                                            start = Offset(x, size.height / 2f - halfHeight),
                                            end = Offset(x, size.height / 2f + halfHeight),
                                            strokeWidth = barWidth.coerceAtLeast(1f),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
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
                            val clipDurationMs = if (track.durationMs > 0L) (track.clipEndMs - track.clipStartMs).coerceIn(1L, track.durationMs) else track.durationMs
                            val maxFade = (clipDurationMs / 1000f).coerceAtMost(8f)
                            Text("STEREO PAN  ·  ${if (track.pan < -0.01f) "L ${(-track.pan * 100).toInt()}%" else if (track.pan > 0.01f) "R ${(track.pan * 100).toInt()}%" else "CENTRE"}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = track.pan, onValueChange = {
                                tracks[index] = track.copy(pan = it); exported = null
                            }, valueRange = -1f..1f)
                            Text("PAN IS APPLIED TO THE EXPORTED MIX", color = Muted, fontSize = 8.sp)
                            val trackKey = track.uri.toString()
                            val effectsExpanded = trackKey in expandedTrackEffects
                            Text(
                                if (effectsExpanded) "HIDE TRACK EFFECTS  ▴" else "SHOW TRACK EFFECTS  ▾",
                                color = Mint,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    expandedTrackEffects = if (effectsExpanded) expandedTrackEffects - trackKey else expandedTrackEffects + trackKey
                                }.padding(vertical = 5.dp)
                            )
                            if (effectsExpanded) {
                                Text("LOW EQ  ·  ${track.lowGainDb.toInt()} dB", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Slider(value = track.lowGainDb, onValueChange = { tracks[index] = track.copy(lowGainDb = it); exported = null }, valueRange = -12f..12f)
                                Text("MID EQ  ·  ${track.midGainDb.toInt()} dB", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Slider(value = track.midGainDb, onValueChange = { tracks[index] = track.copy(midGainDb = it); exported = null }, valueRange = -12f..12f)
                                Text("HIGH EQ  ·  ${track.highGainDb.toInt()} dB", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Slider(value = track.highGainDb, onValueChange = { tracks[index] = track.copy(highGainDb = it); exported = null }, valueRange = -12f..12f)
                                Text("ROOM REVERB  ·  ${(track.reverbMix * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Slider(value = track.reverbMix, onValueChange = { tracks[index] = track.copy(reverbMix = it); exported = null }, valueRange = 0f..0.35f)
                                Text("CLIP PREVIEW PLAYS THE SELECTED SOURCE RANGE. EQ, REVERB, PAN AND FADES APPLY ON EXPORT.", color = Muted, fontSize = 8.sp, lineHeight = 12.sp)
                            }
                            if (track.durationMs > 0L) {
                                val minClip = 500L.coerceAtMost(track.durationMs)
                                val clipEnd = track.clipEndMs.coerceIn((track.clipStartMs + minClip).coerceAtMost(track.durationMs), track.durationMs)
                                val clipStart = track.clipStartMs.coerceIn(0L, (clipEnd - minClip).coerceAtLeast(0L))
                                Text("CLIP TIMELINE  ·  ${formatMixTime(clipStart)} – ${formatMixTime(clipEnd)}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Surface(color = Panel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("IN  ${formatMixTime(clipStart)}", color = Gold, fontSize = 8.sp)
                                        Text("▰  AUDIO CLIP  ·  ${formatMixTime(clipEnd - clipStart)}", color = Midnight, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.background(Mint, RoundedCornerShape(5.dp)).padding(7.dp))
                                        Text("OUT  ${formatMixTime(clipEnd)}", color = Gold, fontSize = 8.sp)
                                    }
                                }
                                Text("TRIM START · ${formatMixTime(clipStart)}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Slider(value = clipStart.toFloat(), onValueChange = { v ->
                                    val next = v.toLong().coerceIn(0L, (clipEnd - minClip).coerceAtLeast(0L))
                                    tracks[index] = track.copy(clipStartMs = next); exported = null
                                }, valueRange = 0f..(clipEnd - minClip).coerceAtLeast(0L).toFloat(), enabled = track.durationMs > minClip)
                                Text("TRIM END · ${formatMixTime(clipEnd)}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Slider(value = clipEnd.toFloat(), onValueChange = { v ->
                                    val next = v.toLong().coerceIn((clipStart + minClip).coerceAtMost(track.durationMs), track.durationMs)
                                    tracks[index] = track.copy(clipEndMs = next); exported = null
                                }, valueRange = (clipStart + minClip).coerceAtMost(track.durationMs).toFloat()..track.durationMs.toFloat(), enabled = track.durationMs > minClip)
                            }
                            Text("TRACK START POSITION · ${formatMixTime(track.startOffsetMs)}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = track.startOffsetMs.toFloat(), onValueChange = { v ->
                                tracks[index] = track.copy(startOffsetMs = v.toLong().coerceIn(0L, 30_000L)); exported = null
                            }, valueRange = 0f..30_000f, steps = 29)
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
                previewPlayer.pause()
                finalMixSelected = false
                previewUri = null
                exporting = true; exported = null
                message = "Rendering ${activeTracks.size} track(s) with their own volume and fade envelopes…"
                val temporarySilenceFiles = mutableListOf<File>()
                val sequences = activeTracks.map { track ->
                    val effects = Effects(
                        listOf(
                            ToInt16PcmAudioProcessor(),
                            StudioTrackGainProcessor(track.gain, track.pan, if (track.durationMs > 0L) track.clipEndMs - track.clipStartMs else track.durationMs, track.fadeInMs, track.fadeOutMs),
                            StudioTrackToneProcessor(track.lowGainDb, track.midGainDb, track.highGainDb, track.reverbMix)
                        ),
                        emptyList()
                    )
                    val sourceMediaItem = MediaItem.Builder().setUri(track.uri).apply {
                        if (track.durationMs > 0L && track.clipEndMs > track.clipStartMs) {
                            setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(track.clipStartMs).setEndPositionMs(track.clipEndMs).build())
                        }
                    }.build()
                    val item = EditedMediaItem.Builder(sourceMediaItem).setEffects(effects).build()
                    val sequenceItems = if (track.startOffsetMs > 0L) {
                        val silence = createStudioTimelineSilence(context, track.startOffsetMs)
                        temporarySilenceFiles += silence
                        listOf(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silence))).build(), item)
                    } else listOf(item)
                    EditedMediaItemSequence.Builder(sequenceItems).build()
                }
                val composition = Composition.Builder(sequences).build()
                val output = File(File(context.filesDir, "studio-exports").apply { mkdirs() }, "NeaRer_Mix_${System.currentTimeMillis()}.mp4")
                val transformer = Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            exporting = false
                            temporarySilenceFiles.forEach { it.delete() }
                            exported = output.takeIf { it.exists() && it.length() > 0L }
                            message = if (exported != null) "Mix rendered · ${output.length() / 1024L} KB. Original recordings and source tracks remain untouched."
                                else "The render finished but no output file was found. Please try again."
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            exporting = false
                            temporarySilenceFiles.forEach { it.delete() }
                            output.delete()
                            message = "This mix could not be rendered on this device: ${exportException.message ?: "encoder or source format not supported"}"
                        }
                    }).build()
                runCatching { transformer.start(composition, output.absolutePath) }.onFailure {
                    exporting = false; temporarySilenceFiles.forEach { it.delete() }; output.delete()
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
                    if (finalMixSelected && previewPlayer.isPlaying) {
                        previewPlayer.pause()
                    } else {
                        finalMixSelected = true
                        previewUri = null
                        previewPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                        previewPlayer.volume = 1f
                        previewPlayer.prepare()
                        previewPlayer.play()
                    }
                }.onFailure { message = "Could not play the rendered mix: ${it.message ?: "playback unavailable"}" }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)) {
                Text(if (finalMixSelected && finalMixPlaying) "PAUSE FINAL MIX" else "▶  PLAY FINAL MIX")
            }
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
        Text("Each named project saves its own clip trims, start positions and mix controls locally. Export applies real source clipping, timeline offsets, volume, pan, fades, EQ and reverb without changing original files. Waveform editing and tempo automation remain in progress.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
    }
}

/** Creates a bounded stereo PCM silence clip used to place a track later on the mix timeline. */
private fun createStudioTimelineSilence(context: android.content.Context, durationMs: Long): File {
    val safeDuration = durationMs.coerceIn(1L, 30_000L)
    val sampleRate = 48_000
    val channels = 2
    val bitsPerSample = 16
    val dataSize = (sampleRate.toLong() * channels * (bitsPerSample / 8) * safeDuration / 1_000L).toInt()
    val output = File(File(context.cacheDir, "studio-timeline").apply { mkdirs() }, "silence_${safeDuration}_${System.nanoTime()}.wav")
    java.io.FileOutputStream(output).use { stream ->
        fun ascii(value: String) { stream.write(value.toByteArray(Charsets.US_ASCII)) }
        fun le(value: Int, bytes: Int) { repeat(bytes) { stream.write((value ushr (it * 8)) and 0xff) } }
        ascii("RIFF"); le(36 + dataSize, 4); ascii("WAVE")
        ascii("fmt "); le(16, 4); le(1, 2); le(channels, 2)
        le(channels * bitsPerSample / 8, 2); le(bitsPerSample, 2)
        ascii("data"); le(dataSize, 4)
        val silence = ByteArray(16 * 1024)
        var remaining = dataSize
        while (remaining > 0) { val count = minOf(remaining, silence.size); stream.write(silence, 0, count); remaining -= count }
    }
    return output
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
