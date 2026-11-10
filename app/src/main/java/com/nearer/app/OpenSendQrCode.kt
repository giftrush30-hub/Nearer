package com.nearer.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object OpenSendQrCode {
    /** Encode one exact payload per QR. Wi-Fi credentials and the download URL are separate. */
    fun createBitmap(value: String, size: Int = 720): Bitmap {
        require(value.isNotBlank()) { "QR content cannot be empty." }
        require(size in 256..1600) { "QR image size is outside the supported range." }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (y in 0 until size) {
                for (x in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    }

    fun wifiPayload(ssid: String, password: String?): String {
        require(ssid.isNotBlank()) { "Wi-Fi network name is missing." }
        // Android's standard WIFI: QR syntax. Escape reserved characters instead of
        // concatenating raw SSIDs/passwords into the payload.
        val auth = if (password.isNullOrBlank()) "nopass" else "WPA"
        val secret = password.orEmpty()
        return "WIFI:T:$auth;S:${escapeWifi(ssid)};P:${escapeWifi(secret)};;"
    }

    private fun escapeWifi(value: String): String = buildString {
        value.forEach { character ->
            if (character in charArrayOf('\\', ';', ',', ':', '"')) append('\\')
            append(character)
        }
    }
}

@Composable
fun OpenSendQrImage(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) { OpenSendQrCode.createBitmap(value).asImageBitmap() }
    Box(modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(10.dp)) {
        Image(bitmap = bitmap, contentDescription = "NeaRer OpenSend QR code", modifier = Modifier.size(210.dp))
    }
}
