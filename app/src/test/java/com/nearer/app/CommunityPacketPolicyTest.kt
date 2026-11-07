package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityPacketPolicyTest {
    private val sender = "A1B2C3"

    private fun packet(type: String, code: String = "123456", senderId: String = sender): String =
        JSONObject().put("type", type).put("code", code).put("senderId", senderId)
            .put("sender", "Ada").toString()

    @Test fun acceptsValidGroupMessageFromAuthenticatedMember() {
        val message = JSONObject(packet("group_message"))
            .put("id", "message-1").put("body", "Hello community").toString()
        assertTrue(CommunityPacketPolicy.isValid(message, sender))
    }

    @Test fun rejectsSenderIdentitySpoofing() {
        assertFalse(CommunityPacketPolicy.isValid(packet("group_leave", senderId = "ABCDEF"), sender))
    }

    @Test fun rejectsUnknownPacketTypes() {
        assertFalse(CommunityPacketPolicy.isValid(packet("admin_override"), sender))
    }

    @Test fun rejectsInvalidGroupCodes() {
        assertFalse(CommunityPacketPolicy.isValid(packet("group_leave", code = "12x456"), sender))
    }

    @Test fun rejectsOversizedPacketsAndMessages() {
        assertFalse(CommunityPacketPolicy.isValid(" ".repeat(8_193), sender))
        val message = JSONObject(packet("group_message"))
            .put("id", "message-1").put("body", "x".repeat(1_501)).toString()
        assertFalse(CommunityPacketPolicy.isValid(message, sender))
    }

    @Test fun rejectsUnboundedMembershipSnapshots() {
        val members = JSONArray()
        repeat(101) { members.put(JSONObject().put("id", "ID$it").put("name", "Member")) }
        val snapshot = JSONObject(packet("group_members"))
            .put("ownerId", sender).put("members", members)
            .put("pendingMembers", JSONArray()).put("moderators", JSONArray()).toString()
        assertFalse(CommunityPacketPolicy.isValid(snapshot, sender))
    }

    @Test fun rejectsMalformedOrMissingAuthenticatedSender() {
        assertFalse(CommunityPacketPolicy.isValid(packet("group_leave"), ""))
        assertFalse(CommunityPacketPolicy.isValid(packet("group_leave"), "not-a-stable-id"))
    }
}
