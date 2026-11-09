package com.nearer.app

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketWriteStallGuardTest {
    private fun connectedSockets(): Pair<Socket, Socket> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            val client = Socket(InetAddress.getLoopbackAddress(), listener.localPort)
            val server = listener.accept()
            return client to server
        }
    }

    @Test fun closesSocketWhenAnArmedWriteMakesNoProgress() {
        val (client, peer) = connectedSockets()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val guard = SocketWriteStallGuard(client, scheduler, 80L)
        try {
            guard.arm()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!client.isClosed && System.nanoTime() < deadline) {
                Thread.sleep(10L)
            }
            assertTrue("A stalled receiver must release its socket", client.isClosed)
        } finally {
            guard.close()
            scheduler.shutdownNow()
            runCatching { client.close() }
            runCatching { peer.close() }
        }
    }

    @Test fun disarmedGuardDoesNotCloseAnIdleSocket() {
        val (client, peer) = connectedSockets()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val guard = SocketWriteStallGuard(client, scheduler, 80L)
        try {
            guard.arm()
            guard.disarm()
            Thread.sleep(200L)
            assertFalse("Queue idle time must not be treated as a stalled write", client.isClosed)
        } finally {
            guard.close()
            scheduler.shutdownNow()
            runCatching { client.close() }
            runCatching { peer.close() }
        }
    }
}
