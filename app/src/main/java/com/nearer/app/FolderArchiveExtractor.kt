package com.nearer.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Safely restores a NeaRer folder-share ZIP into a new private directory.
 *
 * Extraction is streamed, never overwrites an existing file, rejects traversal
 * and ambiguous paths, and removes the incomplete output if any entry fails.
 * There is no arbitrary archive-size cap; writes stop when the device approaches
 * its available-storage floor.
 */
internal object FolderArchiveExtractor {
    const val MAX_ENTRIES = 10_000
    const val MAX_PATH_LENGTH = TransferBundleManifest.MAX_PATH_LENGTH
    private const val BUFFER_BYTES = 128 * 1024
    private const val MIN_FREE_SPACE_BYTES = 1024L * 1024L

    data class Result(
        val directory: File,
        val fileCount: Int,
        val directoryCount: Int,
        val bytesExtracted: Long
    )

    private enum class EntryKind { FILE, DIRECTORY }

    fun extract(archive: File, receivedRoot: File): Result {
        require(archive.isFile && archive.canRead()) { "The folder archive is unavailable" }
        if (!receivedRoot.exists() && !receivedRoot.mkdirs()) {
            throw IOException("NeaRer could not prepare the received-files folder")
        }
        val canonicalRoot = receivedRoot.canonicalFile
        require(canonicalRoot.isDirectory) { "The received-files location is invalid" }
        val staging = File(canonicalRoot, ".folder-extracting-${UUID.randomUUID()}")
        val destination = File(canonicalRoot, "Folder-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}")
        if (!staging.mkdir()) throw IOException("Could not create a private extraction folder")

        var fileCount = 0
        var directoryCount = 0
        var totalBytes = 0L
        val seen = HashMap<String, EntryKind>()
        val folded = HashSet<String>()
        val buffer = ByteArray(BUFFER_BYTES)

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (seen.size >= MAX_ENTRIES) throw IOException("The archive contains too many entries")
                    val normalized = validateEntryPath(entry.name, entry.isDirectory)
                    val foldedPath = normalized.lowercase(Locale.ROOT)
                    if (!folded.add(foldedPath)) throw IOException("The archive contains duplicate or conflicting paths")
                    val kind = if (entry.isDirectory) EntryKind.DIRECTORY else EntryKind.FILE
                    val parts = normalized.split('/')
                    for (index in 1 until parts.size) {
                        val parent = parts.take(index).joinToString("/")
                        if (seen[parent] == EntryKind.FILE) {
                            throw IOException("A file entry cannot be used as a folder")
                        }
                    }
                    if (kind == EntryKind.FILE && seen.keys.any { it.startsWith("$normalized/") }) {
                        throw IOException("A folder cannot be replaced by a file")
                    }
                    seen[normalized] = kind

                    val output = File(staging, normalized).canonicalFile
                    val stagingPath = staging.canonicalPath + File.separator
                    if (!output.path.startsWith(stagingPath)) throw IOException("The archive contains an unsafe destination")
                    if (kind == EntryKind.DIRECTORY) {
                        if (output.exists() && !output.isDirectory) throw IOException("A folder conflicts with a file")
                        if (!output.exists() && !output.mkdirs()) throw IOException("Could not create an extracted folder")
                        directoryCount++
                    } else {
                        val parent = output.parentFile ?: throw IOException("An extracted file has no parent folder")
                        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create an extracted folder")
                        if (output.exists()) throw IOException("The archive would overwrite an extracted file")
                        var entryBytes = 0L
                        BufferedOutputStream(FileOutputStream(output, false)).use { target ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                if (parent.usableSpace - read < MIN_FREE_SPACE_BYTES) {
                                    throw IOException("Not enough free storage to safely extract this folder")
                                }
                                target.write(buffer, 0, read)
                                entryBytes += read.toLong()
                                totalBytes += read.toLong()
                            }
                            target.flush()
                        }
                        if (entry.size >= 0L && entryBytes != entry.size) {
                            throw IOException("An extracted file did not match its declared size")
                        }
                        fileCount++
                    }
                    zip.closeEntry()
                }
            }
            if (fileCount == 0) throw IOException("The archive contains no files")
            if (!staging.renameTo(destination)) throw IOException("Could not finish restoring the folder")
            return Result(destination, fileCount, directoryCount, totalBytes)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun validateEntryPath(raw: String, directory: Boolean): String {
        if (raw.isBlank() || raw.length > MAX_PATH_LENGTH) throw IOException("The archive contains an invalid path")
        if (raw.startsWith('/') || raw.startsWith('\\') || raw.contains('\\') || raw.contains(':') ||
            raw.any { it.isISOControl() }) throw IOException("The archive contains an unsafe path")
        val path = if (directory) raw.removeSuffix("/") else raw
        val parts = path.split('/')
        if (parts.isEmpty() || parts.any { part ->
                part.isBlank() || part == "." || part == ".." || part != TransferFileName.safeLeafName(part)
            }) throw IOException("The archive contains an unsafe path")
        return parts.joinToString("/")
    }
}
