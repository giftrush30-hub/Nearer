package com.nearer.app

/**
 * Validates and bounds quoted-message metadata before it is attached to a chat
 * message. The target must belong to the same conversation as the new message.
 *
 * This policy is shared by direct and recipient-encrypted message paths.
 */
object ChatReplyPolicy {
    const val MAX_MESSAGE_ID_LENGTH = 80
    const val MAX_PREVIEW_LENGTH = 180
    const val MAX_SENDER_LENGTH = 48

    data class Quote(
        val messageId: String,
        val preview: String,
        val sender: String
    )

    fun createQuote(
        target: ChatMessage?,
        conversationId: String,
        senderLabel: String
    ): Quote? {
        if (target == null || conversationId.isBlank()) return null
        if (target.conversationId != conversationId) return null
        val messageId = target.id.trim()
        if (messageId.isBlank() || messageId.length > MAX_MESSAGE_ID_LENGTH) return null
        val preview = target.body.trim().take(MAX_PREVIEW_LENGTH)
        if (preview.isBlank()) return null
        val sender = senderLabel.trim().take(MAX_SENDER_LENGTH).ifBlank {
            if (target.outgoing) "You" else target.conversationName.take(MAX_SENDER_LENGTH)
        }.ifBlank { "Contact" }
        return Quote(messageId, preview, sender)
    }

    fun isValidIncomingQuote(
        messageId: String?,
        preview: String?,
        sender: String?
    ): Boolean {
        val id = messageId?.trim().orEmpty()
        val text = preview?.trim().orEmpty()
        val name = sender?.trim().orEmpty()
        return id.isNotBlank() && id.length <= MAX_MESSAGE_ID_LENGTH &&
            text.isNotBlank() && text.length <= MAX_PREVIEW_LENGTH &&
            name.isNotBlank() && name.length <= MAX_SENDER_LENGTH
    }

    fun previewForDisplay(preview: String?): String =
        preview.orEmpty().trim().take(MAX_PREVIEW_LENGTH)
}
