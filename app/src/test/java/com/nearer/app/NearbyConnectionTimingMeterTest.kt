package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyConnectionTimingMeterTest {
    @Test fun measuresRequestThroughAuthenticationDuration() {
        val meter = NearbyConnectionTimingMeter()
        assertTrue(meter.begin("endpoint-1", 1_000L))
        assertEquals(1_350L, meter.finish("endpoint-1", 2_350L))
    }

    @Test fun duplicateBeginDoesNotResetOriginalStartTime() {
        val meter = NearbyConnectionTimingMeter()
        assertTrue(meter.begin("endpoint-1", 1_000L))
        assertFalse(meter.begin("endpoint-1", 1_500L))
        assertEquals(1_000L, meter.finish("endpoint-1", 2_000L))
    }

    @Test fun cancelledOrUnknownEndpointsHaveNoDuration() {
        val meter = NearbyConnectionTimingMeter()
        meter.begin("endpoint-1", 100L)
        meter.cancel("endpoint-1")
        assertNull(meter.finish("endpoint-1", 200L))
        assertNull(meter.finish("unknown", 200L))
    }

    @Test fun rejectsInvalidOrBackwardsTimestamps() {
        val meter = NearbyConnectionTimingMeter()
        assertFalse(meter.begin("", 1L))
        assertFalse(meter.begin("endpoint-1", -1L))
        meter.begin("endpoint-1", 500L)
        assertNull(meter.finish("endpoint-1", 400L))
    }
}
