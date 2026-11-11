package com.nearer.app

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Destination-signed final-delivery proof; a hop acknowledgement is not this receipt. */
internal data class MeshDeliveryReceipt(
    val packetId: String,
    val originId: String,
    val destinationId: String,
    val deliveredAtMs: Long,
    val signature: ByteArray
) {
    fun signedBytes(): ByteArray = listOf(
        "NeaRer-delivery-receipt-v1", packetId, originId, destinationId, deliveredAtMs.toString()
    ).joinToString("\n").toByteArray(Charsets.UTF_8)

    fun verify(trustedDestinationKey: ByteArray, nowMs: Long): Boolean = try {
        require(packetId.matches(Regex("[A-Za-z0-9._:-]{1,80}")))
        require(NeaRerIdentity.normalize(originId) == originId)
        require(NeaRerIdentity.normalize(destinationId) == destinationId)
        require(originId != destinationId)
        require(deliveredAtMs in 1..(nowMs + 300_000L))
        require(signature.size in 8..128)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(trustedDestinationKey))
        require((key as? java.security.interfaces.ECPublicKey)?.params?.curve?.field?.fieldSize == 256)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(signedBytes())
        verifier.verify(signature)
    } catch (_: Exception) { false }

    companion object {
        fun create(
            packetId: String, originId: String, destinationId: String,
            deliveredAtMs: Long, sign: (ByteArray) -> ByteArray
        ): MeshDeliveryReceipt {
            val unsigned = MeshDeliveryReceipt(packetId, originId, destinationId, deliveredAtMs, ByteArray(0))
            return unsigned.copy(signature = sign(unsigned.signedBytes()))
        }
    }
}
