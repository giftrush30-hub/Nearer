package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareUploadGateTest {
    @Test fun limitsConcurrentUploads() {
        val gate = FastShareUploadGate()
        assertTrue(gate.tryAcquire(10L, 100L))
        assertTrue(gate.tryAcquire(20L, 100L))
        assertFalse(gate.tryAcquire(1L, 100L))
        gate.release(10L)
        assertTrue(gate.tryAcquire(1L, 100L))
        assertEquals(FastShareUploadGate.Snapshot(2, 21L), gate.snapshot())
    }

    @Test fun reservesFreeSpaceAcrossUploads() {
        val gate = FastShareUploadGate()
        assertTrue(gate.tryAcquire(70L, 100L))
        assertFalse(gate.tryAcquire(31L, 100L))
        assertTrue(gate.tryAcquire(30L, 100L))
        assertEquals(FastShareUploadGate.Snapshot(2, 100L), gate.snapshot())
    }

    @Test fun rejectsNegativeAndOverflowingReservations() {
        val gate = FastShareUploadGate()
        assertFalse(gate.tryAcquire(-1L, 100L))
        assertFalse(gate.tryAcquire(1L, -1L))
        assertTrue(gate.tryAcquire(Long.MAX_VALUE, Long.MAX_VALUE))
        assertFalse(gate.tryAcquire(1L, Long.MAX_VALUE))
    }

    @Test fun releaseRestoresCapacity() {
        val gate = FastShareUploadGate()
        assertTrue(gate.tryAcquire(50L, 100L))
        gate.release(50L)
        assertEquals(FastShareUploadGate.Snapshot(0, 0L), gate.snapshot())
        assertTrue(gate.tryAcquire(100L, 100L))
    }
}
