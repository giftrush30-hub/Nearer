package com.nearer.app

/** Deterministic readiness gate shared by service-restart logic and JVM tests. */
internal object NearbyServiceStartPolicy {
    fun shouldStartDiscovery(
        onboardingComplete: Boolean,
        permissionsGranted: Boolean,
        radioAvailable: Boolean,
        locationServicesEnabled: Boolean
    ): Boolean = onboardingComplete && permissionsGranted && radioAvailable && locationServicesEnabled
}
