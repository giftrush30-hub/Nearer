package com.nearer.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

internal data class StudioFinalMovieMuxResult(
    val file: File,
    val videoSamples: Long,
    val audioSamples: Long,
    val durationUs: Long,
    val bytes: Long
)

/**
 * Remuxes a rendered video-only MP4 and a separately encoded AAC-only MP4 into one MP4.
 *
 * Encoded samples are copied without decoding or recompression. The two extractors are advanced
 * in presentation-time order so MediaMuxer receives a correctly interleaved stream. This is the
 * final container stage; it does not render effects or alter media pixels.
 */
internal object StudioFinalMovieMuxer {
    private const val MAX_SAMPLE_BYTES = 32 * 1024 * 1024

    private data class TrackState(
        val extractor: MediaExtractor,
        val sourceTrack: Int,
        val targetTrack: Int,
        val mime: String,
        var buffer: ByteBuffer,
        var lastPresentationTimeUs: Long = -1L,
        var samplesWritten: Long = 0L
    ) {
        val sampleTimeUs: Long get() = extractor.sampleTime
        val sampleSize: Long get() = extractor.sampleSize
        val hasSample: Boolean get() = sampleTimeUs >= 0L && sampleSize >= 0L
    }

    fun mux(
        videoOnlyMp4: File,
        audioOnlyMp4: File,
        outputMp4: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): StudioFinalMovieMuxResult {
        require(videoOnlyMp4.isFile && videoOnlyMp4.canRead()) { "The rendered video file is unavailable." }
        require(audioOnlyMp4.isFile && audioOnlyMp4.canRead()) { "The AAC audio file is unavailable." }
        require(videoOnlyMp4.absoluteFile != audioOnlyMp4.absoluteFile &&
            outputMp4.absoluteFile != videoOnlyMp4.absoluteFile &&
            outputMp4.absoluteFile != audioOnlyMp4.absoluteFile) { "Input and output files must be different." }
        outputMp4.parentFile?.let { require(it.exists() || it.mkdirs()) { "Could not create the final movie folder." } }
        require(!outputMp4.exists() || outputMp4.length() == 0L) { "The final movie destination already contains data." }

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var videoState: TrackState? = null
        var audioState: TrackState? = null
        var success = false

        try {
            videoExtractor.setDataSource(videoOnlyMp4.absolutePath)
            audioExtractor.setDataSource(audioOnlyMp4.absolutePath)

            val videoTrack = findSingleTrack(videoExtractor, "video/")
            val audioTrack = findSingleTrack(audioExtractor, "audio/")
            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            require(videoMime.startsWith("video/")) { "The rendered file has no supported video track." }
            require(audioMime == "audio/mp4a-latm") { "The audio file must contain an AAC track." }

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)
            muxer = MediaMuxer(outputMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val targetVideoTrack = muxer.addTrack(videoFormat)
            val targetAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            videoState = TrackState(
                videoExtractor, videoTrack, targetVideoTrack, videoMime,
                allocateSampleBuffer(videoFormat, videoExtractor)
            )
            audioState = TrackState(
                audioExtractor, audioTrack, targetAudioTrack, audioMime,
                allocateSampleBuffer(audioFormat, audioExtractor)
            )
            val durationUs = maxOf(
                videoFormat.getLongOrZero(MediaFormat.KEY_DURATION),
                audioFormat.getLongOrZero(MediaFormat.KEY_DURATION)
            )

            while (videoState.hasSample || audioState.hasSample) {
                check(!isCancelled()) { "Final movie export was cancelled." }
                val next = when {
                    !videoState.hasSample -> audioState
                    !audioState.hasSample -> videoState
                    videoState.sampleTimeUs <= audioState.sampleTimeUs -> videoState
                    else -> audioState
                }
                val pts = next.sampleTimeUs
                require(pts >= 0L) { "A media sample has an invalid presentation timestamp." }
                require(next.lastPresentationTimeUs < 0L || pts > next.lastPresentationTimeUs) {
                    "The ${next.mime} source contains duplicate or out-of-order sample timestamps."
                }
                val sampleBytes = next.sampleSize
                require(sampleBytes in 1L..MAX_SAMPLE_BYTES.toLong()) {
                    "A media sample exceeds the safe remux buffer limit."
                }
                if (next.buffer.capacity() < sampleBytes.toInt()) {
                    next.buffer = ByteBuffer.allocateDirect(sampleBytes.toInt())
                }
                next.buffer.clear()
                val bytesRead = next.extractor.readSampleData(next.buffer, 0)
                require(bytesRead == sampleBytes.toInt()) { "Could not read a complete encoded media sample." }
                val flags = next.extractor.sampleFlags
                val info = MediaCodec.BufferInfo().apply { set(0, bytesRead, pts, flags) }
                next.buffer.position(0)
                next.buffer.limit(bytesRead)
                muxer.writeSampleData(next.targetTrack, next.buffer, info)
                next.lastPresentationTimeUs = pts
                next.samplesWritten++
                next.extractor.advance()
                onProgress(maxOf(videoState.lastPresentationTimeUs, audioState.lastPresentationTimeUs).coerceAtLeast(0L), durationUs)
            }

            require(videoState.samplesWritten > 0L) { "The rendered video contains no samples." }
            require(audioState.samplesWritten > 0L) { "The AAC track contains no samples." }
            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            validateFinalMp4(outputMp4)
            success = true
            return StudioFinalMovieMuxResult(
                outputMp4, videoState.samplesWritten, audioState.samplesWritten,
                maxOf(videoState.lastPresentationTimeUs, audioState.lastPresentationTimeUs),
                outputMp4.length()
            )
        } catch (failure: Throwable) {
            outputMp4.delete()
            throw failure
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            if (!success) outputMp4.delete()
        }
    }

    private fun findSingleTrack(extractor: MediaExtractor, prefix: String): Int {
        val matches = (0 until extractor.trackCount).filter {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
        }
        require(matches.size == 1) { "Expected exactly one $prefix track; found ${matches.size}." }
        return matches.single()
    }

    private fun allocateSampleBuffer(format: MediaFormat, extractor: MediaExtractor): ByteBuffer {
        val declared = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(0)
        } else 0
        val current = extractor.sampleSize.coerceAtLeast(0L).coerceAtMost(MAX_SAMPLE_BYTES.toLong()).toInt()
        val capacity = maxOf(64 * 1024, declared, current).coerceAtMost(MAX_SAMPLE_BYTES)
        return ByteBuffer.allocateDirect(capacity)
    }

    private fun validateFinalMp4(file: File) {
        require(file.isFile && file.length() >= 12L) { "The final movie container is empty or incomplete." }
        java.io.RandomAccessFile(file, "r").use { input ->
            input.seek(4L)
            val type = ByteArray(4)
            input.readFully(type)
            require(String(type, Charsets.US_ASCII) == "ftyp") { "The final movie is not an MP4 container." }
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val hasVideo = (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            val hasAudio = (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            require(hasVideo && hasAudio) { "The finished MP4 is missing its video or audio track." }
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.getLongOrZero(key: String): Long =
        if (containsKey(key)) runCatching { getLong(key) }.getOrDefault(0L) else 0L
}
