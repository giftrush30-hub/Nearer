package com.nearer.app

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Socket
import java.security.KeyPair
import java.security.SecureRandom

/**
 * Authenticates the Wi-Fi Direct TCP peer against the identity already verified
 * by Nearby Connections, then derives the transfer-specific AEAD channel.
 *
 * Both peers run this method concurrently on the same socket. The caller must
 * obtain explicit approval for this exact transfer before passing
 * transferApproved=true. A Wi-Fi Direct MAC address or display name is never
 * treated as an identity.
 */
internal object HyperTransferSocketHandshake {
    private const val MAGIC = 0x4E524854 // NRHT
    private const val VERSION = 1
    private const val NONCE_BYTES = 32
    private const val MAX_ID_BYTES = 64
    private const val MAX_KEY_BYTES = 512
    private const val MAX_SIGNATURE_BYTES = 128

    internal data class Hello(
        val transferId: String,
        val peerId: String,
        val isSender: Boolean,
        val nonce: ByteArray,
        val signingPublicKey: ByteArray,
        val ephemeralPublicKey: ByteArray,
        val encryptionPublicKey: ByteArray
    )

    internal data class Session(
        val transferId: String,
        val localPeerId: String,
        val remotePeerId: String,
        val localIsSender: Boolean,
        val channel: HyperTransferSecureChannel
    )

    /**
     * Completes a signed, role-bound ECDH handshake over the P2P socket.
     * expectedRemotePeerId must come from the authenticated Nearby endpoint,
     * never from Wi-Fi Direct discovery metadata.
     */
    fun authenticate(
        context: Context,
        socket: Socket,
        transferId: String,
        expectedRemotePeerId: String,
        localIsSender: Boolean,
        transferApproved: Boolean,
        firstContactApproved: Boolean
    ): Session {
        require(transferApproved) { "The user has not approved this file transfer" }
        require(transferId.matches(Regex("^[a-fA-F0-9]{64}$"))) { "Invalid transfer ID" }
        val expectedRemote = requireNotNull(NeaRerIdentity.normalize(expectedRemotePeerId)) {
            "The authenticated Nearby peer identity is unavailable"
        }

        try {
            socket.soTimeout = minOf(socket.soTimeout.takeIf { it > 0 } ?: 20_000, 20_000)
            val preferences = context.applicationContext.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val storedId = preferences.getString("nearer_stable_id", null)
            val localId = NeaRerIdentity.getOrCreate(storedId)
            require(NeaRerIdentity.normalize(localId) != expectedRemote) { "A phone cannot transfer to itself" }

            val ephemeral: KeyPair = NearbyMessageCrypto.newEphemeralKeyPair()
            val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
            val signingPublicKey = SecurePeerIdentity.publicKeyEncoded()
            val encryptionPublicKey = RecipientEncryptionIdentity.getOrCreate(context).publicKeyEncoded
            val localHello = Hello(
                transferId.lowercase(), localId, localIsSender, nonce,
                signingPublicKey, ephemeral.public.encoded, encryptionPublicKey
            )

            // Both sides write first, then read. No buffered wrapper is used here:
            // the subsequent file engine can safely wrap the same socket streams.
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            writeHello(output, localHello)
            output.flush()
            val remoteHello = readHello(input)
            require(remoteHello.transferId == transferId.lowercase()) { "HyperTransfer ID mismatch" }
            require(remoteHello.isSender != localIsSender) { "HyperTransfer sender/receiver roles do not match" }
            require(remoteHello.peerId == expectedRemote) {
                "The Wi-Fi Direct socket is not connected to the authenticated Nearby peer"
            }

            val localMaterial = HyperTransferSessionTranscript.PeerMaterial(
                localHello.peerId, localHello.signingPublicKey, localHello.nonce,
                localHello.ephemeralPublicKey, localHello.encryptionPublicKey
            )
            val remoteMaterial = HyperTransferSessionTranscript.PeerMaterial(
                remoteHello.peerId, remoteHello.signingPublicKey, remoteHello.nonce,
                remoteHello.ephemeralPublicKey, remoteHello.encryptionPublicKey
            )
            val prepared = HyperTransferSessionAuthenticator.prepare(
                transferId, localMaterial, remoteMaterial, localIsSender, ephemeral
            )
            writeBoundedBytes(output, prepared.localSignature, MAX_SIGNATURE_BYTES)
            output.flush()
            val remoteSignature = readBoundedBytes(input, MAX_SIGNATURE_BYTES)
            val channel = HyperTransferSessionAuthenticator.authenticate(
                context, prepared, remoteSignature,
                transferApproved = transferApproved,
                firstContactApproved = firstContactApproved
            )
            return Session(transferId.lowercase(), localId, expectedRemote, localIsSender, channel)
        } catch (error: Exception) {
            try { socket.close() } catch (_: Exception) { }
            if (error is SecurityException) throw error
            if (error is IllegalArgumentException) throw error
            throw IOException("Secure HyperTransfer socket authentication failed", error)
        }
    }

