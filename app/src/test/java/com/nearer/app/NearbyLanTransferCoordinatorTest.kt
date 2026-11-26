package com.nearer.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class NearbyLanTransferCoordinatorTest {
    @Test
    fun transferWaitsForUserApprovalThenCompletesWithCheckpointAndFinalAcks() {
        val root = File(System.getProperty("java.io.tmpdir"), "nearer-e2e-${UUID.randomUUID()}")
        val source = File(root, "source.bin")
        assertTrue(root.mkdirs())
        val content = ByteArray(4 * 1024 * 1024 + 123) { (it % 251).toByte() }
        source.writeBytes(content)
        val digest = MessageDigest.getInstance("SHA-256").digest(content).toHex()
        val metadata = TransferResumeStreamMetadataCodec.Metadata(
            77L, "transfer-e2e",
            TransferBundleItem("file-1", "folder/result.bin", "result.bin",
                content.size.toLong(), "application/octet-stream"),
            source.lastModified(), digest
        )

        lateinit var senderCoordinator: NearbyLanTransferCoordinator
        lateinit var receiverCoordinator: NearbyLanTransferCoordinator
        var requested: TransferResumeStreamMetadataCodec.Metadata? = null
        var receivedFile: File? = null
        val receiverListener = object : NearbyLanTransferCoordinator.Listener {
            override fun onIncomingTransferRequest(endpointId: String, metadata: TransferResumeStreamMetadataCodec.Metadata) {
                requested = metadata
            }
            override fun onFileReceived(endpointId: String, metadata: TransferResumeStreamMetadataCodec.Metadata, file: File) {
                receivedFile = file
            }
            override fun onTransferError(endpointId: String, message: String) {
                throw AssertionError(message)
            }
        }
        val quietListener = object : NearbyLanTransferCoordinator.Listener {
            override fun onIncomingTransferRequest(endpointId: String, metadata: TransferResumeStreamMetadataCodec.Metadata) {}
            override fun onFileReceived(endpointId: String, metadata: TransferResumeStreamMetadataCodec.Metadata, file: File) {}
            override fun onTransferError(endpointId: String, message: String) { throw AssertionError(message) }
        }
        senderCoordinator = NearbyLanTransferCoordinator(root, { true }, { _, _, callback -> callback(null) }, quietListener)
        receiverCoordinator = NearbyLanTransferCoordinator(
            File(root, "received"), { true },
            { endpoint, frame, callback ->
                senderCoordinator.receiveFrame(endpoint, frame)
                callback(null)
            },
            receiverListener
        )

        val sentFrames = mutableListOf<ByteArray>()
        var completed = false
        NearbyLanFileSender.send(
            source, metadata,
            NearbyLanFrameSink { frame, callback ->
                sentFrames += frame
                receiverCoordinator.receiveFrame("sender", frame)
                callback(null)
            },
            NearbyLanAckWaiter { expected, callback ->
                senderCoordinator.awaitAck("sender", expected, callback)
            }
        ) { result ->
            assertTrue(result.isSuccess)
            completed = true
        }

        assertFalse("Sender must wait for user approval after metadata", completed)
        assertEquals(1, sentFrames.size)
        assertEquals(metadata, requested)
        assertTrue(receiverCoordinator.approveIncoming("sender", metadata.payloadId))
        assertTrue(completed)
        assertNotNull(receivedFile)
        assertArrayEquals(content, receivedFile!!.readBytes())
        receiverCoordinator.close()
        senderCoordinator.close()
        root.deleteRecursively()
    }

    @Test
    fun unauthenticatedEndpointCannotStartIncomingTransfer() {
        val root = File(System.getProperty("java.io.tmpdir"), "nearer-auth-${UUID.randomUUID()}")
        val metadata = TransferResumeStreamMetadataCodec.Metadata(
            5L, "blocked",
            TransferBundleItem("i", "file.bin", "file.bin", 1L, null),
            null, "0".repeat(64)
        )
        var requests = 0
        val listener = object : NearbyLanTransferCoordinator.Listener {
            override fun onIncomingTransferRequest(endpointId: String, metadata: TransferResumeStreamMetadataCodec.Metadata) {
                requests++
            }
            override fun onFileReceived(endpointId: String, metadata: TransferResumeStreamMetadataCodec.Metadata, file: File) {}
            override fun onTransferError(endpointId: String, message: String) {}
        }
        val coordinator = NearbyLanTransferCoordinator(
            root, { false }, { _, _, callback -> callback(null) }, listener
        )
        assertFalse(coordinator.receiveFrame("untrusted-peer", TransferResumeStreamMetadataCodec.encode(metadata)))
        assertEquals(0, requests)
        coordinator.close()
        root.deleteRecursively()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
