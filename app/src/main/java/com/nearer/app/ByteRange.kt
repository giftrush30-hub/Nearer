package com.nearer.app

/**
 * Resolves a single HTTP byte range without allocating or reading file data.
 * A null result means the supplied byte range is unsatisfiable or malformed.
 */
internal data class ResolvedByteRange(
    val start: Long,
    val endInclusive: Long,
    val partial: Boolean
)

internal object ByteRange {
    fun resolve(header: String?, fileLength: Long): ResolvedByteRange? {
        if (fileLength < 0L) return null
        val full = ResolvedByteRange(0L, fileLength - 1L, false)
        if (header.isNullOrBlank()) return full

        val value = header.trim()
        if (!value.startsWith("bytes=", ignoreCase = true)) return full

        val specification = value.substringAfter('=', "")
        // This small server intentionally supports one range per request. Ignore
        // multipart ranges and safely return the complete representation.
        if (specification.contains(',')) return full
        val bounds = specification.split('-', limit = 2)
        if (bounds.size != 2 || fileLength == 0L) return null

        val firstText = bounds[0].trim()
        val lastText = bounds[1].trim()
        val first = firstText.takeIf { it.isNotEmpty() }?.toLongOrNull()
        val last = lastText.takeIf { it.isNotEmpty() }?.toLongOrNull()
        if ((firstText.isNotEmpty() && first == null) ||
            (lastText.isNotEmpty() && last == null) ||
            (first == null && last == null)
        ) return null

        val start: Long
        val end: Long
        if (first == null) {
            val suffixLength = last ?: return null
            if (suffixLength <= 0L) return null
            start = (fileLength - suffixLength).coerceAtLeast(0L)
            end = fileLength - 1L
        } else {
            if (first < 0L || first >= fileLength) return null
            start = first
            end = (last ?: (fileLength - 1L)).coerceAtMost(fileLength - 1L)
            if (end < start) return null
        }
        return ResolvedByteRange(start, end, true)
    }
}
