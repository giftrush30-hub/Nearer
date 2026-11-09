package com.nearer.app

import java.util.Locale

/**
 * Converts peer- or provider-supplied names into safe, portable local file names.
 * Keep only the leaf name, preserve valid Unicode, reject path/control characters,
 * avoid Windows device names, and bound length without splitting surrogate pairs.
 */
internal object TransferFileNamePolicy {
    private val forbidden = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    private val windowsReserved = setOf("CON", "PRN", "AUX", "NUL")

    fun safeName(raw: String): String {
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString {
            var index = 0
            while (index < leaf.length) {
                val codePoint = leaf.codePointAt(index)
                index += Character.charCount(codePoint)
                val category = Character.getType(codePoint)
                val invalidCategory = category == Character.CONTROL.toInt() ||
                    category == Character.FORMAT.toInt() ||
                    category == Character.SURROGATE.toInt() ||
                    category == Character.UNASSIGNED.toInt() ||
                    category == Character.PRIVATE_USE.toInt()
                if (invalidCategory || (codePoint <= Char.MAX_VALUE.code && codePoint.toChar() in forbidden)) {
                    append('_')
                } else {
                    appendCodePoint(codePoint)
                }
            }
        }.trim().trimEnd('.')

        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") return "shared-file"

        val stem = cleaned.substringBefore('.').uppercase(Locale.ROOT)
        val reserved = stem in windowsReserved || stem.matches(Regex("COM[1-9]|LPT[1-9]"))
        val portable = (if (reserved) "_" else "") + cleaned
        val bounded = StringBuilder()
        var index = 0
        var codePoints = 0
        while (index < portable.length && codePoints < 100) {
            val codePoint = portable.codePointAt(index)
            bounded.appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
            codePoints++
        }
        return bounded.toString().ifBlank { "shared-file" }
    }
}
