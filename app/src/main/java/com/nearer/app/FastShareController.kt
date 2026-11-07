package com.nearer.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Temporary, local-only hotspot sharing. The file is streamed from ContentResolver;
 * it is never loaded into memory, so large videos do not require a second full copy.
 */
class FastShareController(private val context: Context) {
    var status by androidx.compose.runtime.mutableStateOf("Choose a video or file to start Fast Share")
        private set
    var details by androidx.compose.runtime.mutableStateOf<FastShareDetails?>(null)
        private set

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var callback: WifiManager.LocalOnlyHotspotCallback? = null
    private var server: FastShareHttpServer? = null
    private var pendingUri: Uri? = null

    fun start(uri: Uri) {
        stop(updateStatus = false)
        pendingUri = uri
        status = "Preparing a private Wi-Fi connection…"
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi == null) {
            status = "This phone does not provide Android Wi-Fi hotspot sharing."
            return
        }
        val hotspotCallback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                reservation = value
                val config = value.wifiConfiguration
                val ssid = config?.SSID?.trim('"').orEmpty()
                val password = config?.preSharedKey.orEmpty()
                val selectedUri = pendingUri
                if (selectedUri == null || ssid.isBlank() || password.isBlank()) {
                    status = "Android started Wi-Fi, but did not provide share details. Stop and try again."
                    return
                }
                try {
                    val name = queryDisplayName(selectedUri)
                    val mime = context.contentResolver.getType(selectedUri) ?: "application/octet-stream"
                    val descriptorLength = context.contentResolver.openFileDescriptor(selectedUri, "r")?.use { it.statSize } ?: -1L
                    val length = if (descriptorLength >= 0L) descriptorLength else {
                        context.contentResolver.query(
                            selectedUri,
                            arrayOf(android.provider.OpenableColumns.SIZE),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                            } else -1L
                        } ?: -1L
                    }
                    val httpServer = FastShareHttpServer(context, selectedUri, name, mime, length)
                    val address = httpServer.start()
                    server = httpServer
                    details = FastShareDetails(ssid, password, "http://$address:8765")
                    status = "Fast Share is ready. Keep NeaRer open while the other phone downloads."
                } catch (error: Exception) {
                    status = "Could not start the download page: ${error.message ?: "please try again"}"
                    stop(updateStatus = false)
                }
            }

            override fun onStopped() {
                server?.stop()
                server = null
                reservation = null
                details = null
                status = "The temporary Wi-Fi connection stopped."
            }

            override fun onFailed(reason: Int) {
                status = when (reason) {
                    ERROR_NO_CHANNEL -> "Wi-Fi is busy. Close other hotspot sharing and try again."
                    ERROR_INCOMPATIBLE_MODE -> "Turn off your phone's personal hotspot, then try Fast Share again."
                    ERROR_TETHERING_DISALLOWED -> "Android or your phone provider has disabled local hotspot sharing."
                    else -> "Android could not start Fast Share (code $reason). Check Wi-Fi and try again."
                }
                details = null
            }
        }
        callback = hotspotCallback
        try {
            wifi.startLocalOnlyHotspot(hotspotCallback, Handler(Looper.getMainLooper()))
        } catch (error: SecurityException) {
            status = "Allow NeaRer nearby Wi-Fi permission, then try again."
        } catch (error: Exception) {
            status = "Could not start Fast Share: ${error.message ?: "please try again"}"
        }
    }

    fun stop(updateStatus: Boolean = true) {
        server?.stop()
        server = null
        details = null
        pendingUri = null
        try { reservation?.close() } catch (_: Exception) {}
        reservation = null
        callback = null
        if (updateStatus) status = "Fast Share stopped."
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: "Shared file"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Shared file"
    }
}

data class FastShareDetails(val ssid: String, val password: String, val url: String)

