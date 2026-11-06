package com.nearer.app

/**
 * Metadata-only description of one logical multi-file transfer.
 * File bytes are deliberately not stored here; they are streamed separately.
 */
internal data class TransferBundleManifest(
    val transferId: String,
    val items: List<TransferBundleItem>,
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION
) {
    companion object {
        const val CURRENT_PROTOCOL_VERSION = 1
        const val MAX_ITEMS = 10_000
        const val MAX_PATH_LENGTH = 1_024
    }

    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (protocolVersion != CURRENT_PROTOCOL_VERSION) errors += "Unsupported transfer manifest version"
        if (transferId.isBlank() || transferId.length > 128 || transferId.any { it.isISOControl() }) {
            errors += "Invalid transfer ID"
        }
        if (items.isEmpty()) errors += "A transfer bundle must contain at least one item"
        if (items.size > MAX_ITEMS) errors += "Transfer bundle contains too many items"

        val ids = HashSet<String>()
        val paths = HashMap<String, TransferBundleItem.Kind>()
        items.forEachIndexed { index, item ->
            val prefix = "Item " + (index + 1)
            if (item.id.isBlank() || item.id.length > 128 || item.id.any { it.isISOControl() } || !ids.add(item.id)) {
                errors += "$prefix has a missing, oversized or duplicate ID"
            }
            if (!isSafeRelativePath(item.relativePath)) {
                errors += "$prefix has an unsafe relative path"
            } else if (paths.put(item.relativePath, item.kind) != null) {
                errors += "$prefix duplicates another destination path"
            }
            if (item.displayName.isBlank() || item.displayName.length > 255 ||
                item.displayName.any { it.isISOControl() } ||
                item.displayName != TransferFileName.safeLeafName(item.displayName)
            ) {
                errors += "$prefix has an unsafe display name"
            }
            if (item.sizeBytes < 0L) errors += "$prefix has a negative file size"
            if (item.kind == TransferBundleItem.Kind.DIRECTORY && item.sizeBytes != 0L) {
                errors += "$prefix directory entries must have zero size"
            }
            if (item.mimeType != null && (item.mimeType.length > 255 || item.mimeType.any { it.isISOControl() })) {
                errors += "$prefix has invalid MIME metadata"
            }
        }

        // A file cannot also be a parent directory. Without this check, a later
        // item such as "Photos/2026/a.jpg" could collide with an earlier file "Photos".
        paths.keys.forEach { path ->
            val components = path.split('/')
            for (index in 1 until components.size) {
                val parent = components.take(index).joinToString("/")
                if (paths[parent] == TransferBundleItem.Kind.FILE) {
                    errors += "A file entry cannot be used as a parent directory"
                    break
                }
            }
            // Child paths are already rejected by the parent lookup above:
            // every descendant must visit this path as one of its parents.
        }
        return errors.distinct()
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.length > MAX_PATH_LENGTH) return false
        if (path.startsWith('/') || path.startsWith('\\') || path.contains('\\')) return false
        if (path.any { it.isISOControl() }) return false
        val parts = path.split('/')
        return parts.all { part ->
            part.isNotBlank() && part != "." && part != ".." &&
                part == TransferFileName.safeLeafName(part)
        }
    }
}

internal data class TransferBundleItem(
    val id: String,
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val kind: Kind = Kind.FILE
) {
    enum class Kind { FILE, DIRECTORY }
}
