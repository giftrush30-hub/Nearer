package com.nearer.app

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Copies exactly one manifest-declared file using a bounded memory buffer.
 * A short source is an incomplete transfer; extra source bytes are not consumed.
 */
internal object TransferStreamIO {
    data class CopyResult(val bytesCopied: Long, val sha256: ByteArray)

    fun copyExactly(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long,
        bufferBytes: Int,
        isCancelled: () -> Boolean = { false }
    ): CopyResult {
        require(expectedBytes >= 0L) { "Expected byte count cannot be negative" }
        require(bufferBytes in 4_096..1_048_576) { "Transfer buffer size is outside safe bounds" }

        val buffer = ByteArray(bufferBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        while (copied < expectedBytes) {
            if (isCancelled()) throw TransferCancelledException()
            val wanted = minOf(buffer.size.toLong(), expectedBytes - copied).toInt()
            val read = input.read(buffer, 0, wanted)
            if (read < 0) throw EOFException("Transfer stream ended before the declared file size")
            if (read == 0) {
                // A few custom/provider streams can legally make no progress on a
                // bulk read. Fall back to one byte so we cannot spin forever.
                val single = input.read()
                if (single < 0) throw EOFException("Transfer stream ended before the declared file size")
                output.write(single)
                digest.update(single.toByte())
                copied += 1L
                continue
            }
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            copied += read
        }
        // Recheck after the final expected byte: cancellation can race with the
        // last read, and must not turn a cancelled transfer into a successful one.
        if (isCancelled()) throw TransferCancelledException()
        if (input.read() != -1) {
            throw IllegalStateException("Received stream contains bytes beyond the declared file size")
        }
        if (isCancelled()) throw TransferCancelledException()
        output.flush()
        return CopyResult(copied, digest.digest())
    }

    fun sha256Exactly(
        input: InputStream,
        expectedBytes: Long,
        bufferBytes: Int,
        isCancelled: () -> Boolean = { false }
    ): ByteArray {
        require(expectedBytes >= 0L) { "Expected byte count cannot be negative" }
        require(bufferBytes in 4_096..1_048_576) { "Transfer buffer size is outside safe bounds" }
        val buffer = ByteArray(bufferBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        var readTotal = 0L
        while (readTotal < expectedBytes) {
            if (isCancelled()) throw TransferCancelledException()
            val wanted = minOf(buffer.size.toLong(), expectedBytes - readTotal).toInt()
            val count = input.read(buffer, 0, wanted)
            if (count < 0) throw EOFException("Selected file ended before its declared size")
            if (count == 0) {
                val single = input.read()
                if (single < 0) throw EOFException("Selected file ended before its declared size")
                digest.update(single.toByte())
                readTotal++
            } else {
                digest.update(buffer, 0, count)
                readTotal += count
            }
        }
        if (isCancelled()) throw TransferCancelledException()
        if (input.read() != -1) throw IllegalStateException("Selected file changed while its integrity check was running")
        return digest.digest()
    }
    /** Hash exactly the requested prefix without consuming bytes beyond it. */
    fun sha256PrefixExactly(
        input: InputStream,
        prefixBytes: Long,
        bufferBytes: Int,
        isCancelled: () -> Boolean = { false }
    ): ByteArray {
        require(prefixBytes >= 0L) { "Prefix length cannot be negative" }
        require(bufferBytes in 4_096..1_048_576) { "Transfer buffer size is outside safe bounds" }
        val buffer = ByteArray(bufferBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        var hashed = 0L
        while (hashed < prefixBytes) {
            if (isCancelled()) throw TransferCancelledException()
            val wanted = minOf(buffer.size.toLong(), prefixBytes - hashed).toInt()
            val count = input.read(buffer, 0, wanted)
            if (count < 0) throw EOFException("Selected file ended before the resume prefix")
            if (count == 0) {
                val single = input.read()
                if (single < 0) throw EOFException("Selected file ended before the resume prefix")
                digest.update(single.toByte())
                hashed++
            } else {
                digest.update(buffer, 0, count)
                hashed += count
            }
        }
        if (isCancelled()) throw TransferCancelledException()
        return digest.digest()
    }

    fun sha256Hex(digestBytes: ByteArray): String =
        digestBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal class TransferCancelledException : Exception("Transfer cancelled")
