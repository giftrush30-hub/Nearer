package com.nearer.app

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/**
 * Small, versioned envelope for NeaRer-to-NeaRer carrier SMS.
 *
 * Android reassembles multipart SMS before delivering the message to the
 * default SMS app. The envelope keeps the original sender label and a stable
 * message ID so the chat layer can deduplicate retries.
 */
data class SmsEnvelope(
    val messageId: String,
    val senderName: String,
    val body: String
) {
    companion object {
        private const val VERSION = "NR1"

        fun create(senderName: String, body: String): SmsEnvelope =
            SmsEnvelope(UUID.randomUUID().toString(), senderName.trim().take(40), body)

        fun encode(message: SmsEnvelope): String {
            require(message.messageId.isNotBlank()) { "Message ID is required" }
            require(message.body.isNotBlank()) { "Message body is required" }
            val encoder = Base64.getUrlEncoder().withoutPadding()
            fun field(value: String): String =
                encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
            return listOf(VERSION, message.messageId, field(message.senderName), field(message.body))
                .joinToString("|")
        }

        fun decode(raw: String): SmsEnvelope? {
            val fields = raw.split("|", limit = 4)
            if (fields.size != 4 || fields[0] != VERSION || fields[1].isBlank()) return null
            return try {
                val decoder = Base64.getUrlDecoder()
                fun read(value: String): String =
                    String(decoder.decode(value), StandardCharsets.UTF_8)
                val sender = read(fields[2]).take(40)
                val body = read(fields[3])
                if (body.isBlank()) null else SmsEnvelope(fields[1], sender, body)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
