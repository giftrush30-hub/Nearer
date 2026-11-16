package com.nearer.app

/** Keeps foreground-service promotion independent from an existing UI bind. */
internal object NearbyServiceBindingPolicy {
    data class Actions(
        val startForegroundService: Boolean,
        val bindService: Boolean
    )

    fun actions(
        requestForeground: Boolean,
        serviceStarted: Boolean,
        serviceBound: Boolean,
        serviceBinding: Boolean
    ): Actions = Actions(
        startForegroundService = requestForeground && !serviceStarted,
        bindService = !serviceBound && !serviceBinding
    )
}
