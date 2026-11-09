package com.nearer.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** One parsed HTTP request and the range header relevant to file downloads. */
internal data class FastShareHttpExchange(
    val request: FastShareHttpRequest,
    val rangeHeader: String?
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
        var sawRangeHeader = false
        var sawIfRangeHeader = false
        var sawHostHeader = false
        var sawContentLengthHeader = false
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
                // This receiver never consumes request bodies or chunk framing.
                return rejectBadRequest(output)
            }
            if (name.equals("Content-Length", ignoreCase = true)) {
                // GET/HEAD requests have no body in this protocol. Accept zero only.
                if (sawContentLengthHeader) return rejectBadRequest(output)
                sawContentLengthHeader = true
                val length = headerValue.trim()
                if (length.isEmpty() || length.any { it !in '0'..'9' } ||
                    length.toLongOrNull() != 0L
                ) return rejectBadRequest(output)
            }
            if (name.equals("Range", ignoreCase = true)) {
                if (sawRangeHeader) return rejectBadRequest(output)
                sawRangeHeader = true
                rangeHeader = headerValue.trim()
            }
            if (name.equals("If-Range", ignoreCase = true)) {
                // This server does not emit ETag or Last-Modified validators.
                // Therefore no If-Range value can be proven current: RFC 9110
                // requires sending the full representation rather than risking
                // a partial append to a changed source file.
                if (sawIfRangeHeader) return rejectBadRequest(output)
                sawIfRangeHeader = true
            }
        }
        // HTTP/1.1 requires exactly one Host field. HTTP/1.0 remains compatible
        // with clients that omit it. Validate headers before dispatching even
        // unsupported methods or unknown paths, so malformed requests fail closed.
        if (request.version == "HTTP/1.1" && !sawHostHeader) return rejectBadRequest(output)
        if (!request.supportsMethod) {
            output.write(FastShareHttpErrors.methodNotAllowed())
            output.flush()
            return null
        }
        if (request.route == FastShareHttpRequest.Route.NOT_FOUND) {
            output.write(FastShareHttpErrors.notFound())
            output.flush()
            return null
        }

        // RFC byte ranges modify GET responses; HEAD must describe the full representation.
        return FastShareHttpExchange(request, if (request.isHeadOnly || sawIfRangeHeader) null else rangeHeader)
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
