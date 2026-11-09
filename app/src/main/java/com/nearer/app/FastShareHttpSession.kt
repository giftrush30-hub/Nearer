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

        var rangeHeader: String? = null
        var sawRangeHeader = false
        var sawHostHeader = false
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
                // A Host field is a single authority value, not a comma-list.
                // Reject duplicates and empty/whitespace-only authorities so
                // different HTTP components cannot disagree about the target host.
                val host = headerValue.trim()
                if (sawHostHeader || host.isEmpty() || host.any { it.isWhitespace() }) {
                    return rejectBadRequest(output)
                }
                sawHostHeader = true
            }
            if (name.equals("Range", ignoreCase = true)) {
                if (sawRangeHeader) return rejectBadRequest(output)
                sawRangeHeader = true
                rangeHeader = headerValue.trim()
            }
        }
        // RFC byte ranges modify GET responses; HEAD must describe the full representation.
        return FastShareHttpExchange(request, if (request.isHeadOnly) null else rangeHeader)
    }

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
    }}
