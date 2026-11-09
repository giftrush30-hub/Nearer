package com.nearer.app


/** Builds safe HTTP header values for metadata supplied by Android file providers. */
internal object FileHttpHeaders {
    private val HEX = "0123456789ABCDEF"

    /**
     * Security and cache headers for the private HTML receiver page.
     * The page intentionally permits its embedded stylesheet but no scripts,
     * external resources, forms, framing, or privileged browser capabilities.
     */
    fun receiverPageHeaders(contentLength: Long): List<Pair<String, String>> {
        require(contentLength >= 0L) { "Content length must be non-negative" }
        return listOf(
            "Content-Type" to "text/html; charset=utf-8",
            "Content-Length" to contentLength.toString(),
            "Cache-Control" to "no-store",
            "Pragma" to "no-cache",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
            "X-Frame-Options" to "DENY",
            "Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
            "Permissions-Policy" to "camera=(), microphone=(), geolocation=()",
            "Connection" to "close"
        )
    }

    private val mimeTypePattern = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")

    fun contentType(value: String): String =
        if (mimeTypePattern.matches(value)) value else "application/octet-stream"

    fun contentDisposition(fileName: String): String {
        // RFC 6266 recommends an ASCII fallback plus RFC 5987 filename*.
        // Encode UTF-8 bytes explicitly; form encoding has different rules.
        val encoded = buildString {
            fileName.toByteArray(Charsets.UTF_8).forEach { byte ->
                val value = byte.toInt() and 0xff
                val allowed = value in 97..122 || value in 65..90 || value in 48..57 ||
                    value.toChar() in "!#$&+-.^_|~" || value == 96
                if (allowed) append(value.toChar())
                else append('%').append(HEX[value ushr 4]).append(HEX[value and 0x0f])
            }
        }
        val fallback = buildString {
            var index = 0
            while (index < fileName.length) {
                val codePoint = fileName.codePointAt(index)
                index += Character.charCount(codePoint)
                when {
                    codePoint in 0x20..0x7e && codePoint != 34 && codePoint != 92 -> append(codePoint.toChar())
                    codePoint == 34 || codePoint == 92 -> { append('\\\\'); append(codePoint.toChar()) }
                    else -> append('_')
                }
            }
        }
        return "Content-Disposition: attachment; filename=\"$fallback\"; filename*=UTF-8''$encoded\r\n"
    }

    /**
     * Headers for an unsatisfied byte range. A 416 response still describes
     * private file metadata, so it receives the same no-cache and sniffing
     * protections as successful file responses.
     */
    fun unsatisfiedRangeHeaders(fileLength: Long): String {
        require(fileLength >= 0L) { "File length must be non-negative" }
        return "Accept-Ranges: bytes\r\n" +
            "Content-Range: bytes */$fileLength\r\n" +
            "Content-Length: 0\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "Connection: close\r\n\r\n"
    }

    /** Headers for a provider URI that disappeared before its download began. */
    fun unavailableFileHeaders(contentLength: Long): List<Pair<String, String>> {
        require(contentLength >= 0L) { "Content length must be non-negative" }
        return listOf(
            "Content-Type" to "text/plain; charset=utf-8",
            "Content-Length" to contentLength.toString(),
            "Cache-Control" to "no-store",
            "Pragma" to "no-cache",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
            "Connection" to "close"
        )
    }

    /**
     * Prevent browsers and intermediary caches from retaining private local-share
     * content, and prevent MIME sniffing of provider-supplied file types.
     */
    fun fileHeaders(mimeType: String, fileName: String): String =
        "Content-Type: ${contentType(mimeType)}\r\n" +
            contentDisposition(fileName) +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Referrer-Policy: no-referrer\r\n"
}
