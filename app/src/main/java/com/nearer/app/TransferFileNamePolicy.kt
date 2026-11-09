package com.nearer.app

/**
 * Converts peer- or provider-supplied names into safe, portable local file names.
 *
 * Never trust a remote filename as a filesystem path. Keep only its final path
 * component, replace unsupported/control characters, and bound its length.
 */
internal object TransferFileNamePolicy {
    fun safeName(raw: String): String {
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = leaf
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .take(100)
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") "shared-file" else cleaned
    }
}
