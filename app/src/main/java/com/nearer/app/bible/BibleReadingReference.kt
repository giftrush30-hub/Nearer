package com.nearer.app.bible

data class BibleReadingReference(
    val bookName: String,
    val chapter: Int,
    val startingVerse: Int?
)

/**
 * Parses common plan references such as "Genesis 1:1–31" and "1 Thessalonians 5:12-24".
 * Only the starting verse is used for reader positioning; the complete reference remains
 * visible in the plan card. A missing verse means open at the chapter start.
 */
fun parseBibleReadingReference(reference: String): BibleReadingReference? {
    val match = Regex("^(.+?)\\s+(\\d+)(?::(\\d+)(?:[-–—]\\d+)?)?$")
        .matchEntire(reference.trim()) ?: return null
    val rawBook = match.groupValues[1].trim()
    val chapter = match.groupValues[2].toIntOrNull() ?: return null
    val verse = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
    if (rawBook.isBlank() || chapter !in 1..200 || (verse != null && verse !in 1..300)) return null
    val book = if (rawBook.equals("Psalm", ignoreCase = true)) "Psalms" else rawBook
    return BibleReadingReference(book, chapter, verse)
}
