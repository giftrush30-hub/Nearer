package com.nearer.app

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketReadDeadlineTest {
    private fun connectedSockets(): Pair<Socket, Socket> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            val client = Socket(InetAddress.getLoopbackAddress(), listener.localPort)
            return client to listener.accept()
        }
    }

    @Test fun closesSocketAtTheOverallRequestDeadline() {
        val (client, peer) = connectedSockets()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val deadline = SocketReadDeadline(client, scheduler, 80L)
        try {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!client.isClosed && System.nanoTime() < end) Thread.sleep(10L)
            assertTrue("A slow-trickle request must not pin a worker forever", client.isClosed)
        } finally {
            deadline.close()
            scheduler.shutdownNow()
            runCatching { client.close() }
            runCatching { peer.close() }
        }
    }

    @Test fun completedRequestCanCancelItsDeadline() {
        val (client, peer) = connectedSockets()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val deadline = SocketReadDeadline(client, scheduler, 120L)
        try {
            deadline.close()
            Thread.sleep(220L)
            assertFalse("A completed request must keep its connection alive", client.isClosed)
        } finally {
            scheduler.shutdownNow()
            runCatching { client.close() }
            runCatching { peer.close() }
        }
    }
}
