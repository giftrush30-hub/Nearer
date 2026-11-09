package com.nearer.app

import java.io.File

/**
 * Canonical-path containment checks for files copied into or out of backup snapshots.
 * Comparing canonical paths prevents a child symlink from escaping its intended root.
 */
internal object NeaRerBackupPathSafety {
    fun isContained(parent: File, child: File): Boolean {
        val parentPath = parent.canonicalPath
        val childPath = child.canonicalPath
        return childPath.startsWith(parentPath + File.separator)
    }
}
