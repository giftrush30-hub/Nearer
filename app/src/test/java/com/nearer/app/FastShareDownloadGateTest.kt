package com.nearer.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class FastShareDownloadGateTest {
    @Test fun requestCapacityIsFiveActivePlusSevenWaiting() {
        assertEquals(5, FastShareDownloadGate.MAX_CONCURRENT_DOWNLOADS)
        assertEquals(7, FastShareDownloadGate.MAX_WAITING_DOWNLOADS)
        assertEquals(12, FastShareDownloadGate.MAX_HANDLED_DOWNLOAD_REQUESTS)
    }

    @Test fun fiveReceiversCanDownloadConcurrently() {
        val gate = FastShareDownloadGate()
        repeat(5) {
            assertTrue("Receiver ${it + 1} should acquire one of five slots", gate.tryAcquire(10, TimeUnit.MILLISECONDS))
        }
        assertFalse("A sixth receiver must wait while all five slots are occupied", gate.tryAcquire(0, TimeUnit.MILLISECONDS))
        repeat(5) { gate.release() }
    }

    @Test fun sixthReceiverWaitsUntilOneOfFiveDownloadsReleasesASlot() {
        val gate = FastShareDownloadGate()
        repeat(5) { assertTrue(gate.tryAcquire(10, TimeUnit.MILLISECONDS)) }
        val acquired = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val worker = Thread {
            started.countDown()
            acquired.set(gate.tryAcquire(2, TimeUnit.SECONDS))
            if (acquired.get()) gate.release()
        }
        worker.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))
        Thread.sleep(40)
        assertFalse("A sixth receiver must wait", acquired.get())
        gate.release()
        worker.join(1500)
        assertFalse(worker.isAlive)
        assertTrue("Waiting receiver should acquire after one release", acquired.get())
        repeat(4) { gate.release() }
    }

    @Test fun receiverTimesOutWhenAllFiveSlotsRemainOccupied() {
        val gate = FastShareDownloadGate()
        repeat(5) { assertTrue(gate.tryAcquire(10, TimeUnit.MILLISECONDS)) }
        assertFalse(gate.tryAcquire(25, TimeUnit.MILLISECONDS))
        repeat(5) { gate.release() }
    }

    @Test fun zeroWaitCanAcquireAnImmediatelyAvailablePermit() {
        val gate = FastShareDownloadGate()
        assertTrue(gate.tryAcquire(0, TimeUnit.MILLISECONDS))
        gate.release()
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeWaitIsRejected() {
        FastShareDownloadGate().tryAcquire(-1, TimeUnit.MILLISECONDS)
    }

    @Test fun interruptedWaiterDoesNotConsumeAnActiveTransferPermit() {
        val gate = FastShareDownloadGate()
        repeat(5) { assertTrue(gate.tryAcquire(0, TimeUnit.MILLISECONDS)) }
        val interrupted = java.util.concurrent.CountDownLatch(1)
        val worker = Thread {
            try {
                gate.tryAcquire(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                interrupted.countDown()
            }
        }
        worker.start()
        worker.interrupt()

        assertTrue("Waiting request should respond to interruption", interrupted.await(1, TimeUnit.SECONDS))
        worker.join(1000)
        assertFalse(worker.isAlive)
        assertFalse("Interrupted waiter must not release or steal a permit", gate.tryAcquire(0, TimeUnit.MILLISECONDS))
        gate.release()
        assertTrue(gate.tryAcquire(0, TimeUnit.MILLISECONDS))
        repeat(5) { gate.release() }
    }

    @Test fun concurrentCallersNeverExceedFiveActivePermitsAndAllComplete() {
        val gate = FastShareDownloadGate()
        val callers = 12
        val ready = CountDownLatch(callers)
        val start = CountDownLatch(1)
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(callers)

        try {
            val tasks = (1..callers).map {
                pool.submit<Boolean> {
                    ready.countDown()
                    if (!start.await(2, TimeUnit.SECONDS)) return@submit false
                    val acquired = gate.tryAcquire(5, TimeUnit.SECONDS)
                    if (!acquired) return@submit false
                    try {
                        val nowActive = active.incrementAndGet()
                        peak.updateAndGet { previous -> maxOf(previous, nowActive) }
                        Thread.sleep(40)
                        completed.incrementAndGet()
                        true
                    } finally {
                        active.decrementAndGet()
                        gate.release()
                    }
                }
            }

            assertTrue("All callers should be ready before the race starts", ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            assertTrue("Every queued caller should finish", tasks.all { it.get(7, TimeUnit.SECONDS) })
            assertEquals(callers, completed.get())
            assertEquals("The five available slots should all be used", 5, peak.get())
            assertEquals(0, active.get())
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun fortyConcurrentRequestsNeverExceedFiveActiveStreamsAndDoNotLeakPermits() {
        val gate = FastShareDownloadGate()
        val callers = 40
        val ready = CountDownLatch(callers)
        val start = CountDownLatch(1)
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(callers)

        try {
            val tasks = (1..callers).map {
                pool.submit<Boolean> {
                    ready.countDown()
                    if (!start.await(3, TimeUnit.SECONDS)) return@submit false
                    if (!gate.tryAcquire(12, TimeUnit.SECONDS)) return@submit false
                    try {
                        val nowActive = active.incrementAndGet()
                        peak.updateAndGet { previous -> maxOf(previous, nowActive) }
                        Thread.sleep(25)
                        completed.incrementAndGet()
                        true
                    } finally {
                        active.decrementAndGet()
                        gate.release()
                    }
                }
            }

            assertTrue("All 40 simulated receivers should reach the start line", ready.await(3, TimeUnit.SECONDS))
            start.countDown()
            assertTrue("All 40 requests should complete within the bounded wait", tasks.all { it.get(15, TimeUnit.SECONDS) })
            assertEquals(40, completed.get())
            assertEquals("The concurrency ceiling must remain exactly five", 5, peak.get())
            assertEquals(0, active.get())

            repeat(5) { assertTrue("Every permit must be returned after the stress run", gate.tryAcquire(0, TimeUnit.MILLISECONDS)) }
            assertFalse("No sixth permit may appear after the stress run", gate.tryAcquire(0, TimeUnit.MILLISECONDS))
            repeat(5) { gate.release() }
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }
}
