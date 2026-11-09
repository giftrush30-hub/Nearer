package com.nearer.app

import android.content.SharedPreferences
import java.util.UUID

/**
 * Creates a device-local NeaRer identity without phone, email, password,
 * or network registration. The ID is generated once and kept stable in
 * the app's private preferences on this device.
 *
 * This is an identity label, not proof of a person's real-world identity.
 * A future account-transfer flow must securely export/import the identity
 * key material rather than silently deriving a new ID on another phone.
 */
internal object NeaRerIdentity {
    private const val PREF_KEY = "nearer_unique_id"
    private const val PREFIX = "NR-"
    private val idPattern = Regex("^NR-[0-9A-F]{32}$")

    fun loadOrCreate(preferences: SharedPreferences): String {
        val existing = preferences.getString(PREF_KEY, null)?.uppercase()
        if (existing != null && idPattern.matches(existing)) return existing

        val generated = PREFIX + UUID.randomUUID().toString()
            .replace("-", "")
            .uppercase()
        preferences.edit().putString(PREF_KEY, generated).apply()
        return generated
    }

    internal fun isValid(id: String): Boolean = idPattern.matches(id)
}
