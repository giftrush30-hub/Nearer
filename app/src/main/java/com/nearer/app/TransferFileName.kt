package com.nearer.app

/**
 * Produces a safe single filename while preserving normal Unicode characters.
 * Path separators are removed so remote metadata can never choose a destination path.
 */
internal object TransferFileName {
    fun safeLeafName(raw: String): String {
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\\\')
        val cleaned = leaf.map { character ->
            if (character.isISOControl() || character in "/\\\\:*?\"<>|") '_' else character
        }.joinToString("")
            .trim()
            .trim('.')
            .take(120)
        return cleaned.ifBlank { "shared-file" }
    }
}
