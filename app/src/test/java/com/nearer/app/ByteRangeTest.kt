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

    @Test fun rangeUnitIsCaseInsensitiveAndAllowsWhitespace() {
        assertEquals(ResolvedByteRange(5, 9, true), ByteRange.resolve(" BYTES=5-9 ", 10))
    }

    @Test fun overflowingAndNegativeRangeNumbersAreRejected() {
        assertNull(ByteRange.resolve("bytes=9223372036854775808-", 100))
        assertNull(ByteRange.resolve("bytes=-9223372036854775808", 100))
        assertNull(ByteRange.resolve("bytes=-5--1", 100))
    }

    @Test fun unsupportedRangeUnitReturnsWholeFile() {
        assertEquals(ResolvedByteRange(0, 9, false), ByteRange.resolve("items=2-4", 10))
    }

    @Test fun emptyFileWithoutRangeHasZeroLengthRepresentation() {
        assertEquals(ResolvedByteRange(0, -1, false), ByteRange.resolve(null, 0))
    }

    @Test fun unknownLengthCannotResolveByteRanges() {
        assertNull(ByteRange.resolve("bytes=0-9", -1))
        assertNull(ByteRange.resolve("bytes=-10", -1))
    }

    @Test fun emptyFileCannotSatisfyAnyExplicitByteRange() {
        assertNull(ByteRange.resolve("bytes=0-", 0))
        assertNull(ByteRange.resolve("bytes=-1", 0))
    }

    @Test fun fullCoverageRangeIsNotReportedAsPartialTransfer() {
        val wholeFileRange = ByteRange.resolve("bytes=0-", 100)!!
        val oversizedSuffixRange = ByteRange.resolve("bytes=-500", 100)!!
        assertFalse(ByteRange.isPartialTransfer(wholeFileRange, 100))
        assertFalse(ByteRange.isPartialTransfer(oversizedSuffixRange, 100))
    }

    @Test fun resumedSectionIsReportedAsPartialTransfer() {
        val section = ByteRange.resolve("bytes=25-49", 100)!!
        assertTrue(ByteRange.isPartialTransfer(section, 100))
    }
}
