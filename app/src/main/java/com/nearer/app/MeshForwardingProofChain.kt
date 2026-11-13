package com.nearer.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * A verifiable sequence of durable next-hop acceptances for one immutable packet.
 *
 * Each record is signed by the accepting peer and commits to the hash of the
 * preceding record. Verification requires an independently trusted signing key
 * for every accepting peer; embedded/self-asserted keys are deliberately absent.
 *
 * This is a protocol primitive. It is not proof of physical radio transmission,
 * honest forwarding, or a globally complete path unless the caller supplies
 * trusted keys and obtains the chain from the authenticated transport.
 */
internal data class MeshForwardingProof(
    val packetId: String,
    val packetDigest: ByteArray,
    val originPeerId: String,
    val acceptingPeerId: String,
    val previousHopPeerId: String,
    val hopIndex: Int,
    val acceptedAtMs: Long,
    val previousProofHash: ByteArray,
    val signature: ByteArray
) {
    fun signedBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write(DOMAIN.toByteArray(Charsets.US_ASCII))
            writeString(data, packetId)
            data.write(packetDigest)
            writeString(data, originPeerId)
            writeString(data, acceptingPeerId)
            writeString(data, previousHopPeerId)
            data.writeByte(hopIndex)
            data.writeLong(acceptedAtMs)
            data.write(previousProofHash)
        }
        return out.toByteArray()
    }

    fun verify(trustedAcceptingSigningKey: ByteArray, nowMs: Long): Boolean = try {
        require(validHeader())
        require(acceptedAtMs <= nowMs + MAX_CLOCK_SKEW_MS)
        require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(trustedAcceptingSigningKey))
        require((key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(signedBytes())
            verify(signature)
        }
    } catch (_: Exception) { false }

    fun hash(): ByteArray = MessageDigest.getInstance("SHA-256").digest(encode())

    fun encode(): ByteArray {
        require(validHeader())
        require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeInt(MAGIC)
            writeString(data, packetId)
            // The decoder and signature verifier require the exact digest bytes.
            // Omitting this field corrupts the record and breaks every chain round-trip.
            data.write(packetDigest)
            writeString(data, originPeerId)
            writeString(data, acceptingPeerId)
            writeString(data, previousHopPeerId)
            data.writeByte(hopIndex)
            data.writeLong(acceptedAtMs)
            data.write(previousProofHash)
            data.writeShort(signature.size)
            data.write(signature)
        }
        return out.toByteArray().also { require(it.size <= MAX_RECORD_BYTES) }
    }

    private fun validHeader(): Boolean =
        packetId.matches(PACKET_ID_PATTERN) && packetDigest.size == HASH_BYTES &&
            NeaRerIdentity.normalize(originPeerId) == originPeerId &&
            NeaRerIdentity.normalize(acceptingPeerId) == acceptingPeerId &&
            NeaRerIdentity.normalize(previousHopPeerId) == previousHopPeerId &&
            originPeerId != acceptingPeerId && acceptingPeerId != previousHopPeerId &&
            hopIndex in 1..MAX_ACCEPTANCE_RECORDS && acceptedAtMs > 0L && previousProofHash.size == HASH_BYTES

    companion object {
        private const val MAGIC = 0x4E524632 // NRF2
        private const val DOMAIN = "NeaRer-forward-proof-v2"
        private const val MAX_RECORD_BYTES = 1024
        private const val MAX_STRING_BYTES = 80
        private const val HASH_BYTES = 32
        private const val MIN_SIGNATURE_BYTES = 8
        private const val MAX_SIGNATURE_BYTES = 128
        private const val MAX_CLOCK_SKEW_MS = 300_000L
        const val MAX_ACCEPTANCE_RECORDS = 5
        private val PACKET_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,80}")
        val GENESIS_HASH: ByteArray get() = ByteArray(HASH_BYTES)

        private fun writeString(out: DataOutputStream, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.isNotEmpty() && bytes.size <= MAX_STRING_BYTES)
            out.writeShort(bytes.size)
            out.write(bytes)
        }

        private fun readString(input: DataInputStream): String {
            val size = input.readUnsignedShort()
            require(size in 1..MAX_STRING_BYTES)
            return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
        }

        fun decode(bytes: ByteArray): MeshForwardingProof {
            require(bytes.size in 1..MAX_RECORD_BYTES)
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readInt() == MAGIC)
            val packetId = readString(input)
            val packetDigest = ByteArray(HASH_BYTES).also(input::readFully)
            val origin = readString(input)
            val accepting = readString(input)
            val previous = readString(input)
            val index = input.readUnsignedByte()
            val acceptedAt = input.readLong()
            val previousHash = ByteArray(HASH_BYTES).also(input::readFully)
            val signatureSize = input.readUnsignedShort()
            require(signatureSize in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
            val signature = ByteArray(signatureSize).also(input::readFully)
            require(input.available() == 0)
            return MeshForwardingProof(packetId, packetDigest, origin, accepting, previous, index, acceptedAt, previousHash, signature)
                .also { require(it.validHeader()) }
        }

        fun create(
            packetId: String,
            packetDigest: ByteArray,
            originPeerId: String,
            acceptingPeerId: String,
            previousHopPeerId: String,
            hopIndex: Int,
            acceptedAtMs: Long,
            previousProofHash: ByteArray,
            sign: (ByteArray) -> ByteArray
        ): MeshForwardingProof {
            val unsigned = MeshForwardingProof(
                packetId, packetDigest.copyOf(), originPeerId, acceptingPeerId, previousHopPeerId,
                hopIndex, acceptedAtMs, previousProofHash.copyOf(), ByteArray(0)
            )
            require(unsigned.validHeader())
            val signature = sign(unsigned.signedBytes())
            require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES)
            return unsigned.copy(signature = signature)
        }
    }
}

