package com.nearer.app

/** Pure ordering and bounds rules shared by the Android MP4 remuxer and JVM tests. */
internal object StudioFinalMovieMuxPolicy {
    const val MAX_SAMPLE_BYTES = 32 * 1024 * 1024

    fun nextTrack(videoPtsUs: Long, audioPtsUs: Long, hasVideo: Boolean, hasAudio: Boolean): String? = when {
        !hasVideo && !hasAudio -> null
        !hasVideo -> "audio"
        !hasAudio -> "video"
        videoPtsUs <= audioPtsUs -> "video"
        else -> "audio"
    }

    fun isTimestampAfter(previousPtsUs: Long, nextPtsUs: Long): Boolean =
        nextPtsUs >= 0L && (previousPtsUs < 0L || nextPtsUs > previousPtsUs)

    fun isSafeSampleSize(sizeBytes: Long): Boolean =
        sizeBytes in 1L..MAX_SAMPLE_BYTES.toLong()
}
