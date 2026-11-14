package com.nearer.app.games.ludo

import kotlin.random.Random

enum class LudoRoomRole { HOST, GUEST }

data class LudoRoomActionRequest(
    val roomId: String,
    val senderPeerId: String,
    val expectedSequence: Long,
    val action: LudoRoomAction
)

/**
 * Deterministic room-session coordinator.
 *
 * The host is the only writer of the canonical board. Guests emit action requests
 * against the last snapshot they have seen; snapshots are accepted only from the
 * expected host and only when their sequence is newer.
 */
class LudoRoomSession private constructor(
    val role: LudoRoomRole,
    val localPeerId: String,
    val remotePeerId: String,
    private var current: LudoRoomSnapshot
) {
    val snapshot: LudoRoomSnapshot get() = current

    fun applyLocalHostAction(action: LudoRoomAction, random: Random = Random.Default): LudoRoomSnapshot? {
        if (role != LudoRoomRole.HOST) return null
        val updated = LudoRoomAuthority.apply(current, localPeerId, current.sequence, action, random) ?: return null
        current = updated
        return current
    }

    fun createGuestRequest(action: LudoRoomAction): LudoRoomActionRequest? {
        if (role != LudoRoomRole.GUEST || current.game.game.activePlayer.id != localPeerId) return null
        return LudoRoomActionRequest(current.roomId, localPeerId, current.sequence, action)
    }

    fun applyGuestRequest(request: LudoRoomActionRequest, random: Random = Random.Default): LudoRoomSnapshot? {
        if (role != LudoRoomRole.HOST || request.roomId != current.roomId ||
            request.senderPeerId != remotePeerId) return null
        val updated = LudoRoomAuthority.apply(
            current, request.senderPeerId, request.expectedSequence, request.action, random
        ) ?: return null
        current = updated
        return current
    }

    fun acceptHostSnapshot(envelope: LudoRoomStateEnvelope): Boolean {
        if (role != LudoRoomRole.GUEST ||
            envelope.roomId != current.roomId ||
            envelope.hostPeerId != remotePeerId ||
            envelope.guestPeerId != localPeerId ||
            envelope.sequence <= current.sequence) return false
        val players = envelope.state.game.players
        if (players.size != 2 ||
            players.map { it.id } != listOf(remotePeerId, localPeerId) ||
            players.any { it.isComputer }) return false
        current = LudoRoomSnapshot(
            roomId = envelope.roomId,
            hostPeerId = envelope.hostPeerId,
            guestPeerId = envelope.guestPeerId,
            sequence = envelope.sequence,
            game = envelope.state
        )
        return true
    }

    fun envelope(): LudoRoomStateEnvelope = LudoRoomStateEnvelope(
        roomId = current.roomId,
        hostPeerId = current.hostPeerId,
        guestPeerId = current.guestPeerId,
        sequence = current.sequence,
        state = current.game
    )

    companion object {
        fun host(
            roomId: String,
            hostPeerId: String,
            guestPeerId: String,
            hostName: String,
            guestName: String
        ): LudoRoomSession? {
            val initial = LudoRoomAuthority.create(roomId, hostPeerId, guestPeerId, hostName, guestName) ?: return null
            return LudoRoomSession(LudoRoomRole.HOST, hostPeerId, guestPeerId, initial)
        }

        fun guest(envelope: LudoRoomStateEnvelope, guestPeerId: String): LudoRoomSession? {
            if (guestPeerId != envelope.guestPeerId ||
                envelope.state.game.players.map { it.id } != listOf(envelope.hostPeerId, envelope.guestPeerId) ||
                envelope.state.game.players.any { it.isComputer }) return null
            val initial = LudoRoomSnapshot(
                envelope.roomId, envelope.hostPeerId, envelope.guestPeerId,
                envelope.sequence, envelope.state
            )
            return LudoRoomSession(LudoRoomRole.GUEST, guestPeerId, envelope.hostPeerId, initial)
        }
    }
}
