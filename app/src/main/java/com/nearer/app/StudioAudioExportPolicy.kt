package com.nearer.app

internal object StudioAudioExportPolicy {
    fun hasUsableOutput(fileExists: Boolean, fileLengthBytes: Long): Boolean =
        fileExists && fileLengthBytes > 0L
}
