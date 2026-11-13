package com.nearer.app

/** Validates voice-note metadata; audio bytes remain files handled by the existing transfer engine. */
internal object VoiceNotePolicy {
    const val MAX_DURATION_MILLIS = 10 * 60 * 1000L
    private val allowedMimeTypes = setOf("audio/mp4", "audio/3gpp", "audio/aac", "audio/ogg", "audio/opus", "audio/mpeg")
    data class Descriptor(val fileName: String, val mimeType: String, val durationMillis: Long, val byteCount: Long)

    fun create(fileName: String, mimeType: String, durationMillis: Long, byteCount: Long): Descriptor? {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .filter { it >= ' ' && it != '\u007f' }.trim().take(120)
        val normalizedMime = mimeType.substringBefore(';').trim().lowercase()
        if (safeName.isBlank() || safeName == "." || safeName == ".." ||
            normalizedMime !in allowedMimeTypes || durationMillis !in 1L..MAX_DURATION_MILLIS ||
            byteCount <= 0L) return null
        return Descriptor(safeName, normalizedMime, durationMillis, byteCount)
    }
}
