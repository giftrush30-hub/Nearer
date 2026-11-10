package com.nearer.app

import android.content.SharedPreferences
import java.util.UUID

/**
 * A stable device-local identity; no phone number, email, password or
 * network registration is required. This ID identifies an app installation,
 * not a verified real-world person. Secure device migration is a later task.
 */
internal object NeaRerIdentity {
    private const val PREF_KEY = "nearer_unique_id"
    private const val PREFIX = "NR-"
    private val idPattern = Regex("^NR-[0-9A-F]{32}$")

    fun loadOrCreate(preferences: SharedPreferences): String {
        val existing = preferences.getString(PREF_KEY, null)?.uppercase()
        if (existing != null && idPattern.matches(existing)) return existing
        val generated = PREFIX + UUID.randomUUID().toString().replace("-", "").uppercase()
        preferences.edit().putString(PREF_KEY, generated).apply()
        return generated
    }

    internal fun isValid(id: String): Boolean = idPattern.matches(id)
}