/** Ordered proof chain. Keys are resolved only from the caller's trusted identity store. */
internal data class MeshForwardingProofChain(val proofs: List<MeshForwardingProof>) {
    /**
     * Verifies an incoming prefix before this phone appends its own durable-acceptance
     * record. An empty prefix is valid only on the first hop directly from the origin.
     */
    fun verifyPrefix(
        packetId: String,
        packetDigest: ByteArray,
        originPeerId: String,
        expectedPreviousHopPeerId: String,
        trustedSigningKey: (String) -> ByteArray?,
        nowMs: Long
    ): Boolean = try {
        require(proofs.size in 0..MeshForwardingProof.MAX_ACCEPTANCE_RECORDS)
        require(packetDigest.size == 32)
        require(NeaRerIdentity.normalize(originPeerId) == originPeerId)
        require(NeaRerIdentity.normalize(expectedPreviousHopPeerId) == expectedPreviousHopPeerId)
        var previousPeer = originPeerId
        var previousHash = MeshForwardingProof.GENESIS_HASH
        var previousTime = 0L
        val visited = linkedSetOf(originPeerId)
        proofs.forEachIndexed { index, proof ->
            require(proof.packetId == packetId && proof.originPeerId == originPeerId)
            require(MessageDigest.isEqual(proof.packetDigest, packetDigest))
            require(proof.hopIndex == index + 1)
            require(proof.previousHopPeerId == previousPeer)
            require(MessageDigest.isEqual(proof.previousProofHash, previousHash))
            require(proof.acceptedAtMs >= previousTime)
            require(visited.add(proof.acceptingPeerId))
            val key = trustedSigningKey(proof.acceptingPeerId) ?: return false
            require(proof.verify(key, nowMs))
            previousPeer = proof.acceptingPeerId
            previousHash = proof.hash()
            previousTime = proof.acceptedAtMs
        }
        require(previousPeer == expectedPreviousHopPeerId)
        true
    } catch (_: Exception) { false }

    /** Adds this phone's signed acceptance only after its caller can commit the result atomically. */
    fun appendAcceptance(
        packetId: String,
        packetDigest: ByteArray,
        originPeerId: String,
        acceptingPeerId: String,
        acceptedAtMs: Long,
        sign: (ByteArray) -> ByteArray
    ): MeshForwardingProofChain {
        require(proofs.size < MeshForwardingProof.MAX_ACCEPTANCE_RECORDS)
        val previous = proofs.lastOrNull()
        val proof = MeshForwardingProof.create(
            packetId = packetId,
            packetDigest = packetDigest,
            originPeerId = originPeerId,
            acceptingPeerId = acceptingPeerId,
            previousHopPeerId = previous?.acceptingPeerId ?: originPeerId,
            hopIndex = proofs.size + 1,
            acceptedAtMs = maxOf(acceptedAtMs, previous?.acceptedAtMs ?: 0L),
            previousProofHash = previous?.hash() ?: MeshForwardingProof.GENESIS_HASH
        ) { bytes -> sign(bytes) }
        return MeshForwardingProofChain(proofs + proof)
    }

    fun verify(
        packetId: String,
        packetDigest: ByteArray,
        originPeerId: String,
        destinationPeerId: String,
        trustedSigningKey: (String) -> ByteArray?,
        nowMs: Long
    ): Boolean = try {
        require(proofs.size in 1..MeshForwardingProof.MAX_ACCEPTANCE_RECORDS)
        require(verifyPrefix(packetId, packetDigest, originPeerId, destinationPeerId, trustedSigningKey, nowMs))
        true
    } catch (_: Exception) { false }

    fun encode(): ByteArray {
        require(proofs.size in 0..MeshForwardingProof.MAX_ACCEPTANCE_RECORDS)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeInt(MAGIC)
            data.writeByte(proofs.size)
            proofs.forEach { proof ->
                val encoded = proof.encode()
                data.writeShort(encoded.size)
                data.write(encoded)
            }
        }
        return out.toByteArray().also { require(it.size <= MAX_CHAIN_BYTES) }
    }

    companion object {
        private const val MAGIC = 0x4E524332 // NRC2
        private const val MAX_CHAIN_BYTES = 5 * 1024

        fun decode(bytes: ByteArray): MeshForwardingProofChain {
            require(bytes.size in 1..MAX_CHAIN_BYTES)
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readInt() == MAGIC)
            val count = input.readUnsignedByte()
            require(count in 0..MeshForwardingProof.MAX_ACCEPTANCE_RECORDS)
            val proofs = List(count) {
                val size = input.readUnsignedShort()
                require(size in 1..1024)
                MeshForwardingProof.decode(ByteArray(size).also(input::readFully))
            }
            require(input.available() == 0)
            return MeshForwardingProofChain(proofs)
        }
    }
}
