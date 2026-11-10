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

    fun methodNotAllowed(): ByteArray =
        response("405 Method Not Allowed", "Allow: GET, HEAD, POST\r\n")

    fun tooManyRequests(): ByteArray =
        response(
            "429 Too Many Requests",
            "Retry-After: 2\r\nContent-Type: text/plain; charset=utf-8\r\n",
            "Another download is already in progress. Wait briefly, then retry.\n"
        )

    fun insufficientStorage(): ByteArray = response("507 Insufficient Storage", "Content-Type: text/plain; charset=utf-8\r\n", "Not enough free storage.\n")

    fun uploadBusy(): ByteArray =
        response("429 Too Many Requests", "Retry-After: 2\r\nContent-Type: text/plain; charset=utf-8\r\n", "Two uploads are already active. Wait briefly, then retry.\n")

    fun requestTimeout(): ByteArray =
        response(
            "408 Request Timeout",
            "Retry-After: 2\r\nContent-Type: text/plain; charset=utf-8\r\n",
            "Upload paused because the connection became idle. Reconnect and retry.\n"
        )

    fun uploadFailed(): ByteArray = response("500 Internal Server Error", "Content-Type: text/plain; charset=utf-8\r\n", "Upload could not be saved.\n")

    fun forbidden(): ByteArray =
        response("403 Forbidden", "Content-Type: text/plain; charset=utf-8\r\n", "This Fast Share link is missing or invalid. Ask the sender for the current link.\n")

    fun notFound(): ByteArray =
        response("404 Not Found")
}
