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
        val displayName: String,
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
        if (!staging.mkdir()) throw IOException("Could not create a private extraction folder")

        var fileCount = 0
        var directoryCount = 0
        var totalBytes = 0L
        // Include implicit parent folders in the entry budget and path-conflict map.
        // This keeps deeply nested archives bounded and avoids quadratic descendant scans.
        val seen = HashMap<String, EntryKind>()
        val folded = HashMap<String, String>()
        val explicitEntries = HashSet<String>()
        val buffer = ByteArray(BUFFER_BYTES)

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val normalized = validateEntryPath(entry.name, entry.isDirectory)
                    val kind = if (entry.isDirectory) EntryKind.DIRECTORY else EntryKind.FILE
                    val parts = normalized.split('/')
                    for (index in 1 until parts.size) {
                        val parent = parts.take(index).joinToString("/")
                        val parentFolded = parent.lowercase(Locale.ROOT)
                        val previousParent = folded.putIfAbsent(parentFolded, parent)
                        if (previousParent != null && previousParent != parent) {
                            throw IOException("The archive contains case-conflicting folder paths")
                        }
                        if (seen[parent] == EntryKind.FILE) {
                            throw IOException("A file entry cannot be used as a folder")
                        }
                        if (seen[parent] == null) {
                            if (seen.size >= MAX_ENTRIES) throw IOException("The archive contains too many entries")
                            seen[parent] = EntryKind.DIRECTORY
                        }
                    }
                    val foldedPath = normalized.lowercase(Locale.ROOT)
                    val previousPath = folded.putIfAbsent(foldedPath, normalized)
                    if (previousPath != null && previousPath != normalized) {
                        throw IOException("The archive contains case-conflicting paths")
                    }
                    val existingKind = seen[normalized]
                    if (!explicitEntries.add(normalized)) {
                        throw IOException("The archive contains duplicate paths")
                    }
                    if (kind == EntryKind.FILE) {
                        if (existingKind != null) throw IOException("A folder cannot be replaced by a file")
                        if (seen.size >= MAX_ENTRIES) throw IOException("The archive contains too many entries")
                        seen[normalized] = EntryKind.FILE
                    } else {
                        if (existingKind == EntryKind.FILE) throw IOException("A file cannot be replaced by a folder")
                        if (existingKind == null) {
                            if (seen.size >= MAX_ENTRIES) throw IOException("The archive contains too many entries")
                            seen[normalized] = EntryKind.DIRECTORY
                        }
                    }

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
                                entryBytes = Math.addExact(entryBytes, read.toLong())
                                totalBytes = Math.addExact(totalBytes, read.toLong())
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
            val displayName = staging.listFiles()?.singleOrNull()?.takeIf { it.isDirectory }?.name ?: "Restored folder"
            val destination = File(
                canonicalRoot,
                "Folder-${TransferFileName.safeLeafName(displayName)}-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
            )
            if (!staging.renameTo(destination)) throw IOException("Could not finish restoring the folder")
            return Result(destination, displayName, fileCount, directoryCount, totalBytes)
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
