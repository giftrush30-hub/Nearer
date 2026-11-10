package com.nearer.app

import java.io.File

/**
 * Chooses a collision-free destination while preserving the final extension.
 * Callers must serialize the exists-check + rename operation with a session lock.
 */
internal object FastShareUploadDestination {
    fun nextAvailable(directory: File, safeName: String): File {
        require(safeName.isNotBlank()) { "A safe filename is required" }
        require(
            safeName != "." && safeName != ".." &&
                '/' !in safeName && '\\' !in safeName &&
                safeName.none { it.code < 0x20 || it.code == 0x7f }
        ) { "Filename must be a single safe path segment" }
        val original = File(directory, safeName)
        if (!original.exists()) return original

        val dot = safeName.lastIndexOf('.')
        val hasExtension = dot > 0 && dot < safeName.lastIndex
        val stem = if (hasExtension) safeName.substring(0, dot) else safeName
        val extension = if (hasExtension) safeName.substring(dot) else ""
        var suffix = 2
        while (true) {
            val candidate = File(directory, "$stem ($suffix)$extension")
            if (!candidate.exists()) return candidate
            if (suffix == Int.MAX_VALUE) throw IllegalStateException("No available filename suffix")
            suffix++
        }
    }
}
