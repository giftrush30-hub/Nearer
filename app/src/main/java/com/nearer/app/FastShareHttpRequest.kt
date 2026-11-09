package com.nearer.app

/** Parsed request-line data kept independent of sockets for deterministic tests. */
internal data class FastShareHttpRequest(
    val method: String,
    val path: String,
    val version: String,
    val query: String = ""
) {
    fun hasAccessKey(expected: String): Boolean {
        // Decode parameter names before counting them. Otherwise an encoded
        // spelling such as %6Bey could create ambiguous duplicate key fields
        // that a browser, proxy, or future parser might interpret differently.
        val values = mutableListOf<String>()
        for (part in query.split('&')) {
            val separator = part.indexOf('=')
            val rawName = if (separator >= 0) part.substring(0, separator) else part
            val decodedName = try {
                java.net.URLDecoder.decode(rawName, Charsets.UTF_8.name())
            } catch (_: IllegalArgumentException) {
                return false
            }
            if (decodedName == "key") {
                if (separator < 0) return false
                values += part.substring(separator + 1)
            }
        }
        return values.size == 1 && FastShareAccessKey.matches(expected, values.single())
    }
    val isHeadOnly: Boolean get() = method == "HEAD"
    val isUpload: Boolean get() = method == "POST" && path == "/upload"
    val supportsMethod: Boolean get() = method == "GET" || isHeadOnly || isUpload

    /** Zero-based item requested by /download/N; legacy /download means item zero. */
    val downloadIndex: Int?
        get() = when {
            path == "/download" -> 0
            path.startsWith("/download/") -> path.removePrefix("/download/").toIntOrNull()
            else -> null
        }

    val route: Route
        get() = when {
            path == "/" || path == "/index.html" -> Route.RECEIVER_PAGE
            path == "/upload" && isUpload -> Route.UPLOAD
            path == "/download" -> Route.DOWNLOAD
            path.matches(Regex("/download/[0-9]{1,4}")) && downloadIndex != null -> Route.DOWNLOAD
            else -> Route.NOT_FOUND
        }

    internal enum class Route { RECEIVER_PAGE, DOWNLOAD, UPLOAD, NOT_FOUND }

    companion object {
        /**
         * Parses the exact HTTP request-line grammar: method SP target SP version.
         * Be deliberately strict here; accepting tabs or collapsed whitespace can
         * make different HTTP parsers disagree about where the request starts/ends.
         */
        fun parse(requestLine: String): FastShareHttpRequest? {
            val firstSpace = requestLine.indexOf(' ')
            if (firstSpace <= 0) return null
            val secondSpace = requestLine.indexOf(' ', firstSpace + 1)
            if (secondSpace <= firstSpace + 1 || secondSpace == requestLine.lastIndex) return null
            if (requestLine.indexOf(' ', secondSpace + 1) >= 0) return null

            val method = requestLine.substring(0, firstSpace)
            val target = requestLine.substring(firstSpace + 1, secondSpace)
            val version = requestLine.substring(secondSpace + 1)
            if (!method.matches(Regex("[A-Za-z!#$%&'*+.^_|~0-9-]+"))) return null
            if (!target.startsWith("/") ||
                target.any { it.code !in 0x21..0x7e } ||
                target.contains('#')
            ) return null
            if (version != "HTTP/1.0" && version != "HTTP/1.1") return null

            return FastShareHttpRequest(
                method = method.uppercase(java.util.Locale.ROOT),
                path = target.substringBefore('?'),
                version = version,
                query = target.substringAfter('?', "")
            )
        }
    }
}
