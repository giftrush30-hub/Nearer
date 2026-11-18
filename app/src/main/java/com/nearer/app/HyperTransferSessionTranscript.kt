package com.nearer.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Canonical, transfer-specific authentication transcript for HyperTransfer.
 *
 * The transcript is intended to be signed with SecurePeerIdentity.signDetached()
 * by each peer and verified against the public key already bound to that peer's
 * trusted identity record. It is not an authentication mechanism by itself.
 */
internal object HyperTransferSessionTranscript {
    const val PROTOCOL_VERSION = 1
    private const val DOMAIN = "NeaRer-HyperTransfer-Authenticated-Session"

    data class PeerMaterial(
        val peerId: String,
        val signingPublicKey: ByteArray,
        val nonce: ByteArray,
        val ephemeralPublicKey: ByteArray,
        val encryptionPublicKey: ByteArray
    )

    /**
     * Produces identical bytes on both phones regardless of which peer calls
     * this method. Sender/receiver roles remain explicit and are not sorted away.
     */
    fun create(
        transferId: String,
        sender: PeerMaterial,
        receiver: PeerMaterial,
        protocolVersion: Int = PROTOCOL_VERSION
    ): ByteArray {
        require(protocolVersion == PROTOCOL_VERSION) { "Unsupported HyperTransfer protocol version" }
        require(transferId.matches(Regex("^[a-fA-F0-9]{64}$"))) { "Invalid transfer ID" }
        val senderId = requireNotNull(NeaRerIdentity.normalize(sender.peerId)) { "Invalid sender peer identity" }
        val receiverId = requireNotNull(NeaRerIdentity.normalize(receiver.peerId)) { "Invalid receiver peer identity" }
        require(senderId != receiverId) { "A peer cannot transfer to itself" }
        validate(sender)
        validate(receiver)

        val normalizedSender = sender.copy(peerId = senderId)
        val normalizedReceiver = receiver.copy(peerId = receiverId)
        val orderedPeers = listOf(normalizedSender, normalizedReceiver).sortedBy { it.peerId }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            writeBytes(data, DOMAIN.toByteArray(Charsets.US_ASCII))
            data.writeInt(protocolVersion)
            writeBytes(data, transferId.lowercase().toByteArray(Charsets.US_ASCII))
            writeBytes(data, senderId.toByteArray(Charsets.UTF_8))
            writeBytes(data, receiverId.toByteArray(Charsets.UTF_8))
            orderedPeers.forEach { peer ->
                writeBytes(data, peer.peerId.toByteArray(Charsets.UTF_8))
                writeBytes(data, peer.signingPublicKey)
                writeBytes(data, peer.nonce)
                writeBytes(data, peer.ephemeralPublicKey)
                writeBytes(data, peer.encryptionPublicKey)
            }
        }
        return output.toByteArray()
    }

    private fun validate(peer: PeerMaterial) {
        require(peer.signingPublicKey.isNotEmpty() && peer.signingPublicKey.size <= 512)
        require(peer.nonce.size == 32) { "Peer nonce must contain 32 bytes" }
        require(peer.ephemeralPublicKey.isNotEmpty() && peer.ephemeralPublicKey.size <= 512)
        require(peer.encryptionPublicKey.isNotEmpty() && peer.encryptionPublicKey.size <= 512)
    }

    private fun writeBytes(output: DataOutputStream, value: ByteArray) {
        require(value.size <= 4096) { "Transcript field is too large" }
        output.writeInt(value.size)
        output.write(value)
    }
}
