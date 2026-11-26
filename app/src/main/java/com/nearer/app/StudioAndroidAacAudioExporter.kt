package com.nearer.app

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class StudioAacAudioExportResult(
    val file: File,
    val sampleRate: Int,
    val channelCount: Int,
    val frameCount: Long,
    val durationMs: Long,
    val encodedBytes: Long
)

/**
 * Encodes the offline mixer's little-endian stereo Float PCM master to an AAC-LC audio-only
 * MP4 container. The resulting local audio asset can be combined with the rendered video track
 * by the final movie mux stage. Codec output is inspected at runtime rather than assumed.
 */
internal object StudioAndroidAacAudioExporter {
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val MAX_IDLE_POLLS = 500
    private const val PCM_READ_FRAMES = 4_096

    fun export(
        pcmMaster: File,
        sampleRate: Int,
        frameCount: Long,
        outputDirectory: File,
        bitRate: Int = 192_000,
        isCancelled: () -> Boolean = { false },
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): StudioAacAudioExportResult {
        val config = StudioAacEncodingConfig(sampleRate, 2, bitRate)
        val validation = StudioAacEncodingPlanner.validate(config)
        require(validation.isValid) { validation.errors.joinToString() }
        require(pcmMaster.isFile && pcmMaster.canRead()) { "The mixed PCM audio master is unavailable." }
        require(frameCount > 0L) { "The audio master is empty." }
        val expectedBytes = Math.multiplyExact(frameCount, 8L)
        require(pcmMaster.length() == expectedBytes) { "The PCM master size does not match its frame count." }
        require(outputDirectory.exists() || outputDirectory.mkdirs()) { "Could not create audio export directory." }
        val output = File.createTempFile("studio-aac-audio-", ".mp4", outputDirectory)
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var codecStarted = false
        var muxerStarted = false
        var trackIndex = -1
        var inputFramePosition = 0L
        var inputEosQueued = false
        var outputEosSeen = false
        var idlePolls = 0
        var encodedBytes = 0L
        val timestampGuard = StudioMuxTimestampGuard()

        try {
            val format = MediaFormat.createAudioFormat(
                StudioAacEncodingPlanner.AAC_LC_MIME, sampleRate, 2
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PCM_READ_FRAMES * 4)
            }
            codec = MediaCodec.createEncoderByType(StudioAacEncodingPlanner.AAC_LC_MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            codecStarted = true
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            BufferedInputStream(FileInputStream(pcmMaster), 64 * 1024).use { input ->
                val info = MediaCodec.BufferInfo()
                val pcmBytes = ByteArray(PCM_READ_FRAMES * 8)
                while (!outputEosSeen) {
                    check(!isCancelled()) { "AAC audio export was cancelled." }
                    var madeProgress = false

                    if (!inputEosQueued) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val codecInput = codec.getInputBuffer(inputIndex)
                                ?: error("AAC encoder supplied no input buffer.")
                            codecInput.clear()
                            val capacityFrames = StudioAacEncodingPlanner.inputFramesForBuffer(
                                codecInput.remaining(), 2
                            )
                            require(capacityFrames > 0) { "AAC encoder input buffer is too small for stereo PCM." }
                            val remainingFrames = (frameCount - inputFramePosition).coerceAtLeast(0L)
                            val wantedFrames = minOf(
                                capacityFrames.toLong(),
                                PCM_READ_FRAMES.toLong(),
                                remainingFrames
                            ).toInt()
                            if (wantedFrames == 0) {
                                val eosPts = StudioAacEncodingPlanner.presentationTimeUs(frameCount, sampleRate)
                                codec.queueInputBuffer(inputIndex, 0, 0, eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEosQueued = true
                            } else {
                                // Source master is stereo Float32 (8 bytes/frame); MediaCodec input is stereo PCM16 (4 bytes/frame).
                                val sourceBytes = wantedFrames * 8
                                val encodedPcmBytes = wantedFrames * 4
                                var read = 0
                                while (read < sourceBytes) {
                                    val count = input.read(pcmBytes, read, sourceBytes - read)
                                    if (count < 0) break
                                    read += count
                                }
                                require(read == sourceBytes) { "PCM master ended before its declared frame count." }
                                val pcm = ByteBuffer.wrap(pcmBytes, 0, sourceBytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                repeat(wantedFrames * 2) {
                                    val sample = pcm.float.coerceIn(-1f, 1f)
                                    codecInput.putShort(StudioAacEncodingPlanner.floatToPcm16(sample))
                                }
                                check(codecInput.position() >= encodedPcmBytes) {
                                    "AAC encoder input buffer did not receive the complete converted PCM block."
                                }
                                val pts = StudioAacEncodingPlanner.presentationTimeUs(inputFramePosition, sampleRate)
                                codec.queueInputBuffer(inputIndex, 0, encodedPcmBytes, pts, 0)
                                inputFramePosition += wantedFrames
                                onProgress(inputFramePosition, frameCount)
                            }
                            madeProgress = true
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(trackIndex < 0) { "AAC encoder changed its output format more than once." }
                            val encodedFormat = codec.outputFormat
                            val mime = encodedFormat.getString(MediaFormat.KEY_MIME)
                            val actualRate = encodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            val actualChannels = encodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val csd = if (encodedFormat.containsKey("csd-0")) encodedFormat.getByteBuffer("csd-0") else null
                            val errors = StudioAacEncodingPlanner.validateEncoderOutputFormat(
                                mime, actualRate, actualChannels, csd?.remaining() ?: 0
                            )
                            require(errors.isEmpty()) { errors.joinToString() }
                            require(actualRate == sampleRate && actualChannels == 2) {
                                "AAC encoder changed the requested sample rate or channel count."
                            }
                            trackIndex = muxer.addTrack(encodedFormat)
                            muxer.start()
                            muxerStarted = true
                            madeProgress = true
                        }
                        outputIndex >= 0 -> {
                            try {
                                val encoded = codec.getOutputBuffer(outputIndex)
                                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    check(muxerStarted && trackIndex >= 0) {
                                        "AAC samples arrived before the encoder output format."
                                    }
                                    require(timestampGuard.accept(info.presentationTimeUs)) {
                                        "AAC encoder produced duplicate or out-of-order timestamps."
                                    }
                                    val sample = encoded ?: error("AAC encoder supplied no output sample.")
                                    sample.position(info.offset)
                                    sample.limit(info.offset + info.size)
                                    muxer.writeSampleData(trackIndex, sample, info)
                                    encodedBytes += info.size
                                }
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    outputEosSeen = true
                                }
                            } finally {
                                codec.releaseOutputBuffer(outputIndex, false)
                            }
                            madeProgress = true
                        }
                    }

                    if (madeProgress) idlePolls = 0 else {
                        idlePolls++
                        check(idlePolls < MAX_IDLE_POLLS) { "AAC encoder stopped making progress." }
                    }
                }
            }
            check(muxerStarted && encodedBytes > 0L) { "AAC encoder produced no muxable audio samples." }
            muxer.stop()
            muxerStarted = false
            val durationMs = frameCount * 1_000L / sampleRate
            return StudioAacAudioExportResult(output, sampleRate, 2, frameCount, durationMs, encodedBytes)
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            if (codecStarted) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }
}
