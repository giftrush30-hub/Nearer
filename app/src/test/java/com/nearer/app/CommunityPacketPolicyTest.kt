package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityPacketPolicyTest {
    private val sender = "A1B2C3"

    private fun packet(
        type: String,
        code: String = "123456",
        senderId: String = sender,
        body: String = "",
        messageId: String = "",
        ownerId: String = "",
        name: String = "",
        memberCount: Int = 0
    ) = CommunityPacketDescriptor(
        rawLength = 100,
        type = type,
        senderId = senderId,
        authenticatedSenderId = sender,
        code = code,
        senderName = "Ada",
        ownerId = ownerId,
        name = name,
        messageId = messageId,
        body = body,
        memberCount = memberCount
    )

    @Test fun acceptsValidGroupMessageFromAuthenticatedMember() {
        assertTrue(CommunityPacketPolicy.isValid(packet(
            "group_message", body = "Hello community", messageId = "message-1"
        )))
    }

    @Test fun rejectsSenderIdentitySpoofing() {
        assertFalse(CommunityPacketPolicy.isValid(packet("group_leave", senderId = "ABCDEF")))
    }

    @Test fun rejectsUnknownPacketTypes() {
        assertFalse(CommunityPacketPolicy.isValid(packet("admin_override")))
    }

    @Test fun rejectsInvalidGroupCodes() {
        assertFalse(CommunityPacketPolicy.isValid(packet("group_leave", code = "12x456")))
    }

    @Test fun rejectsOversizedPacketsAndMessages() {
        assertFalse(CommunityPacketPolicy.isValid(packet("group_message", body = "x", messageId = "m").copy(rawLength = 8_193)))
        assertFalse(CommunityPacketPolicy.isValid(packet(
            "group_message", body = "x".repeat(1_501), messageId = "message-1"
        )))
    }

    @Test fun rejectsUnboundedMembershipSnapshots() {
        assertFalse(CommunityPacketPolicy.isValid(packet("group_members", memberCount = 101)))
    }

    @Test fun rejectsMalformedOrMissingAuthenticatedSender() {
        assertFalse(CommunityPacketPolicy.isValid(packet("group_leave").copy(authenticatedSenderId = "")))
        assertFalse(CommunityPacketPolicy.isValid(packet("group_leave").copy(authenticatedSenderId = "not-a-stable-id")))
    }

    @Test fun validatesCommunityCreationOwnership() {
        assertTrue(CommunityPacketPolicy.isValid(packet("group_created", ownerId = sender, name = "Builders")))
        assertFalse(CommunityPacketPolicy.isValid(packet("group_created", ownerId = "ABCDEF", name = "Builders")))
    }
}
