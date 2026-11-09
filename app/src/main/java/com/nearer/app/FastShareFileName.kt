package com.nearer.app

import java.net.URLDecoder

/** Converts an HTTP upload filename into a safe, bounded leaf name. */
internal object FastShareFileName {
    fun sanitize(encodedName: String): String {
        val decoded = runCatching {
            URLDecoder.decode(encodedName, Charsets.UTF_8.name())
        }.getOrDefault("")
        return decoded
            .map { character ->
                if (character.isLetterOrDigit() || character in " ._-()[]") character else '_'
            }
            .joinToString("")
            .trim()
            .trim('.')
            .take(180)
            .ifBlank { "Received file" }
    }
}
