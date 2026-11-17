package com.nearer.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

internal class StudioAudioWaveformAccumulator(
    private val bucketCount: Int,
    private val expectedFrames: Long
) {
    private val peaks = FloatArray(bucketCount.coerceIn(1, 512))
    private var framesSeen = 0L

    init {
        require(expectedFrames > 0L)
        require(bucketCount in 1..512)
    }

    fun addInterleaved(samples: ShortBuffer, channelCount: Int) {
        require(channelCount > 0)
        val frameCount = samples.remaining() / channelCount
        repeat(frameCount) {
            var peak = 0f
            repeat(channelCount) {
                val amplitude = kotlin.math.abs(samples.get().toInt() / 32768f)
                if (amplitude > peak) peak = amplitude
            }
            recordPeak(peak)
        }
    }

    fun addInterleaved(samples: FloatBuffer, channelCount: Int) {
        require(channelCount > 0)
        val frameCount = samples.remaining() / channelCount
        repeat(frameCount) {
            var peak = 0f
            repeat(channelCount) {
                val value = samples.get()
                val amplitude = if (value.isFinite()) kotlin.math.abs(value).coerceIn(0f, 1f) else 0f
                if (amplitude > peak) peak = amplitude
            }
            recordPeak(peak)
        }
    }

    private fun recordPeak(peak: Float) {
        val bucket = ((framesSeen.coerceAtMost(expectedFrames - 1L) * peaks.size) / expectedFrames)
            .toInt().coerceIn(0, peaks.lastIndex)
        if (peak > peaks[bucket]) peaks[bucket] = peak
        framesSeen++
    }
    fun finish(): List<Float> = peaks.toList()
}

internal data class StudioAudioWaveform(
    val peaks: List<Float>,
    val durationMs: Long
)

internal object StudioAudioWaveformExtractor {
    const val DEFAULT_BUCKETS = 144

    suspend fun extract(
        context: Context,
        uri: Uri,
        knownDurationMs: Long,
        bucketCount: Int = DEFAULT_BUCKETS
    ): StudioAudioWaveform = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var decoderStarted = false
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var sourceFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = index
                    sourceFormat = candidate
                    break
                }
            }
            val format = sourceFormat ?: error("No audio track was found in this file.")
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio format is unavailable.")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val durationUs = when {
                knownDurationMs > 0L -> knownDurationMs * 1_000L
                format.containsKey(MediaFormat.KEY_DURATION) -> format.getLong(MediaFormat.KEY_DURATION)
                else -> 0L
            }
            require(durationUs > 0L) { "The audio duration is unavailable, so a reliable waveform cannot be mapped." }
            require(sampleRate > 0) { "The audio sample rate is unavailable." }
            val expectedFrames = ((durationUs * sampleRate) / 1_000_000L).coerceAtLeast(1L)
            val accumulator = StudioAudioWaveformAccumulator(bucketCount, expectedFrames)

            extractor.selectTrack(audioTrack)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            decoderStarted = true

            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            while (!outputEnded) {
                currentCoroutineContext().ensureActive()

                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                            ?: error("The audio decoder did not provide an input buffer.")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val presentationTime = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inputIndex, 0, size, presentationTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(info, 10_000L)
                if (outputIndex >= 0) {
                    try {
                        if (info.size > 0) {
                            val outputFormat = decoder.getOutputFormat(outputIndex)
                            val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val encoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else AudioFormat.ENCODING_PCM_16BIT
                            val output = decoder.getOutputBuffer(outputIndex)
                                ?: error("The audio decoder returned an empty output buffer.")
                            val bytes = output.duplicate().order(ByteOrder.nativeOrder()).apply {
                                position(info.offset)
                                limit(info.offset + info.size)
                            }
                            when (encoding) {
                                AudioFormat.ENCODING_PCM_16BIT -> {
                                    val shorts = bytes.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                                    accumulator.addInterleaved(shorts, channels)
                                }
                                AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val floats = bytes.slice().order(ByteOrder.nativeOrder()).asFloatBuffer()
                                    accumulator.addInterleaved(floats, channels)
                                }
                                else -> error("This device decoded the file to an unsupported PCM format ($encoding).")
                            }
                        }
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                    } finally {
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            StudioAudioWaveform(accumulator.finish(), (durationUs / 1_000L).coerceAtLeast(1L))
        } finally {
            if (decoderStarted) runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }
}
