package com.nearer.app

import java.security.MessageDigest

/**
 * Stable identity for a resumable one-file transfer.
 *
 * A fresh Nearby payload ID is expected after reconnect or process restart. The
 * transfer ID instead binds the sender, recipient, file size, content digest and
 * safe display name, allowing a reselected identical file to find the receiver's
 * verified partial without trusting an endpoint ID or display name alone.
 *
 * Keep the input field order and separators stable: existing receiver checkpoints
 * already use this exact derivation.
 */
internal object HyperTransferIdentity {
    fun createTransferId(
        senderStablePeerId: String,
        recipientStablePeerId: String,
        item: TransferBundleItem,
        wholeFileSha256: String
    ): String {
        require(senderStablePeerId.isNotBlank()) { "Sender identity is required" }
        require(recipientStablePeerId.isNotBlank()) { "Recipient identity is required" }
        require(item.kind == TransferBundleItem.Kind.FILE) { "Only files can use HyperTransfer identity" }
        require(item.sizeBytes >= 0L) { "Invalid file size" }
        require(wholeFileSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            "A valid whole-file SHA-256 is required"
        }
        val identity = (
            senderStablePeerId + "|" + recipientStablePeerId + "|" +
                item.sizeBytes + "|" + wholeFileSha256.lowercase() + "|" + item.displayName
            ).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(identity)
            .joinToString("") { "%02x".format(it) }
    }
}
