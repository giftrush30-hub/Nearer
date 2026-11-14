package com.nearer.app.games.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LudoRoomStateCodecTest {
    @Test fun roundTripsFreshGame() {
        val state = LudoDoubleDiceState(LudoEngine.newGame("Hope", 4, computerOpponents = false))
        val decoded = LudoRoomStateCodec.decode(LudoRoomStateCodec.encode(state)!!)
        assertNotNull(decoded)
        assertEquals(state, decoded)
    }

    @Test fun roundTripsPartiallyUsedDoubleDiceTurn() {
        val game = LudoEngine.newGame("Hope", 2, computerOpponents = false).copy(
            dice = 6,
            legalTokenIndexes = listOf(0, 1, 2, 3)
        )
        val state = LudoDoubleDiceState(
            game = game,
            turn = LudoTwoDiceTurn(
                LudoDicePair(6, 4),
                remaining = setOf(LudoDieSlot.FIRST, LudoDieSlot.SECOND),
                selected = LudoDieSlot.FIRST
            ),
            extraTurnPending = false
        )
        val encoded = LudoRoomStateCodec.encode(state)
        assertNotNull(encoded)
        assertEquals(state, LudoRoomStateCodec.decode(encoded!!))
    }

    @Test fun rejectsUnsupportedVersionAndInvalidTokens() {
        assertNull(LudoRoomStateCodec.decode("""{"version":99}"""))
        val invalid = """{"version":1,"players":[{"id":"a","name":"A","color":"RED","tokens":[-1,-1,-1,99]},{"id":"b","name":"B","color":"YELLOW","tokens":[-1,-1,-1,-1]}],"activePlayerIndex":0}"""
        assertNull(LudoRoomStateCodec.decode(invalid))
        assertNull(LudoRoomStateCodec.decode("x".repeat(12001)))
    }
}
