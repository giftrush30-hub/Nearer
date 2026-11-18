package com.nearer.app

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperTransferTcpOfferCodecTest {
    @Test
    fun offerRoundTripsAndRetainsExactTransferMetadata() {
        val offer = HyperTransferTcpOfferCodec.Offer(
            "a".repeat(64), "holiday-video.mp4", 4_294_967_296L, "b".repeat(64)
        )
        val encoded = HyperTransferTcpOfferCodec.encodeOffer(offer)
        assertTrue(HyperTransferTcpOfferCodec.isOffer(encoded))
        assertEquals(offer, HyperTransferTcpOfferCodec.decodeOffer(encoded))
    }

    @Test
    fun decisionRoundTripsBothApprovalOutcomes() {
        listOf(true, false).forEach { accepted ->
            val decision = HyperTransferTcpOfferCodec.Decision("c".repeat(64), accepted)
            val encoded = HyperTransferTcpOfferCodec.encodeDecision(decision)
            assertTrue(HyperTransferTcpOfferCodec.isDecision(encoded))
            assertEquals(decision, HyperTransferTcpOfferCodec.decodeDecision(encoded))
        }
    }

    @Test
    fun rejectsInvalidOfferMetadata() {
        val invalid = HyperTransferTcpOfferCodec.Offer("bad", "file.bin", 10, "d".repeat(64))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HyperTransferTcpOfferCodec.encodeOffer(invalid)
        }
        val negative = HyperTransferTcpOfferCodec.Offer("e".repeat(64), "file.bin", -1, "d".repeat(64))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HyperTransferTcpOfferCodec.encodeOffer(negative)
        }
    }

    @Test
    fun rejectsTruncatedAndTrailingOfferBytes() {
        val encoded = HyperTransferTcpOfferCodec.encodeOffer(
            HyperTransferTcpOfferCodec.Offer("f".repeat(64), "file.bin", 1, "0".repeat(64))
        )
        org.junit.Assert.assertThrows(IOException::class.java) {
            HyperTransferTcpOfferCodec.decodeOffer(encoded.copyOf(encoded.size - 1))
        }
        org.junit.Assert.assertThrows(IOException::class.java) {
            HyperTransferTcpOfferCodec.decodeOffer(encoded + byteArrayOf(0))
        }
    }

    @Test
    fun rejectsMalformedDecision() {
        val encoded = HyperTransferTcpOfferCodec.encodeDecision(
            HyperTransferTcpOfferCodec.Decision("1".repeat(64), true)
        )
        val badMagic = encoded.copyOf().also { it[0] = 0 }
        org.junit.Assert.assertThrows(IOException::class.java) {
            HyperTransferTcpOfferCodec.decodeDecision(badMagic)
        }
        assertFalse(HyperTransferTcpOfferCodec.isOffer(encoded))
    }
}
