package com.nearer.app

import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Writes to a private partial file and publishes it only after size and optional SHA-256 checks pass. */
internal object FileTransferIntegrity {
    fun copyAndVerify(
        input: java.io.InputStream,
        directory: File,
        displayName: String,
        expectedSize: Long,
        expectedSha256: String = ""
    ): File {
        require(expectedSize >= 0L) { "Invalid expected file size" }
        val normalizedHash = expectedSha256.trim().lowercase()
        require(normalizedHash.isEmpty() || normalizedHash.matches(Regex("[a-f0-9]{64}"))) {
            "Invalid file checksum metadata"
        }
        if (!directory.exists() && !directory.mkdirs()) throw java.io.IOException("Could not create received-files directory")
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "shared-file" }
        val target = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}-$safeName")
        val partial = File.createTempFile(".nearer-", ".part", directory)
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            java.io.FileOutputStream(partial).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > expectedSize) throw java.io.IOException("Received file exceeded its declared size")
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.flush()
                output.fd.sync()
            }
            if (copied != expectedSize) throw java.io.IOException("Received file size did not match")
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (normalizedHash.isNotEmpty() && actualHash != normalizedHash) {
                throw java.io.IOException("Received file checksum did not match")
            }
            if (!partial.renameTo(target)) throw java.io.IOException("Could not finalize the received file")
            return target
        } catch (error: Exception) {
            partial.delete()
            target.delete()
            throw error
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
