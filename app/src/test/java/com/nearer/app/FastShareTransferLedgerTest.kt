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

    @Test fun clearRemovesAllActiveTransfers() {
        val ledger = FastShareTransferLedger()
        ledger.update(FastShareActiveTransfer("a", "a.bin", 1, 2, 1, 1))
        ledger.update(FastShareActiveTransfer("b", "b.bin", 1, 2, 1, 2))
        ledger.clear()
        assertTrue(ledger.snapshot().isEmpty())
    }
}
