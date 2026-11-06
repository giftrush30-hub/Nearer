package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferManifestFragmentsTest {
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
        val a = TransferManifestFragments.split("bundle-a", byteArrayOf(1, 2, 3)).single()
        val b = TransferManifestFragments.split("bundle-b", byteArrayOf(1, 2, 3)).single()
        val receiver = TransferManifestReassembler()
        receiver.accept(a)
        assertThrows(IllegalArgumentException::class.java) { receiver.accept(b) }
    }

    @Test
    fun rejectsOversizedFragment() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferManifestFragments.decode(ByteArray(TransferManifestFragments.MAX_FRAGMENT_BYTES + 1))
        }
    }
}
