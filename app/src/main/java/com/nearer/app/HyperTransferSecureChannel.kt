package com.nearer.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Directional, transfer-bound AES-256-GCM record layer for HyperTransfer.
 *
 * Callers MUST authenticate both peer identities and obtain transfer approval
 * before constructing this channel. The supplied transcript must be the exact
 * transcript signed and verified by both peers. This class does not authenticate
 * identities or provide a user-confirmation UI.
 *
 * Each direction has an independent key and nonce prefix. The 64-bit sequence
 * number is monotonically increasing and is authenticated as associated data.
 */
internal class HyperTransferSecureChannel private constructor(
    private val sendKey: ByteArray,
    private val receiveKey: ByteArray,
    private val sendNoncePrefix: ByteArray,
    private val receiveNoncePrefix: ByteArray,
    private val transferId: String,
    private val senderId: String,
    private val receiverId: String,
    private val localIsSender: Boolean
) {
    private var sendSequence = 0L
    private var receiveSequence = 0L

    @Synchronized
    fun seal(plaintext: ByteArray): ByteArray {
        check(sendSequence >= 0L && sendSequence < Long.MAX_VALUE) { "HyperTransfer send sequence exhausted" }
        require(plaintext.size in 1..MAX_PLAINTEXT_BYTES) { "Invalid HyperTransfer record size" }
        val sequence = sendSequence
        val nonce = nonce(sendNoncePrefix, sequence)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sendKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(transferId, if (localIsSender) senderId else receiverId,
            if (localIsSender) receiverId else senderId, sequence, plaintext.size))
        val encrypted = cipher.doFinal(plaintext)
        sendSequence++
        return ByteBuffer.allocate(8 + encrypted.size).putLong(sequence).put(encrypted).array()
    }

    @Synchronized
    fun open(record: ByteArray): ByteArray {
        require(record.size in (SEQUENCE_BYTES + TAG_BYTES + 1)..(SEQUENCE_BYTES + TAG_BYTES + MAX_PLAINTEXT_BYTES)) {
            "Invalid encrypted HyperTransfer record length"
        }
        val buffer = ByteBuffer.wrap(record)
        val sequence = buffer.long
        if (sequence != receiveSequence) throw SecurityException("Replayed, skipped or reordered HyperTransfer record")
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val plainSize = encrypted.size - TAG_BYTES
        val nonce = nonce(receiveNoncePrefix, sequence)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(receiveKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(transferId, if (localIsSender) receiverId else senderId,
            if (localIsSender) senderId else receiverId, sequence, plainSize))
        val plaintext = cipher.doFinal(encrypted)
        if (plaintext.size != plainSize) throw SecurityException("Invalid decrypted record length")
        receiveSequence++
        return plaintext
    }

    /** Length-prefixed authenticated record. The length is bounded before allocation. */
    @Throws(IOException::class)
    fun writeRecord(output: DataOutputStream, plaintext: ByteArray) {
        val sealed = seal(plaintext)
        output.writeInt(sealed.size)
        output.write(sealed)
    }

    /** Reads exactly one record; malformed/oversized frames fail before allocation. */
    @Throws(IOException::class)
    fun readRecord(input: DataInputStream): ByteArray {
        val length = input.readInt()
        if (length !in MIN_ENCRYPTED_RECORD_BYTES..MAX_ENCRYPTED_RECORD_BYTES) {
            throw IOException("Invalid encrypted HyperTransfer record length")
        }
        val sealed = ByteArray(length)
        try {
            input.readFully(sealed)
        } catch (error: EOFException) {
            throw EOFException("Connection ended inside an encrypted HyperTransfer record")
        }
        return try {
            open(sealed)
        } catch (error: Exception) {
            throw IOException("HyperTransfer record authentication failed", error)
        }
    }

    @Synchronized
    fun close() {
        sendKey.fill(0)
        receiveKey.fill(0)
        sendNoncePrefix.fill(0)
        receiveNoncePrefix.fill(0)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val TAG_BYTES = 16
        private const val SEQUENCE_BYTES = 8
        private const val NONCE_PREFIX_BYTES = 4
        const val MAX_PLAINTEXT_BYTES = 256 * 1024
        const val MIN_ENCRYPTED_RECORD_BYTES = SEQUENCE_BYTES + TAG_BYTES + 1
        const val MAX_ENCRYPTED_RECORD_BYTES = SEQUENCE_BYTES + TAG_BYTES + MAX_PLAINTEXT_BYTES

        /**
         * Derives complementary directional keys. transcript must already include
         * both authenticated identities, both fresh nonces, both ephemeral keys,
         * transfer ID and sender/receiver roles.
         */
        fun establish(
            localPrivateKey: PrivateKey,
            remoteEphemeralPublicKey: PublicKey,
            transcript: ByteArray,
            transferId: String,
            senderId: String,
            receiverId: String,
            localIsSender: Boolean
        ): HyperTransferSecureChannel {
            require(transferId.matches(Regex("^[a-fA-F0-9]{64}$")))
            require(senderId.isNotBlank() && receiverId.isNotBlank() && senderId != receiverId)
            require(transcript.size in 1..16 * 1024)
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(localPrivateKey)
            agreement.doPhase(remoteEphemeralPublicKey, true)
            val sharedSecret = agreement.generateSecret()
            val salt = MessageDigest.getInstance("SHA-256").digest(transcript)
            val material = try {
                hkdf(sharedSecret, salt, "NeaRer-HyperTransfer-AEAD-v1".toByteArray(Charsets.US_ASCII), 72)
            } finally {
                sharedSecret.fill(0)
                salt.fill(0)
            }
            return try {
                val senderKey = material.copyOfRange(0, 32)
                val receiverKey = material.copyOfRange(32, 64)
                val senderPrefix = material.copyOfRange(64, 68)
                val receiverPrefix = material.copyOfRange(68, 72)
                HyperTransferSecureChannel(
                    sendKey = if (localIsSender) senderKey else receiverKey,
                    receiveKey = if (localIsSender) receiverKey else senderKey,
                    sendNoncePrefix = if (localIsSender) senderPrefix else receiverPrefix,
                    receiveNoncePrefix = if (localIsSender) receiverPrefix else senderPrefix,
                    transferId = transferId.lowercase(),
                    senderId = senderId,
                    receiverId = receiverId,
                    localIsSender = localIsSender
                )
            } finally {
                material.fill(0)
            }
        }

        private fun nonce(prefix: ByteArray, sequence: Long): ByteArray =
            ByteBuffer.allocate(12).put(prefix).putLong(sequence).array()

        private fun aad(transferId: String, from: String, to: String, sequence: Long, plainSize: Int): ByteArray {
            val transfer = transferId.toByteArray(Charsets.US_ASCII)
            val sender = from.toByteArray(Charsets.UTF_8)
            val receiver = to.toByteArray(Charsets.UTF_8)
            return ByteBuffer.allocate(4 + transfer.size + 4 + sender.size + 4 + receiver.size + 8 + 4)
                .putInt(transfer.size).put(transfer)
                .putInt(sender.size).put(sender)
                .putInt(receiver.size).put(receiver)
                .putLong(sequence).putInt(plainSize).array()
        }

        private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
            val extract = Mac.getInstance("HmacSHA256")
            extract.init(SecretKeySpec(salt, "HmacSHA256"))
            val prk = extract.doFinal(ikm)
            try {
                val result = ByteArray(length)
                var previous = ByteArray(0)
                var offset = 0
                var counter = 1
                while (offset < length) {
                    val expand = Mac.getInstance("HmacSHA256")
                    expand.init(SecretKeySpec(prk, "HmacSHA256"))
                    expand.update(previous)
                    expand.update(info)
                    expand.update(counter.toByte())
                    previous = expand.doFinal()
                    val count = minOf(previous.size, length - offset)
                    System.arraycopy(previous, 0, result, offset, count)
                    offset += count
                    counter++
                }
                previous.fill(0)
                return result
            } finally {
                prk.fill(0)
            }
        }
    }
}
