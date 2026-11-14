package com.nearer.app

/** Keeps nearby discovery user-driven and bounded to foreground use. */
internal object NearbyDiscoveryBudgetPolicy {
    const val FOREGROUND_SEARCH_TIMEOUT_MS = 60_000L

    fun shouldStopWhenActivityStops(isSearching: Boolean): Boolean = isSearching
}
