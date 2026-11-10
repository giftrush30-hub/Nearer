package com.nearer.app

import java.io.InputStream
import java.io.OutputStream

/** Copies a received stream without spinning forever if a provider returns a zero-byte bulk read. */
internal object TransferStreamCopy {
    fun copy(input: InputStream, output: OutputStream, bufferSize: Int = 1024 * 1024): Long {
        require(bufferSize > 0) { "bufferSize must be positive" }
        val buffer = ByteArray(bufferSize)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val singleByte = input.read()
                if (singleByte < 0) break
                output.write(singleByte)
                copied = Math.addExact(copied, 1L)
            } else {
                output.write(buffer, 0, count)
                copied = Math.addExact(copied, count.toLong())
            }
        }
        return copied
    }
}
