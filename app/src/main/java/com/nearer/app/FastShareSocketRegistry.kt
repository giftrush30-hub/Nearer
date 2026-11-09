package com.nearer.app

import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns every accepted client socket for one Fast Share session.
 *
 * Closing the session is terminal: a socket accepted concurrently with stop()
 * is closed immediately instead of escaping the shutdown sweep.
 */
internal class FastShareSocketRegistry {
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private var closed = false

    @Synchronized
    fun track(socket: Socket): Boolean {
        if (closed) {
            runCatching { socket.close() }
            return false
        }
        return sockets.add(socket)
    }

    @Synchronized
    fun release(socket: Socket) {
        sockets.remove(socket)
    }

    @Synchronized
    fun closeAll() {
        if (closed) return
        closed = true
        sockets.forEach { socket -> runCatching { socket.close() } }
        sockets.clear()
    }

    @Synchronized
    internal fun trackedCount(): Int = sockets.size
}
