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
import java.util.concurrent.atomic.AtomicBoolean

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
                    val httpServer = FastShareHttpServer(context, selectedUri, name, mime, length) { sent, total, complete, interrupted ->
                        Handler(Looper.getMainLooper()).post {
                            status = when {
                                interrupted -> "Download interrupted. Keep both phones connected and retry the download."
                                complete -> "Download completed successfully."
                                total >= 0L -> "Sending ${formatProgressSize(sent)} of ${formatProgressSize(total)}…"
                                else -> "Sending ${formatProgressSize(sent)}…"
                            }
                        }
                    }
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

    private fun formatProgressSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value /= 1024.0
            unit++
        } while (value >= 1024.0 && unit < units.lastIndex)
        return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
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
    private val fileLength: Long,
    private val onTransferUpdate: (sentBytes: Long, totalBytes: Long, complete: Boolean, interrupted: Boolean) -> Unit
) {
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var running = true
    private val transferActive = AtomicBoolean(false)
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
            val method = parts[0].uppercase()
            val path = parts[1].substringBefore('?')
            if (method != "GET" && method != "HEAD") {
                output.write("HTTP/1.1 405 Method Not Allowed\r\nAllow: GET, HEAD\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }
            val headOnly = method == "HEAD"
            var rangeHeader: String? = null
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", true)) rangeHeader = line.substringAfter(':').trim()
            }
            if (path == "/" || path == "/index.html") {
                val safeName = escapeHtml(fileName)
                val sizeLabel = if (fileLength >= 0L) formatFileSize(fileLength) else "Size unavailable"
                val page = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#101a2b"><title>NeaRer Fast Share</title><style>*{box-sizing:border-box}body{font-family:Arial,sans-serif;background:radial-gradient(circle at 85% 5%,#29466a 0,transparent 36%),#101a2b;color:#f6f8fc;padding:24px 16px;margin:0;min-height:100vh;display:grid;place-items:center}.card{width:100%;max-width:480px;background:#1a2940;border:1px solid #344963;border-radius:26px;padding:26px;box-shadow:0 18px 50px #07101f88}.brand{color:#69e0c0;font-size:13px;font-weight:800;letter-spacing:2px}.mark{width:52px;height:52px;border-radius:16px;background:#ffd12d;color:#101a2b;display:grid;place-items:center;font-size:28px;font-weight:900;margin:18px 0}.eyebrow{color:#ffd12d;font-size:11px;font-weight:800;letter-spacing:1.5px}h1{font-size:25px;margin:8px 0 12px;overflow-wrap:anywhere}.meta{color:#a8b6c9;font-size:13px;line-height:1.6}.steps{margin:24px 0 8px;padding:16px;background:#101a2b;border-radius:16px;color:#cbd6e5;font-size:13px;line-height:1.7}.download{display:block;background:#69e0c0;color:#101a2b;text-decoration:none;font-weight:800;text-align:center;padding:17px;border-radius:14px;margin-top:20px}.foot{color:#a8b6c9;font-size:11px;text-align:center;margin:18px 0 0}</style></head><body><main class="card"><div class="brand">NEARER</div><div class="mark">N</div><div class="eyebrow">PRIVATE LOCAL TRANSFER</div><h1>$safeName</h1><p class="meta">$sizeLabel · Ready to receive</p><div class="steps"><strong>Almost there.</strong><br>1. Stay connected to the sender's NeaRer Wi-Fi.<br>2. Tap the button below to save your file.<br>3. Keep this page open until the download finishes.</div><a class="download" href="/download">Download file</a><p class="foot">Transferred over local Wi-Fi. Mobile data is not used.</p></main></body></html>"""
                val bytes = page.toByteArray(Charsets.UTF_8)
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                if (!headOnly) output.write(bytes)
            } else if (path == "/download") {
                if (headOnly) {
                    streamFile(output, rangeHeader, true)
                } else if (!transferActive.compareAndSet(false, true)) {
                    output.write("HTTP/1.1 429 Too Many Requests\r\nRetry-After: 2\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: 41\r\nConnection: close\r\n\r\nAnother download is already in progress.\n".toByteArray())
                } else {
                    try {
                        streamFile(output, rangeHeader, false)
                    } finally {
                        transferActive.set(false)
                    }
                }
            } else {
                output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
            output.flush()
        }
    }

    private fun streamFile(output: BufferedOutputStream, rangeHeader: String?, headOnly: Boolean) {
        if (fileLength < 0L) {
            streamUnknownLength(output, headOnly)
            return
        }
        val resolvedRange = ByteRange.resolve(rangeHeader, fileLength)
        if (resolvedRange == null) {
            output.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$fileLength\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        val start = resolvedRange.start
        val end = resolvedRange.endInclusive
        val partial = resolvedRange.partial
        val count = end - start + 1
        val stream = if (headOnly) null else try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
        if (!headOnly && stream == null) {
            writeFileUnavailable(output)
            return
        }
        if (stream != null) {
            try {
                skipFully(stream, start)
            } catch (_: Exception) {
                stream.close()
                writeFileUnavailable(output)
                return
            }
        }
        val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        val statusLine = if (partial) "206 Partial Content" else "200 OK"
        output.write("HTTP/1.1 $statusLine\r\nContent-Type: $mimeType\r\nContent-Length: $count\r\nAccept-Ranges: bytes\r\nContent-Disposition: attachment; filename*=UTF-8''$encodedName\r\n".toByteArray())
        if (partial) output.write("Content-Range: bytes $start-$end/$fileLength\r\n".toByteArray())
        output.write("Connection: close\r\n\r\n".toByteArray())
        if (headOnly) return
        stream?.use { source ->
            val buffer = ByteArray(64 * 1024)
            var remaining = count
            var sent = 0L
            try {
                while (remaining > 0 && running) {
                    val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    remaining -= read
                    sent += read
                    if (sent == count || sent % (1024L * 1024L) < read) {
                        output.flush()
                        onTransferUpdate(sent, count, false, false)
                    }
                }
                output.flush()
                val completed = sent == count && running
                onTransferUpdate(sent, count, completed, !completed)
            } catch (error: Exception) {
                onTransferUpdate(sent, count, false, true)
                throw error
            }
        }
    }

    /**
     * Some Android providers cannot report a file size. Chunked HTTP keeps
     * these files shareable without buffering the entire file in memory.
     * Resume/range requests are not available when the length is unknown.
     */
    private fun streamUnknownLength(output: BufferedOutputStream, headOnly: Boolean) {
        val stream = if (headOnly) null else try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
        if (!headOnly && stream == null) {
            writeFileUnavailable(output)
            return
        }
        val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        output.write("HTTP/1.1 200 OK\\r\\nContent-Type: $mimeType\\r\\nContent-Disposition: attachment; filename*=UTF-8''$encodedName\\r\\nTransfer-Encoding: chunked\\r\\nConnection: close\\r\\n\\r\\n".toByteArray())
        if (headOnly) return
        stream?.use { source ->
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            try {
                while (running) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(Integer.toHexString(read).toByteArray(Charsets.US_ASCII))
                    output.write("\\r\\n".toByteArray(Charsets.US_ASCII))
                    output.write(buffer, 0, read)
                    output.write("\\r\\n".toByteArray(Charsets.US_ASCII))
                    sent += read
                    // Avoid forcing a socket flush for every 64 KB chunk.
                    // Let BufferedOutputStream batch writes and flush at ~1 MB intervals.
                    if (sent % (1024L * 1024L) < read) {
                        output.flush()
                        onTransferUpdate(sent, -1L, false, false)
                    }
                }
                val completed = running
                if (completed) {
                    output.write("0\\r\\n\\r\\n".toByteArray(Charsets.US_ASCII))
                    output.flush()
                }
                onTransferUpdate(sent, -1L, completed, !completed)
            } catch (error: Exception) {
                onTransferUpdate(sent, -1L, false, true)
                throw error
            }
        }
    }

    private fun writeFileUnavailable(output: BufferedOutputStream) {
        onTransferUpdate(0L, fileLength, false, true)
        val message = "The selected file is no longer available. Choose it again in NeaRer."
        val body = message.toByteArray(Charsets.UTF_8)
        output.write("HTTP/1.1 503 Service Unavailable\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        output.write(body)
        output.flush()
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

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value /= 1024.0
            unit++
        } while (value >= 1024.0 && unit < units.lastIndex)
        return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}
