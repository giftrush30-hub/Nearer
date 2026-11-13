package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatReactionPolicyTest {
    private val peerA = "23456789ABCD"
    private val peerB = "23456789ABCE"

    @Test
    fun onePeerCanChangeOrRemoveItsReaction() {
        val first = ChatReactionPolicy.toggle(emptyMap(), peerA, "👍")
        assertEquals("👍", first[peerA])
        val changed = ChatReactionPolicy.toggle(first, peerA, "❤️")
        assertEquals("❤️", changed[peerA])
        val removed = ChatReactionPolicy.toggle(changed, peerA, "❤️")
        assertEquals(emptyMap<String, String>(), removed)
    }

    @Test
    fun reactionsFromDifferentPeersRemainIndependent() {
        val first = ChatReactionPolicy.toggle(emptyMap(), peerA, "😂")
        val second = ChatReactionPolicy.toggle(first, peerB, "🙏")
        assertEquals(2, second.size)
        assertEquals("😂", second[peerA])
        assertEquals("🙏", second[peerB])
    }

    @Test
    fun rejectsUnknownEmojiAndInvalidPeerIds() {
        assertEquals(emptyMap<String, String>(), ChatReactionPolicy.toggle(emptyMap(), peerA, "🔥"))
        assertEquals(emptyMap<String, String>(), ChatReactionPolicy.toggle(emptyMap(), "not-a-peer", "👍"))
    }

    @Test
    fun applyingTheSameIncomingReactionTwiceIsIdempotent() {
        val first = ChatReactionPolicy.set(emptyMap(), peerA, "👍")
        val replayed = ChatReactionPolicy.set(first, peerA, "👍")
        assertEquals(first, replayed)
        val removed = ChatReactionPolicy.set(replayed, peerA, null)
        val replayedRemoval = ChatReactionPolicy.set(removed, peerA, null)
        assertEquals(emptyMap<String, String>(), replayedRemoval)
    }

    @Test
    fun doesNotMutateTheOriginalMap() {
        val original = mapOf(peerA to "👍")
        val updated = ChatReactionPolicy.toggle(original, peerB, "❤️")
        assertEquals(1, original.size)
        assertEquals(2, updated.size)
        assertNotEquals(original, updated)
    }

    @Test
    fun capsDistinctReactors() {
        val full = (0 until 32).associate { index ->
            val id = NeaRerIdentity.generate()
            id to listOf("👍", "❤️", "😂", "😮", "😢", "🙏")[index % 6]
        }
        assertEquals(32, ChatReactionPolicy.toggle(full, NeaRerIdentity.generate(), "👍").size)
    }
}
