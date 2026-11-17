package com.nearer.app

import org.json.JSONObject

/**
 * Keeps background alerts limited to genuinely new messages that were safely
 * persisted locally. Edits, reactions, duplicates and failed writes must not
 * produce a second "new message" notification.
 */
internal object ChatNotificationPolicy {
    fun shouldNotify(isActivityResumed: Boolean, isNewMessage: Boolean, isPersisted: Boolean): Boolean =
        !isActivityResumed && isNewMessage && isPersisted

    /** Internal protocol packets are not user messages and must not raise alerts. */
    fun isUserVisibleMessageBody(body: String): Boolean {
        if (body.isBlank()) return false
        if (body.startsWith("NR_COMMUNITY:")) {
            val packet = runCatching { JSONObject(body.removePrefix("NR_COMMUNITY:")) }.getOrNull() ?: return false
            return packet.optString("type") == "group_message"
        }
        return listOf(
            "NR_TYPING:",
            "NR_DELETE:",
            "NR_READ_RECEIPT:",
            "NR_REACTION:",
            "NR_EDIT:"
        ).none(body::startsWith)
    }

    /** Stable, non-negative ID so an ACK retry cannot create a second alert. */
    fun notificationId(messageId: String): Int {
        val id = messageId.hashCode() and Int.MAX_VALUE
        return if (id == 0) 1 else id
    }
}
