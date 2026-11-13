package com.nearer.app

/** Bounded, per-peer reactions. A peer can hold one reaction per message. */
internal object ChatReactionPolicy {
    private val allowedEmojis = setOf("👍", "❤️", "😂", "😮", "😢", "🙏")
    private const val MAX_REACTORS = 32

    fun isValidEmoji(emoji: String): Boolean = emoji in allowedEmojis

    fun isValidActor(actorPeerId: String): Boolean =
        NeaRerIdentity.normalize(actorPeerId) == actorPeerId

    fun set(
        current: Map<String, String>,
        actorPeerId: String,
        emoji: String?
    ): Map<String, String> {
        if (!isValidActor(actorPeerId) || (emoji != null && !isValidEmoji(emoji))) return current
        if (current.keys.any { !isValidActor(it) } || current.values.any { !isValidEmoji(it) }) return current
        val updated = LinkedHashMap(current)
        if (emoji == null) {
            updated.remove(actorPeerId)
        } else {
            if (actorPeerId !in updated && updated.size >= MAX_REACTORS) return current
            updated[actorPeerId] = emoji
        }
        return updated
    }

    fun toggle(current: Map<String, String>, actorPeerId: String, emoji: String): Map<String, String> =
        set(current, actorPeerId, if (current[actorPeerId] == emoji) null else emoji)
}
