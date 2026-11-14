package com.nearer.app

/** Formats a fresh forward without copying stable sender or source-message identifiers. */
internal object ChatForwardPolicy {
    const val MAX_BODY_LENGTH = 3500
    const val MAX_ATTRIBUTION_LENGTH = 48
    data class Draft(val body: String, val forwardedAt: Long, val attribution: String?)

    fun create(source: ChatMessage?, targetConversationId: String,
        includeAttribution: Boolean = false, senderDisplayLabel: String? = null, now: Long): Draft? {
        if (source == null || targetConversationId.isBlank() || source.conversationId.isBlank() ||
            source.body.isBlank() || source.body.length > MAX_BODY_LENGTH || now <= 0L) return null
        val attribution = if (includeAttribution) {
            sanitizeLabel(senderDisplayLabel).takeIf { it.isNotBlank() } ?: return null
        } else null
        val header = if (attribution == null) "Forwarded message" else "Forwarded from $attribution"
        val body = "$header\n${source.body.trim()}"
        if (body.length > MAX_BODY_LENGTH) return null
        return Draft(body, now, attribution)
    }

    private fun sanitizeLabel(value: String?): String = value.orEmpty()
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .filter { it >= ' ' && it != '\u007f' }
        .trim().take(MAX_ATTRIBUTION_LENGTH)
}
