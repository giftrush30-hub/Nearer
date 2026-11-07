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
 * Reads one raw HTTP/1.x request from a stream and writes protocol-level errors.
 * Kept free of Android and sockets so the exact wire contract can be tested.
 */
internal object FastShareHttpSession {
    private const val MAX_LINE_BYTES = 8192

    fun readRequest(input: InputStream, output: OutputStream): FastShareHttpExchange? {
        val requestLine = readLine(input) ?: return null
        if (requestLine.overflow) {
            output.write(FastShareHttpErrors.badRequest())
            output.flush()
            return null
        }

        val request = FastShareHttpRequest.parse(requestLine.value)
        if (request == null) {
            output.write(FastShareHttpErrors.badRequest())
            output.flush()
            return null
        }
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
        while (true) {
            val header = readLine(input) ?: break
            if (header.overflow) {
                output.write(FastShareHttpErrors.badRequest())
                output.flush()
                return null
            }
            if (header.value.isEmpty()) break
            val separator = header.value.indexOf(':')
            if (separator > 0 && header.value.substring(0, separator).equals("Range", ignoreCase = true)) {
                rangeHeader = header.value.substring(separator + 1).trim()
            }
        }
        return FastShareHttpExchange(request, rangeHeader)
    }

    private data class Line(val value: String, val overflow: Boolean)

    private fun readLine(input: InputStream): Line? {
        val bytes = ByteArrayOutputStream()
        var terminated = false
        while (bytes.size() <= MAX_LINE_BYTES) {
            val value = input.read()
            if (value < 0) break
            if (value == 10) {
                terminated = true
                break
            }
            if (value != 13) bytes.write(value)
        }
        if (bytes.size() > MAX_LINE_BYTES) return Line("", true)
        if (bytes.size() == 0 && !terminated) return null
        return Line(bytes.toString("UTF-8"), false)
    }
}
