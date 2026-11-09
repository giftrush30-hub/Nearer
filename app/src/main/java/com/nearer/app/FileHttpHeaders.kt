package com.nearer.app

import java.net.URLEncoder

/** Builds safe HTTP header values for metadata supplied by Android file providers. */
internal object FileHttpHeaders {
    private val mimeTypePattern = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")

    fun contentType(value: String): String =
        if (mimeTypePattern.matches(value)) value else "application/octet-stream"

    fun contentDisposition(fileName: String): String {
        val encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        return "Content-Disposition: attachment; filename*=UTF-8''$encoded\r\n"
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
