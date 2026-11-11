package com.nearer.app

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Validates a persisted partial file against its durable checkpoint before it is
 * offered for resume. Bytes beyond the committed offset are uncommitted and are
 * truncated only after the committed prefix has passed SHA-256 verification.
 *
 * This is deliberately a local safety primitive: transport negotiation and
 * Nearby Payload.setOffset integration are still the caller's responsibility.
 */
internal object TransferResumeFileVerifier {
    private const val BUFFER_SIZE = 64 * 1024

    fun verifyAndTrim(partialFile: File, checkpoint: TransferResumeCheckpoint): Boolean {
        if (checkpoint.validationError() != null || !partialFile.isFile) return false
        val committed = checkpoint.committedOffsetBytes
        if (partialFile.length() < committed || partialFile.length() > checkpoint.expectedSizeBytes) {
            return false
        }

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            RandomAccessFile(partialFile, "rw").use { file ->
                file.seek(0L)
                val buffer = ByteArray(BUFFER_SIZE)
                var remaining = committed
                while (remaining > 0L) {
                    val read = file.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) return false
                    digest.update(buffer, 0, read)
                    remaining -= read.toLong()
                }

                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!MessageDigest.isEqual(
                        actual.toByteArray(Charsets.US_ASCII),
                        checkpoint.prefixSha256.lowercase().toByteArray(Charsets.US_ASCII)
                    )
                ) return false

                if (file.length() > committed) {
                    file.setLength(committed)
                    file.fd.sync()
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
