package com.nearer.app

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Observes incoming calls only for opt-in NeaRer Flash signalling.
 * Every incoming call is immediately allowed; this service never rejects or silences calls.
 */
class NeaRerFlashScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        // Keep the phone's normal incoming-call behaviour intact before doing any local work.
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .build()
        )

        try {
            val handle = callDetails.handle ?: return
            if (handle.scheme != "tel") return
            val number = handle.schemeSpecificPart.orEmpty()
            val digits = number.filter(Char::isDigit)
            if (digits.length < 7) return

            val senderKey = senderFingerprint(digits)
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val key = EVENTS_PREFIX + senderKey
            val now = android.os.SystemClock.elapsedRealtime()
            val previous = prefs.getString(key, "")
                .orEmpty()
                .split(',')
                .mapNotNull { it.toLongOrNull() }
                .filter { now >= it && now - it <= MAX_SEQUENCE_AGE_MS }
                .takeLast(3)
            val updated = (previous + now).takeLast(4)
            val signal = FlashSignalPattern.decode(updated)
            if (signal == null) {
                prefs.edit().putString(key, updated.joinToString(",")).apply()
            } else {
                prefs.edit()
                    .remove(key)
                    .putString(LAST_SIGNAL, signal)
                    .putLong(LAST_SIGNAL_AT, System.currentTimeMillis())
                    .putString(LAST_SIGNAL_FROM, senderKey)
                    .apply()
            }
        } catch (_: Exception) {
            // Flash is best-effort only; it must never interfere with ordinary calls.
        }
    }

    private fun senderFingerprint(number: String): String {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        var salt = prefs.getString(SALT, null)
        if (salt == null) {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            salt = Base64.encodeToString(bytes, Base64.NO_WRAP)
            prefs.edit().putString(SALT, salt).commit()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(salt, Base64.NO_WRAP))
        return digest.digest(number.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PREFS = "nearer_flash_signals"
        const val LAST_SIGNAL = "last_signal"
        const val LAST_SIGNAL_AT = "last_signal_at"
        const val LAST_SIGNAL_FROM = "last_signal_from"
        private const val SALT = "sender_hash_salt"
        private const val EVENTS_PREFIX = "events_"
        private const val MAX_SEQUENCE_AGE_MS = 12_000L
    }
}
