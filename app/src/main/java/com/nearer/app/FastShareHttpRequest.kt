package com.nearer.app

/** Parsed request-line data kept independent of sockets for deterministic tests. */
internal data class FastShareHttpRequest(
    val method: String,
    val path: String,
    val version: String
) {
    val isHeadOnly: Boolean get() = method == "HEAD"
    val supportsMethod: Boolean get() = method == "GET" || isHeadOnly

    val route: Route
        get() = when (path) {
            "/", "/index.html" -> Route.RECEIVER_PAGE
            "/download" -> Route.DOWNLOAD
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
                version = version
            )
        }
    }
}
