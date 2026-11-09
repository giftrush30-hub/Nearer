package com.nearer.app

/**
 * Builds an HTTP response head using real CRLF delimiters.
 * Keeping this tiny wire-format helper pure makes malformed line endings
 * detectable in JVM tests without an Android device.
 */
internal object FastShareHttpWire {
    fun responseHead(statusLine: String, headers: List<Pair<String, String>>): ByteArray {
        require(statusLine.isNotBlank() && !statusLine.contains('\r') && !statusLine.contains('\n')) {
            "Invalid HTTP status line"
        }
        return buildString {
            append(statusLine).append("\r\n")
            headers.forEach { (name, value) ->
                require(name.isNotBlank() && name.none { it == ':' || it == '\r' || it == '\n' }) {
                    "Invalid HTTP header name"
                }
                require(value.none { it == '\r' || it == '\n' }) { "Invalid HTTP header value" }
                append(name).append(": ").append(value).append("\r\n")
            }
            append("\r\n")
        }.toByteArray(Charsets.US_ASCII)
    }
}
