package com.nearer.app

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Observes incoming calls only for opt-in NeaRer Flash signalling.
 * Every incoming call is immediately allowed; this service never rejects or silences calls.
 */
class NeaRerFlashScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        // Respond first: ordinary incoming calls must not wait for Flash processing.
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .build()
        )

        try {
            val handle = callDetails.handle ?: return
            if (handle.scheme != "tel") return
            val number = handle.schemeSpecificPart.orEmpty()
            if (!FlashTrustedSenders.isTrusted(this, number)) return

            val senderKey = FlashTrustedSenders.fingerprint(this, number) ?: return
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
                val receivedAt = System.currentTimeMillis()
                val eventId = "$receivedAt-$now-$signal"
                val queued = try {
                    JSONArray(prefs.getString(PENDING_EVENTS, "[]") ?: "[]")
                } catch (_: Exception) {
                    JSONArray()
                }
                queued.put(
                    JSONObject()
                        .put("id", eventId)
                        .put("signal", signal)
                        .put("timestamp", receivedAt)
                        .put("senderTag", senderKey.takeLast(8))
                        .put("senderName", FlashTrustedSenders.displayName(this, number))
                )
                while (queued.length() > MAX_PENDING_EVENTS) queued.remove(0)
                prefs.edit()
                    .remove(key)
                    .putString(LAST_SIGNAL, signal)
                    .putLong(LAST_SIGNAL_AT, receivedAt)
                    .putString(LAST_SIGNAL_FROM, senderKey)
                    .putString(PENDING_EVENTS, queued.toString())
                    .apply()
            }
        } catch (_: Exception) {
            // Flash is best-effort only; it must never interfere with ordinary calls.
        }
    }

    companion object {
        const val PREFS = "nearer_flash_signals"
        const val LAST_SIGNAL = "last_signal"
        const val LAST_SIGNAL_AT = "last_signal_at"
        const val LAST_SIGNAL_FROM = "last_signal_from"
        const val PENDING_EVENTS = "pending_events"
        private const val MAX_PENDING_EVENTS = 100
        private const val EVENTS_PREFIX = "events_"
        private const val MAX_SEQUENCE_AGE_MS = 12_000L
    }
}

/**
 * Local allowlist for Flash senders. Only salted SHA-256 fingerprints are persisted;
 * raw telephone numbers are never written to preferences.
 *
 * Number matching deliberately uses the digits as presented by Android. Users should
 * pair using the same country-code/local format that appears on the receiving phone.
 */
object FlashTrustedSenders {
    private const val PREFS = "nearer_flash_trusted_senders"
    private const val SALT_KEY = "salt"
    private const val ALLOWLIST_KEY = "trusted_fingerprints"

    fun normalize(number: String): String = number.filter(Char::isDigit)

    fun fingerprint(context: Context, number: String): String? {
        val normalized = normalize(number)
        if (normalized.length < 7) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var salt = prefs.getString(SALT_KEY, null)
        if (salt == null) {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            salt = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (!prefs.edit().putString(SALT_KEY, salt).commit()) return null
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(salt, Base64.NO_WRAP))
        return digest.digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun addTrusted(context: Context, number: String, displayName: String = ""): Boolean {
        val hash = fingerprint(context, number) ?: return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(ALLOWLIST_KEY, emptySet()).orEmpty().toMutableSet()
        current.add(hash)
        val names = prefs.getStringSet("trusted_sender_names", emptySet()).orEmpty().toMutableSet()
        val safeName = displayName.trim().take(32)
        if (safeName.isNotBlank()) names.add("$hash|$safeName")
        return prefs.edit()
            .putStringSet(ALLOWLIST_KEY, current)
            .putStringSet("trusted_sender_names", names)
            .commit()
    }

    fun displayName(context: Context, number: String): String? {
        val hash = fingerprint(context, number) ?: return null
        val entries = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("trusted_sender_names", emptySet()).orEmpty()
        return entries.firstOrNull { it.startsWith("$hash|") }
            ?.substringAfter("|")
            ?.takeIf { it.isNotBlank() }
    }

    fun isTrusted(context: Context, number: String): Boolean {
        val hash = fingerprint(context, number) ?: return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(ALLOWLIST_KEY, emptySet())
            .orEmpty()
            .contains(hash)
    }

    fun trustedCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(ALLOWLIST_KEY, emptySet())
            .orEmpty()
            .size
}
