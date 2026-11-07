package com.nearer.app

import android.content.Context
import android.content.Intent
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

            // Keep a normal paired call visible in chat, but replace the provisional
            // call bubble if this call completes a recognised multi-call preset.
            val receivedAt = System.currentTimeMillis()
            val callEventId = "call-$receivedAt-$now"
            val senderTag = senderKey.takeLast(8)
            val senderName = FlashTrustedSenders.displayName(this, number)
            val queue = try {
                JSONArray(prefs.getString(PENDING_EVENTS, "[]") ?: "[]")
            } catch (_: Exception) {
                JSONArray()
            }

            val previous = prefs.getString(key, "")
                .orEmpty()
                .split(',')
                .mapNotNull { it.toLongOrNull() }
                .filter { now >= it && now - it <= MAX_SEQUENCE_AGE_MS }
                .takeLast(3)
            val updated = (previous + now).takeLast(4)
            val signal = FlashSignalPattern.decode(updated)

            if (signal == null) {
                queue.put(
                    JSONObject()
                        .put("id", callEventId)
                        .put("signal", "FLASH_CALL")
                        .put("timestamp", receivedAt)
                        .put("senderTag", senderTag)
                        .put("senderName", senderName)
                )
                while (queue.length() > MAX_PENDING_EVENTS) queue.remove(0)
                prefs.edit()
                    .putString(key, updated.joinToString(","))
                    .putString(PENDING_EVENTS, queue.toString())
                    .apply()
            } else {
                // The calls in a recognised pattern are one message, not several
                // generic call bubbles plus a decoded preset bubble. Tell the chat
                // importer which provisional call bubbles must be replaced too.
                val replacedEventIds = JSONArray()
                for (index in queue.length() - 1 downTo 0) {
                    val queuedEvent = queue.optJSONObject(index) ?: continue
                    val queuedAt = queuedEvent.optLong("timestamp", 0L)
                    if (queuedEvent.optString("signal") == "FLASH_CALL" &&
                        queuedEvent.optString("senderTag") == senderTag &&
                        queuedAt > 0L && receivedAt >= queuedAt &&
                        receivedAt - queuedAt <= MAX_SEQUENCE_AGE_MS
                    ) {
                        queuedEvent.optString("id").takeIf { it.isNotBlank() }?.let { replacedEventIds.put(it) }
                        queue.remove(index)
                    }
                }
                queue.put(
                    JSONObject()
                        .put("id", "$receivedAt-$now-$signal")
                        .put("signal", signal)
                        .put("timestamp", receivedAt)
                        .put("senderTag", senderTag)
                        .put("senderName", senderName)
                        .put("replaces", replacedEventIds)
                )
                while (queue.length() > MAX_PENDING_EVENTS) queue.remove(0)
                prefs.edit()
                    .remove(key)
                    .putString(LAST_SIGNAL, signal)
                    .putLong(LAST_SIGNAL_AT, receivedAt)
                    .putString(LAST_SIGNAL_FROM, senderKey)
                    .putString(PENDING_EVENTS, queue.toString())
                    .apply()
            }

            // Wake an open NeaRer chat after the queue has been updated.
            sendBroadcast(Intent(ACTION_FLASH_MESSAGE_RECEIVED).setPackage(packageName))
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
        const val ACTION_FLASH_MESSAGE_RECEIVED = "com.nearer.app.FLASH_MESSAGE_RECEIVED"
        private const val MAX_PENDING_EVENTS = 100
        private const val EVENTS_PREFIX = "events_"
        private const val MAX_SEQUENCE_AGE_MS = 55_000L
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

    fun normalize(number: String): String {
        val digits = number.filter(Char::isDigit)
        // Nigerian local mobile numbers and their international +234 form match.
        return when {
            digits.length == 11 && digits.startsWith("0") -> "234" + digits.drop(1)
            digits.length == 13 && digits.startsWith("234") -> digits
            else -> digits
        }
    }

    private fun fingerprintDigits(context: Context, digits: String): String? {
        if (digits.length < 7) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var salt = prefs.getString(SALT_KEY, null)
        if (salt == null) {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            salt = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (!prefs.edit().putString(SALT_KEY, salt).commit()) return null
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(salt, Base64.NO_WRAP))
        return digest.digest(digits.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun fingerprint(context: Context, number: String): String? =
        fingerprintDigits(context, normalize(number))

    private fun matchingFingerprints(context: Context, number: String): Set<String> {
        val digits = number.filter(Char::isDigit)
        val canonical = normalize(number)
        val candidates = mutableSetOf(canonical, digits)
        // Check the pre-normalisation local form too, so old pairings survive
        // when a carrier presents the same Nigerian line as +234 instead of 0.
        if (canonical.length == 13 && canonical.startsWith("234")) {
            candidates.add("0" + canonical.drop(3))
        }
        return candidates.mapNotNull { fingerprintDigits(context, it) }.toSet()
    }

    fun addTrusted(context: Context, number: String, displayName: String = ""): Boolean {
        val hash = fingerprint(context, number) ?: return false
        val hashesToReplace = matchingFingerprints(context, number) + hash
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(ALLOWLIST_KEY, emptySet()).orEmpty().toMutableSet()
        current.removeAll(hashesToReplace)
        current.add(hash)
        val names = prefs.getStringSet("trusted_sender_names", emptySet()).orEmpty().toMutableSet()
        names.removeAll { entry -> hashesToReplace.any { entry.startsWith("$it|") } }
        val safeName = displayName.trim().take(32)
        if (safeName.isNotBlank()) names.add("$hash|$safeName")
        return prefs.edit()
            .putStringSet(ALLOWLIST_KEY, current)
            .putStringSet("trusted_sender_names", names)
            .commit()
    }

    fun displayName(context: Context, number: String): String? {
        val hashes = matchingFingerprints(context, number)
        val entries = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("trusted_sender_names", emptySet()).orEmpty()
        return entries.firstOrNull { entry -> hashes.any { entry.startsWith("$it|") } }
            ?.substringAfter("|")
            ?.takeIf { it.isNotBlank() }
    }

    fun isTrusted(context: Context, number: String): Boolean {
        val hashes = matchingFingerprints(context, number)
        val allowed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(ALLOWLIST_KEY, emptySet()).orEmpty()
        return hashes.any { it in allowed }
    }

    fun trustedCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(ALLOWLIST_KEY, emptySet())
            .orEmpty()
            .size
}
