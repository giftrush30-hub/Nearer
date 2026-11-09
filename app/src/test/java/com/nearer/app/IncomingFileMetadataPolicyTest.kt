package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingFileMetadataPolicyTest {
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    private fun identity(
        endpoint: String = "endpoint-1",
        peer: String = "nearer-peer-1",
        name: String = "video.mp4",
        size: Long = 1024L,
        hash: String = hashA
    ) = IncomingFileMetadataIdentity(endpoint, peer, name, size, hash)

    @Test fun acceptsFirstValidMetadata() {
        assertTrue(IncomingFileMetadataPolicy.canAccept(null, identity()))
    }

    @Test fun acceptsReconnectFromSameStablePeerWithNewEndpointId() {
        assertTrue(IncomingFileMetadataPolicy.canAccept(identity(), identity(endpoint = "endpoint-2")))
    }

    @Test fun rejectsDifferentPeerTryingToReusePayloadId() {
        assertFalse(IncomingFileMetadataPolicy.canAccept(identity(), identity(endpoint = "endpoint-2", peer = "nearer-peer-2")))
    }

    @Test fun rejectsConflictingNameOrSize() {
        assertFalse(IncomingFileMetadataPolicy.canAccept(identity(), identity(name = "other.mp4")))
        assertFalse(IncomingFileMetadataPolicy.canAccept(identity(), identity(size = 2048L)))
    }

    @Test fun rejectsConflictingStrongHashesButAllowsLegacyHashUpgrade() {
        assertFalse(IncomingFileMetadataPolicy.canAccept(identity(), identity(hash = hashB)))
        assertTrue(IncomingFileMetadataPolicy.canAccept(identity(hash = ""), identity(hash = hashA)))
        assertTrue(IncomingFileMetadataPolicy.canAccept(identity(), identity(hash = "")))
    }

    @Test fun rejectsMalformedIncomingMetadata() {
        assertFalse(IncomingFileMetadataPolicy.canAccept(null, identity(endpoint = "", peer = "")))
        assertFalse(IncomingFileMetadataPolicy.canAccept(null, identity(size = -1L)))
        assertFalse(IncomingFileMetadataPolicy.canAccept(null, identity(hash = "not-a-hash")))
    }

    @Test fun permitsLegacyPeerOnlyOnSameEndpoint() {
        val legacy = identity(peer = "")
        assertTrue(IncomingFileMetadataPolicy.canAccept(legacy, identity(peer = "", endpoint = "endpoint-1")))
        assertFalse(IncomingFileMetadataPolicy.canAccept(legacy, identity(peer = "", endpoint = "endpoint-2")))
    }
}
