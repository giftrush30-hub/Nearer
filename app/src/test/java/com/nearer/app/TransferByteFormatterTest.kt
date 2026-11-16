package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferByteFormatterTest {
    @Test
    fun formatsBytesAndKilobytes() {
        assertEquals("0 B", formatTransferBytes(0L))
        assertEquals("1023 B", formatTransferBytes(1023L))
        assertEquals("1 KB", formatTransferBytes(1024L))
    }

    @Test
    fun formatsMegabytesAndGigabytes() {
        assertEquals("1.0 MB", formatTransferBytes(1024L * 1024L))
        assertEquals("1.00 GB", formatTransferBytes(1024L * 1024L * 1024L))
    }

    @Test
    fun usesStableUsDecimalFormatting() {
        assertEquals("1.5 MB", formatTransferBytes(1_572_864L))
        assertEquals("1.50 GB", formatTransferBytes(1_610_612_736L))
    }
}
