package com.nearer.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal data class StudioMovieExportClip(
    val id: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val timelineStartMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long
)

@OptIn(androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
internal data class StudioOfflineMovieExportRequest(
    val composition: Composition,
    val clips: List<StudioMovieExportClip>,
    val musicUri: String?,
    val musicMuted: Boolean,
    val outputFile: File,
    val workingDirectory: File,
    val minimumDurationMs: Long = 0L,
    val musicGain: Float = 0.7f
)

internal data class StudioOfflineMovieExportResult(
    val file: File,
    val durationMs: Long,
    val videoSamples: Long,
    val audioSamples: Long,
    val bytes: Long,
    val warnings: List<String>
)

/**
 * Cancellation-aware, offline-only orchestration for a complete movie export:
 * probe local audio -> decode/mix PCM -> encode AAC -> render video-only -> final MP4 remux.
 * All intermediate assets are private temporary files and are removed on success or failure.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
internal object StudioOfflineMovieExportCoordinator {
    private const val AUDIO_SAMPLE_RATE = 48_000
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun export(
        context: Context,
        request: StudioOfflineMovieExportRequest,
        onProgress: (String, Int?) -> Unit = { _, _ -> }
    ): StudioOfflineMovieExportResult {
        val coroutineContext = currentCoroutineContext()
        require(request.clips.isNotEmpty()) { "Add at least one video clip before exporting." }
        require(request.minimumDurationMs in 0L..StudioRenderPlanBuilder.MAX_DURATION_MS)
        require(request.outputFile.absoluteFile != request.workingDirectory.absoluteFile)

        val directory = request.workingDirectory
        require(directory.exists() || directory.mkdirs()) { "Could not create the movie export work folder." }
        val videoOnly = File.createTempFile("studio-video-only-", ".mp4", directory)
        check(videoOnly.delete()) { "Could not prepare the temporary video-only output path." }
        var pcmMaster: File? = null
        var aacOnly: File? = null
        var success = false

        fun cancelled(): Boolean = !coroutineContext.isActive
        fun progress(stage: String, percent: Int?) {
            if (Looper.myLooper() == Looper.getMainLooper()) onProgress(stage, percent)
            else mainHandler.post { if (coroutineContext.isActive) onProgress(stage, percent) }
        }

        try {
            progress("Checking local audio sources", null)
            val clipInputs = withContext(Dispatchers.IO) {
                request.clips.map { clip ->
                    check(!cancelled()) { "Movie export was cancelled." }
                    val source = StudioMovieAudioSourceProbe.inspect(context, Uri.parse(clip.sourceUri))
                    StudioMovieAudioClipInput(
                        id = clip.id,
                        sourceUri = clip.sourceUri,
                        timelineStartMs = clip.timelineStartMs,
                        sourceInMs = clip.sourceInMs,
                        sourceOutMs = clip.sourceOutMs,
                        sourceDurationMs = clip.sourceDurationMs,
                        hasAudioTrack = source.hasAudioTrack
                    )
                }
            }
            val music = request.musicUri?.takeUnless { request.musicMuted }?.let { uri ->
                val source = withContext(Dispatchers.IO) {
                    StudioMovieAudioSourceProbe.inspect(context, Uri.parse(uri))
                }
                require(source.hasAudioTrack) { "The selected background music has no audio track." }
                require(source.durationMs > 0L) { "Could not determine the background music duration." }
                StudioMovieMusicInput(uri, source.durationMs, request.musicGain)
            }
            val cuePlan = StudioMovieAudioCuePlanner.build(clipInputs, music, request.musicMuted)
            require(cuePlan.isValid) { cuePlan.errors.joinToString(" ") }
            val movieDurationMs = maxOf(cuePlan.durationMs, request.minimumDurationMs)
            progress("Mixing original clip audio and music", 0)

            val mixed = withContext(Dispatchers.IO) {
                StudioOfflinePcmMixPipeline.render(
                    context = context,
                    cues = cuePlan.cues,
                    sampleRate = AUDIO_SAMPLE_RATE,
                    workingDirectory = directory,
                    minimumDurationMs = movieDurationMs,
                    isCancelled = ::cancelled,
                    onProgress = { stage, done, total ->
                        val percent = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else null
                        progress(stage, percent)
                    }
                )
            }
            pcmMaster = mixed.pcmFile

            progress("Encoding synchronized AAC audio", 0)
            val encodedAudio = withContext(Dispatchers.IO) {
                StudioAndroidAacAudioExporter.export(
                    pcmMaster = mixed.pcmFile,
                    sampleRate = mixed.sampleRate,
                    frameCount = mixed.frameCount,
                    outputDirectory = directory,
                    isCancelled = ::cancelled,
                    onProgress = { done, total ->
                        val percent = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else null
                        progress("Encoding AAC audio", percent)
                    }
                )
            }
            aacOnly = encodedAudio.file

            progress("Rendering video and visual effects", null)
            renderVideoOnly(context, request.composition, videoOnly)

            check(!cancelled()) { "Movie export was cancelled." }
            progress("Assembling final MP4", 0)
            val muxed = withContext(Dispatchers.IO) {
                StudioFinalMovieMuxer.mux(
                    videoOnlyMp4 = videoOnly,
                    audioOnlyMp4 = encodedAudio.file,
                    outputMp4 = request.outputFile,
                    isCancelled = ::cancelled,
                    onProgress = { done, total ->
                        val percent = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else null
                        progress("Assembling final MP4", percent)
                    }
                )
            }
            success = true
            progress("Movie export complete", 100)
            return StudioOfflineMovieExportResult(
                file = muxed.file,
                durationMs = muxed.durationUs / 1_000L,
                videoSamples = muxed.videoSamples,
                audioSamples = muxed.audioSamples,
                bytes = muxed.bytes,
                warnings = cuePlan.warnings
            )
        } finally {
            videoOnly.delete()
            pcmMaster?.delete()
            aacOnly?.delete()
            if (!success) request.outputFile.delete()
        }
    }

    private suspend fun renderVideoOnly(
        context: Context,
        composition: Composition,
        outputFile: File
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine<Unit> { continuation ->
            try {
                val activeTransformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            continuation.completeSafely(Result.success(Unit))
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            continuation.completeSafely(Result.failure(exportException))
                        }
                    })
                    .build()
                continuation.invokeOnCancellation {
                    mainHandler.post { runCatching { activeTransformer.cancel() } }
                }
                activeTransformer.start(composition, outputFile.absolutePath)
            } catch (failure: Throwable) {
                continuation.completeSafely(Result.failure(failure))
            }
        }
    }

    private fun <T> CancellableContinuation<T>.completeSafely(result: Result<T>) {
        if (!isActive) return
        resumeWith(result)
    }
}
