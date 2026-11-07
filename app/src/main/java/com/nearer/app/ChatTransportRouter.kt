package com.nearer.app

/**
 * Chooses an already-approved direct route for a private chat message.
 *
 * Nearby Connections abstracts Bluetooth and nearby Wi-Fi. Wi-Fi Direct is
 * retained as a separate socket route for devices using the legacy link.
 * This router never broadcasts private chat messages through mesh relays.
 */
internal enum class ChatTransportRoute {
    NEARBY_CONNECTIONS,
    WIFI_DIRECT
}

internal class ChatTransportRouter {
    fun send(
        nearbyReady: Boolean,
        wifiDirectReady: Boolean,
        sendNearby: () -> Boolean,
        sendWifiDirect: () -> Boolean
    ): ChatTransportRoute? {
        if (nearbyReady && sendNearby()) return ChatTransportRoute.NEARBY_CONNECTIONS
        if (wifiDirectReady && sendWifiDirect()) return ChatTransportRoute.WIFI_DIRECT
        return null
    }
}
