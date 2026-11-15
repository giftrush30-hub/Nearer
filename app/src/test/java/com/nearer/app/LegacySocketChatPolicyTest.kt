package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Test

class LegacySocketChatPolicyTest {
    @Test fun legacySocketCannotSendPrivateChat() {
        assertFalse(LegacySocketChatPolicy.mayUseSocketChat())
    }

    @Test fun legacySocketCannotAuthenticateIncomingMessagesOrReceipts() {
        assertFalse(LegacySocketChatPolicy.mayAcceptSocketChatFrame())
    }
}
