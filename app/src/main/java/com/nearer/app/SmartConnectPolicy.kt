package com.nearer.app

/**
 * Small, deterministic decisions shared by the SmartConnect lifecycle and tests.
 * The policy never turns radios on or bypasses Android permission prompts.
 */
internal object SmartConnectPolicy {
    fun shouldStartDiscovery(
        onboardingComplete: Boolean,
        permissionsGranted: Boolean,
        radioAvailable: Boolean,
        alreadySearching: Boolean,
        alreadyConnected: Boolean
    ): Boolean =
        onboardingComplete && permissionsGranted && radioAvailable &&
            !alreadySearching && !alreadyConnected

    /** Both previously verified key pins are required for frictionless reconnect. */
    fun shouldAutoAcceptReconnect(
        hasSigningKeyPin: Boolean,
        hasRecipientEncryptionKeyPin: Boolean
    ): Boolean = hasSigningKeyPin && hasRecipientEncryptionKeyPin
}
