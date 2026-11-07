package com.nearer.app

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.Closeable

data class OpenSendHotspotCredentials(
    val ssid: String,
    val password: String?
)

/**
 * Owns a temporary Android local-only hotspot reservation for OpenSend.
 * This does not enable mobile data or provide internet access.
 *
 * The Activity must request the platform's required nearby-Wi-Fi permission
 * before calling start(). Keep this reservation alive for the entire share.
 */
class OpenSendHotspot(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Volatile private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun start(
        onReady: (OpenSendHotspotCredentials) -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        check(reservation == null) { "OpenSend hotspot is already active." }
        wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                reservation = value
                val credentials = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val config = value.softApConfiguration
                        OpenSendHotspotCredentials(
                            ssid = config.ssid?.takeIf { it.isNotBlank() }
                                ?: error("Android did not provide a hotspot name."),
                            password = config.passphrase?.takeIf { it.isNotBlank() }
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        val config = value.wifiConfiguration
                        OpenSendHotspotCredentials(
                            ssid = config?.SSID?.trim('"')?.takeIf { it.isNotBlank() }
                                ?: error("Android did not provide a hotspot name."),
                            password = config?.preSharedKey?.takeIf { it.isNotBlank() }?.trim('"')
                        )
                    }
                } catch (error: Exception) {
                    close()
                    onUnavailable(error.localizedMessage ?: "Android started a hotspot but did not provide connection details.")
                    return
                }
                onReady(credentials)
            }

            override fun onStopped() {
                reservation = null
                onUnavailable("Android stopped the temporary sharing hotspot.")
            }

            override fun onFailed(reason: Int) {
                reservation = null
                onUnavailable(when (reason) {
                    ERROR_NO_CHANNEL -> "Wi-Fi is busy. Close other hotspot or Wi-Fi Direct sessions and try again."
                    ERROR_GENERIC -> "Android could not start the temporary sharing hotspot."
                    ERROR_INCOMPATIBLE_MODE -> "This phone is using an incompatible Wi-Fi mode. Turn off its existing hotspot and try again."
                    ERROR_TETHERING_DISALLOWED -> "This phone or its administrator does not allow a local sharing hotspot."
                    else -> "Could not start the sharing hotspot (Android error $reason)."
                })
            }
        }, Handler(Looper.getMainLooper()))
    }

    override fun close() {
        try { reservation?.close() } catch (_: Exception) { }
        reservation = null
    }
}
