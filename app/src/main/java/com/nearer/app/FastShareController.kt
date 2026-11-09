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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Temporary, local-only hotspot sharing. The file is streamed from ContentResolver;
 * it is never loaded into memory, so large videos do not require a second full copy.
 */
class FastShareController(private val context: Context) {
    var status by androidx.compose.runtime.mutableStateOf("Choose a video or file to start Fast Share")
        private set
    var details by androidx.compose.runtime.mutableStateOf<FastShareDetails?>(null)
        private set
    var history by androidx.compose.runtime.mutableStateOf(loadHistory())
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
            pendingUris = emptyList()
            status = "This phone does not provide Android Wi-Fi hotspot sharing."
            return
        }
        val hotspotCallback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                // Android may deliver this callback after the user has stopped or
                // replaced the session. Never resurrect a server for an old request.
                if (callback !== this) {
                    runCatching { value.close() }
                    return
                }
                reservation = value
                val config = value.wifiConfiguration
                val ssid = config?.SSID?.trim('"').orEmpty()
                val password = config?.preSharedKey.orEmpty()
                val selectedUris = pendingUris
                if (selectedUris.isEmpty() || ssid.isBlank() || password.isBlank()) {
                    // Do not leave a hotspot reservation alive when Android cannot
                    // provide the credentials needed by the receiving device.
                    stop(updateStatus = false)
                    status = "Android started Wi-Fi, but did not provide share details. Try Fast Share again."
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
                    val accessKey = FastShareAccessKey.create()
                    // Each HTTP worker owns its own progress clock. Two concurrent
                    // receivers must never overwrite one another's speed/timing state.
                    val progressByThread = ThreadLocal.withInitial { LongArray(4) }
                    val httpServer = FastShareHttpServer(context, selectedUris, catalog, accessKey) { fileName, sent, total, complete, interrupted, partialResponse ->
                        val progress = progressByThread.get()
                        val now = android.os.SystemClock.elapsedRealtime()
                        var transferStartedAt = progress[0]
                        var lastProgressAt = progress[1]
                        var lastProgressBytes = progress[2]
                        var bytesPerSecond = progress[3].toDouble()
                        if ((sent == 0L && !complete && !interrupted) || transferStartedAt == 0L || sent < lastProgressBytes) {
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
                        progress[0] = transferStartedAt
                        progress[1] = lastProgressAt
                        progress[2] = lastProgressBytes
                        progress[3] = bytesPerSecond.toLong()
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
                            if (complete || interrupted) {
                                val result = when {
                                    interrupted -> "Interrupted"
                                    partialResponse -> "Range sent"
                                    else -> "Completed"
                                }
                                recordHistory(fileName, sent, result)
                            }
                        }
                        if (complete || interrupted) {
                            progressByThread.remove()
                        }
                    }
                    val address = httpServer.start()
                    server = httpServer
                    details = FastShareDetails(ssid, password, "http://$address:8765/?key=$accessKey")
                    status = "Fast Share is ready · ${selectedUris.size} file(s). Keep NeaRer open while the other phone downloads."
                } catch (error: Exception) {
                    status = "Could not start the download page: ${error.message ?: "please try again"}"
                    stop(updateStatus = false)
                }
            }

            override fun onStopped() {
                if (callback !== this) return
                server?.stop()
                server = null
                reservation = null
                pendingUris = emptyList()
                details = null
                callback = null
                status = "The temporary Wi-Fi connection stopped."
            }

            override fun onFailed(reason: Int) {
                if (callback !== this) return
                stop(updateStatus = false)
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
            // Clear the pending session as well as the callback so a failed
            // permission attempt cannot leave stale state for the next start.
            stop(updateStatus = false)
            status = "Allow NeaRer nearby Wi-Fi permission, then try again."
        } catch (error: Exception) {
            stop(updateStatus = false)
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

    fun clearHistory() {
        history = emptyList()
        context.getSharedPreferences("nearer_fast_share_history", Context.MODE_PRIVATE)
            .edit().remove("entries").apply()
    }

    private fun recordHistory(fileName: String, bytes: Long, result: String) {
        val entry = FastShareHistoryEntry(fileName, bytes, result, System.currentTimeMillis())
        history = (listOf(entry) + history).take(50)
        val array = org.json.JSONArray()
        history.forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("name", item.fileName)
                put("bytes", item.bytesTransferred)
                put("result", item.result)
                put("time", item.timestampMillis)
            })
        }
        context.getSharedPreferences("nearer_fast_share_history", Context.MODE_PRIVATE)
            .edit().putString("entries", array.toString()).apply()
    }

    private fun loadHistory(): List<FastShareHistoryEntry> {
        val raw = context.getSharedPreferences("nearer_fast_share_history", Context.MODE_PRIVATE)
            .getString("entries", null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                FastShareHistoryEntry(
                    item.optString("name", "Shared file"),
                    item.optLong("bytes", 0L),
                    item.optString("result", "Unknown"),
                    item.optLong("time", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
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

data class FastShareHistoryEntry(val fileName: String, val bytesTransferred: Long, val result: String, val timestampMillis: Long)

/** Small HTTP server with byte-range support; streams from the selected content URI. */
private class FastShareHttpServer(
    private val context: Context,
    private val uris: List<Uri>,
    private val catalog: FastShareDownloadCatalog,
    private val accessKey: String,
    private val onTransferUpdate: (fileName: String, sentBytes: Long, totalBytes: Long, complete: Boolean, interrupted: Boolean, partialResponse: Boolean) -> Unit
) {
    // Bound client work so a busy or hostile local-network peer cannot
    // create an unbounded number of server threads.
    private val executor = ThreadPoolExecutor(
        2,
        4,
        60L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(8)
    )
    @Volatile private var running = true
    private val downloadGate = FastShareDownloadGate()
    private val clients = FastShareSocketRegistry()
    private var socket: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun start(): String {
        val listener = ServerSocket(8765, 8, java.net.InetAddress.getByName("0.0.0.0"))
        socket = listener
        // Keep accepting on its own daemon thread. If the accept loop occupied
        // a worker-pool slot, only one core worker would remain for downloads,
        // silently defeating the two-receiver concurrency limit.
        acceptThread = Thread({
            while (running) {
                try {
                    val client = listener.accept()
                    // Register before dispatch so stop() can close both active and
                    // queued clients. track() also closes sockets accepted during
                    // the shutdown race.
                    if (!running) {
                        runCatching { client.close() }
                        continue
                    }
                    if (!clients.track(client)) continue
                    try {
                        executor.execute { handle(client) }
                    } catch (_: RejectedExecutionException) {
                        clients.release(client)
                        runCatching { client.close() }
                    }
                } catch (_: Exception) {
                    // A closed listener is the normal stop path. Other accept
                    // failures should terminate this loop rather than spin hot.
                    if (running) running = false
                    break
                }
            }
        }, "NeaRer-FastShare-Accept").apply {
            isDaemon = true
            start()
        }
        return findHotspotAddress()
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        acceptThread = null
        // shutdownNow interrupts workers but does not close sockets already
        // accepted or waiting in the executor queue.
        clients.closeAll()
        executor.shutdownNow()
    }

    private fun findHotspotAddress(): String {
        // A phone can have several 192.168.x.x interfaces at once (for example,
        // Wi-Fi, VPN, or a virtual adapter). Prefer an active Wi-Fi/AP interface
        // instead of whichever private address the OS happens to enumerate first.
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
            .getOrDefault(emptyList())
            .filter { network ->
                runCatching { network.isUp && !network.isLoopback }.getOrDefault(false)
            }
        val candidates = interfaces.flatMap { network ->
            val wifiLike = network.name.lowercase(java.util.Locale.ROOT).let { name ->
                name.contains("wlan") || name.contains("wifi") ||
                    name.startsWith("ap") || name.contains("softap")
            }
            Collections.list(network.inetAddresses).filterIsInstance<Inet4Address>()
                .filter { address ->
                    !address.isLoopbackAddress &&
                        address.hostAddress?.startsWith("192.168.") == true
                }
                .mapNotNull { address ->
                    address.hostAddress?.let { addressText ->
                        Triple(addressText, wifiLike, network.name)
                    }
                }
        }
        // These are common Android local-hotspot gateway addresses. Keep the
        // interface-name preference as a fallback for OEM-specific subnets.
        return candidates.firstOrNull { it.first == "192.168.43.1" }?.first
            ?: candidates.firstOrNull { it.first == "192.168.49.1" }?.first
            ?: candidates.firstOrNull { it.second && it.first.endsWith(".1") }?.first
            ?: candidates.firstOrNull { it.first.endsWith(".1") }?.first
            ?: candidates.firstOrNull { it.second }?.first
            ?: candidates.firstOrNull()?.first
            ?: "192.168.43.1"
    }

    private fun handle(client: Socket) {
        try {
            handleConnection(client)
        } finally {
            clients.release(client)
        }
    }

    private fun handleConnection(client: Socket) {
        client.use { connection ->
            connection.soTimeout = 15000
            val input = BufferedInputStream(connection.getInputStream())
            val output = BufferedOutputStream(connection.getOutputStream())
            val exchange = FastShareHttpSession.readRequest(input, output) ?: return
            val request = exchange.request
            val headOnly = request.isHeadOnly
            val rangeHeader = exchange.rangeHeader
            if (!request.hasAccessKey(accessKey)) {
                output.write(FastShareHttpErrors.forbidden())
                output.flush()
                return
            }
            when (request.route) {
                FastShareHttpRequest.Route.RECEIVER_PAGE -> {
                    val bytes = catalog.receiverPage(accessKey).toByteArray(Charsets.UTF_8)
                    output.write(FastShareHttpWire.responseHead("HTTP/1.1 200 OK", FileHttpHeaders.receiverPageHeaders(bytes.size.toLong())))
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
                    } else {
                        // Allow two file streams for small-group sharing while keeping memory
                        // bounded. Additional receivers wait fairly instead of being
                        // rejected immediately; the wait is bounded.
                        val acquired = try {
                            downloadGate.tryAcquire(120, TimeUnit.SECONDS)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            false
                        }
                        if (!acquired) {
                            output.write(FastShareHttpErrors.tooManyRequests())
                        } else {
                            try {
                                if (running) streamFile(output, uri, item, rangeHeader, false)
                            } finally {
                                downloadGate.release()
                            }
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
            output.write("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n".toByteArray())
            output.write(FileHttpHeaders.unsatisfiedRangeHeaders(item.fileLength).toByteArray())
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
        if (!headOnly) onTransferUpdate(item.fileName, 0L, count, false, false, partialTransfer)
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
                        onTransferUpdate(item.fileName, copied, count, false, false, partialTransfer)
                    }
                )
                sent = result.bytesCopied
                onTransferUpdate(item.fileName, sent, count, result.complete, !result.complete, partialTransfer)
            } catch (error: Exception) {
                onTransferUpdate(item.fileName, sent, count, false, true, partialTransfer)
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
        if (!headOnly) onTransferUpdate(item.fileName, 0L, -1L, false, false, false)
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
                        onTransferUpdate(item.fileName, copied, -1L, false, false, false)
                    }
                )
                sent = result.bytesCopied
                onTransferUpdate(item.fileName, sent, -1L, result.complete, !result.complete, false)
            } catch (error: Exception) {
                onTransferUpdate(item.fileName, sent, -1L, false, true, false)
                throw error
            }
        }
    }

    private fun writeFileUnavailable(output: BufferedOutputStream, item: FastShareDownloadItem) {
        onTransferUpdate(item.fileName, 0L, item.fileLength, false, true, false)
        val message = "The selected file is no longer available. Choose it again in NeaRer."
        val body = message.toByteArray(Charsets.UTF_8)
        output.write(FastShareHttpWire.responseHead(
            "HTTP/1.1 503 Service Unavailable",
            FileHttpHeaders.unavailableFileHeaders(body.size.toLong())
        ))
        output.write(body)
        output.flush()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}
