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
    private var pendingUris: List<Uri> = emptyList()

    fun start(uri: Uri) = start(listOf(uri))

    fun start(uris: List<Uri>) {
        val selectedUris = uris.distinct()
        if (selectedUris.isEmpty()) {
            status = "Choose at least one file to start Fast Share."
            return
        }
        if (selectedUris.size > FastShareDownloadCatalog.MAX_FILES) {
            status = "Choose up to ${FastShareDownloadCatalog.MAX_FILES} files per Fast Share session."
            return
        }
        stop(updateStatus = false)
        pendingUris = selectedUris
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
                val selectedUris = pendingUris
                if (selectedUris.isEmpty() || ssid.isBlank() || password.isBlank()) {
                    status = "Android started Wi-Fi, but did not provide share details. Stop and try again."
                    return
                }
                try {
                    val items = selectedUris.mapIndexed { index, selectedUri ->
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
                        FastShareDownloadItem(index, name, mime, length)
                    }
                    val catalog = FastShareDownloadCatalog(items)
                    var transferStartedAt = 0L
                    var lastProgressAt = 0L
                    var lastProgressBytes = 0L
                    var bytesPerSecond = 0.0
                    val httpServer = FastShareHttpServer(context, selectedUris, catalog) { sent, total, complete, interrupted, partialResponse ->
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (transferStartedAt == 0L || sent < lastProgressBytes) {
                            transferStartedAt = now
                            lastProgressAt = now
                            lastProgressBytes = 0L
                            bytesPerSecond = 0.0
                        }
                        val elapsed = now - lastProgressAt
                        if (elapsed > 0L && sent >= lastProgressBytes) {
                            bytesPerSecond = (sent - lastProgressBytes) * 1000.0 / elapsed
                        }
                        lastProgressAt = now
                        lastProgressBytes = sent
                        val speedLabel = if (bytesPerSecond > 0.0) " · ${formatProgressSize(bytesPerSecond.toLong())}/s" else ""
                        val averageSpeed = formatProgressSize(((sent * 1000.0) / (now - transferStartedAt).coerceAtLeast(1L)).toLong())
                        Handler(Looper.getMainLooper()).post {
                            status = when {
                                interrupted -> "Download interrupted at ${formatProgressSize(sent)}. Keep both phones connected and retry."
                                complete && partialResponse -> "Download section sent · ${formatProgressSize(sent)}"
                                complete -> "Transfer completed · average $averageSpeed/s"
                                total >= 0L -> "Sending ${formatProgressSize(sent)} of ${formatProgressSize(total)}$speedLabel…"
                                else -> "Sending ${formatProgressSize(sent)}$speedLabel…"
                            }
                        }
                        if (complete || interrupted) {
                            transferStartedAt = 0L
                            lastProgressAt = 0L
                            lastProgressBytes = 0L
                            bytesPerSecond = 0.0
                        }
                    }
                    val address = httpServer.start()
                    server = httpServer
                    details = FastShareDetails(ssid, password, "http://$address:8765")
                    status = "Fast Share is ready · ${selectedUris.size} file(s). Keep NeaRer open while the other phone downloads."
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
        pendingUris = emptyList()
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
    private val uris: List<Uri>,
    private val catalog: FastShareDownloadCatalog,
    private val onTransferUpdate: (sentBytes: Long, totalBytes: Long, complete: Boolean, interrupted: Boolean, partialResponse: Boolean) -> Unit
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
            val exchange = FastShareHttpSession.readRequest(input, output) ?: return
            val request = exchange.request
            val headOnly = request.isHeadOnly
            val rangeHeader = exchange.rangeHeader
            when (request.route) {
                FastShareHttpRequest.Route.RECEIVER_PAGE -> {
                    val bytes = catalog.receiverPage().toByteArray(Charsets.UTF_8)
                    output.write(FastShareHttpWire.responseHead("HTTP/1.1 200 OK", listOf("Content-Type" to "text/html; charset=utf-8", "Content-Length" to bytes.size.toString(), "Connection" to "close")))
                    if (!headOnly) output.write(bytes)
                }
                FastShareHttpRequest.Route.DOWNLOAD -> {
                    val index = request.downloadIndex
                    val item = index?.let { catalog.itemAt(it) }
                    val uri = index?.let { uris.getOrNull(it) }
                    if (item == null || uri == null) {
                        output.write(FastShareHttpErrors.notFound())
                    } else if (headOnly) {
                        streamFile(output, uri, item, rangeHeader, true)
                    } else if (!transferActive.compareAndSet(false, true)) {
                        output.write(FastShareHttpErrors.tooManyRequests())
                    } else {
                        try {
                            streamFile(output, uri, item, rangeHeader, false)
                        } finally {
                            transferActive.set(false)
                        }
                    }
                }
                FastShareHttpRequest.Route.NOT_FOUND -> output.write(FastShareHttpErrors.notFound())
            }
            output.flush()
        }
    }

    private fun streamFile(output: BufferedOutputStream, uri: Uri, item: FastShareDownloadItem, rangeHeader: String?, headOnly: Boolean) {
        if (item.fileLength < 0L) {
            streamUnknownLength(output, uri, item, headOnly)
            return
        }
        val response = FileHttpResponses.resolve(rangeHeader, item.fileLength)
        val resolvedRange = response.range
        if (resolvedRange == null) {
            output.write("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\nContent-Range: ${response.contentRange}\r\nContent-Length: ${response.contentLength}\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        val start = resolvedRange.start
        val end = resolvedRange.endInclusive
        val partial = resolvedRange.partial
        val count = response.contentLength
        val stream = if (headOnly) null else try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
        if (!headOnly && stream == null) {
            writeFileUnavailable(output, item)
            return
        }
        if (stream != null) {
            try {
                StreamRangeCopy.skipFully(stream, start)
            } catch (_: Exception) {
                stream.close()
                writeFileUnavailable(output, item)
                return
            }
        }
        // Start the throughput clock before sending the HTTP response or first byte.
        val partialTransfer = ByteRange.isPartialTransfer(resolvedRange, item.fileLength)
        if (!headOnly) onTransferUpdate(0L, count, false, false, partialTransfer)
        output.write("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n".toByteArray())
        output.write(FileHttpHeaders.fileHeaders(item.mimeType, item.fileName).toByteArray())
        output.write("Content-Length: $count\r\nAccept-Ranges: bytes\r\n".toByteArray())
        response.contentRange?.let { output.write("Content-Range: $it\r\n".toByteArray()) }
        output.write("Connection: close\r\n\r\n".toByteArray())
        if (headOnly) return
        stream?.use { source ->
            var sent = 0L
            try {
                val result = StreamCopy.copy(
                    input = source,
                    output = output,
                    expectedBytes = count,
                    shouldContinue = { running },
                    onBytesCopied = { copied -> sent = copied },
                    onProgress = { copied ->
                        onTransferUpdate(copied, count, false, false, partialTransfer)
                    }
                )
                sent = result.bytesCopied
                onTransferUpdate(sent, count, result.complete, !result.complete, partialTransfer)
            } catch (error: Exception) {
                onTransferUpdate(sent, count, false, true, partialTransfer)
                throw error
            }
        }
    }

    /**
     * Some Android providers cannot report a file size. Chunked HTTP keeps
     * these files shareable without buffering the entire file in memory.
     * Resume/range requests are not available when the length is unknown.
     */
    private fun streamUnknownLength(output: BufferedOutputStream, uri: Uri, item: FastShareDownloadItem, headOnly: Boolean) {
        val stream = if (headOnly) null else try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
        if (!headOnly && stream == null) {
            writeFileUnavailable(output, item)
            return
        }
        // Start the throughput clock before the first chunk is read or written.
        if (!headOnly) onTransferUpdate(0L, -1L, false, false, false)
        output.write("HTTP/1.1 200 OK\r\n".toByteArray())
        output.write(FileHttpHeaders.fileHeaders(item.mimeType, item.fileName).toByteArray())
        output.write("Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n".toByteArray())
        if (headOnly) return
        stream?.use { source ->
            var sent = 0L
            try {
                val result = ChunkedStreamCopy.copy(
                    input = source,
                    output = output,
                    shouldContinue = { running },
                    onBytesCopied = { copied -> sent = copied },
                    onProgress = { copied ->
                        onTransferUpdate(copied, -1L, false, false, false)
                    }
                )
                sent = result.bytesCopied
                onTransferUpdate(sent, -1L, result.complete, !result.complete, false)
            } catch (error: Exception) {
                onTransferUpdate(sent, -1L, false, true, false)
                throw error
            }
        }
    }

    private fun writeFileUnavailable(output: BufferedOutputStream, item: FastShareDownloadItem) {
        onTransferUpdate(0L, item.fileLength, false, true, false)
        val message = "The selected file is no longer available. Choose it again in NeaRer."
        val body = message.toByteArray(Charsets.UTF_8)
        output.write("HTTP/1.1 503 Service Unavailable\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        output.write(body)
        output.flush()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}
