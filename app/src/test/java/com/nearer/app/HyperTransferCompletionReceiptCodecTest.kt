package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperTransferCompletionReceiptCodecTest {
    private fun receipt(saved: Boolean = true) = HyperTransferCompletionReceiptCodec.Receipt(
        payloadId = 93L,
        transferId = "transfer-abc",
        itemId = "item-123",
        sha256 = "AB".repeat(32),
        saved = saved
    )

    @Test fun roundTripsVerifiedSaveReceipt() {
        val decoded = HyperTransferCompletionReceiptCodec.decode(
            HyperTransferCompletionReceiptCodec.encode(receipt())
        )
        assertEquals(receipt().copy(sha256 = "ab".repeat(32)), decoded)
        assertTrue(decoded.saved)
    }

    @Test fun roundTripsExplicitReceiverFailure() {
        val decoded = HyperTransferCompletionReceiptCodec.decode(
            HyperTransferCompletionReceiptCodec.encode(receipt(saved = false))
        )
        assertFalse(decoded.saved)
    }

    @Test fun rejectsWrongHeaderTrailingBytesAndInvalidIdentity() {
        val encoded = HyperTransferCompletionReceiptCodec.encode(receipt())
        try {
            HyperTransferCompletionReceiptCodec.decode(byteArrayOf(0, 0, 0, 0))
            throw AssertionError("Wrong header must be rejected")
        } catch (_: IllegalArgumentException) { }

        try {
            HyperTransferCompletionReceiptCodec.decode(encoded + byteArrayOf(1))
            throw AssertionError("Trailing bytes must be rejected")
        } catch (_: IllegalArgumentException) { }

        try {
            HyperTransferCompletionReceiptCodec.encode(receipt().copy(payloadId = 0L))
            throw AssertionError("Invalid payload ID must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
