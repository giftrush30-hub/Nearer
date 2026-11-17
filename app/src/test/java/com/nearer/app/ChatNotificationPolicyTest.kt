package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationPolicyTest {
    @Test fun notifiesOnlyForPersistedNewMessagesWhileBackgrounded() {
        assertTrue(ChatNotificationPolicy.shouldNotify(false, true, true))
        assertFalse(ChatNotificationPolicy.shouldNotify(true, true, true))
        assertFalse(ChatNotificationPolicy.shouldNotify(false, false, true))
        assertFalse(ChatNotificationPolicy.shouldNotify(false, true, false))
    }

    @Test fun notificationIdIsStableAndNonNegative() {
        val first = ChatNotificationPolicy.notificationId("message-123")
        assertEquals(first, ChatNotificationPolicy.notificationId("message-123"))
        assertTrue(first > 0)
        assertTrue(ChatNotificationPolicy.notificationId("another-message") >= 0)
    }
}
