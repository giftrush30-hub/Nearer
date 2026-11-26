package com.nearer.app

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class StudioOfflinePcmMixResult(
    val pcmFile: File,
    val sampleRate: Int,
    val frameCount: Long,
    val durationMs: Long,
    val peakBeforeLimiter: Float,
    val peakAfterLimiter: Float,
    val decodedSourceCount: Int
)

/**
 * End-to-end offline staging pipeline: decode each unique local source to disk-backed Float
 * PCM, then mix the timeline in bounded blocks into a new Float PCM file.
 *
 * The resulting file is an intermediate audio master, not an MP4/AAC export. A minimum
 * timeline duration allows silent movies to receive a valid, synchronized silent AAC track.
 */
internal object StudioOfflinePcmMixPipeline {
    fun render(
        context: Context,
        cues: List<StudioAudioMixCue>,
        sampleRate: Int,
        workingDirectory: File,
        minimumDurationMs: Long = 0L,
        isCancelled: () -> Boolean = { false },
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }
    ): StudioOfflinePcmMixResult {
        val validation = StudioAudioMixPlanner.validate(cues, sampleRate)
        require(validation.isValid) { validation.errors.joinToString() }
        require(workingDirectory.exists() || workingDirectory.mkdirs()) { "Could not create Studio audio work directory." }

        require(minimumDurationMs in 0L..StudioRenderPlanBuilder.MAX_DURATION_MS) {
            "Minimum audio duration is outside the supported 24-hour range."
        }
        val activeCues = cues.filterNot { it.muted }
        val timelineEndMs = maxOf(activeCues.maxOfOrNull { it.timelineEndMs } ?: 0L, minimumDurationMs)
        val totalFrames = studioAudioMillisecondsToFrames(timelineEndMs, sampleRate)
        require(totalFrames > 0L) { "The audio timeline is empty." }
        require(totalFrames <= sampleRate.toLong() * 60L * 60L * 24L) { "Audio render exceeds the 24-hour safety limit." }
        val estimatedBytes = totalFrames * 8L
        val usableBytes = workingDirectory.usableSpace
        require(estimatedBytes > 0L && estimatedBytes <= usableBytes) {
            "There is not enough free phone storage for the temporary audio master."
        }

        val decodedFiles = linkedMapOf<String, StudioDecodedPcmInfo>()
        val sources = linkedMapOf<String, StudioDecodedPcmFileSource>()
        val output = File.createTempFile("studio-audio-master-", ".f32", workingDirectory)
        try {
            activeCues.forEachIndexed { index, cue ->
                check(!isCancelled()) { "Audio render was cancelled." }
                val uri = cue.sourceUri
                val decoded = decodedFiles[uri] ?: run {
                    onProgress("Decoding audio", index.toLong(), activeCues.size.toLong())
                    StudioAndroidAudioDecoder.decodeToFloatPcm(
                        context, Uri.parse(uri), workingDirectory, sampleRate, isCancelled
                    ).also { info ->
                        decodedFiles[uri] = info
                        // Decoded sources and the final Float master coexist temporarily.
                        // Recheck free space after every new source instead of trusting only
                        // the initial master-file estimate.
                        require(workingDirectory.usableSpace >= estimatedBytes) {
                            "Phone storage is too low to finish the audio master safely."
                        }
                    }
                }
                sources[cue.id] = sources[decoded.file.absolutePath]
                    ?: StudioDecodedPcmFileSource(decoded).also { sources[decoded.file.absolutePath] = it }
                onProgress("Preparing tracks", (index + 1).toLong(), activeCues.size.toLong())
            }

            var peakBefore = 0f
            var peakAfter = 0f
            var timelineFrame = 0L
            BufferedOutputStream(FileOutputStream(output), 64 * 1024).use { stream ->
                while (timelineFrame < totalFrames) {
                    check(!isCancelled()) { "Audio render was cancelled." }
                    val blockFrames = minOf(
                        StudioPcmBlockMixer.MAX_BLOCK_FRAMES.toLong(),
                        totalFrames - timelineFrame
                    ).toInt()
                    val block = FloatArray(blockFrames * 2)
                    val result = StudioPcmBlockMixer.mixBlock(
                        activeCues, sources, timelineFrame, blockFrames, sampleRate, block
                    )
                    peakBefore = maxOf(peakBefore, result.peakBeforeLimiter)
                    peakAfter = maxOf(peakAfter, result.peakAfterLimiter)
                    val bytes = ByteBuffer.allocate(block.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                    block.forEach { bytes.putFloat(it) }
                    stream.write(bytes.array(), 0, bytes.position())
                    timelineFrame += blockFrames
                    onProgress("Mixing audio", timelineFrame, totalFrames)
                }
            }
            return StudioOfflinePcmMixResult(
                output, sampleRate, totalFrames, timelineEndMs, peakBefore, peakAfter, decodedFiles.size
            )
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            sources.values.distinct().forEach { runCatching { it.close() } }
            decodedFiles.values.forEach { it.file.delete() }
        }
    }
}
