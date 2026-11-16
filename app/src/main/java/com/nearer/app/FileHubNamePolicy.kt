package com.nearer.app

import java.util.Locale

internal object FileHubNamePolicy {
    const val MAX_NAME_LENGTH = 120

    fun normalize(raw: String): String? {
        val name = raw.trim()
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) return null
        if (name == "." || name == "..") return null
        if (name.any { it == '/' || it == '\\' || it.code < 32 || it.code == 127 }) return null
        return name
    }

    fun uniqueCopyName(sourceName: String, existingNames: Collection<String>): String {
        val safeName = normalize(sourceName) ?: "File"
        val existing = existingNames.map { it.lowercase(Locale.ROOT) }.toHashSet()
        if (safeName.lowercase(Locale.ROOT) !in existing) return safeName

        val dot = safeName.lastIndexOf('.').takeIf { it > 0 } ?: safeName.length
        val extension = safeName.substring(dot)
        val maxStemLength = (MAX_NAME_LENGTH - extension.length - 6).coerceAtLeast(1)
        val stem = safeName.substring(0, dot).take(maxStemLength)
        var suffix = 1
        while (true) {
            val candidate = "${stem} ($suffix)$extension"
            if (candidate.lowercase(Locale.ROOT) !in existing) return candidate
            suffix++
        }
    }
}
