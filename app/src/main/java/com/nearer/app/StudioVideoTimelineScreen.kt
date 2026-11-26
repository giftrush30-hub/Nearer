package com.nearer.app

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TimestampWrapper
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.UUID

private data class StudioVideoClip(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val inMs: Long = 0L,
    val outMs: Long = durationMs
)

@OptIn(androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
@Composable
internal fun StudioVideoTimelineScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val projectPreferences = remember(context, projectId) { context.getSharedPreferences("nearer_studio_video_timeline_$projectId", android.content.Context.MODE_PRIVATE) }
    val restoredProject = remember(context, projectId) { StudioVideoProjectCodec.decode(projectPreferences.getString("project_v1", null)) }
    val clips = remember(context, projectId) {
        mutableStateListOf<StudioVideoClip>().apply {
            addAll(restoredProject.clips.map { saved -> StudioVideoClip(Uri.parse(saved.uri), saved.name, saved.durationMs, saved.inMs, saved.outMs) })
        }
    }
    var backgroundAudio by remember(context, projectId) { mutableStateOf(restoredProject.backgroundAudioUri?.let { Uri.parse(it) }) }
    var backgroundName by remember(context, projectId) { mutableStateOf(restoredProject.backgroundAudioName) }
    LaunchedEffect(projectId, clips.map { StudioVideoClipState(it.uri.toString(), it.name, it.durationMs, it.inMs, it.outMs) }, backgroundAudio?.toString(), backgroundName) {
        val project = StudioVideoProjectState(
            clips = clips.map { StudioVideoClipState(it.uri.toString(), it.name, it.durationMs, it.inMs, it.outMs) },
            backgroundAudioUri = backgroundAudio?.toString(),
            backgroundAudioName = backgroundName
        )
        val encoded = StudioVideoProjectCodec.encode(project)
        projectPreferences.edit().putString("project_v1", encoded).apply()
        withContext(Dispatchers.IO) {
            val store = StudioProjectLibraryStore(context)
            store.read().projects.firstOrNull { it.id == projectId }?.let { record ->
                store.upsert(record.copy(payloadJson = encoded, updatedAtMs = System.currentTimeMillis()))
            }
        }
    }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Add video clips, arrange their order, trim each clip and optionally lay music underneath.") }
    var exported by remember { mutableStateOf<File?>(null) }
    var exportProgress by remember { mutableStateOf<Int?>(null) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    val exportScope = rememberCoroutineScope()
    var exportPendingFile by remember { mutableStateOf<File?>(null) }
    var exportCancellationRequested by remember { mutableStateOf(false) }
    val gradePreferences = remember(context, projectId) { context.getSharedPreferences("nearer_studio_video_grade_$projectId", android.content.Context.MODE_PRIVATE) }
    val restoredGrade = remember(context, projectId) { StudioVideoGradeCodec.decode(gradePreferences.getString("grade_v1", null)) }
    var brightness by remember(context, projectId) { mutableStateOf(restoredGrade.brightness) }
    var contrast by remember(context, projectId) { mutableStateOf(restoredGrade.contrast) }
    var saturation by remember(context, projectId) { mutableStateOf(restoredGrade.saturation) }
    val chromaPreferences = remember(context, projectId) { context.getSharedPreferences("nearer_studio_chroma_$projectId", android.content.Context.MODE_PRIVATE) }
    var chromaKeyEnabled by remember(context, projectId) { mutableStateOf(chromaPreferences.getBoolean("enabled", false)) }
    var chromaKeyColor by remember(context, projectId) { mutableStateOf(chromaPreferences.getInt("key_color", android.graphics.Color.GREEN)) }
    var chromaTolerance by remember(context, projectId) { mutableStateOf(chromaPreferences.getFloat("tolerance", 0.22f).coerceIn(0f, 1f)) }
    var chromaSoftness by remember(context, projectId) { mutableStateOf(chromaPreferences.getFloat("softness", 0.12f).coerceIn(0.001f, 1f)) }
    var chromaDespill by remember(context, projectId) { mutableStateOf(chromaPreferences.getFloat("despill", 0.65f).coerceIn(0f, 1f)) }
    var replacementBackground by remember(context, projectId) { mutableStateOf(chromaPreferences.getString("background_uri", null)?.let(Uri::parse)) }
    val textPreferences = remember(context, projectId) { context.getSharedPreferences("nearer_studio_video_text_$projectId", android.content.Context.MODE_PRIVATE) }
    var textOverlay by remember(context, projectId) { mutableStateOf(StudioVideoTextOverlayCodec.decode(textPreferences.getString("overlay_v1", null))) }
    val captionPreferences = remember(context, projectId) { context.getSharedPreferences("nearer_studio_video_captions_$projectId", android.content.Context.MODE_PRIVATE) }
    val captionLayers = remember(context, projectId) {
        mutableStateListOf<StudioVideoCaptionLayer>().apply {
            addAll(StudioVideoCaptionCodec.decode(captionPreferences.getString("captions_v1", null)))
        }
    }
    LaunchedEffect(projectId, captionLayers.toList()) {
        captionPreferences.edit().putString("captions_v1", StudioVideoCaptionCodec.encode(captionLayers.toList())).apply()
        withContext(Dispatchers.IO) { StudioProjectLibraryStore(context).touch(projectId) }
    }
    val transitionPreferences = remember(context, projectId) { context.getSharedPreferences("nearer_studio_video_transitions_$projectId", android.content.Context.MODE_PRIVATE) }
    var transitionStyle by remember(context, projectId) {
        mutableStateOf(runCatching {
            StudioVideoTransitionStyle.valueOf(transitionPreferences.getString("style", StudioVideoTransitionStyle.CUT.name)!!)
        }.getOrDefault(StudioVideoTransitionStyle.CUT))
    }
    var transitionDurationMs by remember(context, projectId) { mutableStateOf(transitionPreferences.getInt("duration_ms", 350).coerceIn(200, 1_000)) }
    LaunchedEffect(projectId, transitionStyle, transitionDurationMs) {
        transitionPreferences.edit().putString("style", transitionStyle.name).putInt("duration_ms", transitionDurationMs).apply()
        withContext(Dispatchers.IO) { StudioProjectLibraryStore(context).touch(projectId) }
    }
    val multiTrackStore = remember(context, projectId) { StudioMultiTrackProjectStore(context, projectId) }
    var multiTrackProject by remember(context, projectId) { mutableStateOf(multiTrackStore.read()) }
    var musicMuted by remember(context, projectId) {
        mutableStateOf(multiTrackProject.tracks.firstOrNull { it.id == "audio-music" }?.muted ?: false)
    }
    var videoTrackLocked by remember(context, projectId) {
        mutableStateOf(multiTrackProject.tracks.firstOrNull { it.id == "video-main" }?.locked ?: false)
    }
    LaunchedEffect(projectId, clips.toList(), backgroundAudio?.toString(), musicMuted, videoTrackLocked, captionLayers.toList(), brightness, contrast, saturation, textOverlay) {
        delay(250L)
        var videoCursor = 0L
        val videoItems = clips.mapIndexed { index, clip ->
            val item = StudioTimelineItem(
                id = "clip-${clip.uri.toString().hashCode()}",
                sourceUri = clip.uri.toString(),
                timelineStartMs = videoCursor,
                sourceInMs = clip.inMs,
                sourceOutMs = clip.outMs
            )
            videoCursor = (videoCursor + item.durationMs).coerceAtMost(86_400_000L)
            item
        }
        val textItems = buildList {
            if (textOverlay.text.isNotBlank() && videoCursor > 0L) {
                add(StudioTimelineItem("main-title", null, 0L, 0L, videoCursor))
            }
            captionLayers.forEachIndexed { index, layer ->
                val clipOffset = clips.take(layer.clipIndex.coerceIn(0, clips.size)).sumOf { (it.outMs - it.inMs).coerceAtLeast(0L) }
                val duration = (layer.endMs - layer.startMs).coerceAtLeast(1L)
                if (layer.clipIndex in clips.indices && layer.text.isNotBlank()) {
                    add(StudioTimelineItem("caption-${layer.id.ifBlank { index.toString() }}", null,
                        (clipOffset + layer.startMs).coerceAtMost(86_400_000L), 0L, duration))
                }
            }
        }
        val effectItems = if (videoCursor > 0L && (brightness != 0f || contrast != 0f || saturation != 0f)) {
            listOf(StudioTimelineItem("colour-grade", null, 0L, 0L, videoCursor))
        } else emptyList()
        val synchronized = StudioMultiTrackEditor.fromVideoSources(
            videos = videoItems,
            audioUri = backgroundAudio?.toString(),
            audioDurationMs = videoCursor,
            audioMuted = musicMuted,
            textItems = textItems,
            effectItems = effectItems
        )
        val withVideoLock = StudioMultiTrackEditor.setLocked(synchronized, "video-main", videoTrackLocked) ?: synchronized
        multiTrackProject = withVideoLock
        val saved = withContext(Dispatchers.IO) { multiTrackStore.write(withVideoLock) }
        if (!saved) message = "Your movie is still editable, but the multi-track backup could not be saved. Check phone storage and try again."
    }
    fun videoEffects(clipIndex: Int = 0) = buildList<androidx.media3.common.Effect> {
        // The same draw-command bridge feeds the live player and Transformer export.
        val visualNodes = buildList {
            if (chromaKeyEnabled) add(StudioVisualEffectNode(
                id = "chroma-key",
                kind = StudioVisualEffectKind.CHROMA_KEY,
                parameters = mapOf("tolerance" to chromaTolerance, "softness" to chromaSoftness, "despill" to chromaDespill),
                keyColorArgb = chromaKeyColor
            ))
            if (saturation != 0f) add(StudioVisualEffectNode(
                id = "colour-saturation",
                kind = StudioVisualEffectKind.COLOR_GRADE,
                parameters = mapOf("saturation" to (1f + saturation / 100f))
            ))
        }
        val sourceClip = clips.getOrNull(clipIndex)
        val drawCommand = StudioVisualDrawCommand(
            trackId = "video-main",
            itemId = sourceClip?.let { "clip-${it.uri.toString().hashCode()}" } ?: "preview-clip-$clipIndex",
            sourceUri = sourceClip?.uri?.toString().orEmpty(),
            sourceTimeUs = (sourceClip?.inMs ?: 0L) * 1_000L,
            zIndex = 0,
            sourceKind = StudioVisualSourceKind.VIDEO,
            blendMode = StudioVisualBlendMode.NORMAL,
            transform = StudioVisualTransform(
                x = 0f, y = 0f, scaleX = 1f, scaleY = 1f,
                rotationDegrees = 0f, opacity = 1f,
                cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f,
                blurRadius = 0f, maskFeather = 0f
            ),
            masks = emptyList(),
            effects = visualNodes
        )
        val compiledVisualEffects = StudioVisualRenderBridge.compile(drawCommand)
        check(compiledVisualEffects.isValid) {
            "The selected visual effects cannot be rendered: ${compiledVisualEffects.errors.joinToString(" ")}"
        }
        addAll(compiledVisualEffects.effects)
        if (brightness != 0f) add(Brightness(brightness))
        if (contrast != 0f) add(Contrast(contrast))
        if (textOverlay.text.isNotBlank()) {
            val caption = SpannableString(textOverlay.text)
            caption.setSpan(ForegroundColorSpan(textOverlay.colorArgb), 0, caption.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            caption.setSpan(BackgroundColorSpan(Color.argb(150, 8, 14, 22)), 0, caption.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            caption.setSpan(StyleSpan(Typeface.BOLD), 0, caption.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val anchorY = when (textOverlay.verticalPosition) { 0 -> 0.78f; 1 -> 0f; else -> -0.78f }
            val settings = StaticOverlaySettings.Builder()
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, anchorY)
                .setScale(0.85f, 0.85f)
                .build()
            add(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(caption, settings))))
        }
        val timedLayers = captionLayers.filter { it.clipIndex == clipIndex && it.text.isNotBlank() }
        if (timedLayers.isNotEmpty()) add(OverlayEffect(timedLayers.map { StudioTimedTextOverlay(it) }))
        if (transitionStyle == StudioVideoTransitionStyle.FADE_TO_BLACK && clips.size > 1) {
            val clip = clips.getOrNull(clipIndex)
            if (clip != null) {
                StudioVideoTransitionPlanner.plan(
                    clipDurationMs = (clip.outMs - clip.inMs).coerceAtLeast(0L),
                    transitionDurationMs = transitionDurationMs,
                    includeFadeIn = clipIndex > 0,
                    includeFadeOut = clipIndex < clips.lastIndex
                ).forEach { segment ->
                    add(TimestampWrapper(Brightness(segment.brightness), segment.startUs, segment.endUs))
                }
            }
        }
    }
    LaunchedEffect(projectId, brightness, contrast, saturation) {
        gradePreferences.edit().putString("grade_v1", StudioVideoGradeCodec.encode(StudioVideoGrade(brightness, contrast, saturation))).apply()
        withContext(Dispatchers.IO) { StudioProjectLibraryStore(context).touch(projectId) }
    }
    LaunchedEffect(projectId, chromaKeyEnabled, chromaKeyColor, chromaTolerance, chromaSoftness, chromaDespill, replacementBackground?.toString()) {
        chromaPreferences.edit()
            .putBoolean("enabled", chromaKeyEnabled)
            .putInt("key_color", chromaKeyColor)
            .putFloat("tolerance", chromaTolerance)
            .putFloat("softness", chromaSoftness)
            .putFloat("despill", chromaDespill)
            .putString("background_uri", replacementBackground?.toString())
            .apply()
        withContext(Dispatchers.IO) { StudioProjectLibraryStore(context).touch(projectId) }
    }
    LaunchedEffect(projectId, textOverlay) {
        textPreferences.edit().putString("overlay_v1", StudioVideoTextOverlayCodec.encode(textOverlay)).apply()
        withContext(Dispatchers.IO) { StudioProjectLibraryStore(context).touch(projectId) }
    }
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            StudioExportFiles.cleanupAbandoned(File(context.filesDir, "studio-exports"))
        }
    }
    val latestExportJob by rememberUpdatedState(exportJob)
    DisposableEffect(projectId) {
        onDispose {
            // The coordinator cancels the active codec/render stage and removes all intermediate files.
            latestExportJob?.cancel()
        }
    }

    val player = remember(context, projectId) { ExoPlayer.Builder(context).build() }
    val compositionPlayer = remember(context, projectId) {
        CompositionPlayer.Builder(context)
            // Foreground video plus replacement image/video are separate visual sequences.
            // The default SingleInputVideoGraph cannot composite both input streams.
            .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
            .build()
    }
    var useCompositionPreview by remember(context, projectId) { mutableStateOf(false) }
    var compositionPreviewFailed by remember(context, projectId) { mutableStateOf(false) }

    DisposableEffect(projectId, player, compositionPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                compositionPreviewFailed = true
                useCompositionPreview = false
                message = "Layered preview is not supported for this media combination on this phone. The regular clip preview remains available."
            }
        }
        compositionPlayer.addListener(listener)
        onDispose {
            compositionPlayer.removeListener(listener)
            player.release()
            compositionPlayer.release()
        }
    }

    LaunchedEffect(projectId, clips.toList(), brightness, contrast, saturation, chromaKeyEnabled, chromaKeyColor, chromaTolerance, chromaSoftness, chromaDespill, replacementBackground?.toString(), backgroundAudio?.toString(), musicMuted, textOverlay, captionLayers.toList(), transitionStyle, transitionDurationMs, compositionPreviewFailed) {
        player.setVideoEffects(videoEffects(player.currentMediaItemIndex.coerceAtLeast(0)))
        if (clips.isEmpty()) {
            player.clearMediaItems()
            useCompositionPreview = false
        } else {
            val canLayerPreview = chromaKeyEnabled && replacementBackground != null && !compositionPreviewFailed
            val layered = if (canLayerPreview) runCatching {
                val movieDurationMs = clips.sumOf { (it.outMs - it.inMs).coerceAtLeast(0L) }.coerceAtLeast(1L)
                val videoItems = clips.mapIndexed { clipIndex, clip ->
                    val mediaItem = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.inMs)
                            .setEndPositionMs(clip.outMs)
                            .build()
                    ).build()
                    EditedMediaItem.Builder(mediaItem)
                        .setDurationUs(clip.durationMs.coerceAtLeast(1L) * 1_000L)
                        .setEffects(Effects(emptyList(), videoEffects(clipIndex)))
                        .build()
                }
                val sequences = mutableListOf(EditedMediaItemSequence.Builder(videoItems).build())
                val backgroundUri = replacementBackground!!
                val backgroundMime = context.contentResolver.getType(backgroundUri).orEmpty()
                val backgroundIsImage = backgroundMime.startsWith("image/")
                val backgroundMedia = if (backgroundIsImage) {
                    MediaItem.Builder().setUri(backgroundUri).setImageDurationMs(movieDurationMs).build()
                } else MediaItem.fromUri(backgroundUri)
                val backgroundEditedBuilder = EditedMediaItem.Builder(backgroundMedia).setRemoveAudio(true)
                if (!backgroundIsImage) {
                    val backgroundDurationMs = withContext(Dispatchers.IO) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, backgroundUri)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                                ?: error("Could not read the replacement video's duration.")
                        } finally { runCatching { retriever.release() } }
                    }
                    require(backgroundDurationMs > 0L) { "Replacement background video has no readable duration." }
                    backgroundEditedBuilder.setDurationUs(backgroundDurationMs * 1_000L)
                }
                sequences.add(EditedMediaItemSequence.Builder(listOf(backgroundEditedBuilder.build()))
                    .setIsLooping(!backgroundIsImage).build())

                if (backgroundAudio != null && !musicMuted) {
                    val musicUri = backgroundAudio!!
                    val musicDurationMs = withContext(Dispatchers.IO) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, musicUri)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                                ?: error("Could not read the music track duration.")
                        } finally { runCatching { retriever.release() } }
                    }
                    require(musicDurationMs > 0L) { "Music track has no readable duration." }
                    val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(musicUri))
                        .setDurationUs(musicDurationMs * 1_000L)
                        .build()
                    sequences.add(EditedMediaItemSequence.Builder(listOf(musicItem)).setIsLooping(true).build())
                }
                val composition = Composition.Builder(sequences).build()
                compositionPlayer.setComposition(composition)
                compositionPlayer.prepare()
                compositionPlayer.playWhenReady = false
                true
            }.getOrDefault(false) else false

            if (layered) {
                useCompositionPreview = true
            } else {
                useCompositionPreview = false
                val previewItems = clips.map { clip ->
                    MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.inMs).setEndPositionMs(clip.outMs).build()
                    ).build()
                }
                player.setMediaItems(previewItems)
                player.prepare()
                player.playWhenReady = false
            }
        }
    }

    DisposableEffect(projectId, player, clips.size, brightness, contrast, saturation, chromaKeyEnabled, chromaKeyColor, chromaTolerance, chromaSoftness, chromaDespill, textOverlay, captionLayers.toList(), transitionStyle, transitionDurationMs) {
        val captionListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                player.setVideoEffects(videoEffects(player.currentMediaItemIndex.coerceAtLeast(0)))
            }
        }
        player.addListener(captionListener)
        onDispose { player.removeListener(captionListener) }
    }

    val activePreviewPlayer: Player = if (useCompositionPreview) compositionPlayer else player

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
    val replacementBackgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            replacementBackground = uri
            exported = null
        }.onFailure { message = "Could not access replacement background: ${it.message}" }
    }

    Column(Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("VIDEO TIMELINE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("Shape the story.", color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Arrange up to 12 clips in sequence. Trim each one, preview the ordered reel and optionally add a looping music bed.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Text("● TIMELINE AUTO-SAVED ON THIS PHONE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("COLOUR LAB  ·  LIVE PREVIEW + EXPORT", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                Text("Fine-tune the look of your whole sequence. These same adjustments are used in the preview and final render.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                Text("BRIGHTNESS  ·  ${((brightness + 0.5f) * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = brightness, onValueChange = { brightness = it; exported = null }, valueRange = -0.5f..0.5f)
                Text("CONTRAST  ·  ${((contrast + 0.5f) * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = contrast, onValueChange = { contrast = it; exported = null }, valueRange = -0.5f..0.5f)
                Text("SATURATION  ·  ${saturation.toInt()}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = saturation, onValueChange = { saturation = it; exported = null }, valueRange = -50f..50f)
                Button(onClick = { brightness = 0f; contrast = 0f; saturation = 0f; exported = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) { Text("RESET COLOUR") }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GREEN SCREEN / BACKGROUND REPLACEMENT", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Remove a green or blue studio backdrop with an offline GPU keyer, soften the edge and reduce colour spill. Choose a replacement image or video underneath your subject.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                Button(onClick = { chromaKeyEnabled = !chromaKeyEnabled; exported = null },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (chromaKeyEnabled) Mint else Midnight, contentColor = if (chromaKeyEnabled) Midnight else White)) {
                    Text(if (chromaKeyEnabled) "CHROMA KEY · ON" else "TURN ON CHROMA KEY")
                }
                if (chromaKeyEnabled) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("GREEN" to android.graphics.Color.GREEN, "BLUE" to 0xFF0000FF.toInt()).forEach { (label, color) ->
                            Button(onClick = { chromaKeyColor = color; exported = null }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (chromaKeyColor == color) Mint else Midnight,
                                    contentColor = if (chromaKeyColor == color) Midnight else White)) { Text(label) }
                        }
                    }
                    Text("KEY TOLERANCE · ${(chromaTolerance * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Slider(value = chromaTolerance, onValueChange = { chromaTolerance = it; exported = null }, valueRange = 0.05f..0.65f)
                    Text("EDGE SOFTNESS · ${(chromaSoftness * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Slider(value = chromaSoftness, onValueChange = { chromaSoftness = it.coerceAtLeast(0.001f); exported = null }, valueRange = 0.01f..0.4f)
                    Text("SPILL CLEANUP · ${(chromaDespill * 100).toInt()}%", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Slider(value = chromaDespill, onValueChange = { chromaDespill = it; exported = null }, valueRange = 0f..1f)
                    Text(replacementBackground?.lastPathSegment ?: "No replacement background selected", color = if (replacementBackground == null) Gold else Mint, fontSize = 10.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(onClick = { replacementBackgroundPicker.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) { Text("CHOOSE BACKGROUND") }
                        if (replacementBackground != null) Button(onClick = { replacementBackground = null; exported = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) { Text("REMOVE") }
                    }
                    Text("The exported film composites your keyed foreground over this background. The current single-clip preview shows the keyed foreground; full layered preview is a separate integration step.", color = Muted, fontSize = 9.sp, lineHeight = 14.sp)
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TEXT & CAPTION OVERLAY", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                Text("Add a title, subtitle or creator tag. It appears in both the live preview and the exported clips.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                androidx.compose.material3.OutlinedTextField(
                    value = textOverlay.text,
                    onValueChange = { textOverlay = textOverlay.copy(text = it.take(80)); exported = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Type your video text") },
                    singleLine = true
                )
                Text("TEXT COLOUR", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        "WHITE" to 0xFFFFFFFF.toInt(),
                        "GOLD" to 0xFFFFD166.toInt(),
                        "MINT" to 0xFF67E8C5.toInt()
                    ).forEach { (label, color) ->
                        Button(onClick = { textOverlay = textOverlay.copy(colorArgb = color); exported = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (textOverlay.colorArgb == color) Mint else Midnight,
                                contentColor = if (textOverlay.colorArgb == color) Midnight else White
                            )) { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Text("PLACEMENT", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("TOP", "CENTRE", "BOTTOM").forEachIndexed { index, label ->
                        Button(onClick = { textOverlay = textOverlay.copy(verticalPosition = index); exported = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (textOverlay.verticalPosition == index) Mint else Midnight,
                                contentColor = if (textOverlay.verticalPosition == index) Midnight else White
                            )) { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                if (textOverlay.text.isNotBlank()) {
                    Button(onClick = { textOverlay = StudioVideoTextOverlay(); exported = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) {
                        Text("REMOVE TEXT OVERLAY")
                    }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TIMED CAPTION LAYERS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${captionLayers.size}/4", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("Build captions that appear only during selected moments of a clip. Each layer has its own clip, in/out timing, colour and screen position.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                if (clips.isEmpty()) Text("Add video clips first to create timed captions.", color = Muted, fontSize = 10.sp)
                captionLayers.forEachIndexed { layerIndex, layer ->
                    val selectedClip = clips.getOrNull(layer.clipIndex)
                    val layerDuration = selectedClip?.let { (it.outMs - it.inMs).coerceAtLeast(250L) } ?: 2_000L
                    Surface(color = Midnight, shape = RoundedCornerShape(13.dp)) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("LAYER ${layerIndex + 1}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("REMOVE", color = Gold, fontSize = 9.sp, modifier = Modifier.clickable { captionLayers.removeAt(layerIndex); exported = null }.padding(4.dp))
                            }
                            androidx.compose.material3.OutlinedTextField(
                                value = layer.text,
                                onValueChange = { captionLayers[layerIndex] = layer.copy(text = it.take(120)); exported = null },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Caption text") },
                                singleLine = false,
                                maxLines = 2
                            )
                            Text("APPLY TO CLIP", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                clips.forEachIndexed { clipIndex, clip ->
                                    Button(onClick = {
                                        val duration = (clip.outMs - clip.inMs).coerceAtLeast(250L)
                                        captionLayers[layerIndex] = layer.copy(clipIndex = clipIndex, startMs = 0L, endMs = minOf(2_000L, duration))
                                        exported = null
                                    }, modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (layer.clipIndex == clipIndex) Mint else Panel, contentColor = if (layer.clipIndex == clipIndex) Midnight else White)) {
                                        Text("CLIP ${clipIndex + 1}", fontSize = 8.sp)
                                    }
                                }
                            }
                            Text("IN · ${formatStudioTimelineTime(layer.startMs)}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = layer.startMs.toFloat().coerceIn(0f, (layerDuration - 250L).coerceAtLeast(0L).toFloat()),
                                onValueChange = { value ->
                                    captionLayers[layerIndex] = layer.copy(startMs = value.toLong().coerceAtMost(layer.endMs - 250L))
                                    exported = null
                                }, valueRange = 0f..(layerDuration - 250L).coerceAtLeast(0L).toFloat())
                            Text("OUT · ${formatStudioTimelineTime(layer.endMs)}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = layer.endMs.toFloat().coerceIn((layer.startMs + 250L).toFloat(), layerDuration.toFloat()),
                                onValueChange = { value ->
                                    captionLayers[layerIndex] = layer.copy(endMs = value.toLong().coerceAtLeast(layer.startMs + 250L).coerceAtMost(layerDuration))
                                    exported = null
                                }, valueRange = (layer.startMs + 250L).coerceAtMost(layerDuration).toFloat()..layerDuration.toFloat())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("WHITE" to 0xFFFFFFFF.toInt(), "GOLD" to 0xFFFFD166.toInt(), "MINT" to 0xFF67E8C5.toInt()).forEach { (label, color) ->
                                    Button(onClick = { captionLayers[layerIndex] = layer.copy(colorArgb = color); exported = null },
                                        modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp, 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (layer.colorArgb == color) Mint else Panel, contentColor = if (layer.colorArgb == color) Midnight else White)) {
                                        Text(label, fontSize = 8.sp)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("TOP", "CENTRE", "BOTTOM").forEachIndexed { position, label ->
                                    Button(onClick = { captionLayers[layerIndex] = layer.copy(verticalPosition = position); exported = null },
                                        modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp, 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (layer.verticalPosition == position) Mint else Panel, contentColor = if (layer.verticalPosition == position) Midnight else White)) {
                                        Text(label, fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = {
                    if (clips.isNotEmpty() && captionLayers.size < 4) {
                        val firstDuration = (clips.first().outMs - clips.first().inMs).coerceAtLeast(250L)
                        captionLayers.add(StudioVideoCaptionLayer(System.currentTimeMillis().toString(), "", 0, 0L, minOf(2_000L, firstDuration)))
                        exported = null
                    }
                }, enabled = clips.isNotEmpty() && captionLayers.size < 4, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                    Text("＋ ADD CAPTION LAYER")
                }
            }
        }

        if (clips.isNotEmpty()) {
            AndroidView(factory = { viewContext -> PlayerView(viewContext).apply { this.player = activePreviewPlayer; useController = true } },
                update = { it.player = activePreviewPlayer }, modifier = Modifier.fillMaxWidth().height(190.dp))
        } else Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Text("Your preview will appear here after you add video clips.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(18.dp))
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CLIP TRANSITIONS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Choose a clean cut or a fade through black between clips. Fade-through-black dims the end of one clip and brings in the next; it does not overlap the clips.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(StudioVideoTransitionStyle.CUT, StudioVideoTransitionStyle.FADE_TO_BLACK).forEach { style ->
                        Button(onClick = { transitionStyle = style; exported = null }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (transitionStyle == style) Mint else Midnight,
                                contentColor = if (transitionStyle == style) Midnight else White
                            )) {
                            Text(if (style == StudioVideoTransitionStyle.CUT) "CLEAN CUT" else "FADE TO BLACK", fontSize = 9.sp)
                        }
                    }
                }
                if (transitionStyle == StudioVideoTransitionStyle.FADE_TO_BLACK) {
                    Text("TRANSITION LENGTH · ${transitionDurationMs} ms", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Slider(value = transitionDurationMs.toFloat(), onValueChange = { transitionDurationMs = it.toInt(); exported = null }, valueRange = 200f..1_000f, steps = 7)
                    Text("The fade is applied to the outgoing and incoming edges of each middle clip. Very short clips automatically shorten the fade to fit.", color = Muted, fontSize = 9.sp, lineHeight = 14.sp)
                }
            }
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
                                Text("↑", color = Mint, modifier = Modifier.clickable(enabled = index > 0 && !videoTrackLocked) {
                                    val item = clips.removeAt(index); clips.add(index - 1, item)
                                    val remapped = remapStudioCaptionLayersForClipMove(captionLayers.toList(), index, index - 1)
                                    captionLayers.clear(); captionLayers.addAll(remapped)
                                    exported = null
                                }.padding(5.dp))
                                Text("↓", color = Mint, modifier = Modifier.clickable(enabled = index < clips.lastIndex && !videoTrackLocked) {
                                    val item = clips.removeAt(index); clips.add(index + 1, item)
                                    val remapped = remapStudioCaptionLayersForClipMove(captionLayers.toList(), index, index + 1)
                                    captionLayers.clear(); captionLayers.addAll(remapped)
                                    exported = null
                                }.padding(5.dp))
                                Text("×", color = if (videoTrackLocked) Muted else Gold, modifier = Modifier.clickable(enabled = !videoTrackLocked) {
                                    clips.removeAt(index)
                                    val remapped = removeStudioCaptionLayersForClip(captionLayers.toList(), index)
                                    captionLayers.clear(); captionLayers.addAll(remapped)
                                    exported = null
                                }.padding(5.dp))
                            }
                            Text("IN  ·  ${formatStudioTimelineTime(clip.inMs)}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = clip.inMs.toFloat(), onValueChange = { value ->
                                clips[index] = clip.copy(inMs = value.toLong().coerceAtMost(clip.outMs - 500L)); exported = null
                            }, valueRange = 0f..(clip.durationMs - 500L).coerceAtLeast(0L).toFloat(), enabled = !videoTrackLocked)
                            Text("OUT  ·  ${formatStudioTimelineTime(clip.outMs)}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Slider(value = clip.outMs.toFloat(), onValueChange = { value ->
                                clips[index] = clip.copy(outMs = value.toLong().coerceAtLeast(clip.inMs + 500L).coerceAtMost(clip.durationMs)); exported = null
                            }, valueRange = (clip.inMs + 500L).coerceAtMost(clip.durationMs).toFloat()..clip.durationMs.toFloat(), enabled = !videoTrackLocked)
                        }
                    }
                }
                Button(onClick = { videoPicker.launch(arrayOf("video/*")) }, enabled = clips.size < 12 && !videoTrackLocked, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                    Text("＋  ADD VIDEO CLIPS", fontWeight = FontWeight.Bold)
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("MULTI-TRACK LAYERS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${multiTrackProject.tracks.size} LANES", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text("Your picture, music, words and visual adjustments are kept on separate editing lanes.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                multiTrackProject.tracks.forEach { track ->
                    Surface(color = Midnight, shape = RoundedCornerShape(11.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(track.name, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("${track.items.size} item(s)", color = Mint, fontSize = 9.sp)
                            }
                            if (track.kind == StudioTrackKind.AUDIO && backgroundAudio != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text(if (musicMuted) "UNMUTE MUSIC" else "MUTE MUSIC", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { musicMuted = !musicMuted }.padding(5.dp))
                                }
                            }
                            if (track.kind == StudioTrackKind.VIDEO) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text(if (videoTrackLocked) "UNLOCK VIDEO LANE" else "LOCK VIDEO LANE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { videoTrackLocked = !videoTrackLocked }.padding(5.dp))
                                }
                            }
                            if (track.items.isNotEmpty()) {
                                Text(track.items.joinToString("   ·   ") { "${formatStudioTimelineTime(it.timelineStartMs)}  +${formatStudioTimelineTime(it.durationMs)}" },
                                    color = Muted, fontSize = 9.sp)
                            } else {
                                Text("No items on this lane yet", color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
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
            var preflightCursor = 0L
            val preflightVideoItems = clips.mapIndexed { index, clip ->
                val item = StudioTimelineItem(
                    id = "preflight-video-$index",
                    sourceUri = clip.uri.toString(),
                    timelineStartMs = preflightCursor,
                    sourceInMs = clip.inMs,
                    sourceOutMs = clip.outMs
                )
                preflightCursor += item.durationMs
                item
            }
            val preflightTracks = mutableListOf(
                StudioTimelineTrack("video-main", "Main video", StudioTrackKind.VIDEO, items = preflightVideoItems)
            )
            if (backgroundAudio != null) {
                preflightTracks += StudioTimelineTrack(
                    "audio-music", "Background music", StudioTrackKind.AUDIO, muted = musicMuted,
                    items = listOf(StudioTimelineItem("music-bed", backgroundAudio.toString(), 0L, 0L, preflightCursor.coerceAtLeast(1L)))
                )
            }
            val preflight = StudioRenderPlanBuilder.build(StudioMultiTrackProject(preflightTracks, preflightCursor))
            if (clips.isEmpty()) {
                message = "Add at least one video clip before exporting."
            } else if (chromaKeyEnabled && replacementBackground == null) {
                message = "Choose a replacement image or video before exporting the chroma-keyed film."
            } else if (!preflight.isValid) {
                message = "Export preflight stopped: " + preflight.errors.joinToString(" ")
            } else {
                exporting = true
                exported = null
                exportProgress = 0
                exportCancellationRequested = false
                message = "Preparing the complete movie export…"
                val videoItems = clips.mapIndexed { clipIndex, clip ->
                    val item = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.inMs).setEndPositionMs(clip.outMs).build()
                    ).build()
                    // Audio is rendered separately through the offline PCM/AAC pipeline so clip
                    // sound and music are mixed once, not duplicated by Transformer.
                    EditedMediaItem.Builder(item).setRemoveAudio(true)
                        .setEffects(Effects(emptyList(), videoEffects(clipIndex))).build()
                }
                val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()
                val sequences = mutableListOf(videoSequence)
                if (chromaKeyEnabled && replacementBackground != null) {
                    val movieDurationMs = clips.sumOf { (it.outMs - it.inMs).coerceAtLeast(0L) }
                    val backgroundUri = replacementBackground!!
                    val backgroundMime = context.contentResolver.getType(backgroundUri).orEmpty()
                    val backgroundMedia = if (backgroundMime.startsWith("image/")) {
                        MediaItem.Builder().setUri(backgroundUri).setImageDurationMs(movieDurationMs.coerceAtLeast(1L)).build()
                    } else {
                        MediaItem.fromUri(backgroundUri)
                    }
                    val backgroundItem = EditedMediaItem.Builder(backgroundMedia).setRemoveAudio(true).build()
                    sequences.add(EditedMediaItemSequence.Builder(listOf(backgroundItem))
                        .setIsLooping(!backgroundMime.startsWith("image/")).build())
                }
                val composition = Composition.Builder(sequences).build()
                val outputDirectory = File(context.filesDir, "studio-exports")
                val exportId = UUID.randomUUID().toString()
                val pending = StudioExportFiles.pendingFile(outputDirectory, exportId)
                val finalOutput = StudioExportFiles.finalFile(outputDirectory, exportId)
                exportPendingFile = pending

                runCatching {
                    if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                        error("Could not create the export folder. Check available storage.")
                    }
                    var timelineCursor = 0L
                    val audioClips = clips.mapIndexed { index, clip ->
                        val item = StudioMovieExportClip(
                            id = "clip-$index-${clip.uri.toString().hashCode()}",
                            sourceUri = clip.uri.toString(),
                            sourceDurationMs = clip.durationMs,
                            timelineStartMs = timelineCursor,
                            sourceInMs = clip.inMs,
                            sourceOutMs = clip.outMs
                        )
                        timelineCursor += (clip.outMs - clip.inMs).coerceAtLeast(0L)
                        item
                    }
                    exportJob = exportScope.launch {
                        try {
                            val result = StudioOfflineMovieExportCoordinator.export(
                                context = context,
                                request = StudioOfflineMovieExportRequest(
                                    composition = composition,
                                    clips = audioClips,
                                    musicUri = backgroundAudio?.toString(),
                                    musicMuted = musicMuted,
                                    outputFile = pending,
                                    workingDirectory = outputDirectory,
                                    minimumDurationMs = timelineCursor
                                ),
                                onProgress = { stage, percent ->
                                    message = stage
                                    exportProgress = percent
                                }
                            )
                            if (exportCancellationRequested) {
                                StudioExportFiles.discard(pending)
                                exported = null
                                message = "Export cancelled. Temporary media was removed; your project and original clips are safe."
                            } else if (StudioExportFiles.publish(pending, finalOutput)) {
                                exported = finalOutput
                                exportProgress = 100
                                message = "Film export complete · ${result.bytes / (1024L * 1024L)} MB. Original clips remain unchanged."
                                if (result.warnings.isNotEmpty()) message += " " + result.warnings.joinToString(" ")
                            } else {
                                StudioExportFiles.discard(pending)
                                exported = null
                                message = "The assembled movie did not pass the final file safety check. Your project is still saved."
                            }
                        } catch (cancelled: CancellationException) {
                            StudioExportFiles.discard(pending)
                            exported = null
                            message = "Export cancelled. Temporary media was removed; your project and original clips are safe."
                        } catch (failure: Throwable) {
                            StudioExportFiles.discard(pending)
                            exported = null
                            exportProgress = null
                            message = if (exportCancellationRequested) {
                                "Export cancelled. Temporary media was removed; your project and original clips are safe."
                            } else {
                                "Film export failed: ${failure.message ?: "unsupported source combination or device encoder error"}. Your project is still saved."
                            }
                        } finally {
                            exporting = false
                            exportJob = null
                            exportPendingFile = null
                        }
                    }
                }.onFailure { failure ->
                    StudioExportFiles.discard(pending)
                    exporting = false
                    exportJob = null
                    exportPendingFile = null
                    exportProgress = null
                    message = "Could not start film export: ${failure.message ?: "unknown error"}. Your project is still saved."
                }
            }
        }, enabled = !exporting && clips.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight), shape = RoundedCornerShape(14.dp)) {
            Text(if (exporting) "RENDERING…" else "EXPORT COMPLETE MOVIE", fontWeight = FontWeight.ExtraBold)
        }
        if (exporting) {
            Text(
                exportProgress?.let { "EXPORT PROGRESS · $it%" } ?: "EXPORT PROGRESS · Preparing encoder…",
                color = Mint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            if (exportProgress == null) {
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = exportProgress!!.coerceIn(0, 100) / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = {
                    if (exportJob != null) {
                        exportCancellationRequested = true
                        message = "Cancelling export safely…"
                        exportJob?.cancel()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)
            ) { Text("CANCEL EXPORT") }
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
        Text("The timeline, trims, colour grade, title and timed caption layers are saved locally. Timed captions are attached to individual clips. Crossfades remain unsupported by the current Media3 composition pipeline; fade-through-black and timed caption layers are now available in the editor.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
    }
}


private fun formatStudioTimelineTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return String.format("%d:%02d", seconds / 60L, seconds % 60L)
}
