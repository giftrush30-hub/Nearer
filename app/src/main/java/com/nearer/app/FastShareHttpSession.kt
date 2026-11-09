package com.nearer.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** One parsed HTTP request with optional download-range or upload-body metadata. */
internal data class FastShareHttpExchange(
    val request: FastShareHttpRequest,
    val rangeHeader: String?,
    val ifRangeHeader: String? = null,
    val contentLength: Long = 0L,
    val contentType: String? = null
)

/**
 * Reads one bounded HTTP/1.x request from a stream and writes protocol-level errors.
 * Kept free of Android and sockets so the exact wire contract can be tested.
 */
internal object FastShareHttpSession {
    private const val MAX_LINE_BYTES = 8192
    private const val MAX_HEADER_BYTES = 32768
    private const val MAX_HEADER_COUNT = 100

    fun readRequest(input: InputStream, output: OutputStream): FastShareHttpExchange? {
        val requestLine = readLine(input)
        if (requestLine == null || !requestLine.terminated || requestLine.overflow) {
            return rejectBadRequest(output)
        }

        val request = FastShareHttpRequest.parse(requestLine.value)
            ?: return rejectBadRequest(output)
        var rangeHeader: String? = null
        var ifRangeHeader: String? = null
        var sawRangeHeader = false
        var sawIfRangeHeader = false
        var sawHostHeader = false
        var contentLength: Long? = null
        var contentType: String? = null
        var sawContentLengthHeader = false
        var sawContentTypeHeader = false
        var headerBytes = 0
        var headerCount = 0
        while (true) {
            val header = readLine(input) ?: return rejectBadRequest(output)
            if (!header.terminated || header.overflow) return rejectBadRequest(output)

            headerBytes += header.byteCount + 2
            if (headerBytes > MAX_HEADER_BYTES) return rejectBadRequest(output)
            if (header.value.isEmpty()) break

            headerCount++
            if (headerCount > MAX_HEADER_COUNT) return rejectBadRequest(output)

            val separator = header.value.indexOf(':')
            if (separator <= 0) return rejectBadRequest(output)
            val name = header.value.substring(0, separator)
            if (name.any { !isTokenCharacter(it) }) return rejectBadRequest(output)
            val headerValue = header.value.substring(separator + 1)
            if (headerValue.any { (it.code < 0x20 && it != '\t') || it.code == 0x7f }) {
                return rejectBadRequest(output)
            }
            if (name.equals("Host", ignoreCase = true)) {
                // Host is an HTTP authority, not a URL or a list of authorities.
                // Restrict it to visible ASCII and reject URI delimiters so
                // malformed values cannot be interpreted differently by clients.
                val host = headerValue.trim()
                if (sawHostHeader || !isValidHostAuthority(host)) {
                    return rejectBadRequest(output)
                }
                sawHostHeader = true
            }
            if (name.equals("Transfer-Encoding", ignoreCase = true)) {
                // Chunked request bodies are unsupported; uploads must use one exact Content-Length.
                return rejectBadRequest(output)
            }
            if (name.equals("Content-Length", ignoreCase = true)) {
                // GET/HEAD requests must be bodyless; PUT uploads declare their exact body length.
                if (sawContentLengthHeader) return rejectBadRequest(output)
                sawContentLengthHeader = true
                val length = headerValue.trim()
                val parsedLength = if (length.isNotEmpty() && length.all { it in '0'..'9' }) length.toLongOrNull() else null
                if (parsedLength == null || parsedLength < 0L) return rejectBadRequest(output)
                contentLength = parsedLength
            }
            if (name.equals("Content-Type", ignoreCase = true)) {
                if (sawContentTypeHeader) return rejectBadRequest(output)
                sawContentTypeHeader = true
                contentType = headerValue.trim().takeIf { it.isNotEmpty() }
            }
            if (name.equals("Range", ignoreCase = true)) {
                if (sawRangeHeader) return rejectBadRequest(output)
                sawRangeHeader = true
                rangeHeader = headerValue.trim()
            }
            if (name.equals("If-Range", ignoreCase = true)) {
                if (sawIfRangeHeader) return rejectBadRequest(output)
                sawIfRangeHeader = true
                ifRangeHeader = headerValue.trim()
            }
        }
        // HTTP/1.1 requires exactly one Host field. HTTP/1.0 remains compatible
        // with clients that omit it. Validate headers before dispatching even
        // unsupported methods or unknown paths, so malformed requests fail closed.
        if (request.version == "HTTP/1.1" && !sawHostHeader) return rejectBadRequest(output)
        if (!request.supportsMethod) {
            val allow = if (request.route == FastShareHttpRequest.Route.UPLOAD) "PUT" else "GET, HEAD"
            output.write(FastShareHttpErrors.methodNotAllowed(allow))
            output.flush()
            return null
        }
        if (request.route == FastShareHttpRequest.Route.UPLOAD) {
            if (contentLength == null) {
                output.write(FastShareHttpErrors.lengthRequired())
                output.flush()
                return null
            }
            if (rangeHeader != null || ifRangeHeader != null) return rejectBadRequest(output)
        } else if (contentLength != null && contentLength != 0L) {
            return rejectBadRequest(output)
        }
        if (request.route == FastShareHttpRequest.Route.NOT_FOUND) {
            output.write(FastShareHttpErrors.notFound())
            output.flush()
            return null
        }

        // RFC byte ranges modify GET responses; HEAD must describe the full representation.
        // The server decides whether If-Range still matches the selected source file.
        return FastShareHttpExchange(request, if (request.isHeadOnly) null else rangeHeader, ifRangeHeader, contentLength ?: 0L, contentType)
    }

    private fun isValidHostAuthority(value: String): Boolean =
        FastShareHostAuthority.isValid(value)

    private fun isTokenCharacter(value: Char): Boolean =
        value in 'a'..'z' || value in 'A'..'Z' || value in '0'..'9' ||
            value in "!#$%&'*+-.^_`|~"

    private fun rejectBadRequest(output: OutputStream): FastShareHttpExchange? {
        output.write(FastShareHttpErrors.badRequest())
        output.flush()
        return null
    }

    private data class Line(
        val value: String,
        val byteCount: Int,
        val terminated: Boolean,
        val overflow: Boolean
    )

    private fun readLine(input: InputStream): Line? {
        val bytes = ByteArrayOutputStream()
        var terminated = false
        var sawCarriageReturn = false
        while (bytes.size() <= MAX_LINE_BYTES) {
            val value = input.read()
            if (value < 0) break
            if (sawCarriageReturn) {
                // HTTP/1.x lines must end in CRLF. A bare CR or embedded CR
                // is malformed and must not be silently removed from a header.
                if (value == 10) terminated = true
                break
            }
            when (value) {
                13 -> sawCarriageReturn = true
                10 -> break
                else -> bytes.write(value)
            }
            if (terminated) break
        }
        if (bytes.size() > MAX_LINE_BYTES) return Line("", bytes.size(), false, true)
        if (bytes.size() == 0 && !terminated && !sawCarriageReturn) return null
        return Line(bytes.toString("UTF-8"), bytes.size(), terminated, false)
    }
}
