package com.nearer.app

internal object StudioAudioExportPolicy {
    /** Basic guard retained for callers that only know filesystem state. */
    fun hasUsableOutput(fileExists: Boolean, fileLengthBytes: Long): Boolean =
        fileExists && fileLengthBytes > 0L

    /**
     * A finished export is publishable only after the container is readable,
     * contains an audio track and reports a positive duration.
     */
    fun hasUsableOutput(
        fileExists: Boolean,
        fileLengthBytes: Long,
        hasAudioTrack: Boolean,
        durationMs: Long
    ): Boolean = fileExists && fileLengthBytes > 0L && hasAudioTrack && durationMs > 0L
}
