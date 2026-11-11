package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferBundleSessionTest {
    private fun manifest() = TransferBundleManifest("bundle-session-1", listOf(
        TransferBundleItem("folder", "Photos", "Photos", 0L, null, TransferBundleItem.Kind.DIRECTORY),
        TransferBundleItem("a", "Photos/a.jpg", "a.jpg", 100L, "image/jpeg"),
        TransferBundleItem("b", "Photos/b.jpg", "b.jpg", 200L, "image/jpeg")
    ))

    @Test
    fun requiresAcceptanceBeforePayloadCanStart() {
        val session = TransferBundleSession(manifest())
        assertThrows(IllegalStateException::class.java) { session.attachPayload("a", 10L) }
        session.accept()
        session.attachPayload("a", 10L)
    }

    @Test
    fun tracksProgressCompletionAndDigest() {
        val session = TransferBundleSession(manifest())
        session.accept()
        session.attachPayload("a", 10L)
        session.updateProgress(10L, 60L, 100L)
        session.complete(10L, 100L, "a".repeat(64))
        val snapshot = session.snapshot()
        assertEquals(100L, snapshot.completedBytes)
        assertEquals(1, snapshot.completedItems)
        assertFalse(session.isFinished())
    }

    @Test
    fun rejectsProgressThatMovesBackwardsOrExceedsSize() {
        val session = TransferBundleSession(manifest())
        session.accept()
        session.attachPayload("a", 10L)
        session.updateProgress(10L, 60L, 100L)
        assertThrows(IllegalArgumentException::class.java) { session.updateProgress(10L, 50L, 100L) }
        assertThrows(IllegalArgumentException::class.java) { session.updateProgress(10L, 101L, 100L) }
    }

    @Test
    fun rejectsWrongSizeAndMalformedDigest() {
        val session = TransferBundleSession(manifest())
        session.accept()
        session.attachPayload("a", 10L)
        assertThrows(IllegalArgumentException::class.java) { session.complete(10L, 99L, "a".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { session.complete(10L, 100L, "not-a-digest") }
    }

    @Test
    fun preventsDuplicatePayloadIdsAndSupportsSkipAndFailure() {
        val session = TransferBundleSession(manifest())
        session.accept()
        session.attachPayload("a", 10L)
        assertThrows(IllegalArgumentException::class.java) { session.attachPayload("b", 10L) }
        session.fail(10L)
        session.skip("b")
        assertTrue(session.isFinished())
        assertEquals(1, session.snapshot().failedItems)
        assertEquals(1, session.snapshot().skippedItems)
    }

    @Test
    fun rejectingBundleSkipsAllFileItems() {
        val session = TransferBundleSession(manifest())
        session.reject()
        assertTrue(session.isFinished())
        assertEquals(2, session.snapshot().skippedItems)
        assertThrows(IllegalStateException::class.java) { session.accept() }
    }
}
