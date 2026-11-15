package com.nearer.app

/**
 * Keeps background alerts limited to genuinely new messages that were safely
 * persisted locally. Edits, reactions, duplicates and failed writes must not
 * produce a second "new message" notification.
 */
internal object ChatNotificationPolicy {
    fun shouldNotify(isActivityResumed: Boolean, isNewMessage: Boolean, isPersisted: Boolean): Boolean =
        !isActivityResumed && isNewMessage && isPersisted

    /** Stable, non-negative ID so an ACK retry cannot create a second alert. */
    fun notificationId(messageId: String): Int {
        val id = messageId.hashCode() and Int.MAX_VALUE
        return if (id == 0) 1 else id
    }
}
