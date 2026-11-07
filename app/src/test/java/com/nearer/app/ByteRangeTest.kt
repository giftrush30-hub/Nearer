package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class ByteRangeTest {
    @Test fun missingRangeReturnsWholeFile() {
        assertEquals(ResolvedByteRange(0, 99, false), ByteRange.resolve(null, 100))
    }

    @Test fun closedRangeIsResolvedAndClamped() {
        assertEquals(ResolvedByteRange(10, 99, true), ByteRange.resolve("bytes=10-120", 100))
    }

    @Test fun openEndedRangeRunsToEnd() {
        assertEquals(ResolvedByteRange(25, 99, true), ByteRange.resolve("bytes=25-", 100))
    }

    @Test fun suffixRangeReturnsLastBytes() {
        assertEquals(ResolvedByteRange(90, 99, true), ByteRange.resolve("bytes=-10", 100))
    }

    @Test fun suffixLargerThanFileReturnsWholeFileAsPartial() {
        assertEquals(ResolvedByteRange(0, 9, true), ByteRange.resolve("bytes=-50", 10))
    }

    @Test fun invalidAndUnsatisfiableRangesAreRejected() {
        assertNull(ByteRange.resolve("bytes=100-", 100))
        assertNull(ByteRange.resolve("bytes=8-2", 100))
        assertNull(ByteRange.resolve("bytes=-0", 100))
        assertNull(ByteRange.resolve("bytes=abc-def", 100))
        assertNull(ByteRange.resolve("bytes=0-", 0))
    }

    @Test fun unsupportedMultiRangeFallsBackToFullResponse() {
        assertEquals(ResolvedByteRange(0, 99, false), ByteRange.resolve("bytes=0-1,5-8", 100))
    }
}
