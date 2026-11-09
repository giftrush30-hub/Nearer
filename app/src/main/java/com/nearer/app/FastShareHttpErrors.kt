package com.nearer.app

/** Small, deterministic HTTP error responses shared by the local Fast Share server. */
internal object FastShareHttpErrors {
    private fun response(status: String, headers: String = "", body: String = ""): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            if (headers.isNotEmpty()) append(headers)
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        return head.toByteArray(Charsets.UTF_8) + bytes
    }

    fun badRequest(): ByteArray =
        response("400 Bad Request")

    fun methodNotAllowed(allow: String = "GET, HEAD"): ByteArray =
        response("405 Method Not Allowed", "Allow: $allow\r\n")

    fun lengthRequired(): ByteArray =
        response("411 Length Required", "Content-Type: text/plain; charset=utf-8\r\n", "Upload requires a valid Content-Length header.\n")

    fun tooManyRequests(): ByteArray =
        response(
            "429 Too Many Requests",
            "Retry-After: 2\r\nContent-Type: text/plain; charset=utf-8\r\n",
            "Another download is already in progress. Wait briefly, then retry.\n"
        )

    fun forbidden(): ByteArray =
        response("403 Forbidden", "Content-Type: text/plain; charset=utf-8\r\n", "This Fast Share link is missing or invalid. Ask the sender for the current link.\n")

    fun notFound(): ByteArray =
        response("404 Not Found")
}
