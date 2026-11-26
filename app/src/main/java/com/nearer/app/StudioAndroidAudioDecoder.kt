package com.nearer.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor

internal data class StudioDecodedPcmInfo(
    val file: File,
    val sampleRate: Int,
    val frameCount: Long,
    val sourceSampleRate: Int,
    val sourceChannels: Int
)

/**
 * Decodes one local audio URI through Android's platform codecs into a disk-backed,
 * interleaved stereo Float PCM cache. Memory stays bounded to codec buffers and a small
 * output staging buffer; the entire decoded recording is never held in RAM.
 */
internal object StudioAndroidAudioDecoder {
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val MAX_IDLE_POLLS = 500

    fun decodeToFloatPcm(
        context: Context,
        sourceUri: Uri,
        outputDirectory: File,
        targetSampleRate: Int,
        isCancelled: () -> Boolean = { false },
        onProgress: (Long) -> Unit = {}
    ): StudioDecodedPcmInfo {
        require(targetSampleRate in StudioAudioMixPlanner.MIN_SAMPLE_RATE..StudioAudioMixPlanner.MAX_SAMPLE_RATE)
        require(sourceUri.scheme in setOf("content", "file", "android.resource")) {
            "Only local offline audio files are supported."
        }
        require(outputDirectory.exists() || outputDirectory.mkdirs()) { "Could not create audio cache directory." }

        val output = File.createTempFile("studio-decoded-", ".f32", outputDirectory)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var started = false
        var sourceRate = 0
        var sourceChannels = 0
        var outputRate = 0
        var outputChannels = 0
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var inputDone = false
        var outputDone = false
        var idlePolls = 0
        var inputFrames = 0L
        var outputFrames = 0L
        var previousLeft = 0f
        var previousRight = 0f
        var havePrevious = false
        var nextOutputPosition = 0.0
        val staging = FloatArray(StudioPcmBlockMixer.MAX_BLOCK_FRAMES * 2)
        var stagingFrames = 0

        fun flushStaging(stream: BufferedOutputStream) {
            if (stagingFrames == 0) return
            val bytes = ByteBuffer.allocate(stagingFrames * 8).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until stagingFrames * 2) bytes.putFloat(staging[i])
            stream.write(bytes.array(), 0, bytes.position())
            stagingFrames = 0
        }

        fun emit(left: Float, right: Float, stream: BufferedOutputStream) {
            val index = stagingFrames * 2
            staging[index] = left.coerceIn(-1f, 1f)
            staging[index + 1] = right.coerceIn(-1f, 1f)
            stagingFrames++
            outputFrames++
            if (stagingFrames == StudioPcmBlockMixer.MAX_BLOCK_FRAMES) flushStaging(stream)
        }

        try {
            extractor.setDataSource(context, sourceUri, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = index
                    audioFormat = candidate
                    break
                }
            }
            require(audioTrack >= 0 && audioFormat != null) { "The selected file has no decodable audio track." }
            extractor.selectTrack(audioTrack)
            val format = audioFormat
            sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(sourceRate in 8_000..384_000 && sourceChannels in 1..8) { "Unsupported source audio format." }
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio codec type is missing.")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            started = true

