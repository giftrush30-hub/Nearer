package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DeferredTransferInputStreamTest {
    @Test fun readerWaitsUntilNegotiatedSourceIsSupplied() {
        val stream = DeferredTransferInputStream()
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        try {
            val future = executor.submit<Int> {
                started.countDown()
                stream.read()
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            stream.provide(ByteArrayInputStream(byteArrayOf(42, 43)))
            assertEquals(42, future.get(2, TimeUnit.SECONDS))
            assertEquals(43, stream.read())
            assertEquals(-1, stream.read())
        } finally {
            stream.close()
            executor.shutdownNow()
        }
    }

    @Test fun closingBeforeApprovalReleasesBlockedReader() {
        val stream = DeferredTransferInputStream()
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        try {
            val future = executor.submit<Boolean> {
                started.countDown()
                try {
                    stream.read()
                    false
                } catch (_: IOException) {
                    true
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            stream.close()
            assertTrue(future.get(2, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun rejectedNegotiationReleasesBlockedReaderWithFailure() {
        val stream = DeferredTransferInputStream()
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        try {
            val future = executor.submit<Boolean> {
                started.countDown()
                try {
                    stream.read()
                    false
                } catch (error: IOException) {
                    error.cause?.message == "sender rejected resume"
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            stream.fail(IllegalStateException("sender rejected resume"))
            assertTrue(future.get(2, TimeUnit.SECONDS))
        } finally {
            stream.close()
            executor.shutdownNow()
        }
    }

    @Test fun sourceCanBeSuppliedOnlyOnceAndIsClosedWithTheGate() {
        val stream = DeferredTransferInputStream()
        var closed = false
        val source = object : ByteArrayInputStream(byteArrayOf(7)) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        stream.provide(source)
        try {
            stream.provide(ByteArrayInputStream(byteArrayOf(8)))
            fail("A second source must be rejected")
        } catch (_: IllegalStateException) { }
        stream.close()
        assertTrue(closed)
    }

    @Test fun zeroLengthReadDoesNotWaitForSource() {
        val stream = DeferredTransferInputStream()
        assertEquals(0, stream.read(ByteArray(0), 0, 0))
        stream.close()
    }
}
