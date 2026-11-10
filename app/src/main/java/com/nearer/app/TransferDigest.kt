package com.nearer.app

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Copies a provider-backed source to disk while calculating its integrity hash in the same pass. */
internal object TransferDigest {
    data class CopyResult(val bytesCopied: Long, val sha256: String)

    fun copyAndSha256(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = 1024 * 1024
    ): CopyResult {
        require(bufferSize > 0) { "bufferSize must be positive" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            copied = Math.addExact(copied, count.toLong())
        }
        val hex = "0123456789abcdef"
        val hash = buildString(64) {
            digest.digest().forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
        return CopyResult(copied, hash)
    }
}
