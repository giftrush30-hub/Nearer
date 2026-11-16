package com.nearer.app

/** Deterministic readiness gate shared by Activity/service lifecycle and JVM tests. */
internal object NearbyServiceStartPolicy {
    /** Android 8–11 require Location services enabled for nearby radio discovery. */
    fun requiresLocationServices(sdkInt: Int): Boolean = sdkInt <= 30

    fun shouldStartDiscovery(
        onboardingComplete: Boolean,
        permissionsGranted: Boolean,
        radioAvailable: Boolean,
        sdkInt: Int,
        locationServicesEnabled: Boolean
    ): Boolean = onboardingComplete && permissionsGranted && radioAvailable &&
        (!requiresLocationServices(sdkInt) || locationServicesEnabled)
}
