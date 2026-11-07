package com.nearer.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.NetworkInterface
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class OpenSendFile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val file: File,
    val mimeType: String = "application/octet-stream"
)

data class OpenSendServerInfo(
    val port: Int,
    val accessToken: String,
    val localAddresses: List<String>
)

/**
 * Small, dependency-free HTTP server for browser-based OpenSend downloads.
 *
 * Start a LocalOnlyHotspot separately, then start this server. The hotspot's
 * credentials protect the local network; this per-session token protects the
 * download endpoint from other clients on that network. Always close both when
 * the sender stops sharing.
 */
class OpenSendHttpServer(
    files: List<OpenSendFile>,
    private val accessToken: String = createToken(),
    private val bindAddress: InetAddress? = null
) : Closeable {
    private val sharedFiles = files.associateBy { it.id }
    private val running = AtomicBoolean(false)
    private val clients = ThreadPoolExecutor(2, 4, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(12))
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var listener: ServerSocket? = null
    private var acceptThread: Thread? = null

    init {
        require(sharedFiles.isNotEmpty()) { "Choose at least one file to share." }
        require(sharedFiles.size == files.size) { "Each shared file must have a unique ID." }
        require(accessToken.length >= 24) { "The OpenSend access token is too short." }
        require(files.all { it.file.isFile && it.file.canRead() }) { "A selected file is no longer available." }
    }

    @Synchronized
    fun start(): OpenSendServerInfo {
        check(!running.get()) { "OpenSend is already running." }
        val server = ServerSocket(0, 16, bindAddress)
        listener = server
        running.set(true)
        acceptThread = Thread({
            while (running.get()) {
                try {
                    val socket = server.accept()
                    if (!running.get()) { socket.close(); break }
                    activeSockets.add(socket)
                    try {
                        clients.execute { handleClient(socket) }
                    } catch (_: RejectedExecutionException) {
                        activeSockets.remove(socket)
                        try { socket.close() } catch (_: Exception) { }
                    }
                } catch (_: Exception) {
                    if (running.get()) close()
                }
            }
        }, "nearer-opensend-accept").apply { isDaemon = true; start() }

        return OpenSendServerInfo(
            port = server.localPort,
            accessToken = accessToken,
            localAddresses = localIpv4Addresses()
        )
    }

    fun landingPath(): String = "/?token=$accessToken"

    private fun handleClient(socket: Socket) {
        try {
            socket.use { client ->
            client.soTimeout = 15_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            try {
                val requestLine = readAsciiLine(input, 8_192) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) {
                    respond(output, 400, "Bad Request", "Invalid request.")
                    return
                }
                val method = parts[0]
                if (method != "GET" && method != "HEAD") {
                    respond(output, 405, "Method Not Allowed", "Only GET and HEAD are supported.", "Allow: GET, HEAD\r\n")
                    return
                }
                val target = parts[1]
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readAsciiLine(input, 8_192) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
                val path = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))
                if (!secureEquals(query["token"].orEmpty(), accessToken)) {
                    respond(output, 403, "Forbidden", "This NeaRer share link is invalid or has expired.")
                    return
                }
                if (path == "/" || path == "/index.html") {
                    respond(output, 200, "OK", landingPage(), "Content-Type: text/html; charset=utf-8\r\n", headOnly = method == "HEAD")
                    return
                }
                if (path.startsWith("/download/")) {
                    val id = path.removePrefix("/download/")
                    val item = sharedFiles[id]
                    if (item == null || !item.file.isFile || !item.file.canRead()) {
                        respond(output, 404, "Not Found", "This shared file is no longer available.")
                        return
                    }
                    sendFile(output, item, headers["range"], method == "HEAD")
                    return
                }
                respond(output, 404, "Not Found", "Page not found.")
            } catch (_: Exception) {
                // A receiver may close the browser or move out of range mid-download.
            }
            }
        } finally {
            activeSockets.remove(socket)
        }
    }

    private fun landingPage(): String {
        val includesApk = sharedFiles.values.any { it.file.extension.equals("apk", ignoreCase = true) }
        val rows = sharedFiles.values.joinToString("\n") { item ->
            val name = htmlEscape(item.displayName)
            val size = humanSize(item.file.length())
            val href = "/download/${item.id}?token=$accessToken"
            "<li><div><strong>$name</strong><small>$size</small></div><a href=\"$href\">Download</a></li>"
        }
        val title = if (includesApk) "Android apps shared with you" else "Files shared with you"
        val intro = if (includesApk) {
            "Download the APK file to an Android phone, then open it from Downloads. Android may ask you to allow your browser to install unknown apps. iPhone and iPad cannot install Android APK files."
        } else {
            "Choose a file below to download it to this device. No NeaRer installation is required."
        }
        return """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="dark">
            <title>NeaRer OpenSend</title>
            <style>
            *{box-sizing:border-box}body{margin:0;background:#0d1728;color:#f6f8fc;font:16px system-ui,sans-serif}
            main{max-width:620px;margin:0 auto;padding:32px 20px}
            .brand{color:#f4c64e;font-weight:800;letter-spacing:.12em;font-size:13px}
            h1{font-size:30px;margin:18px 0 8px}p{color:#aebbd0;line-height:1.5}
            ul{list-style:none;padding:0;margin:28px 0}li{display:flex;align-items:center;justify-content:space-between;gap:16px;
            background:#182943;border:1px solid #2b405d;border-radius:18px;padding:18px;margin:12px 0}
            strong{display:block;overflow-wrap:anywhere}small{display:block;color:#aebbd0;margin-top:5px}
            a{background:#f4c64e;color:#172338;text-decoration:none;font-weight:800;padding:11px 16px;border-radius:12px;white-space:nowrap}
            .notice{background:#26364d;border-left:3px solid #f4c64e;border-radius:10px;padding:12px}
            footer{color:#8393aa;font-size:12px;margin-top:30px}
            </style></head><body><main><div class="brand">NEARER OPENSEND</div>
            <h1>$title</h1><p>$intro</p>
            ${if (includesApk) "<p class=\"notice\">Only install apps from people and sources you trust. Android will display its own install confirmation; review the app before accepting.</p>" else ""}
            <ul>$rows</ul><footer>Shared locally by NeaRer. This private link works only while the sender keeps sharing.</footer>
            </main></body></html>
        """.trimIndent()
    }

    private fun sendFile(output: BufferedOutputStream, item: OpenSendFile, rangeHeader: String?, headOnly: Boolean) {
        val length = item.file.length()
        val range = parseRange(rangeHeader, length)
        if (rangeHeader != null && range == null) {
            val body = "Requested byte range is not available."
            writeHeaders(output, 416, "Range Not Satisfiable", body.toByteArray().size.toLong(),
                "Content-Type: text/plain; charset=utf-8\r\nContent-Range: bytes */$length\r\n")
            if (!headOnly) output.write(body.toByteArray())
            output.flush()
            return
        }
        val start = range?.first ?: 0L
        val end = range?.last ?: (length - 1L)
        val count = if (length == 0L) 0L else end - start + 1L
        val status = if (range != null) 206 else 200
        val reason = if (range != null) "Partial Content" else "OK"
        val encodedName = URLEncoder.encode(item.displayName, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val extra = buildString {
            append("Content-Type: ${safeMime(item.mimeType)}\r\n")
            append("Content-Disposition: attachment; filename=\"download\"; filename*=UTF-8''$encodedName\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (range != null) append("Content-Range: bytes $start-$end/$length\r\n")
        }
        writeHeaders(output, status, reason, count, extra)
        if (!headOnly && count > 0L) {
            RandomAccessFile(item.file, "r").use { source ->
                source.seek(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = count
                while (remaining > 0L) {
                    val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        output.flush()
    }

    private fun parseRange(header: String?, length: Long): LongRange? {
        if (header == null) return null
        if (length <= 0L || !header.startsWith("bytes=")) return null
        val value = header.removePrefix("bytes=").substringBefore(',').trim()
        val pieces = value.split('-', limit = 2)
        if (pieces.size != 2) return null
        return try {
            val start: Long
            val end: Long
            if (pieces[0].isBlank()) {
                val suffix = pieces[1].toLong()
                if (suffix <= 0L) return null
                start = (length - suffix).coerceAtLeast(0L)
                end = length - 1L
            } else {
                start = pieces[0].toLong()
                end = if (pieces[1].isBlank()) length - 1L else pieces[1].toLong()
            }
            if (start < 0L || start >= length || end < start) null
            else start..end.coerceAtMost(length - 1L)
        } catch (_: NumberFormatException) { null }
    }

    private fun respond(
        output: BufferedOutputStream,
        status: Int,
        reason: String,
        body: String,
        extraHeaders: String = "Content-Type: text/plain; charset=utf-8\r\n",
        headOnly: Boolean = false
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writeHeaders(output, status, reason, bytes.size.toLong(), extraHeaders)
        if (!headOnly) output.write(bytes)
        output.flush()
    }

    private fun writeHeaders(output: BufferedOutputStream, status: Int, reason: String, length: Long, extra: String = "") {
        val headers = "HTTP/1.1 $status $reason\r\n" +
            "Content-Length: $length\r\n" +
            "Connection: close\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            extra + "\r\n"
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun readAsciiLine(input: BufferedInputStream, maxBytes: Int): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size < maxBytes) {
            val value = input.read()
            if (value < 0) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            if (value == 10) break
            if (value != 13) bytes += value.toByte()
        }
        return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').filter { it.isNotBlank() }.mapNotNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            try {
                URLDecoder.decode(key, StandardCharsets.UTF_8.name()) to URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            } catch (_: Exception) { null }
        }.toMap()

    private fun secureEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8))

    private fun localIpv4Addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().asSequence().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence().toList() }
            .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
            .map { it.hostAddress.orEmpty() }
            .distinct()
    } catch (_: Exception) { emptyList() }

    private fun htmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun safeMime(value: String): String =
        value.takeIf { it.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) }
            ?: "application/octet-stream"

    @Synchronized
    override fun close() {
        if (!running.getAndSet(false)) return
        try { listener?.close() } catch (_: Exception) { }
        listener = null
        activeSockets.toList().forEach { socket -> try { socket.close() } catch (_: Exception) { } }
        activeSockets.clear()
        clients.shutdownNow()
    }

    companion object {
        private fun createToken(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
