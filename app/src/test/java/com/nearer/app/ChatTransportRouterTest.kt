package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTransportRouterTest {
    private val router = ChatTransportRouter()

    @Test fun prefersNearbyConnectionsWhenAvailable() {
        var wifiAttempted = false
        val route = router.send(true, true, { true }, { wifiAttempted = true; true })
        assertEquals(ChatTransportRoute.NEARBY_CONNECTIONS, route)
        assertEquals(false, wifiAttempted)
    }

    @Test fun fallsBackToWifiDirectWhenNearbyCannotStart() {
        val route = router.send(true, true, { false }, { true })
        assertEquals(ChatTransportRoute.WIFI_DIRECT, route)
    }

    @Test fun usesWifiDirectWhenNearbyIsUnavailable() {
        val route = router.send(false, true, { false }, { true })
        assertEquals(ChatTransportRoute.WIFI_DIRECT, route)
    }

    @Test fun leavesMessageQueuedWhenNoDirectRouteWorks() {
        val route = router.send(true, true, { false }, { false })
        assertNull(route)
    }

    @Test fun neverAttemptsAnyRouteThatIsNotReady() {
        var nearbyAttempted = false
        var wifiAttempted = false
        val route = router.send(false, false,
            { nearbyAttempted = true; true },
            { wifiAttempted = true; true })
        assertNull(route)
        assertEquals(false, nearbyAttempted)
        assertEquals(false, wifiAttempted)
    }
}
