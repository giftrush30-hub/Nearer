package com.nearer.app

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastShareSocketRegistryTest {
    private fun connectedSockets(): Pair<Socket, Socket> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            val client = Socket(InetAddress.getLoopbackAddress(), listener.localPort)
            val server = listener.accept()
            return client to server
        }
    }

    @Test fun closeAllClosesEveryTrackedClientAndEmptiesRegistry() {
        val registry = FastShareSocketRegistry()
        val (client, server) = connectedSockets()
        try {
            assertTrue(registry.track(client))
            assertEquals(1, registry.trackedCount())

            registry.closeAll()

            assertTrue(client.isClosed)
            assertEquals(0, registry.trackedCount())
            assertFalse(registry.track(server))
            assertTrue(server.isClosed)
        } finally {
            runCatching { client.close() }
            runCatching { server.close() }
        }
    }

    @Test fun releasedSocketIsNotClosedWhenSessionStops() {
        val registry = FastShareSocketRegistry()
        val (client, server) = connectedSockets()
        try {
            assertTrue(registry.track(client))
            registry.release(client)
            registry.closeAll()

            assertFalse(client.isClosed)
            assertEquals(0, registry.trackedCount())
        } finally {
            client.close()
            server.close()
        }
    }

    @Test fun socketsAcceptedAfterShutdownAreClosedImmediately() {
        val registry = FastShareSocketRegistry()
        val (client, server) = connectedSockets()
        try {
            registry.closeAll()

            assertFalse(registry.track(client))
            assertTrue(client.isClosed)
            assertEquals(0, registry.trackedCount())
        } finally {
            runCatching { client.close() }
            runCatching { server.close() }
        }
    }
}
