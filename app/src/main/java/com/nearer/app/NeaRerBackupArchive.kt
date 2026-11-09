package com.nearer.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Packages a caller-prepared NeaRer snapshot into an authenticated backup.
 *
 * The caller owns snapshot selection (chat DB export, profile, preferences and media).
 * This layer guarantees a bounded, path-safe archive and verifies every payload hash
 * before publishing a restored directory.
 */
internal object NeaRerBackupArchive {
    private const val MANIFEST = "nearer-backup-manifest.json"
    private const val FORMAT_VERSION = 1
    private const val MAX_ENTRIES = 10_000
    private const val MAX_ENTRY_BYTES = 512L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L
    private const val BUFFER_BYTES = 64 * 1024

    data class RestoreSummary(val files: Int, val bytes: Long)

    fun createEncryptedBackup(snapshotDirectory: File, destination: File, password: CharArray) {
        require(snapshotDirectory.isDirectory) { "Backup snapshot folder is missing" }
        val parent = destination.absoluteFile.parentFile ?: throw IOException("Backup destination has no parent")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create backup destination folder")
        if (destination.exists()) throw IOException("Backup destination already exists")
        val zipFile = File.createTempFile(".nearer-archive-", ".zip", parent)
        try {
            createZip(snapshotDirectory, zipFile)
            NeaRerBackupCrypto.encryptFile(zipFile, destination, password)
        } finally {
            zipFile.delete()
            password.fill('\\u0000')
        }
    }

    fun restoreEncryptedBackup(
        encryptedBackup: File,
        destinationDirectory: File,
        password: CharArray
    ): RestoreSummary {
        require(!destinationDirectory.exists()) { "Restore folder already exists; choose a new empty destination" }
        val parent = destinationDirectory.absoluteFile.parentFile ?: throw IOException("Restore folder has no parent")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create restore parent folder")
        val encryptedZip = File.createTempFile(".nearer-restore-", ".zip", parent)
        val staging = File.createTempFile(".nearer-restore-stage-", ".tmp", parent).apply {
            delete()
            if (!mkdirs()) throw IOException("Could not create private restore staging folder")
        }
        try {
            NeaRerBackupCrypto.decryptFile(encryptedBackup, encryptedZip, password)
            val summary = extractAndVerify(encryptedZip, staging)
            try {
                Files.move(staging.toPath(), destinationDirectory.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(staging.toPath(), destinationDirectory.toPath())
            }
            return summary
        } catch (failure: Exception) {
            staging.deleteRecursively()
            throw failure
        } finally {
            encryptedZip.delete()
        }
    }

    private data class EntryRecord(val path: String, val size: Long, val sha256: String)

    private fun createZip(source: File, zipFile: File) {
        val root = source.canonicalFile
        val files = root.walkTopDown().filter { it.isFile }.toList()
        if (files.size > MAX_ENTRIES) throw IOException("Backup contains too many files")
        val records = mutableListOf<EntryRecord>()
        var total = 0L
        files.forEach { file ->
            val canonical = file.canonicalFile
            val path = file.relativeTo(root).invariantSeparatorsPath
            validatePath(path)
            if (!canonical.path.startsWith(root.path + File.separator)) {
                throw IOException("Snapshot contains a file outside its folder")
            }
            val size = file.length()
            if (size > MAX_ENTRY_BYTES) throw IOException("A backup file is too large")
            total += size
            if (total > MAX_TOTAL_BYTES) throw IOException("Backup exceeds the supported total size")
            records.add(EntryRecord(path, size, sha256(file)))
        }
        if (records.any { it.path == MANIFEST }) throw IOException("Snapshot uses a reserved backup filename")
        val manifest = JSONObject()
            .put("format", "NeaRerBackup")
            .put("version", FORMAT_VERSION)
            .put("files", JSONArray().apply {
                records.forEach { record ->
                    put(JSONObject().put("path", record.path).put("size", record.size).put("sha256", record.sha256))
                }
            })
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            // Put the manifest first so extraction knows the exact allow-list and
            // expected sizes before writing any payload bytes to disk.
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEach { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(path))
                FileInputStream(file).use { input -> input.copyTo(zip, BUFFER_BYTES) }
                zip.closeEntry()
            }
        }
    }

    private fun extractAndVerify(zipFile: File, staging: File): RestoreSummary {
        val expected = linkedMapOf<String, EntryRecord>()
        var manifestSeen = false
        var actualFiles = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    validatePath(entry.name.trimEnd('/'))
                    zip.closeEntry()
                    continue
                }
                validatePath(entry.name)
                if (entry.name == MANIFEST) {
                    if (manifestSeen) throw IOException("Backup has duplicate manifests")
                    val bytes = zip.readBounded(1L * 1024L * 1024L)
                    val manifest = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }
                        .getOrElse { throw IOException("Backup manifest is invalid") }
                    if (manifest.optString("format") != "NeaRerBackup" ||
                        manifest.optInt("version") != FORMAT_VERSION
                    ) throw IOException("Backup format is not supported")
                    val files = manifest.optJSONArray("files") ?: throw IOException("Backup manifest has no file list")
                    if (files.length() > MAX_ENTRIES) throw IOException("Backup has too many files")
                    for (i in 0 until files.length()) {
                        val item = files.optJSONObject(i) ?: throw IOException("Backup manifest entry is invalid")
                        val path = item.optString("path")
                        validatePath(path)
                        val size = item.optLong("size", -1L)
                        val hash = item.optString("sha256")
                        if (size < 0L || size > MAX_ENTRY_BYTES || !hash.matches(Regex("[a-f0-9]{64}"))) {
                            throw IOException("Backup manifest metadata is invalid")
                        }
                        if (expected.put(path, EntryRecord(path, size, hash)) != null) {
                            throw IOException("Backup manifest contains duplicate files")
                        }
                    }
                    manifestSeen = true
                    zip.closeEntry()
                    continue
                }
                if (!manifestSeen) throw IOException("Backup manifest must appear before its files")
                val record = expected[entry.name] ?: throw IOException("Backup contains an unlisted file")
                val target = File(staging, entry.name).canonicalFile
                val rootPath = staging.canonicalPath + File.separator
                if (!target.path.startsWith(rootPath)) throw IOException("Backup contains an unsafe path")
                target.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create restore folder") }
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        written += count
                        totalBytes += count
                        if (written > record.size || written > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                            throw IOException("Backup expands beyond its declared size")
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
                if (written != record.size) throw IOException("A backup file is incomplete")
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (actualHash != record.sha256) throw IOException("A backup file failed its integrity check")
                actualFiles++
                if (actualFiles > MAX_ENTRIES) throw IOException("Backup contains too many files")
                zip.closeEntry()
            }
        }
        if (!manifestSeen) throw IOException("Backup manifest is missing")
        if (actualFiles != expected.size || expected.keys.any { !File(staging, it).isFile }) {
            throw IOException("Backup is missing one or more files")
        }
        return RestoreSummary(actualFiles, totalBytes)
    }

    private fun validatePath(path: String) {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\') ||
            path.contains('\\') || path.split('/').any { it.isBlank() || it == "." || it == ".." } ||
            Regex("^[A-Za-z]:").containsMatchIn(path)
        ) throw IOException("Backup contains an unsafe file path")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun java.io.InputStream.readBounded(maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw IOException("Backup manifest is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
