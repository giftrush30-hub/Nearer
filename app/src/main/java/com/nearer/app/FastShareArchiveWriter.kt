package com.nearer.app

import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class FastShareArchiveItem(
    val sourceIndex: Int,
    val fileName: String,
    val expectedBytes: Long
)

/** Safe, unique leaf names for a flat archive of user-selected files. */
internal object FastShareArchiveNames {
    fun uniqueEntryNames(fileNames: List<String>): List<String> {
        val used = HashSet<String>()
        return fileNames.map { raw ->
            val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
            val safe = buildString {
                leaf.forEach { char ->
                    append(
                        if (char.code < 0x20 || char.code == 0x7f || char in ":*?\"<>|/\\") '_'
                        else char
                    )
                }
            }.trim().ifBlank { "shared-file" }
            val safeBase = if (safe == "." || safe == "..") "shared-file" else safe
            val dot = safeBase.lastIndexOf('.')
            val stem = if (dot > 0) safeBase.substring(0, dot) else safeBase
            val extension = if (dot > 0) safeBase.substring(dot) else ""
            var candidate = safeBase
            var suffix = 2
            while (!used.add(candidate.lowercase(Locale.ROOT))) {
                candidate = "$stem ($suffix)$extension"
                suffix++
            }
            candidate
        }
    }
}

/**
 * Streams selected source files into one ZIP response using a fixed-size buffer.
 * It never stages the archive or an individual source file in memory or on disk.
 */
internal object FastShareArchiveWriter {
    const val BUFFER_SIZE = 64 * 1024

    fun write(
        items: List<FastShareArchiveItem>,
        openInput: (FastShareArchiveItem) -> InputStream,
        output: OutputStream,
        shouldContinue: () -> Boolean = { true },
        onBytesCopied: (Long) -> Unit = {}
    ): Long {
        require(items.isNotEmpty()) { "An archive needs at least one file." }
        require(items.all { it.expectedBytes >= -1L }) { "Archive file sizes must be known or -1." }
        val names = FastShareArchiveNames.uniqueEntryNames(items.map { it.fileName })
        val zip = ZipOutputStream(output)
        zip.setLevel(0)
        val buffer = ByteArray(BUFFER_SIZE)
        var totalCopied = 0L

        items.forEachIndexed { index, item ->
            if (!shouldContinue()) throw InterruptedIOException("Archive transfer cancelled.")
            zip.putNextEntry(ZipEntry(names[index]))
            var itemCopied = 0L
            openInput(item).use { input ->
                while (true) {
                    if (!shouldContinue()) throw InterruptedIOException("Archive transfer cancelled.")
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (item.expectedBytes >= 0L && itemCopied > item.expectedBytes - count) {
                        throw IllegalStateException("Source file grew while the archive was being prepared.")
                    }
                    zip.write(buffer, 0, count)
                    itemCopied += count
                    totalCopied += count
                    onBytesCopied(totalCopied)
                }
            }
            if (item.expectedBytes >= 0L && itemCopied != item.expectedBytes) {
                throw IllegalStateException("Source file size changed while the archive was being prepared.")
            }
            zip.closeEntry()
        }

        zip.finish()
        zip.flush()
        return totalCopied
    }
}
