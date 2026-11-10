package com.nearer.app

/** Incremental SHA-256 helper for large files; never buffers the complete payload. */
internal class FastShareSha256 {
    private val digest = java.security.MessageDigest.getInstance("SHA-256")
    private var finished = false

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        check(!finished) { "Digest is already finalized" }
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        digest.update(bytes, offset, length)
    }

    fun finishHex(): String {
        check(!finished) { "Digest is already finalized" }
        finished = true
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
