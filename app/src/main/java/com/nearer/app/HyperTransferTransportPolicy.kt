package com.nearer.app

/**
 * Chooses a HyperTransfer route only when both phones can use it and a
 * successful, integrity-checked benchmark exists for this exact device pair
 * and direction. Unmeasured transports are never promoted just because their
 * theoretical radio rate looks attractive.
 *
 * This is a decision layer, not a Wi-Fi Direct/Wi-Fi Aware socket
 * implementation. The selected route must be wired to a transport before it
 * can be used for a real transfer.
 */
internal object HyperTransferTransportPolicy {
    enum class Transport {
        WIFI_DIRECT_TCP,
        WIFI_AWARE_TCP,
        NEARBY_POINT_TO_POINT
    }

    enum class Direction {
        LOCAL_TO_REMOTE,
        REMOTE_TO_LOCAL
    }

    data class PeerCapabilities(
        val apiLevel: Int,
        val nearbyPointToPointAvailable: Boolean,
        val wifiDirectHardwareSupported: Boolean,
        val wifiDirectPermissionGranted: Boolean,
        val wifiAwareHardwareSupported: Boolean,
        val wifiAwarePermissionGranted: Boolean,
        /** Current framework/radio availability, not just a static feature flag. */
        val wifiDirectAvailableNow: Boolean = false,
        val wifiAwareAvailableNow: Boolean = false
    )

    data class BenchmarkProfile(
        val transport: Transport,
        val devicePairKey: String,
        val direction: Direction,
        val medianBytesPerSecond: Long,
        val successfulRuns: Int,
        val integrityVerified: Boolean
    )

    data class Decision(
        val transport: Transport?,
        val reason: String,
        val measuredBytesPerSecond: Long? = null,
        val benchmarkRuns: Int = 0
    )

    fun isEligible(
        transport: Transport,
        local: PeerCapabilities,
        remote: PeerCapabilities
    ): Boolean = when (transport) {
        Transport.NEARBY_POINT_TO_POINT ->
            local.nearbyPointToPointAvailable && remote.nearbyPointToPointAvailable

        Transport.WIFI_DIRECT_TCP ->
            local.apiLevel >= 26 && remote.apiLevel >= 26 &&
                local.wifiDirectHardwareSupported && remote.wifiDirectHardwareSupported &&
                local.wifiDirectPermissionGranted && remote.wifiDirectPermissionGranted &&
                local.wifiDirectAvailableNow && remote.wifiDirectAvailableNow

        Transport.WIFI_AWARE_TCP ->
            local.apiLevel >= 26 && remote.apiLevel >= 26 &&
                local.wifiAwareHardwareSupported && remote.wifiAwareHardwareSupported &&
                local.wifiAwarePermissionGranted && remote.wifiAwarePermissionGranted &&
                local.wifiAwareAvailableNow && remote.wifiAwareAvailableNow
    }

    fun choose(
        local: PeerCapabilities,
        remote: PeerCapabilities,
        devicePairKey: String,
        direction: Direction,
        profiles: List<BenchmarkProfile>
    ): Decision {
        require(devicePairKey.isNotBlank()) { "A stable device-pair key is required" }

        val qualified = profiles.asSequence()
            .filter { it.devicePairKey == devicePairKey }
            .filter { it.direction == direction }
            .filter { it.successfulRuns >= HyperTransferPerformanceGate.MINIMUM_SUCCESSFUL_RUNS }
            .filter { it.integrityVerified }
            .filter { it.medianBytesPerSecond > 0L }
            .filter { isEligible(it.transport, local, remote) }
            .groupBy { it.transport }
            .mapNotNull { (transport, samples) ->
                val best = samples.maxByOrNull { it.medianBytesPerSecond } ?: return@mapNotNull null
                transport to best
            }
            .sortedByDescending { it.second.medianBytesPerSecond }
            .firstOrNull()

        if (qualified != null) {
            val (transport, profile) = qualified
            return Decision(
                transport = transport,
                reason = "Selected from a verified benchmark for this device pair and transfer direction.",
                measuredBytesPerSecond = profile.medianBytesPerSecond,
                benchmarkRuns = profile.successfulRuns
            )
        }

        return if (isEligible(Transport.NEARBY_POINT_TO_POINT, local, remote)) {
            Decision(
                transport = Transport.NEARBY_POINT_TO_POINT,
                reason = "No eligible alternative has enough matching, integrity-verified benchmark evidence; use the existing Nearby point-to-point route."
            )
        } else {
            Decision(
                transport = null,
                reason = "No mutually available HyperTransfer route was detected."
            )
        }
    }
}
