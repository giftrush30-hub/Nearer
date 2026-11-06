package com.nearer.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat

/** Handles only user-approved Flash senders; all other calls pass through. */
class FlashCallScreeningService : CallScreeningService() {
    private val handler = Handler(Looper.getMainLooper())
    private val finishBurst = Runnable { publishDecodedBurst() }

    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT < 29) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        val incoming = normalize(details.handle?.schemeSpecificPart.orEmpty())
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trusted = incoming.isNotBlank() && incoming == normalize(prefs.getString(KEY_TRUSTED_NUMBER, "").orEmpty()) && prefs.getBoolean(KEY_ENABLED, false)
        if (!trusted) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        respondToCall(details, CallResponse.Builder().setSilenceCall(true).build())
        synchronized(BURST_LOCK) {
            val now = System.currentTimeMillis()
            val last = prefs.getLong(KEY_LAST_CALL, 0L)
            val count = if (now - last > BURST_WINDOW_MS) 1 else (prefs.getInt(KEY_BURST_COUNT, 0) + 1).coerceAtMost(10)
            prefs.edit().putInt(KEY_BURST_COUNT, count).putLong(KEY_LAST_CALL, now).apply()
            handler.removeCallbacks(finishBurst)
            handler.postDelayed(finishBurst, DEBOUNCE_MS)
        }
    }

    private fun publishDecodedBurst() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val message = FLASH_CODES[prefs.getInt(KEY_BURST_COUNT, 0)] ?: return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "NeaRer Flash", NotificationManager.IMPORTANCE_HIGH))
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call).setContentTitle("NeaRer Flash").setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setAutoCancel(true).build())
        prefs.edit().putInt(KEY_BURST_COUNT, 0).putLong(KEY_LAST_CALL, 0L).apply()
    }

    private fun normalize(value: String) = value.filter(Char::isDigit).takeLast(10)

    companion object {
        const val PREFS = "nearer_flash"
        const val KEY_TRUSTED_NUMBER = "trusted_number"
        const val KEY_ENABLED = "receiver_enabled"
        const val KEY_BURST_COUNT = "burst_count"
        const val KEY_LAST_CALL = "last_call"
        const val CHANNEL_ID = "nearer_flash_messages"
        const val BURST_WINDOW_MS = 300_000L
        const val DEBOUNCE_MS = 15_000L
        val BURST_LOCK = Any()
        val FLASH_CODES = mapOf(1 to "Call me when you can", 2 to "Come outside", 3 to "I'm on my way", 4 to "I'm waiting for you", 5 to "Come to Room 8", 6 to "Come to Room 10", 7 to "Food is ready", 8 to "Lecturer is coming", 9 to "I need help", 10 to "EMERGENCY — contact me now")
    }
}
