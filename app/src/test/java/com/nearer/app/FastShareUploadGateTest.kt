package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FastShareUploadGateTest {
    @Test fun limitsConcurrentUploads() {
        val gate = FastShareUploadGate()
        assertEquals(FastShareUploadGate.Admission.ACCEPTED, gate.tryAcquire(10L, 100L))
        assertEquals(FastShareUploadGate.Admission.ACCEPTED, gate.tryAcquire(20L, 100L))
        assertEquals(FastShareUploadGate.Admission.BUSY, gate.tryAcquire(1L, 100L))
        gate.release(10L)
        assertEquals(FastShareUploadGate.Admission.ACCEPTED, gate.tryAcquire(1L, 100L))
        assertEquals(FastShareUploadGate.Snapshot(2, 21L), gate.snapshot())
    }

    @Test fun reservesFreeSpaceAcrossUploads() {
        val gate = FastShareUploadGate()
        assertEquals(FastShareUploadGate.Admission.ACCEPTED, gate.tryAcquire(70L, 100L))
        assertEquals(FastShareUploadGate.Admission.INSUFFICIENT_STORAGE, gate.tryAcquire(31L, 100L))
        assertEquals(FastShareUploadGate.Admission.ACCEPTED, gate.tryAcquire(30L, 100L))
        assertEquals(FastShareUploadGate.Snapshot(2, 100L), gate.snapshot())
    }

    @Test fun rejectsInvalidAndOverflowingReservations() {
        val gate = FastShareUploadGate()
        assertEquals(FastShareUploadGate.Admission.INVALID_SIZE, gate.tryAcquire(-1L, 100L))
        assertEquals(FastShareUploadGate.Admission.INVALID_SIZE, gate.tryAcquire(1L, -1L))
        assertEquals(FastShareUploadGate.Admission.ACCEPTED, gate.tryAcquire(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(FastShareUploadGate.Admission.INSUFFICIENT_STORAGE, gate.tryAcquire(1L, Long.MAX_VALUE))
    }

    @Test fun releaseRestoresCapacity() {
        val gate = FastShareUploadGate()
        assertEquals(FastShareUploadGate.Admission.ACCEPTED, gate.tryAcquire(50L, 100L))
        gate.release(50L)
        assertEquals(FastShareUploadGate.Snapshot(0, 0L), gate.snapshot())
        assertEquals(FastShareUploadGate.Admission.ACCEPTED, gate.tryAcquire(100L, 100L))
    }
}
