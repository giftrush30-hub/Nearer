package com.nearer.app.games.ludo

import kotlin.random.Random

/** Actions a room participant may request. The host applies them to the canonical board. */
enum class LudoRoomActionType { ROLL, SELECT_DIE, MOVE }

data class LudoRoomAction(
    val type: LudoRoomActionType,
    val dieSlot: LudoDieSlot? = null,
    val tokenIndex: Int? = null
)

/** Authoritative room state. Sequence increments only when a legal action changes the board. */
data class LudoRoomSnapshot(
    val roomId: String,
    val hostPeerId: String,
    val guestPeerId: String,
    val sequence: Long,
    val game: LudoDoubleDiceState
)

object LudoRoomAuthority {
    fun create(
        roomId: String,
        hostPeerId: String,
        guestPeerId: String,
        hostName: String,
        guestName: String
    ): LudoRoomSnapshot? {
        if (roomId.isBlank() || roomId.length > 64 ||
            hostPeerId.isBlank() || guestPeerId.isBlank() ||
            hostPeerId == guestPeerId || hostName.isBlank() || guestName.isBlank()) return null
        val initial = LudoEngine.newGame(hostName, playerCount = 2, computerOpponents = false)
        val players = initial.players.mapIndexed { index, player ->
            if (index == 0) player.copy(id = hostPeerId, name = hostName.take(24), isComputer = false)
            else player.copy(id = guestPeerId, name = guestName.take(24), isComputer = false)
        }
        return LudoRoomSnapshot(
            roomId = roomId,
            hostPeerId = hostPeerId,
            guestPeerId = guestPeerId,
            sequence = 0L,
            game = LudoDoubleDiceState(initial.copy(players = players))
        )
    }

    /**
     * Accept an action only from a room member whose identity matches the active player.
     * expectedSequence is the sender's last acknowledged snapshot, preventing stale actions.
     */
    fun apply(
        snapshot: LudoRoomSnapshot,
        senderPeerId: String,
        expectedSequence: Long,
        action: LudoRoomAction,
        random: Random = Random.Default
    ): LudoRoomSnapshot? {
        if (senderPeerId != snapshot.hostPeerId && senderPeerId != snapshot.guestPeerId) return null
        if (expectedSequence != snapshot.sequence || snapshot.game.isFinished) return null
        if (snapshot.game.game.activePlayer.id != senderPeerId) return null

        val next = when (action.type) {
            LudoRoomActionType.ROLL -> {
                if (snapshot.game.turn != null || action.dieSlot != null || action.tokenIndex != null) return null
                LudoDoubleDiceEngine.roll(snapshot.game, random)
            }
            LudoRoomActionType.SELECT_DIE -> {
                val slot = action.dieSlot ?: return null
                if (action.tokenIndex != null) return null
                LudoDoubleDiceEngine.selectDie(snapshot.game, slot)
            }
            LudoRoomActionType.MOVE -> {
                val token = action.tokenIndex ?: return null
                if (action.dieSlot != null) return null
                LudoDoubleDiceEngine.move(snapshot.game, token)
            }
        }
        if (next == snapshot.game) return null
        return snapshot.copy(sequence = snapshot.sequence + 1L, game = next)
    }
}
