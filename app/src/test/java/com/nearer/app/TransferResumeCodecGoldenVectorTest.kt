package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Android side of the shared Swift/Kotlin resume protocol golden vectors. */
class TransferResumeCodecGoldenVectorTest {
    private val digest = "0".repeat(64)

    @Test
    fun proposalMatchesSwiftGoldenVector() {
        val proposal = TransferResumeNegotiation.Proposal(
            transferId = "x",
            itemId = "i",
            relativePath = "a",
            expectedSizeBytes = 8_388_608L,
            sourceModifiedAtMillis = null,
            committedOffsetBytes = 4L * 1024L * 1024L,
            prefixSha256 = digest
        )
        val actual = TransferResumeMessageCodec.encode(7L, proposal)
            .joinToString("") { "%02x".format(it) }
        val expected = "4e52525301000000000000000700017800016900016100000000008000000000000000004000000040" + "30".repeat(64)
        assertEquals(expected, actual)
        val decoded = TransferResumeMessageCodec.decode(expected.hexToBytes())
        assertEquals(7L, decoded.payloadId)
        assertEquals(proposal, decoded.proposal)
    }

    @Test
    fun decisionAndNoResumeMatchSwiftGoldenVectors() {
        val decision = TransferResumeNegotiation.Decision(
            4L * 1024L * 1024L,
            TransferResumeNegotiation.Reason.RESUME_ACCEPTED
        )
        val actual = TransferResumeMessageCodec.encodeDecision(7L, decision)
            .joinToString("") { "%02x".format(it) }
        assertEquals("4e52524401000000000000000700000000004000000f524553554d455f4143434550544544", actual)
        assertEquals(7L, TransferResumeMessageCodec.decodeNoResume(
            "4e52524e010000000000000007".hexToBytes()
        ).payloadId)
    }

    @Test
    fun rejectsUnalignedCheckpointOffsets() {
        val invalid = TransferResumeNegotiation.Proposal(
            "x", "i", "a", 10_000_000L, null, 123L, digest
        )
        assertThrows(IllegalArgumentException::class.java) {
            TransferResumeMessageCodec.encode(1L, invalid)
        }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
