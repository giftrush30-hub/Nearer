package com.nearer.app

/** Parsed request-line data kept independent of sockets for deterministic tests. */
internal data class FastShareHttpRequest(
    val method: String,
    val path: String,
    val version: String,
    val query: String = ""
) {
    fun hasAccessKey(expected: String): Boolean {
        val values = query.split('&').mapNotNull { part ->
            if (part.startsWith("key=")) part.substringAfter("key=") else null
        }
        return values.size == 1 && FastShareAccessKey.matches(expected, values.single())
    }
    val isHeadOnly: Boolean get() = method == "HEAD"
    val supportsMethod: Boolean get() = method == "GET" || isHeadOnly

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
            path == "/download" -> Route.DOWNLOAD
            path.matches(Regex("/download/[0-9]{1,4}")) && downloadIndex != null -> Route.DOWNLOAD
            else -> Route.NOT_FOUND
        }

    internal enum class Route { RECEIVER_PAGE, DOWNLOAD, NOT_FOUND }

    companion object {
        /** Returns null unless the request line uses the exact HTTP/1.x SP-delimited form. */
        fun parse(requestLine: String): FastShareHttpRequest? {
            // HTTP request-line is: method SP request-target SP HTTP-version.
            // Do not normalize whitespace: accepting tabs, repeated spaces, or
            // leading/trailing spaces can make different parsers see different requests.
            val firstSpace = requestLine.indexOf(' ')
            if (firstSpace <= 0) return null
            val secondSpace = requestLine.indexOf(' ', firstSpace + 1)
            if (secondSpace <= firstSpace + 1) return null
            if (requestLine.indexOf(' ', secondSpace + 1) >= 0) return null

            val method = requestLine.substring(0, firstSpace)
            val target = requestLine.substring(firstSpace + 1, secondSpace)
            val version = requestLine.substring(secondSpace + 1)
            if (!method.matches(Regex("[A-Za-z!#$%&'*+.^_`|~0-9-]+"))) return null
            if (!target.startsWith("/") || target.any { it <= ' ' || it.code >= 127 }) return null
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
