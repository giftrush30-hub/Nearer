package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialTransferQueueTest {
    @Test fun sendsItemsInOriginalOrder() {
        val queue = SequentialTransferQueue<String>()
        queue.enqueue(listOf("photo.jpg", "video.mp4", "document.pdf"))

        assertEquals("photo.jpg", queue.currentOrNext())
        assertEquals("photo.jpg", queue.currentOrNext())
        assertTrue(queue.completeCurrent())
        assertEquals("video.mp4", queue.currentOrNext())
        assertTrue(queue.completeCurrent())
        assertEquals("document.pdf", queue.currentOrNext())
        assertTrue(queue.completeCurrent())
        assertFalse(queue.hasPending)
        assertEquals(3, queue.completed)
    }

    @Test fun interruptedItemRemainsCurrentForRetry() {
        val queue = SequentialTransferQueue<String>()
        queue.enqueue(listOf("large-video.mp4", "photo.jpg"))

        assertEquals("large-video.mp4", queue.currentOrNext())
        // A disconnect does not complete or discard the active item.
        assertEquals("large-video.mp4", queue.currentOrNext())
        assertEquals(0, queue.completed)
        assertEquals("large-video.mp4", queue.currentOrNext())
    }

    @Test fun nextItemDoesNotAdvanceUntilCurrentIsCompleted() {
        val queue = SequentialTransferQueue<Int>()
        queue.enqueue(listOf(10, 20))

        assertEquals(10, queue.currentOrNext())
        assertFalse(queue.completeCurrent().not())
        assertEquals(20, queue.currentOrNext())
        assertEquals(1, queue.completed)
    }

    @Test fun emptyQueueAndClearAreSafe() {
        val queue = SequentialTransferQueue<String>()
        assertNull(queue.currentOrNext())
        assertFalse(queue.completeCurrent())
        queue.enqueue(listOf("one"))
        assertTrue(queue.hasPending)
        queue.clear()
        assertFalse(queue.hasPending)
        assertEquals(0, queue.total)
        assertEquals(0, queue.completed)
        assertNull(queue.currentOrNext())
    }
}
