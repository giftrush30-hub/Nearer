package com.nearer.app

/**
 * Normalizes user-facing transfer status messages into lifecycle decisions.
 * HyperTransfer's success is emitted only after the authenticated recipient
 * returns a receipt matching the transfer ID, item ID and SHA-256 digest.
 */
internal object NearbyTransferStatusPolicy {
    fun isVerifiedOutgoingCompletion(message: String): Boolean =
        message.startsWith("File sent to the nearby phone: ") ||
            message.startsWith("Verified and saved on the recipient's phone: ")
}