/** Small HTTP server with byte-range support; streams from the selected content URI. */
private class FastShareHttpServer(
    private val context: Context,
    private val uri: Uri,
    private val fileName: String,
    private val mimeType: String,
    private val fileLength: Long
) {
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var running = true
    private var socket: ServerSocket? = null

    fun start(): String {
        socket = ServerSocket(8765, 8, java.net.InetAddress.getByName("0.0.0.0"))
        executor.execute {
            while (running) {
                try {
                    val client = socket?.accept() ?: break
                    executor.execute { handle(client) }
                } catch (_: Exception) {
                    if (running) { /* allow the accept loop to retry */ }
                }
            }
        }
        return findHotspotAddress()
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
    }

    private fun findHotspotAddress(): String {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        val candidates = interfaces.flatMap { network ->
            Collections.list(network.inetAddresses).filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.hostAddress?.startsWith("192.168.") == true }
                .map { it.hostAddress ?: "" }
        }
        return candidates.firstOrNull { it.endsWith(".1") } ?: candidates.firstOrNull()
            ?: "192.168.43.1"
    }

    private fun handle(client: Socket) {
        client.use { connection ->
            connection.soTimeout = 15000
            val input = BufferedInputStream(connection.getInputStream())
            val output = BufferedOutputStream(connection.getOutputStream())
            val request = readLine(input) ?: return
            val parts = request.split(" ")
            if (parts.size < 2) return
            val path = parts[1].substringBefore('?')
            var rangeHeader: String? = null
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", true)) rangeHeader = line.substringAfter(':').trim()
            }
            if (path == "/" || path == "/index.html") {
                val safeName = escapeHtml(fileName)
                val page = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>NeaRer Fast Share</title><style>body{font-family:Arial,sans-serif;background:#101a2b;color:#f6f8fc;padding:28px;max-width:520px;margin:auto}main{background:#1a2940;padding:24px;border-radius:22px}a{display:block;background:#69e0c0;color:#101a2b;text-decoration:none;font-weight:bold;text-align:center;padding:16px;border-radius:14px;margin-top:22px}small{color:#a8b6c9}</style></head><body><main><h2>NeaRer Fast Share</h2><p>Your file is ready.</p><h3>$safeName</h3><small>Download directly over this local Wi-Fi connection. Mobile data is not used.</small><a href="/download">Download file</a></main></body></html>"""
                val bytes = page.toByteArray(Charsets.UTF_8)
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(bytes)
            } else if (path == "/download") {
                streamFile(output, rangeHeader)
            } else {
                output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
            output.flush()
        }
    }

    private fun streamFile(output: BufferedOutputStream, rangeHeader: String?) {
        if (fileLength < 0) {
            output.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        var start = 0L
        var end = fileLength - 1
        var partial = false
        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=").substringBefore(',')
            val bounds = range.split("-", limit = 2)
            val first = bounds.getOrNull(0)?.toLongOrNull()
            val last = bounds.getOrNull(1)?.toLongOrNull()
            when {
                bounds.size != 2 || (first == null && last == null) -> {
                    output.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$fileLength\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    return
                }
                first == null -> {
                    val suffixLength = last ?: 0L
                    if (suffixLength <= 0L || fileLength <= 0L) {
                        output.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$fileLength\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        return
                    }
                    start = (fileLength - suffixLength).coerceAtLeast(0L)
                }
                else -> {
                    start = first
                    end = last ?: end
                }
            }
            if (start < 0L || start >= fileLength || end < start) {
                output.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$fileLength\r\nContent-Length: 0\r\n\r\n".toByteArray())
                return
            }
            end = minOf(end, fileLength - 1)
            partial = true
        }
        val count = end - start + 1
        val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        val statusLine = if (partial) "206 Partial Content" else "200 OK"
        output.write("HTTP/1.1 $statusLine\r\nContent-Type: $mimeType\r\nContent-Length: $count\r\nAccept-Ranges: bytes\r\nContent-Disposition: attachment; filename*=UTF-8''$encodedName\r\n".toByteArray())
        if (partial) output.write("Content-Range: bytes $start-$end/$fileLength\r\n".toByteArray())
        output.write("Connection: close\r\n\r\n".toByteArray())
        context.contentResolver.openInputStream(uri)?.use { stream ->
            skipFully(stream, start)
            val buffer = ByteArray(64 * 1024)
            var remaining = count
            while (remaining > 0 && running) {
                val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        } ?: throw IllegalStateException("Cannot read the selected file")
    }

    private fun skipFully(stream: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (stream.read() < 0) throw IllegalStateException("Cannot seek to requested file section")
            else remaining--
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < 8192) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
            if (value == 10) break
            if (value != 13) bytes.write(value)
        }
        return bytes.toString("UTF-8")
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}