            BufferedOutputStream(FileOutputStream(output), 64 * 1024).use { stream ->
                val info = MediaCodec.BufferInfo()
                while (!outputDone) {
                    check(!isCancelled()) { "Audio decoding was cancelled." }
                    var madeProgress = false

                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                ?: error("Decoder supplied no input buffer.")
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                            madeProgress = true
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                    when {
                        outputIndex >= 0 -> {
                            val buffer = codec.getOutputBuffer(outputIndex)
                            if (buffer != null && info.size > 0) {
                                val decodedFormat = codec.getOutputFormat(outputIndex)
                                outputRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                outputChannels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                pcmEncoding = if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                    decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                } else AudioFormat.ENCODING_PCM_16BIT
                                require(outputRate in 8_000..384_000 && outputChannels in 1..8)
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val pcm = buffer.slice().order(ByteOrder.nativeOrder())
                                val bytesPerSample = when (pcmEncoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT -> 4
                                    AudioFormat.ENCODING_PCM_16BIT -> 2
                                    else -> error("This phone's decoder returned an unsupported PCM encoding.")
                                }
                                val frameBytes = outputChannels * bytesPerSample
                                val completeFrames = pcm.remaining() / frameBytes
                                for (frame in 0 until completeFrames) {
                                    val left: Float
                                    val right: Float
                                    if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                        left = pcm.float
                                        right = if (outputChannels > 1) pcm.float else left
                                        repeat((outputChannels - 2).coerceAtLeast(0)) { pcm.float }
                                    } else {
                                        left = pcm.short / 32768f
                                        right = if (outputChannels > 1) pcm.short / 32768f else left
                                        repeat((outputChannels - 2).coerceAtLeast(0)) { pcm.short }
                                    }
                                    if (!havePrevious) {
                                        emit(left, right, stream)
                                        previousLeft = left
                                        previousRight = right
                                        havePrevious = true
                                        nextOutputPosition = outputRate.toDouble() / targetSampleRate
                                    } else {
                                        val currentPosition = inputFrames.toDouble()
                                        while (nextOutputPosition <= currentPosition + 1e-9) {
                                            val fraction = (nextOutputPosition - (inputFrames - 1).toDouble()).coerceIn(0.0, 1.0).toFloat()
                                            emit(
                                                previousLeft + (left - previousLeft) * fraction,
                                                previousRight + (right - previousRight) * fraction,
                                                stream
                                            )
                                            nextOutputPosition += outputRate.toDouble() / targetSampleRate
                                        }
                                        previousLeft = left
                                        previousRight = right
                                    }
                                    inputFrames++
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            madeProgress = true
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> madeProgress = true
                    }

                    if (madeProgress) idlePolls = 0 else {
                        idlePolls++
                        check(idlePolls < MAX_IDLE_POLLS) { "Audio decoder stopped making progress." }
                    }
                    if (inputFrames % 48_000L < 2_048L) onProgress(inputFrames)
                }
                flushStaging(stream)
            }
            require(outputFrames > 0L) { "The selected audio file contains no decoded samples." }
            return StudioDecodedPcmInfo(output, targetSampleRate, outputFrames, sourceRate, sourceChannels)
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            if (started) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

/** Random-access reader for the decoder's little-endian stereo Float PCM cache. */
internal class StudioDecodedPcmFileSource(
    private val decoded: StudioDecodedPcmInfo
) : StudioPcmStereoSource, AutoCloseable {
    private val file = java.io.RandomAccessFile(decoded.file, "r")
    private val scratch = ByteArray(StudioPcmBlockMixer.MAX_BLOCK_FRAMES * 8)

    @Synchronized
    override fun readStereoFrames(
        sourceStartFrame: Long,
        frameCount: Int,
        destination: FloatArray,
        destinationOffset: Int
    ): Int {
        require(sourceStartFrame >= 0L && frameCount in 0..StudioPcmBlockMixer.MAX_BLOCK_FRAMES)
        require(destinationOffset >= 0 && destinationOffset <= destination.size - frameCount * 2)
        if (sourceStartFrame >= decoded.frameCount || frameCount == 0) return 0
        val available = minOf(frameCount.toLong(), decoded.frameCount - sourceStartFrame).toInt()
        val byteCount = available * 8
        require(byteCount <= scratch.size) { "Read exceeds the bounded PCM source window." }
        file.seek(sourceStartFrame * 8L)
        file.readFully(scratch, 0, byteCount)
        val bytes = ByteBuffer.wrap(scratch, 0, byteCount).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until available) {
            destination[destinationOffset + frame * 2] = bytes.float
            destination[destinationOffset + frame * 2 + 1] = bytes.float
        }
        return available
    }

    override fun close() {
        file.close()
    }
}