    internal fun encodeHello(hello: Hello): ByteArray {
        validateHello(hello)
        val bytes = java.io.ByteArrayOutputStream()
        DataOutputStream(bytes).use { output -> writeHello(output, hello) }
        return bytes.toByteArray()
    }

    internal fun decodeHello(bytes: ByteArray): Hello {
        val input = DataInputStream(bytes.inputStream())
        val hello = readHello(input)
        if (input.available() != 0) throw IOException("Unexpected bytes after HyperTransfer hello")
        return hello
    }

    private fun writeHello(output: DataOutputStream, hello: Hello) {
        validateHello(hello)
        output.writeInt(MAGIC)
        output.writeByte(VERSION)
        writeBoundedBytes(output, hello.transferId.toByteArray(Charsets.US_ASCII), 64)
        writeBoundedBytes(output, hello.peerId.toByteArray(Charsets.UTF_8), MAX_ID_BYTES)
        output.writeBoolean(hello.isSender)
        output.write(hello.nonce)
        writeBoundedBytes(output, hello.signingPublicKey, MAX_KEY_BYTES)
        writeBoundedBytes(output, hello.ephemeralPublicKey, MAX_KEY_BYTES)
        writeBoundedBytes(output, hello.encryptionPublicKey, MAX_KEY_BYTES)
    }

    private fun readHello(input: DataInputStream): Hello {
        if (input.readInt() != MAGIC) throw IOException("Unrecognized HyperTransfer handshake")
        if (input.readUnsignedByte() != VERSION) throw IOException("Unsupported HyperTransfer handshake version")
        val transferId = String(readBoundedBytes(input, 64), Charsets.US_ASCII)
        val peerId = String(readBoundedBytes(input, MAX_ID_BYTES), Charsets.UTF_8)
        val isSender = input.readBoolean()
        val nonce = ByteArray(NONCE_BYTES)
        try { input.readFully(nonce) } catch (error: EOFException) { throw IOException("Truncated HyperTransfer nonce", error) }
        val signing = readBoundedBytes(input, MAX_KEY_BYTES)
        val ephemeral = readBoundedBytes(input, MAX_KEY_BYTES)
        val encryption = readBoundedBytes(input, MAX_KEY_BYTES)
        return Hello(transferId, peerId, isSender, nonce, signing, ephemeral, encryption).also(::validateHello)
    }

    private fun validateHello(hello: Hello) {
        require(hello.transferId.matches(Regex("^[a-fA-F0-9]{64}$")))
        require(NeaRerIdentity.normalize(hello.peerId) != null)
        require(hello.nonce.size == NONCE_BYTES)
        require(hello.signingPublicKey.isNotEmpty() && hello.signingPublicKey.size <= MAX_KEY_BYTES)
        require(hello.ephemeralPublicKey.isNotEmpty() && hello.ephemeralPublicKey.size <= MAX_KEY_BYTES)
        require(hello.encryptionPublicKey.isNotEmpty() && hello.encryptionPublicKey.size <= MAX_KEY_BYTES)
    }

    private fun writeBoundedBytes(output: DataOutputStream, value: ByteArray, maximum: Int) {
        require(value.isNotEmpty() && value.size <= maximum) { "Invalid HyperTransfer handshake field length" }
        output.writeShort(value.size)
        output.write(value)
    }

    private fun readBoundedBytes(input: DataInputStream, maximum: Int): ByteArray {
        val length = input.readUnsignedShort()
        if (length !in 1..maximum) throw IOException("Invalid HyperTransfer handshake field length")
        return ByteArray(length).also {
            try { input.readFully(it) } catch (error: EOFException) {
                throw IOException("Truncated HyperTransfer handshake field", error)
            }
        }
    }
}
