package com.nearer.app

import java.io.File

/**
 * Removes abandoned private upload staging files left behind if Android kills
 * NeaRer during a browser upload. Call only when a new Fast Share server starts,
 * before it accepts clients, so files belonging to live uploads are never swept.
 */
internal object FastShareTempFileCleanup {
    private const val PREFIX = ".nearer-upload-"
    private const val SUFFIX = ".part"

    fun removeAbandoned(directory: File): Int {
        if (!directory.isDirectory) return 0
        val abandoned = directory.listFiles()
            ?.filter { file ->
                file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX)
            }
            .orEmpty()
        var removed = 0
        abandoned.forEach { file ->
            if (runCatching { file.delete() }.getOrDefault(false)) removed++
        }
        return removed
    }
}
