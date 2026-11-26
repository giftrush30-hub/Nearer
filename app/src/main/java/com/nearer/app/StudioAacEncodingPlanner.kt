package com.nearer.app

/**
 * Platform-independent AAC-LC input and mux timestamp rules.
 *
 * Android MediaCodec remains responsible for the actual codec configuration and output format.
 * This class plans PCM16 input boundaries and exact sample-clock timestamps.
 */
internal data class StudioAacEncodingConfig(
    val sampleRate: Int = 48_000,
    val channelCount: Int = 2,
    val bitRate: Int = 192_000
)

internal data class StudioAacEncodingValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

internal data class StudioAacInputChunk(
    val firstFrame: Long,
    val frameCount: Int,
    val presentationTimeUs: Long
)

internal object StudioAacEncodingPlanner {
    const val AAC_LC_MIME = "audio/mp4a-latm"
    const val AAC_LC_FRAMES_PER_ACCESS_UNIT = 1_024
    const val BYTES_PER_PCM16_SAMPLE = 2
    private val supportedSampleRates = setOf(
        7_350, 8_000, 11_025, 12_000, 16_000, 22_050, 24_000,
        32_000, 44_100, 48_000, 64_000, 88_200, 96_000
    )

    fun validate(config: StudioAacEncodingConfig): StudioAacEncodingValidation {
        val errors = mutableListOf<String>()
        if (config.sampleRate !in supportedSampleRates) errors += "AAC-LC sample rate is not in the supported MPEG-4 audio sample-rate table."
        if (config.channelCount !in 1..2) errors += "AAC-LC export supports mono or stereo output."
        val minimumBitRate = 16_000 * config.channelCount.coerceAtLeast(1)
        val maximumBitRate = 256_000 * config.channelCount.coerceAtMost(2)
        if (config.bitRate !in minimumBitRate..maximumBitRate) {
            errors += "AAC-LC bitrate is outside the safe range for the selected channel count."
        }
        return StudioAacEncodingValidation(errors)
    }

    /** Convert normalized float PCM into signed little-endian PCM16 without asymmetric clipping. */
    fun floatToPcm16(sample: Float): Short {
        val clipped = sample.coerceIn(-1f, 1f)
        val scale = if (clipped < 0f) 32_768f else 32_767f
        return (clipped * scale).toInt().coerceIn(-32_768, 32_767).toShort()
    }

    /** Largest whole interleaved PCM16 frames that fit in one codec input buffer. */
    fun inputFramesForBuffer(inputCapacityBytes: Int, channelCount: Int): Int {
        require(channelCount in 1..2) { "AAC input must be mono or stereo." }
        require(inputCapacityBytes >= 0) { "Codec input capacity cannot be negative." }
        return inputCapacityBytes / (BYTES_PER_PCM16_SAMPLE * channelCount)
    }

    /** Convert an absolute PCM frame position to microseconds without cumulative packet rounding. */
    fun presentationTimeUs(framePosition: Long, sampleRate: Int): Long {
        require(framePosition >= 0L) { "PCM frame position cannot be negative." }
        require(sampleRate in supportedSampleRates) { "Unsupported AAC sample rate." }
        val wholeSeconds = framePosition / sampleRate
        val remainingFrames = framePosition % sampleRate
        return try {
            Math.addExact(
                Math.multiplyExact(wholeSeconds, 1_000_000L),
                remainingFrames * 1_000_000L / sampleRate
            )
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("PCM timestamp exceeds the supported movie duration.")
        }
    }

    /** Plan a bounded PCM16 input chunk while keeping timestamps anchored to absolute frames. */
    fun planInputChunk(
        firstFrame: Long,
        remainingFrames: Long,
        inputCapacityBytes: Int,
        config: StudioAacEncodingConfig
    ): StudioAacInputChunk? {
        val validation = validate(config)
        require(validation.isValid) { validation.errors.joinToString() }
        require(firstFrame >= 0L && remainingFrames >= 0L) { "PCM frame positions cannot be negative." }
        if (remainingFrames == 0L) return null
        val capacityFrames = inputFramesForBuffer(inputCapacityBytes, config.channelCount)
        require(capacityFrames > 0) { "AAC codec input buffer cannot hold one complete PCM frame." }
        val count = minOf(remainingFrames, capacityFrames.toLong()).toInt()
        return StudioAacInputChunk(
            firstFrame = firstFrame,
            frameCount = count,
            presentationTimeUs = presentationTimeUs(firstFrame, config.sampleRate)
        )
    }

    /** Validate the real format returned by MediaCodec before registering the audio muxer track. */
    fun validateEncoderOutputFormat(
        mimeType: String?,
        sampleRate: Int,
        channelCount: Int,
        codecSpecificDataBytes: Int
    ): List<String> {
        val errors = mutableListOf<String>()
        if (mimeType != AAC_LC_MIME) errors += "Encoder output is not AAC in an MP4-compatible format."
        if (sampleRate !in supportedSampleRates) errors += "Encoder returned an unsupported AAC sample rate."
        if (channelCount !in 1..2) errors += "Encoder returned an unsupported AAC channel count."
        if (codecSpecificDataBytes <= 0) errors += "Encoder output is missing AAC codec-specific configuration data."
        return errors
    }
}

/** Per-track guard: MediaMuxer samples must arrive in strictly increasing PTS order. */
internal class StudioMuxTimestampGuard {
    private var lastPresentationTimeUs: Long? = null

    fun accept(presentationTimeUs: Long): Boolean {
        if (presentationTimeUs < 0L) return false
        val previous = lastPresentationTimeUs
        if (previous != null && presentationTimeUs <= previous) return false
        lastPresentationTimeUs = presentationTimeUs
        return true
    }

    fun lastAcceptedPresentationTimeUs(): Long? = lastPresentationTimeUs
}
