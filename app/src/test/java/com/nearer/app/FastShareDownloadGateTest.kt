package com.nearer.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class FastShareDownloadGateTest {
    @Test fun secondReceiverWaitsUntilCurrentDownloadReleasesPermit() {
        val gate = FastShareDownloadGate()
        assertTrue(gate.tryAcquire(10, TimeUnit.MILLISECONDS))
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
        assertFalse("A second receiver must not stream concurrently", acquired.get())
        gate.release()
        worker.join(1500)
        assertFalse(worker.isAlive)
        assertTrue("Waiting receiver should acquire after release", acquired.get())
    }

    @Test fun receiverTimesOutWhenCurrentTransferNeverFinishes() {
        val gate = FastShareDownloadGate()
        assertTrue(gate.tryAcquire(10, TimeUnit.MILLISECONDS))
        assertFalse(gate.tryAcquire(25, TimeUnit.MILLISECONDS))
        gate.release()
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
    @Test fun interruptedWaiterDoesNotConsumeTheActiveTransferPermit() {
        val gate = FastShareDownloadGate()
        assertTrue(gate.tryAcquire(0, TimeUnit.MILLISECONDS))
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
        assertFalse("Interrupted waiter must not release or steal the permit", gate.tryAcquire(0, TimeUnit.MILLISECONDS))
        gate.release()
        assertTrue(gate.tryAcquire(0, TimeUnit.MILLISECONDS))
        gate.release()
    }

}