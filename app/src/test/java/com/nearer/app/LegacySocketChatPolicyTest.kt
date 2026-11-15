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

    @Test fun legacySocketCannotSendOrAcceptCommunityAndGamePackets() {
        assertFalse(LegacySocketChatPolicy.maySendCommunityOrGameOverSocket())
        assertFalse(LegacySocketChatPolicy.mayAcceptCommunityOrGameSocketFrame())
    }
}
