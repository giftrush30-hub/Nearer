package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityIngressPolicyTest {
    private val sender = "23456789ABCD"

    private fun packet(type: String, block: JSONObject.() -> Unit = {}): String =
        JSONObject().put("type", type).put("sender", "Ada").apply(block).toString()

    @Test fun acceptsCurrentGroupMessageFromAuthenticatedStablePeer() {
        assertTrue(CommunityIngressPolicy.isValid(
            packet("group_message") {
                put("code", "123456").put("id", "message-1").put("body", "Hello")
            }, sender
        ))
    }

    @Test fun rejectsGroupMessageWithoutAuthenticatedStablePeer() {
        assertFalse(CommunityIngressPolicy.isValid(
            packet("group_message") {
                put("code", "123456").put("id", "message-1").put("body", "Hello")
            }, ""
        ))
    }

    @Test fun rejectsSpoofedGroupCreationOwner() {
        assertFalse(CommunityIngressPolicy.isValid(
            packet("group_created") {
                put("code", "123456").put("name", "Builders").put("ownerPeerId", "ABCDEF")
            }, sender
        ))
    }

    @Test fun rejectsMalformedAndOversizedGroupMessages() {
        assertFalse(CommunityIngressPolicy.isValid(
            packet("group_message") {
                put("code", "12x456").put("id", "m").put("body", "Hello")
            }, sender
        ))
        assertFalse(CommunityIngressPolicy.isValid(
            packet("group_message") {
                put("code", "123456").put("id", "m").put("body", "x".repeat(1_501))
            }, sender
        ))
    }

    @Test fun acceptsGroupReadReceiptOnlyForItsClaimedAuthenticatedReader() {
        val valid = packet("group_read_receipt") {
            put("code", "123456")
            put("readerPeerId", sender)
            put("messageIds", JSONArray(listOf("message-1", "message-2")))
            put("readAt", 100L)
        }
        assertTrue(CommunityIngressPolicy.isValid(valid, sender))

        val spoofedReader = packet("group_read_receipt") {
            put("code", "123456")
            put("readerPeerId", "ABCDEF")
            put("messageIds", JSONArray(listOf("message-1")))
            put("readAt", 100L)
        }
        assertFalse(CommunityIngressPolicy.isValid(spoofedReader, sender))
    }

    @Test fun rejectsEmptyDuplicateAndOversizedGroupReadReceipts() {
        val empty = packet("group_read_receipt") {
            put("code", "123456").put("readerPeerId", sender)
            put("messageIds", JSONArray()).put("readAt", 100L)
        }
        assertFalse(CommunityIngressPolicy.isValid(empty, sender))

        val duplicate = packet("group_read_receipt") {
            put("code", "123456").put("readerPeerId", sender)
            put("messageIds", JSONArray(listOf("m1", "m1"))).put("readAt", 100L)
        }
        assertFalse(CommunityIngressPolicy.isValid(duplicate, sender))

        val oversized = packet("group_read_receipt") {
            put("code", "123456").put("readerPeerId", sender)
            put("messageIds", JSONArray((1..41).map { "m$it" })).put("readAt", 100L)
        }
        assertFalse(CommunityIngressPolicy.isValid(oversized, sender))
    }

    @Test fun rejectsUnknownAndOversizedPackets() {
        assertFalse(CommunityIngressPolicy.isValid(packet("admin_override"), sender))
        assertFalse(CommunityIngressPolicy.isValid("x".repeat(24 * 1024 + 1), sender))
    }

    @Test fun validatesBoundedRosterAndStableIds() {
        val good = packet("group_members") {
            put("code", "123456").put("ownerPeerId", sender)
            put("memberPeerIds", JSONArray(listOf(sender, "ABCDEF")))
            put("members", JSONArray(listOf("Ada", "Bo")))
        }
        assertTrue(CommunityIngressPolicy.isValid(good, sender))

        val bad = packet("group_members") {
            put("code", "123456").put("ownerPeerId", sender)
            put("memberPeerIds", JSONArray(listOf(sender, "not-an-id")))
            put("members", JSONArray(listOf("Ada", "Bo")))
        }
        assertFalse(CommunityIngressPolicy.isValid(bad, sender))
    }

    @Test fun permitsBoundedLegacySocketLudoButNotLegacyGroupMessages() {
        val invite = packet("ludo_invite") { put("matchId", "room-1") }
        assertTrue(CommunityIngressPolicy.isValid(invite, "", allowLegacySocketLudo = true))
        assertFalse(CommunityIngressPolicy.isValid(invite, ""))
        val group = packet("group_message") {
            put("code", "123456").put("id", "m").put("body", "Hello")
        }
        assertFalse(CommunityIngressPolicy.isValid(group, "", allowLegacySocketLudo = true))
    }

    @Test fun validatesLudoActionVocabularyAndRevision() {
        val valid = packet("ludo_action") {
            put("matchId", "room-1").put("action", "move").put("token", 2).put("revision", 4)
        }
        assertTrue(CommunityIngressPolicy.isValid(valid, sender))
        val invalid = packet("ludo_action") {
            put("matchId", "room-1").put("action", "delete").put("token", 99).put("revision", -1)
        }
        assertFalse(CommunityIngressPolicy.isValid(invalid, sender))
    }

    @Test fun rejectsLudoStateWithoutBoundedStateObject() {
        val invalid = packet("ludo_state") { put("matchId", "room-1").put("revision", 1) }
        assertFalse(CommunityIngressPolicy.isValid(invalid, sender))
    }
}
