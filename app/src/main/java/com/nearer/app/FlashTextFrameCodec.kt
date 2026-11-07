package com.nearer.app

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.CRC32

/**
 * Framing layer for custom NeaRer Flash text.
 *
 * This class prepares reliable, ordered, integrity-checked text frames. It does
 * not claim that missed-call metadata can carry these frames: a separate carrier
 * signalling transport must deliver each frame before this codec can be used
 * end-to-end.
 */
object FlashTextFrameCodec {
    private const val VERSION = "NF1"
    private const val MAX_TEXT_BYTES = 160
    private const val CHUNK_BYTES = 12
    private const val MAX_MESSAGE_ID = 0xFFFF

    data class Frame(
        val messageId: Int,
        val index: Int,
        val total: Int,
        val payloadHex: String,
        val checksum: String
    ) {
        fun serialize(): String =
            "$VERSION:%04X:%02X:%02X:%s:%s".format(
                Locale.ROOT, messageId, index, total, payloadHex, checksum
            )
    }

    /** Split UTF-8 text into small frames suitable for a future transport. */
    fun encode(text: String, messageId: Int): List<Frame> {
        require(messageId in 0..MAX_MESSAGE_ID) { "Message ID must fit in 16 bits." }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        require(bytes.isNotEmpty()) { "Flash message cannot be empty." }
        require(bytes.size <= MAX_TEXT_BYTES) { "Flash text is limited to $MAX_TEXT_BYTES UTF-8 bytes." }

        val chunks = bytes.toList().chunked(CHUNK_BYTES)
        require(chunks.size <= 0xFF) { "Too many Flash frames." }
        return chunks.mapIndexed { index, chunk ->
            val payload = chunk.toByteArray()
            val hex = payload.joinToString("") { "%02X".format(Locale.ROOT, it.toInt() and 0xFF) }
            Frame(messageId, index, chunks.size, hex, checksum(messageId, index, chunks.size, payload))
        }
    }

    /** Reassemble only a complete, consistent set of frames with valid checksums. */
    fun decode(frames: List<Frame>): String? {
        if (frames.isEmpty()) return null
        val first = frames.first()
        if (first.total !in 1..0xFF || frames.size != first.total) return null
        if (frames.any { it.messageId != first.messageId || it.total != first.total }) return null
        val ordered = frames.sortedBy { it.index }
        if (ordered.map { it.index } != (0 until first.total).toList()) return null

        val bytes = ArrayList<Byte>()
        for (frame in ordered) {
            if (frame.payloadHex.length % 2 != 0 || frame.payloadHex.length > CHUNK_BYTES * 2) return null
            val payload = try {
                frame.payloadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (_: NumberFormatException) {
                return null
            }
            if (checksum(frame.messageId, frame.index, frame.total, payload) != frame.checksum) return null
            bytes.addAll(payload.toList())
        }
        if (bytes.size > MAX_TEXT_BYTES) return null
        val decoded = bytes.toByteArray().toString(StandardCharsets.UTF_8)
        // Reject malformed UTF-8 rather than silently replacing invalid bytes.
        if (!decoded.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes.toByteArray())) return null
        return decoded
    }

    fun parse(serialized: String): Frame? {
        val parts = serialized.split(':')
        if (parts.size != 6 || parts[0] != VERSION) return null
        return try {
            val id = parts[1].toInt(16)
            val index = parts[2].toInt(16)
            val total = parts[3].toInt(16)
            val payload = parts[4]
            val crc = parts[5]
            if (id !in 0..MAX_MESSAGE_ID || total !in 1..0xFF || index !in 0 until total) return null
            if (payload.length % 2 != 0 || payload.length > CHUNK_BYTES * 2 || !payload.matches(Regex("[0-9A-Fa-f]*"))) return null
            if (!crc.matches(Regex("[0-9A-F]{8}"))) return null
            Frame(id, index, total, payload.uppercase(Locale.ROOT), crc)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun checksum(messageId: Int, index: Int, total: Int, payload: ByteArray): String {
        val crc = CRC32()
        crc.update(byteArrayOf(
            (messageId shr 8).toByte(), messageId.toByte(), index.toByte(), total.toByte()
        ))
        crc.update(payload)
        return "%08X".format(Locale.ROOT, crc.value)
    }
}
