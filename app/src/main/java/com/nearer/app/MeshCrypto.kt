package com.nearer.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic primitives for recipient-only mesh messages.
 *
 * The sender signs the packet and encrypts its body to the recipient's verified
 * P-256 agreement public key. Relays may carry [MeshCryptoPacket] unchanged but
 * cannot decrypt its body. Identity public keys MUST be authenticated out of band
 * (for example by comparing a QR/fingerprint or a trusted contact record) before use.
 *
 * This class deliberately does not persist private keys or silently trust keys
 * supplied by an arbitrary nearby peer.
 */
internal data class MeshIdentityKeys(
    val signing: KeyPair,
    val agreement: KeyPair
) {
    val signingPublicKey: String get() = MeshCrypto.encode(signing.public.encoded)
    val agreementPublicKey: String get() = MeshCrypto.encode(agreement.public.encoded)
}

internal data class MeshCryptoPacket(
    val messageId: String,
    val originPeerId: String,
    val destinationPeerId: String,
    val ephemeralPublicKey: String,
    val senderSigningPublicKey: String,
    val salt: String,
    val nonce: String,
    val ciphertext: String,
    val signature: String
)

internal object MeshCrypto {
    private const val CURVE = "secp256r1"
    private const val GCM_TAG_BITS = 128
    private const val NONCE_BYTES = 12
    private const val SALT_BYTES = 32

    fun generateIdentity(): MeshIdentityKeys = MeshIdentityKeys(
        signing = generateEcKeyPair(),
        agreement = generateEcKeyPair()
    )

    fun seal(
        plaintext: ByteArray,
        messageId: String,
        originPeerId: String,
        destinationPeerId: String,
        senderSigningKeys: KeyPair,
        recipientAgreementPublicKey: String
    ): MeshCryptoPacket {
        require(plaintext.isNotEmpty()) { "Message cannot be empty" }
        require(plaintext.size <= 64 * 1024) { "Message exceeds mesh packet limit" }
        require(messageId.isNotBlank() && messageId.length <= 128)
        require(originPeerId.isNotBlank() && originPeerId.length <= 64)
        require(destinationPeerId.isNotBlank() && destinationPeerId.length <= 64)

        val recipientPublic = decodePublic(recipientAgreementPublicKey)
        val ephemeral = generateEcKeyPair()
        val salt = ByteArray(SALT_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val aad = associatedData(messageId, originPeerId, destinationPeerId)
        val shared = agree(ephemeral.private, recipientPublic)
        val key = hkdf(shared, salt, aad, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plaintext)

        val unsigned = MeshCryptoPacket(
            messageId, originPeerId, destinationPeerId,
            encode(ephemeral.public.encoded), encode(senderSigningKeys.public.encoded),
            encode(salt), encode(nonce), encode(encrypted), ""
        )
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(senderSigningKeys.private)
        signature.update(canonical(unsigned))
        return unsigned.copy(signature = encode(signature.sign()))
    }

    /** Returns null for an untrusted signature, wrong recipient, malformed packet or bad tag. */
    fun open(
        packet: MeshCryptoPacket,
        localPeerId: String,
        recipientAgreementPrivateKey: PrivateKey,
        trustedSenderSigningPublicKey: String
    ): ByteArray? {
        return try {
            if (!hasValidBounds(packet) ||
                packet.destinationPeerId != localPeerId ||
                packet.senderSigningPublicKey != trustedSenderSigningPublicKey
            ) {
                null
            } else {
                val trustedKey = decodePublic(trustedSenderSigningPublicKey)
                val verifier = Signature.getInstance("SHA256withECDSA")
                verifier.initVerify(trustedKey)
                verifier.update(canonical(packet.copy(signature = "")))
                if (!verifier.verify(decode(packet.signature))) {
                    null
                } else {
                    val shared = agree(recipientAgreementPrivateKey, decodePublic(packet.ephemeralPublicKey))
                    val aad = associatedData(packet.messageId, packet.originPeerId, packet.destinationPeerId)
                    val key = hkdf(shared, decode(packet.salt), aad, 32)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(key, "AES"),
                        GCMParameterSpec(GCM_TAG_BITS, decode(packet.nonce))
                    )
                    cipher.updateAAD(aad)
                    cipher.doFinal(decode(packet.ciphertext))
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasValidBounds(packet: MeshCryptoPacket): Boolean =
        packet.messageId.isNotBlank() && packet.messageId.length <= 128 &&
            packet.originPeerId.isNotBlank() && packet.originPeerId.length <= 64 &&
            packet.destinationPeerId.isNotBlank() && packet.destinationPeerId.length <= 64 &&
            packet.ephemeralPublicKey.length in 1..512 &&
            packet.senderSigningPublicKey.length in 1..512 &&
            packet.salt.length in 1..128 &&
            packet.nonce.length in 1..64 &&
            packet.ciphertext.length in 1..96 * 1024 &&
            packet.signature.length in 1..256 &&
            decode(packet.salt).size == SALT_BYTES &&
            decode(packet.nonce).size == NONCE_BYTES

    private fun generateEcKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVE))
        }.generateKeyPair()

    private fun decodePublic(value: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decode(value)))

    @Suppress("unused")
    private fun decodePrivate(value: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decode(value)))

    private fun agree(privateKey: PrivateKey, publicKey: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }

    private fun hkdf(inputKey: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = extract.doFinal(inputKey)
        val output = ByteArrayOutputStream()
        var previous = ByteArray(0)
        var counter = 1
        while (output.size() < size) {
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            expand.update(previous)
            expand.update(info)
            expand.update(counter.toByte())
            previous = expand.doFinal()
            output.write(previous)
            counter++
        }
        return output.toByteArray().copyOf(size)
    }

    private fun associatedData(messageId: String, origin: String, destination: String): ByteArray =
        canonicalStrings("NEARER-MESH-V1", messageId, origin, destination)

    private fun canonical(packet: MeshCryptoPacket): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            listOf(
                "NEARER-MESH-PACKET-V1", packet.messageId, packet.originPeerId,
                packet.destinationPeerId, packet.ephemeralPublicKey,
                packet.senderSigningPublicKey, packet.salt, packet.nonce, packet.ciphertext
            ).forEach(out::writeUTF)
        }
        bytes.toByteArray()
    }

    private fun canonicalStrings(vararg values: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out -> values.forEach(out::writeUTF) }
        bytes.toByteArray()
    }

    internal fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    internal fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
