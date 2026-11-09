package com.nearer.app

import java.net.URLEncoder

/** Builds safe HTTP header values for metadata supplied by Android file providers. */
internal object FileHttpHeaders {

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
        val encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        return "Content-Disposition: attachment; filename*=UTF-8''$encoded\r\n"
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
