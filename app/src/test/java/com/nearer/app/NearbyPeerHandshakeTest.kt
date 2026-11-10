package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPeerHandshakeTest {
    @Test
    fun transcriptIsIndependentOfWhichPeerBuildsIt() {
        val aNonce = ByteArray(32) { (it + 1).toByte() }
        val bNonce = ByteArray(32) { (it + 65).toByte() }
        val fromA = NearbyPeerHandshake.transcriptChallenge("peer-a", aNonce, "peer-b", bNonce)
        val fromB = NearbyPeerHandshake.transcriptChallenge("peer-b", bNonce, "peer-a", aNonce)
        assertArrayEquals(fromA, fromB)
    }

    @Test
    fun transcriptChangesWhenEitherChallengeChanges() {
        val aNonce = ByteArray(32) { (it + 1).toByte() }
        val bNonce = ByteArray(32) { (it + 65).toByte() }
        val original = NearbyPeerHandshake.transcriptChallenge("peer-a", aNonce, "peer-b", bNonce)
        val changed = bNonce.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val altered = NearbyPeerHandshake.transcriptChallenge("peer-a", aNonce, "peer-b", changed)
        assertFalse(original.contentEquals(altered))
    }

    @Test
    fun transcriptRejectsSameIdentityOrMalformedNonce() {
        val nonce = ByteArray(32)
        assertTrue(runCatching {
            NearbyPeerHandshake.transcriptChallenge("same", nonce, "same", nonce)
        }.isFailure)
        assertTrue(runCatching {
            NearbyPeerHandshake.transcriptChallenge("a", ByteArray(4), "b", nonce)
        }.isFailure)
    }
}
