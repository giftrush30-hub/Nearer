package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatEditPolicyTest {
    private val peerA = NeaRerIdentity.generate()
    private val peerB = NeaRerIdentity.generate()

    @Test
    fun onlyOriginalAuthorCanEditItsIncomingConversationMessage() {
        assertEquals(true, ChatEditPolicy.mayApplyToMessage(peerA, peerA, false))
        assertEquals(false, ChatEditPolicy.mayApplyToMessage(peerB, peerA, false))
        assertEquals(false, ChatEditPolicy.mayApplyToMessage(peerA, peerA, true))
        assertEquals(false, ChatEditPolicy.mayApplyToMessage(peerA, "not-a-peer", false))
    }

    @Test
    fun createsNextRevisionOnlyForChangedNonBlankText() {
        val revision = ChatEditPolicy.create("Hello", 0, " Hello again ", 100L)
        assertEquals(ChatEditPolicy.Revision(1, "Hello again", 100L), revision)
        assertNull(ChatEditPolicy.create("Hello", 0, "Hello", 100L))
        assertNull(ChatEditPolicy.create("Hello", 0, "   ", 100L))
    }

    @Test
    fun rejectsOversizedTextAndInvalidRevisionState() {
        assertNull(ChatEditPolicy.create("old", 0, "x".repeat(ChatEditPolicy.MAX_BODY_LENGTH + 1), 100L))
        assertNull(ChatEditPolicy.create("old", ChatEditPolicy.MAX_REVISION, "new", 100L))
        assertNull(ChatEditPolicy.create("old", 0, "new", 0L))
    }

    @Test
    fun acceptsExactlyNextRevision() {
        assertEquals(
            ChatEditPolicy.ApplyResult.APPLIED,
            ChatEditPolicy.apply("old", 0, 0L, ChatEditPolicy.Revision(1, "new", 100L))
        )
    }

    @Test
    fun duplicateRevisionIsIdempotent() {
        assertEquals(
            ChatEditPolicy.ApplyResult.ALREADY_APPLIED,
            ChatEditPolicy.apply("new", 1, 100L, ChatEditPolicy.Revision(1, "new", 100L))
        )
    }

    @Test
    fun olderRevisionIsStaleAndCannotOverwriteNewerText() {
        assertEquals(
            ChatEditPolicy.ApplyResult.STALE,
            ChatEditPolicy.apply("latest", 2, 200L, ChatEditPolicy.Revision(1, "old edit", 100L))
        )
    }

    @Test
    fun rejectsSkippedOrConflictingRevisions() {
        assertEquals(
            ChatEditPolicy.ApplyResult.REJECTED,
            ChatEditPolicy.apply("old", 0, 0L, ChatEditPolicy.Revision(2, "new", 100L))
        )
        assertEquals(
            ChatEditPolicy.ApplyResult.REJECTED,
            ChatEditPolicy.apply("different", 1, 90L, ChatEditPolicy.Revision(1, "new", 100L))
        )
    }
}
