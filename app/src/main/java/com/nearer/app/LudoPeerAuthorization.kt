package com.nearer.app

/** Stable-identity authorization for the current legacy Ludo room packet flow. */
internal object LudoPeerAuthorization {
    fun matches(
        expectedPeerId: String?,
        incomingPeerId: String?,
        expectedName: String?,
        incomingName: String?,
        allowLegacyNameFallback: Boolean
    ): Boolean {
        val expectedId = expectedPeerId?.trim().orEmpty()
        val incomingId = incomingPeerId?.trim().orEmpty()
        if (expectedId.isNotEmpty()) return incomingId.isNotEmpty() && expectedId == incomingId
        if (incomingId.isNotEmpty() || !allowLegacyNameFallback) return false
        return !expectedName.isNullOrBlank() && expectedName == incomingName
    }

    fun findRemoteColor(
        remotePeerIds: Map<LudoColor, String>,
        remoteNames: Map<LudoColor, String>,
        incomingPeerId: String?,
        incomingName: String?,
        allowLegacyNameFallback: Boolean
    ): LudoColor? {
        val incomingId = incomingPeerId?.trim().orEmpty()
        if (incomingId.isNotEmpty()) {
            return remotePeerIds.entries.singleOrNull { it.value.trim() == incomingId }?.key
        }
        if (!allowLegacyNameFallback) return null
        return remoteNames.entries.filter { it.value == incomingName }.map { it.key }.singleOrNull()
    }
}
