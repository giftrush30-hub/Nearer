package com.nearer.app

/**
 * Per-sender bundle state. This class contains no Android/UI dependencies and is
 * deliberately conservative: no file payload may begin before explicit acceptance.
 */
internal class TransferBundleSession(
    val manifest: TransferBundleManifest
) {
    enum class Decision { AWAITING_APPROVAL, ACCEPTED, REJECTED }
    enum class ItemState { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }

    data class ItemSnapshot(
        val itemId: String,
        val state: ItemState,
        val transferredBytes: Long,
        val sha256: String?
    )

    data class Snapshot(
        val transferId: String,
        val decision: Decision,
        val totalBytes: Long,
        val completedBytes: Long,
        val completedItems: Int,
        val failedItems: Int,
        val skippedItems: Int,
        val items: List<ItemSnapshot>
    )

    private data class MutableItem(
        val item: TransferBundleItem,
        var state: ItemState = ItemState.PENDING,
        var transferred: Long = 0L,
        var payloadId: Long? = null,
        var sha256: String? = null
    )

    private val items: LinkedHashMap<String, MutableItem>
    private val payloadToItem = HashMap<Long, String>()
    private val declaredTotalBytes: Long
    var decision: Decision = Decision.AWAITING_APPROVAL
        private set

    init {
        val errors = manifest.validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        items = LinkedHashMap<String, MutableItem>().apply {
            manifest.items.filter { it.kind == TransferBundleItem.Kind.FILE }
                .forEach { put(it.id, MutableItem(it)) }
        }
        declaredTotalBytes = items.values.fold(0L) { total, entry -> Math.addExact(total, entry.item.size) }
    }

    @Synchronized
    fun accept() {
        check(decision == Decision.AWAITING_APPROVAL) { "Bundle decision has already been made" }
        decision = Decision.ACCEPTED
    }

    @Synchronized
    fun reject() {
        check(decision == Decision.AWAITING_APPROVAL) { "Bundle decision has already been made" }
        decision = Decision.REJECTED
        items.values.filter { it.state == ItemState.PENDING }.forEach { it.state = ItemState.SKIPPED }
    }

    @Synchronized
    fun skip(itemId: String) {
        check(decision == Decision.ACCEPTED) { "Bundle has not been accepted" }
        val entry = items[itemId] ?: throw IllegalArgumentException("Unknown file item")
        check(entry.state == ItemState.PENDING) { "Only pending files can be skipped" }
        entry.state = ItemState.SKIPPED
    }

    @Synchronized
    fun attachPayload(itemId: String, payloadId: Long) {
        check(decision == Decision.ACCEPTED) { "Bundle has not been accepted" }
        require(payloadId >= 0L)
        val entry = items[itemId] ?: throw IllegalArgumentException("Unknown file item")
        check(entry.state == ItemState.PENDING) { "File is not pending" }
        require(!payloadToItem.containsKey(payloadId)) { "Payload is already assigned" }
        entry.payloadId = payloadId
        entry.state = ItemState.IN_PROGRESS
        payloadToItem[payloadId] = itemId
    }

    @Synchronized
    fun updateProgress(payloadId: Long, transferredBytes: Long, totalBytes: Long) {
        require(transferredBytes >= 0L && totalBytes >= 0L)
        val entry = findPayload(payloadId)
        require(totalBytes == entry.item.size) { "Payload size differs from manifest" }
        require(transferredBytes <= totalBytes) { "Progress exceeds declared file size" }
        require(transferredBytes >= entry.transferred) { "Progress moved backwards" }
        check(entry.state == ItemState.IN_PROGRESS) { "File is not in progress" }
        entry.transferred = transferredBytes
    }

    @Synchronized
    fun complete(payloadId: Long, actualBytes: Long, sha256: String) {
        val entry = findPayload(payloadId)
        check(entry.state == ItemState.IN_PROGRESS) { "File is not in progress" }
        require(actualBytes == entry.item.size) { "Received file size differs from manifest" }
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256 digest" }
        entry.transferred = actualBytes
        entry.sha256 = sha256.lowercase()
        entry.state = ItemState.COMPLETED
    }

    @Synchronized
    fun fail(payloadId: Long) {
        val entry = findPayload(payloadId)
        check(entry.state == ItemState.IN_PROGRESS) { "File is not in progress" }
        entry.state = ItemState.FAILED
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val snapshots = items.values.map {
            ItemSnapshot(it.item.id, it.state, it.transferred, it.sha256)
        }
        return Snapshot(
            transferId = manifest.transferId,
            decision = decision,
            totalBytes = declaredTotalBytes,
            completedBytes = items.values.filter { it.state == ItemState.COMPLETED }.sumOf { it.transferred },
            completedItems = snapshots.count { it.state == ItemState.COMPLETED },
            failedItems = snapshots.count { it.state == ItemState.FAILED },
            skippedItems = snapshots.count { it.state == ItemState.SKIPPED },
            items = snapshots
        )
    }

    @Synchronized
    fun isFinished(): Boolean = items.values.all {
        it.state == ItemState.COMPLETED || it.state == ItemState.FAILED || it.state == ItemState.SKIPPED
    }

    private fun findPayload(payloadId: Long): MutableItem {
        val itemId = payloadToItem[payloadId] ?: throw IllegalArgumentException("Unknown payload ID")
        return items[itemId] ?: throw IllegalStateException("Payload item is missing")
    }
}
