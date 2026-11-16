package com.nearer.app

/**
 * A packet ID may have only one final signed receipt. Recreating it with a new
 * timestamp/signature after process death would collide with the durable outbox.
 */
internal object MeshReceiptRecoveryPolicy {
    fun reuseOrCreate(
        persistedReceipt: MeshDeliveryReceipt?,
        create: () -> MeshDeliveryReceipt
    ): MeshDeliveryReceipt = persistedReceipt ?: create()
}
