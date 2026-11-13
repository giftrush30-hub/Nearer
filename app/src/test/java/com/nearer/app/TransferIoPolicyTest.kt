package com.nearer.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferIoPolicyTest {
    @Test
    fun receiveFinalizationCanRunTwoLargeFilesWithoutSerialQueueing() {
        val executor = TransferIoPolicy.createReceiveExecutor()
        val bothStarted = CountDownLatch(TransferIoPolicy.RECEIVE_WORKERS)
        val releaseWorkers = CountDownLatch(1)
        try {
            repeat(TransferIoPolicy.RECEIVE_WORKERS) {
                executor.execute {
                    bothStarted.countDown()
                    releaseWorkers.await(2, TimeUnit.SECONDS)
                }
            }

            assertTrue(
                "Both receive workers should start before either finishes",
                bothStarted.await(2, TimeUnit.SECONDS)
            )
            assertEquals(2, TransferIoPolicy.RECEIVE_WORKERS)
        } finally {
            releaseWorkers.countDown()
            executor.shutdownNow()
        }
    }
}
