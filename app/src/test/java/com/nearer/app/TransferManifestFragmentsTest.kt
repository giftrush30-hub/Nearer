package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferManifestFragmentsTest {
    @Test
    fun identifiesOnlyManifestFragmentFrames() {
        val fragment = TransferManifestFragments.split("bundle-1", byteArrayOf(1, 2, 3)).single()
        assertTrue(TransferManifestFragments.isFragment(fragment))
        assertFalse(TransferManifestFragments.isFragment(byteArrayOf(0x7B, 0x7D)))
        assertFalse(TransferManifestFragments.isFragment(null))
    }

    @Test
    fun splitsAndReassemblesLargeManifestOutOfOrder() {
        val original = ByteArray(180_000) { (it % 251).toByte() }
        val fragments = TransferManifestFragments.split("bundle-123", original)
        fragments.forEach { assertTrue(it.size <= TransferManifestFragments.MAX_FRAGMENT_BYTES) }
        val receiver = TransferManifestReassembler()
        var assembled: ByteArray? = null
        fragments.reversed().forEach { encoded ->
            receiver.accept(encoded)?.let { assembled = it }
        }
        assertArrayEquals(original, assembled)
    }

    @Test
    fun acceptsAnIdenticalDuplicateFragment() {
        val fragment = TransferManifestFragments.split("bundle-1", ByteArray(40_000) { it.toByte() }).first()
        val receiver = TransferManifestReassembler()
        assertNull(receiver.accept(fragment))
        assertNull(receiver.accept(fragment))
    }

    @Test
    fun rejectsAConflictingDuplicateFragment() {
        val encoded = TransferManifestFragments.split("bundle-1", ByteArray(40_000) { it.toByte() }).first()
        val altered = encoded.copyOf()
        altered[altered.lastIndex] = (altered.last().toInt() xor 0x01).toByte()
        val receiver = TransferManifestReassembler()
        assertNull(receiver.accept(encoded))
        assertThrows(IllegalArgumentException::class.java) { receiver.accept(altered) }
    }

    @Test
    fun rejectsFragmentsFromAnotherTransfer() {
        // Keep the first transfer incomplete so its identity remains bound
        // to this reassembler while a fragment from another transfer arrives.
        val a = TransferManifestFragments.split("bundle-a", ByteArray(40_000) { it.toByte() }).first()
        val b = TransferManifestFragments.split("bundle-b", ByteArray(40_000) { it.toByte() }).first()
        val receiver = TransferManifestReassembler()
        assertNull(receiver.accept(a))
        assertThrows(IllegalArgumentException::class.java) { receiver.accept(b) }
    }

    @Test
    fun rejectsForgedFragmentCountAndIncorrectPartLength() {
        val valid = TransferManifestFragments.split("bundle-a", ByteArray(40_000) { it.toByte() }).first()

        // Header layout: magic, ID length + ID, index, count, total bytes, part length.
        val forgedCount = valid.copyOf().also {
            java.nio.ByteBuffer.wrap(it).putInt(18, Int.MAX_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferManifestFragments.decode(forgedCount)
        }

        val forgedPartLength = valid.copyOf().also {
            java.nio.ByteBuffer.wrap(it).putInt(26, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferManifestFragments.decode(forgedPartLength)
        }
    }

    @Test
    fun rejectsMalformedUtf8InTransferIdentity() {
        val encoded = TransferManifestFragments.split("bundle-a", byteArrayOf(1, 2, 3)).single()
        // Fragment header stores the UTF-8 transfer-ID length at bytes 4..5,
        // followed immediately by the ID. Replace its first byte with an
        // incomplete two-byte UTF-8 lead byte.
        encoded[6] = 0xC3.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            TransferManifestFragments.decode(encoded)
        }
    }

    @Test
    fun rejectsOversizedFragment() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferManifestFragments.decode(ByteArray(TransferManifestFragments.MAX_FRAGMENT_BYTES + 1))
        }
    }
}
