package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MeshRelayPolicyTest {
    @Test fun relaysCommunityMessagesWithinHopBudget() {
        assertEquals(true, MeshRelayPolicy.shouldRelay(true, "NR_COMMUNITY:{\"type\":\"broadcast\"}", 4, 0))
        assertEquals(true, MeshRelayPolicy.shouldRelay(true, "NR_COMMUNITY:{\"type\":\"broadcast\"}", 1, 3))
    }

    @Test fun neverRelaysPrivateChatEvenWhenPacketHasTtl() {
        assertEquals(false, MeshRelayPolicy.shouldRelay(true, "Hello privately", 4, 0))
    }

    @Test fun disabledRelayDoesNotForwardCommunityMessages() {
        assertEquals(false, MeshRelayPolicy.shouldRelay(false, "NR_COMMUNITY:{}", 4, 0))
    }

    @Test fun rejectsExpiredOrOverHoppedPackets() {
        assertEquals(false, MeshRelayPolicy.shouldRelay(true, "NR_COMMUNITY:{}", 0, 0))
        assertEquals(false, MeshRelayPolicy.shouldRelay(true, "NR_COMMUNITY:{}", 4, 4))
    }
}
