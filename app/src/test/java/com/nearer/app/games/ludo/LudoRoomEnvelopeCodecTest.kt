package com.nearer.app.games.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LudoRoomEnvelopeCodecTest {
    private fun envelope(sequence: Long = 9L): LudoRoomStateEnvelope {
        val room = LudoRoomAuthority.create("room-123", "host-device", "guest-device", "Host", "Guest")!!
        return LudoRoomStateEnvelope(room.roomId, room.hostPeerId, room.guestPeerId, sequence, room.game)
    }

    @Test fun roundTripsRoomBoundSnapshot() {
        val encoded = LudoRoomEnvelopeCodec.encode(envelope())!!
        val decoded = LudoRoomEnvelopeCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals("room-123", decoded!!.roomId)
        assertEquals("host-device", decoded.hostPeerId)
        assertEquals("guest-device", decoded.guestPeerId)
        assertEquals(9L, decoded.sequence)
        assertEquals(envelope().state, decoded.state)
    }

    @Test fun rejectsWrongPlayerIdentityAndComputerOpponent() {
        val original = envelope()
        val wrong = original.copy(guestPeerId = "different-device")
        assertNull(LudoRoomEnvelopeCodec.encode(wrong))
        val computer = original.copy(
            state = original.state.copy(
                game = original.state.game.copy(
                    players = original.state.game.players.mapIndexed { i, p ->
                        if (i == 1) p.copy(isComputer = true) else p
                    }
                )
            )
        )
        assertNull(LudoRoomEnvelopeCodec.encode(computer))
    }

    @Test fun rejectsMalformedAndStaleVersionEnvelope() {
        assertNull(LudoRoomEnvelopeCodec.decode(""))
        assertNull(LudoRoomEnvelopeCodec.decode("""{"version":99}"""))
        assertNull(LudoRoomEnvelopeCodec.decode("x".repeat(16_000)))
    }
}

class LudoRoomActionCodecTest {
    @Test fun roundTripsAllSupportedActions() {
        val actions = listOf(
            LudoRoomAction(LudoRoomActionType.ROLL),
            LudoRoomAction(LudoRoomActionType.SELECT_DIE, dieSlot = LudoDieSlot.SECOND),
            LudoRoomAction(LudoRoomActionType.MOVE, tokenIndex = 3)
        )
        actions.forEach { action ->
            assertEquals(action, LudoRoomActionCodec.decode(LudoRoomActionCodec.encode(action)!!))
        }
    }

    @Test fun rejectsInvalidActionCombinations() {
        assertNull(LudoRoomActionCodec.encode(LudoRoomAction(LudoRoomActionType.ROLL, dieSlot = LudoDieSlot.FIRST)))
        assertNull(LudoRoomActionCodec.encode(LudoRoomAction(LudoRoomActionType.MOVE, tokenIndex = 4)))
        assertNull(LudoRoomActionCodec.decode("""{"action":"EXPLOIT","dieSlot":null,"tokenIndex":null}"""))
        assertNull(LudoRoomActionCodec.decode("""{"action":"SELECT_DIE","dieSlot":"FIRST","tokenIndex":2}"""))
    }
}
