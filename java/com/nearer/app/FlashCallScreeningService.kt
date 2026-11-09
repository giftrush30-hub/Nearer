package com.nearer.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat

/** Screens only the sender explicitly trusted by the device owner. */
class FlashCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT < 29) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return

        val incoming = normalize(details.handle?.schemeSpecificPart.orEmpty())
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trusted = incoming.isNotBlank() &&
            incoming == normalize(prefs.getString(KEY_TRUSTED_NUMBER, "").orEmpty()) &&
            prefs.getBoolean(KEY_ENABLED, false)

        if (!trusted) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        // Silence the trusted signal call. It is not rejected or hidden from call history.
        respondToCall(details, CallResponse.Builder().setSilenceCall(true).build())

        val now = System.currentTimeMillis()
        val previous = prefs.getLong(KEY_LAST_CALL, 0L)
        val count = if (now - previous > BURST_WINDOW_MS) 1
            else (prefs.getInt(KEY_BURST_COUNT, 0) + 1).coerceAtMost(10)
        prefs.edit().putInt(KEY_BURST_COUNT, count).putLong(KEY_LAST_CALL, now).apply()
        publishDecodedBurst(count)
    }

    private fun publishDecodedBurst(count: Int) {
        val message = FLASH_CODES[count] ?: return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "NeaRer Flash", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("NeaRer Flash • Signal $count/10")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun normalize(value: String) = value.filter(Char::isDigit).takeLast(10)

    companion object {
        const val PREFS = "nearer_flash"
        const val KEY_TRUSTED_NUMBER = "trusted_number"
        const val KEY_ENABLED = "receiver_enabled"
        const val KEY_BURST_COUNT = "burst_count"
        const val KEY_LAST_CALL = "last_call"
        const val CHANNEL_ID = "nearer_flash_messages"
        const val NOTIFICATION_ID = 7042
        const val BURST_WINDOW_MS = 120_000L
        val FLASH_CODES = mapOf(
            1 to "Call me when you can",
            2 to "Come outside",
            3 to "I'm on my way",
            4 to "I'm waiting for you",
            5 to "Come to Room 8",
            6 to "Come to Room 10",
            7 to "Food is ready",
            8 to "Lecturer is coming",
            9 to "I need help",
            10 to "EMERGENCY — contact me now"
        )
    }
}
