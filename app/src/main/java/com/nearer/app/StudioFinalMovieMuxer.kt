package com.nearer.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
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
    private const val MAX_SAMPLE_BYTES = StudioFinalMovieMuxPolicy.MAX_SAMPLE_BYTES

    private data class TrackState(
        val extractor: MediaExtractor,
        val targetTrack: Int,
        val mime: String,
        var buffer: ByteBuffer,
        var lastPresentationTimeUs: Long = -1L,
        var samplesWritten: Long = 0L
    ) {
        val sampleTimeUs: Long get() = extractor.sampleTime
        val hasSample: Boolean get() = sampleTimeUs >= 0L
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
                videoExtractor, targetVideoTrack, videoMime,
                allocateSampleBuffer(videoFormat)
            )
            audioState = TrackState(
                audioExtractor, targetAudioTrack, audioMime,
                allocateSampleBuffer(audioFormat)
            )
            val durationUs = maxOf(
                videoFormat.getLongOrZero(MediaFormat.KEY_DURATION),
                audioFormat.getLongOrZero(MediaFormat.KEY_DURATION)
            )

            while (videoState.hasSample || audioState.hasSample) {
                check(!isCancelled()) { "Final movie export was cancelled." }
                val nextName = StudioFinalMovieMuxPolicy.nextTrack(
                    videoState.sampleTimeUs, audioState.sampleTimeUs,
                    videoState.hasSample, audioState.hasSample
                ) ?: break
                val next = if (nextName == "video") videoState else audioState
                val pts = next.sampleTimeUs
                require(StudioFinalMovieMuxPolicy.isTimestampAfter(next.lastPresentationTimeUs, pts)) {
                    "The ${next.mime} source contains duplicate or out-of-order sample timestamps."
                }
                if (Build.VERSION.SDK_INT >= 28) {
                    val declaredSampleBytes = next.extractor.sampleSize
                    require(declaredSampleBytes in 1L..MAX_SAMPLE_BYTES.toLong()) {
                        "A media sample exceeds the safe remux buffer limit."
                    }
                    if (declaredSampleBytes > next.buffer.capacity()) {
                        next.buffer = ByteBuffer.allocateDirect(declaredSampleBytes.toInt())
                    }
                }
                val bytesRead = next.extractor.readSampleData(next.buffer, 0)
                require(bytesRead > 0 && bytesRead <= MAX_SAMPLE_BYTES) {
                    "Could not read a complete encoded media sample within the safe buffer limit."
                }
                val flags = next.extractor.sampleFlags
                val info = MediaCodec.BufferInfo().apply { set(0, bytesRead, pts, flags) }
                next.buffer.position(0)
                next.buffer.limit(bytesRead)
                muxer.writeSampleData(next.targetTrack, next.buffer, info)
                next.lastPresentationTimeUs = pts
                next.samplesWritten++
                next.extractor.advance()
                onProgress(pts, durationUs)
            }

            require(videoState.samplesWritten > 0L) { "The rendered video contains no samples." }
            require(audioState.samplesWritten > 0L) { "The AAC track contains no samples." }
            require(durationUs > 0L) { "The source tracks do not report a usable movie duration." }
            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            validateFinalMp4(outputMp4)
            success = true
            return StudioFinalMovieMuxResult(
                outputMp4, videoState.samplesWritten, audioState.samplesWritten,
                durationUs,
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

    private fun allocateSampleBuffer(format: MediaFormat): ByteBuffer {
        val declared = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(0)
        } else 0
        require(declared <= MAX_SAMPLE_BYTES) { "A track declares an input sample larger than the safe remux limit." }
        val capacity = maxOf(1024 * 1024, declared).coerceAtMost(MAX_SAMPLE_BYTES)
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
            val durationUs = (0 until extractor.trackCount).mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                if (format.containsKey(MediaFormat.KEY_DURATION)) runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrNull() else null
            }.maxOrNull() ?: 0L
            require(hasVideo && hasAudio && durationUs > 0L) {
                "The finished MP4 is missing a media track or a valid duration."
            }
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.getLongOrZero(key: String): Long =
        if (containsKey(key)) runCatching { getLong(key) }.getOrDefault(0L) else 0L
}
