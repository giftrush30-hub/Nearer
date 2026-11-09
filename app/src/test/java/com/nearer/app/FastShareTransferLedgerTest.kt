package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FastShareTransferLedgerTest {
    @Test fun simultaneousTransfersWithSameFileNameRemainIndependent() {
        val ledger = FastShareTransferLedger()
        val pool = Executors.newFixedThreadPool(12)
        try {
            val tasks = (1..12).map { index ->
                pool.submit {
                    ledger.update(FastShareActiveTransfer(
                        id = "receiver-$index",
                        fileName = "video.mp4",
                        bytesSent = index * 100L,
                        totalBytes = 10_000L,
                        bytesPerSecond = index * 10L,
                        startedAtMillis = index.toLong()
                    ))
                }
            }
            tasks.forEach { it.get(2, TimeUnit.SECONDS) }
            val snapshot = ledger.snapshot()
            assertEquals(12, snapshot.size)
            assertEquals(12, snapshot.map { it.id }.distinct().size)
            assertEquals((1..12).map { "receiver-$it" }, snapshot.map { it.id })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun updatingOneTransferDoesNotReplaceAnotherAndFinishIsScoped() {
        val ledger = FastShareTransferLedger()
        ledger.update(FastShareActiveTransfer("a", "same.mp4", 10, 100, 5, 1))
        ledger.update(FastShareActiveTransfer("b", "same.mp4", 20, 100, 7, 2))
        ledger.update(FastShareActiveTransfer("a", "same.mp4", 50, 100, 15, 1))

        assertEquals(2, ledger.snapshot().size)
        assertEquals(50L, ledger.snapshot().first { it.id == "a" }.bytesSent)
        ledger.finish("a")
        assertEquals(listOf("b"), ledger.snapshot().map { it.id })
    }

    @Test fun queuedTransferBecomesActiveWithoutChangingItsIdentity() {
        val ledger = FastShareTransferLedger()
        ledger.update(FastShareActiveTransfer("receiver-6", "large.mp4", 0, -1, 0, 1, isQueued = true))
        assertTrue(ledger.snapshot().single().isQueued)
        ledger.update(FastShareActiveTransfer("receiver-6", "large.mp4", 1024, 4096, 512, 1))
        val active = ledger.snapshot().single()
        assertEquals("receiver-6", active.id)
        assertEquals(1024L, active.bytesSent)
        assertEquals(1, ledger.snapshot().size)
        assertTrue(!active.isQueued)
    }

    @Test fun twelveRequestsShowFiveActiveAndSevenWaitingUntilSlotsOpen() {
        val gate = FastShareDownloadGate()
        val ledger = FastShareTransferLedger()
        val pool = Executors.newFixedThreadPool(12)
        val allQueued = java.util.concurrent.CountDownLatch(12)
        val fiveActive = java.util.concurrent.CountDownLatch(5)
        val releaseTransfers = java.util.concurrent.CountDownLatch(1)
        val active = java.util.concurrent.atomic.AtomicInteger(0)
        val peak = java.util.concurrent.atomic.AtomicInteger(0)
        try {
            val tasks = (1..12).map { index ->
                pool.submit {
                    val id = "receiver-$index"
                    ledger.update(FastShareActiveTransfer(id, "video.mp4", 0, 1000, 0, index.toLong(), isQueued = true))
                    allQueued.countDown()
                    assertTrue(gate.tryAcquire(3, TimeUnit.SECONDS))
                    try {
                        ledger.update(FastShareActiveTransfer(id, "video.mp4", 1, 1000, 1, index.toLong()))
                        val now = active.incrementAndGet()
                        peak.updateAndGet { previous -> maxOf(previous, now) }
                        fiveActive.countDown()
                        assertTrue(releaseTransfers.await(3, TimeUnit.SECONDS))
                    } finally {
                        active.decrementAndGet()
                        ledger.finish(id)
                        gate.release()
                    }
                }
            }
            assertTrue(allQueued.await(2, TimeUnit.SECONDS))
            assertTrue(fiveActive.await(2, TimeUnit.SECONDS))
            val waitingSnapshot = ledger.snapshot()
            assertEquals(5, waitingSnapshot.count { !it.isQueued })
            assertEquals(7, waitingSnapshot.count { it.isQueued })
            assertEquals(5, peak.get())
            releaseTransfers.countDown()
            tasks.forEach { it.get(4, TimeUnit.SECONDS) }
            assertTrue(ledger.snapshot().isEmpty())
            assertEquals(0, active.get())
        } finally {
            releaseTransfers.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun clearRemovesAllActiveTransfers() {
        val ledger = FastShareTransferLedger()
        ledger.update(FastShareActiveTransfer("a", "a.bin", 1, 2, 1, 1))
        ledger.update(FastShareActiveTransfer("b", "b.bin", 1, 2, 1, 2))
        ledger.clear()
        assertTrue(ledger.snapshot().isEmpty())
    }
}
