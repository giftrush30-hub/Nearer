package com.nearer.app

import java.io.File
import java.io.RandomAccessFile

/**
 * Keeps incomplete exports separate from finished movies.
 *
 * Media3 does not remove a partially written output when an export is cancelled, so callers must
 * explicitly discard pending files. A completed file is published only after a basic MP4-container
 * sanity check and a same-directory rename.
 */
internal object StudioExportFiles {
    private const val PENDING_PREFIX = ".nearer-export-"
    private const val PENDING_SUFFIX = ".pending.mp4"

    fun pendingFile(directory: File, exportId: String): File {
        require(exportId.isNotBlank() && exportId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "Export ID contains unsupported characters"
        }
        return File(directory, "$PENDING_PREFIX$exportId$PENDING_SUFFIX")
    }

    fun finalFile(directory: File, exportId: String): File {
        require(exportId.isNotBlank() && exportId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "Export ID contains unsupported characters"
        }
        return File(directory, "NeaRer_Film_$exportId.mp4")
    }

    /** Returns false rather than publishing an empty, malformed, or colliding output. */
    fun publish(pending: File, destination: File): Boolean {
        if (!pending.isFile || pending.length() < 12L || destination.exists()) return false
        if (!hasMp4FileTypeBox(pending)) return false
        destination.parentFile?.let { if (!it.exists() && !it.mkdirs()) return false }
        return pending.renameTo(destination)
    }

    fun discard(pending: File): Boolean = !pending.exists() || pending.delete()

    /** Removes only this feature's hidden pending files, never finished exports or user media. */
    fun cleanupAbandoned(directory: File): Int {
        val files = directory.listFiles() ?: return 0
        return files.count { file ->
            file.isFile && file.name.startsWith(PENDING_PREFIX) &&
                file.name.endsWith(PENDING_SUFFIX) && file.delete()
        }
    }

    private fun hasMp4FileTypeBox(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { input ->
            input.seek(4L)
            val type = ByteArray(4)
            input.readFully(type)
            String(type, Charsets.US_ASCII) == "ftyp"
        }
    }.getOrDefault(false)
}
