package com.nearer.app

import java.io.File
import java.nio.file.Files

/**
 * Canonical-path containment checks for files copied into or out of backup snapshots.
 * Existing paths are resolved through the filesystem so symlinks cannot escape a root.
 */
internal object NeaRerBackupPathSafety {
    fun isContained(parent: File, child: File): Boolean {
        val parentPath = resolveForSafety(parent)
        val childPath = resolveForSafety(child)
        return childPath.startsWith(parentPath + File.separator)
    }

    /** True only when a not-yet-created path resolves to a direct child of parent. */
    fun isDirectChild(parent: File, child: File): Boolean {
        val parentPath = resolveForSafety(parent)
        val childPath = resolveForSafety(child)
        return File(childPath).parentFile?.path == parentPath
    }

    private fun resolveForSafety(file: File): String {
        return if (Files.exists(file.toPath())) {
            file.toPath().toRealPath().toString()
        } else {
            file.canonicalPath
        }
    }
}
