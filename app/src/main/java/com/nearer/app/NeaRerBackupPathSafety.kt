package com.nearer.app

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Canonical-path containment checks for files copied into or out of backup snapshots.
 * A symlink used as the storage root is rejected as well as a child that escapes it.
 */
internal object NeaRerBackupPathSafety {
    fun isContained(parent: File, child: File): Boolean {
        val parentPath = trustedExistingPath(parent) ?: return false
        val childPath = existingOrNormalizedPath(child) ?: return false
        return childPath != parentPath && childPath.startsWith(parentPath)
    }

    /** True only when a not-yet-created path resolves to a direct child of parent. */
    fun isDirectChild(parent: File, child: File): Boolean {
        val parentPath = trustedExistingPath(parent) ?: return false
        val childPath = existingOrNormalizedPath(child) ?: return false
        return childPath.parent == parentPath
    }

    private fun trustedExistingPath(file: File): Path? {
        return try {
            val lexical = file.toPath().toAbsolutePath().normalize()
            val real = lexical.toRealPath()
            // If the supplied storage root itself is a symlink, do not treat its
            // target as an acceptable root for a potentially redirected directory.
            if (lexical != real) null else real
        } catch (_: Exception) {
            null
        }
    }

    private fun existingOrNormalizedPath(file: File): Path? {
        return try {
            val lexical = file.toPath().toAbsolutePath().normalize()
            if (Files.exists(lexical)) lexical.toRealPath() else lexical
        } catch (_: Exception) {
            null
        }
    }
}
