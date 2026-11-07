package com.nearer.app

import android.content.Context
import android.net.wifi.WifiManager
import java.io.Closeable
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

data class OpenSendSession(
    val ssid: String,
    val password: String?,
    val downloadUrl: String,
    val fileCount: Int,
    val totalBytes: Long
)

/**
 * Owns the complete sender-side OpenSend lifecycle. The caller is responsible for
 * obtaining NEARBY_WIFI_DEVICES (Android 13+) or ACCESS_FINE_LOCATION (Android 12-)
 * before start(), and must keep this manager alive while the share is active.
 */
class OpenSendSessionManager(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private var hotspot: OpenSendHotspot? = null
    private var server: OpenSendHttpServer? = null
    private val active = AtomicBoolean(false)

    @Synchronized
    fun start(
        files: List<OpenSendFile>,
        onReady: (OpenSendSession) -> Unit,
        onFailure: (String) -> Unit
    ) {
        check(!active.get()) { "An OpenSend session is already active." }
        require(files.isNotEmpty()) { "Choose at least one file to share." }
        require(files.all { it.file.isFile && it.file.canRead() }) { "A selected file is no longer available." }

        active.set(true)
        val localHotspot = OpenSendHotspot(appContext)
        hotspot = localHotspot
        try {
            localHotspot.start(
                onReady = { credentials ->
                    try {
                        if (!active.get()) {
                            localHotspot.close()
                            return@start
                        }
                        val http = OpenSendHttpServer(files)
                        val serverInfo = http.start()
                        val host = selectHotspotAddress(serverInfo.localAddresses)
                        server = http
                        onReady(
                            OpenSendSession(
                                ssid = credentials.ssid,
                                password = credentials.password,
                                downloadUrl = "http://$host:${serverInfo.port}${http.landingPath()}",
                                fileCount = files.size,
                                totalBytes = files.sumOf { it.file.length() }
                            )
                        )
                    } catch (error: Exception) {
                        stop()
                        onFailure(error.localizedMessage ?: "NeaRer could not start the download page.")
                    }
                },
                onUnavailable = { message ->
                    if (active.get()) {
                        stop()
                        onFailure(message)
                    }
                }
            )
        } catch (error: Exception) {
            stop()
            onFailure(error.localizedMessage ?: "NeaRer could not start local sharing.")
        }
    }

    private fun selectHotspotAddress(addresses: List<String>): String {
        val candidates = addresses.mapNotNull { raw ->
            try { InetAddress.getByName(raw) as? Inet4Address } catch (_: Exception) { null }
        }.filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
        val hotspotInterfaceAddresses = try {
            NetworkInterface.getNetworkInterfaces().asSequence().toList()
                .filter { it.isUp && !it.isLoopback && (it.name.startsWith("wlan", true) || it.name.startsWith("ap", true)) }
                .flatMap { it.inetAddresses.asSequence().toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
                .toList()
        } catch (_: Exception) { emptyList() }
        return (hotspotInterfaceAddresses + candidates).distinct().firstOrNull()?.hostAddress
            ?: error("Android started the hotspot, but NeaRer could not find its local Wi-Fi address. Try again after toggling Wi-Fi.")
    }

    @Synchronized
    fun stop() {
        active.set(false)
        try { server?.close() } catch (_: Exception) { }
        server = null
        try { hotspot?.close() } catch (_: Exception) { }
        hotspot = null
    }

    override fun close() = stop()

    fun isActive(): Boolean = active.get()
}
