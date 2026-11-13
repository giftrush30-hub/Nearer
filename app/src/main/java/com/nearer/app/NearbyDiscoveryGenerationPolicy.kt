package com.nearer.app

/** Rejects endpoint callbacks from a stopped or superseded discovery session. */
internal object NearbyDiscoveryGenerationPolicy {
    fun isCurrent(started: Boolean, callbackGeneration: Int, currentGeneration: Int): Boolean =
        started && callbackGeneration == currentGeneration
}
