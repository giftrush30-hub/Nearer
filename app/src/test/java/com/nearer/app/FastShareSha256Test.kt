package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareSha256Test {
    @Test fun matchesKnownSha256Vector() {
        val digest = FastShareSha256()
        digest.update("abc".toByteArray(Charsets.UTF_8))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest.finishHex())
    }

    @Test fun incrementalChunksMatchSingleChunk() {
        val bytes = "NeaRer large-file transfer integrity".toByteArray(Charsets.UTF_8)
        val single = FastShareSha256().apply { update(bytes) }.finishHex()
        val chunked = FastShareSha256().apply {
            update(bytes, 0, 7)
            update(bytes, 7, bytes.size - 7)
        }.finishHex()
        assertEquals(single, chunked)
        assertTrue(chunked.matches(Regex("[0-9a-f]{64}")))
    }
}
