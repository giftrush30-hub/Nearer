package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HyperTransferIdentityTest {
    private val digest = "a".repeat(64)
    private fun item(name: String = "clip.mp4", size: Long = 8_192) =
        TransferBundleItem("item-id", name, name, size, "video/mp4")

    @Test fun identicalReselectedFileKeepsTransferIdentityAcrossFreshPayloadAttempts() {
        val first = HyperTransferIdentity.createTransferId("SENDER123456", "PEER12345678", item(), digest)
        val afterReconnect = HyperTransferIdentity.createTransferId("SENDER123456", "PEER12345678", item(), digest)
        assertEquals(first, afterReconnect)
        assertEquals(64, first.length)
    }

    @Test fun transferIdentityIsScopedToTheRecipient() {
        val first = HyperTransferIdentity.createTransferId("SENDER123456", "PEER12345678", item(), digest)
        val second = HyperTransferIdentity.createTransferId("SENDER123456", "OTHER1234567", item(), digest)
        assertNotEquals(first, second)
    }

    @Test fun changedContentSizeOrNameGetsANewTransferIdentity() {
        val original = HyperTransferIdentity.createTransferId("SENDER123456", "PEER12345678", item(), digest)
        assertNotEquals(original, HyperTransferIdentity.createTransferId("SENDER123456", "PEER12345678", item(), "b".repeat(64)))
        assertNotEquals(original, HyperTransferIdentity.createTransferId("SENDER123456", "PEER12345678", item(size = 9_000), digest))
        assertNotEquals(original, HyperTransferIdentity.createTransferId("SENDER123456", "PEER12345678", item(name = "other.mp4"), digest))
    }

    @Test fun rejectsMissingPeerIdentityAndMalformedDigest() {
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferIdentity.createTransferId("", "PEER12345678", item(), digest)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HyperTransferIdentity.createTransferId("SENDER123456", "PEER12345678", item(), "not-a-digest")
        }
    }
}
