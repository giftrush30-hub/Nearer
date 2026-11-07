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
        /** Returns null for malformed request lines; method matching is case-insensitive. */
        fun parse(requestLine: String): FastShareHttpRequest? {
            val parts = requestLine.trim().split(Regex("\\s+"))
            if (parts.size != 3) return null

            val method = parts[0]
            val target = parts[1]
            val version = parts[2]
            if (!method.matches(Regex("[A-Za-z!#$%&'*+.^_`|~0-9-]+"))) return null
            if (!target.startsWith("/") || target.any { it <= ' ' || it.code == 127 }) return null
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
