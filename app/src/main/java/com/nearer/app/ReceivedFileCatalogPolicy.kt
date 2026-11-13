package com.nearer.app

import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class ReceivedFileCatalogEntry(
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val isAppBundle: Boolean
)

/**
 * Small, versioned catalog for files stored in NeaRer's private received directory.
 * The catalog stores display metadata only; every restored path is checked against
 * the actual received directory and file length before it is shown.
 */
internal object ReceivedFileCatalogPolicy {
    const val MAX_ENTRIES = 200
    private const val HEADER = "nearer-received-v1"
    private const val MAX_ENCODED_LENGTH = 256 * 1024

    fun create(
        displayName: String,
        relativePath: String,
        sizeBytes: Long,
        modifiedAt: Long,
        isAppBundle: Boolean
    ): ReceivedFileCatalogEntry? {
        val leafName = displayName.substringAfterLast('/').substringAfterLast('\\')
        val cleanedName = leafName.filterNot { Character.isISOControl(it) }.trim().take(140)
        val safeDisplayName = if (cleanedName.isBlank() || cleanedName == "." || cleanedName == "..") {
            "received-file"
        } else cleanedName
        if (relativePath.isBlank() || relativePath == "." || relativePath == ".." ||
            relativePath.contains('/') || relativePath.contains('\\') ||
            relativePath.length > 180 || sizeBytes < 0L || modifiedAt < 0L) return null
        return ReceivedFileCatalogEntry(safeDisplayName, relativePath, sizeBytes, modifiedAt, isAppBundle)
    }

    /** Recovers the original leaf name from legacy timestamp[-uuid]-name storage paths. */
    fun legacyDisplayName(storedName: String): String {
        val afterTimestamp = storedName.substringAfter('-', storedName)
        val possibleUuid = afterTimestamp.substringBefore('-')
        return if (possibleUuid.matches(Regex("[0-9a-fA-F]{8}"))) {
            afterTimestamp.substringAfter('-', afterTimestamp)
        } else afterTimestamp
    }

    fun encode(entries: List<ReceivedFileCatalogEntry>): String {
        val unique = LinkedHashMap<String, ReceivedFileCatalogEntry>()
        entries.forEach { entry ->
            val valid = create(entry.displayName, entry.relativePath, entry.sizeBytes, entry.modifiedAt, entry.isAppBundle)
                ?: return@forEach
            unique[valid.relativePath] = valid
        }
        return buildString {
            append(HEADER)
            unique.values.take(MAX_ENTRIES).forEach { entry ->
                append('\n')
                append(encodeField(entry.displayName)).append('|')
                append(encodeField(entry.relativePath)).append('|')
                append(entry.sizeBytes).append('|')
                append(entry.modifiedAt).append('|')
                append(if (entry.isAppBundle) '1' else '0')
            }
        }
    }

    fun decode(encoded: String?): List<ReceivedFileCatalogEntry> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_LENGTH) return emptyList()
        val lines = encoded.lineSequence().take(MAX_ENTRIES + 1).toList()
        if (lines.firstOrNull() != HEADER) return emptyList()
        val entries = LinkedHashMap<String, ReceivedFileCatalogEntry>()
        lines.drop(1).forEach { line ->
            val fields = line.split('|')
            if (fields.size != 5) return@forEach
            val name = decodeField(fields[0]) ?: return@forEach
            val path = decodeField(fields[1]) ?: return@forEach
            val size = fields[2].toLongOrNull() ?: return@forEach
            val modified = fields[3].toLongOrNull() ?: return@forEach
            val bundle = when (fields[4]) { "1" -> true; "0" -> false; else -> return@forEach }
            val entry = create(name, path, size, modified, bundle) ?: return@forEach
            entries[entry.relativePath] = entry
        }
        return entries.values.take(MAX_ENTRIES)
    }

    private fun encodeField(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}

/** Storage-space comparison kept pure so boundary conditions are covered by JVM tests. */
internal object ReceivedFileStoragePolicy {
    fun hasEnoughSpace(requiredBytes: Long, availableBytes: Long): Boolean =
        requiredBytes >= 0L && availableBytes >= 0L && requiredBytes <= availableBytes
}
