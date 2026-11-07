package com.nearer.app.games.ludo

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class LudoRoomSessionTest {
    private fun host() = LudoRoomSession.host("room-1", "host", "guest", "Host", "Guest")!!

    @Test fun hostIsOnlyWriterAndPublishesSequencedSnapshot() {
        val host = host()
        assertNull(host.createGuestRequest(LudoRoomAction(LudoRoomActionType.ROLL)))
        val updated = host.applyLocalHostAction(LudoRoomAction(LudoRoomActionType.ROLL), Random(12))
        assertNotNull(updated)
        assertEquals(1L, updated!!.sequence)
        assertEquals(updated, host.snapshot)
        val expectedActive = if (updated.game.turn != null) "host" else "guest"
        assertEquals(expectedActive, host.snapshot.game.game.activePlayer.id)
    }

    @Test fun guestRequestCarriesCurrentSequenceAndIdentity() {
        val host = host()
        val first = host.applyLocalHostAction(LudoRoomAction(LudoRoomActionType.ROLL), Random(12))!!
        val guest = LudoRoomSession.guest(host.envelope(), "guest")!!
        val next = first.copy(game = first.game.copy(
            game = first.game.game.copy(activePlayerIndex = 1, dice = null, legalTokenIndexes = emptyList()),
            turn = null
        ))
        val guestSession = LudoRoomSession.guest(
            LudoRoomStateEnvelope(next.roomId, next.hostPeerId, next.guestPeerId, next.sequence, next.game),
            "guest"
        )!!
        val request = guestSession.createGuestRequest(LudoRoomAction(LudoRoomActionType.ROLL))
        assertNotNull(request)
        assertEquals("room-1", request!!.roomId)
        assertEquals("guest", request.senderPeerId)
        assertEquals(next.sequence, request.expectedSequence)
        assertNull(guest.createGuestRequest(LudoRoomAction(LudoRoomActionType.ROLL)))
    }

    @Test fun hostRejectsWrongRoomSenderAndStaleGuestRequest() {
        val host = host()
        val wrongRoom = LudoRoomActionRequest("other", "guest", 0L, LudoRoomAction(LudoRoomActionType.ROLL))
        val wrongSender = LudoRoomActionRequest("room-1", "intruder", 0L, LudoRoomAction(LudoRoomActionType.ROLL))
        assertNull(host.applyGuestRequest(wrongRoom, Random(2)))
        assertNull(host.applyGuestRequest(wrongSender, Random(2)))
        assertNull(host.applyGuestRequest(
            LudoRoomActionRequest("room-1", "guest", 4L, LudoRoomAction(LudoRoomActionType.ROLL)), Random(2)
        ))
    }

    @Test fun guestAcceptsOnlyNewSnapshotsFromItsHost() {
        val host = host()
        val guest = LudoRoomSession.guest(host.envelope(), "guest")!!
        val updated = host.applyLocalHostAction(LudoRoomAction(LudoRoomActionType.ROLL), Random(12))!!
        assertFalse(guest.acceptHostSnapshot(LudoRoomStateEnvelope(
            "wrong-room", "host", "guest", updated.sequence, updated.game
        )))
        assertFalse(guest.acceptHostSnapshot(LudoRoomStateEnvelope(
            "room-1", "intruder", "guest", updated.sequence, updated.game
        )))
        assertTrue(guest.acceptHostSnapshot(host.envelope()))
        assertEquals(updated.sequence, guest.snapshot.sequence)
        assertFalse(guest.acceptHostSnapshot(host.envelope()))
    }

    @Test fun guestCannotJoinEnvelopeWithDifferentIdentityOrder() {
        val host = host()
        val bad = host.envelope().copy(guestPeerId = "intruder")
        assertNull(LudoRoomSession.guest(bad, "intruder"))
    }
}
