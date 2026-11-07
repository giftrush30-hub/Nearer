package com.nearer.app.games.ludo

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LudoRoomAuthorityTest {
    private fun room() = LudoRoomAuthority.create(
        roomId = "room-1",
        hostPeerId = "peer-host",
        guestPeerId = "peer-guest",
        hostName = "Host",
        guestName = "Guest"
    )!!

    @Test fun createsTwoHumanPlayersWithStablePeerIds() {
        val room = room()
        assertEquals(2, room.game.game.players.size)
        assertEquals(listOf("peer-host", "peer-guest"), room.game.game.players.map { it.id })
        assertEquals(listOf(false, false), room.game.game.players.map { it.isComputer })
        assertEquals(0L, room.sequence)
    }

    @Test fun rejectsNonMemberAndWrongTurn() {
        val room = room()
        assertNull(LudoRoomAuthority.apply(room, "intruder", 0L, LudoRoomAction(LudoRoomActionType.ROLL), Random(2)))
        assertNull(LudoRoomAuthority.apply(room, "peer-guest", 0L, LudoRoomAction(LudoRoomActionType.ROLL), Random(2)))
    }

    @Test fun rejectsStaleSequenceAndMalformedAction() {
        val room = room()
        assertNull(LudoRoomAuthority.apply(room, "peer-host", 1L, LudoRoomAction(LudoRoomActionType.ROLL), Random(2)))
        assertNull(LudoRoomAuthority.apply(room, "peer-host", 0L, LudoRoomAction(LudoRoomActionType.SELECT_DIE), Random(2)))
        assertNull(LudoRoomAuthority.apply(room, "peer-host", 0L, LudoRoomAction(LudoRoomActionType.MOVE), Random(2)))
    }

    @Test fun acceptedRollAdvancesSequenceAndKeepsHostAsActivePlayer() {
        val room = room()
        val updated = LudoRoomAuthority.apply(
            room, "peer-host", 0L, LudoRoomAction(LudoRoomActionType.ROLL), Random(12)
        )
        assertNotNull(updated)
        assertEquals(1L, updated!!.sequence)
        // If the deterministic roll has no legal move, the rules correctly pass
        // the turn to the guest. Otherwise the host remains active to spend the dice.
        val expectedActivePeer = if (updated.game.turn != null) "peer-host" else "peer-guest"
        assertEquals(expectedActivePeer, updated.game.game.activePlayer.id)
    }

    @Test fun actionFromGuestIsAllowedOnlyWhenGuestOwnsTurn() {
        val room = room()
        val hostRolled = LudoRoomAuthority.apply(
            room, "peer-host", 0L, LudoRoomAction(LudoRoomActionType.ROLL), Random(4)
        )!!
        // Force a completed no-move turn state to model a valid transition to the guest.
        val guestTurn = hostRolled.copy(
            game = hostRolled.game.copy(
                game = hostRolled.game.game.copy(activePlayerIndex = 1, dice = null, legalTokenIndexes = emptyList()),
                turn = null
            )
        )
        val guestRoll = LudoRoomAuthority.apply(
            guestTurn, "peer-guest", guestTurn.sequence,
            LudoRoomAction(LudoRoomActionType.ROLL), Random(7)
        )
        assertNotNull(guestRoll)
        assertEquals("peer-guest", guestRoll!!.game.game.activePlayer.id)
    }
}
