package com.nearer.app.games.ludo

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoEngineTraditionalRulesTest {
    private class AlwaysSixRandom : Random() {
        override fun nextBits(bitCount: Int): Int = minOf(5, (1 shl bitCount) - 1)
    }

    @Test
    fun thirdConsecutiveSixForfeitsTheTurn() {
        var state = LudoEngine.newGame("Hope", playerCount = 2, computerOpponents = false)

        repeat(2) {
            state = LudoEngine.roll(state, AlwaysSixRandom)
            assertEquals(6, state.dice)
            assertTrue(state.legalTokenIndexes.isNotEmpty())
            state = LudoEngine.move(state, state.legalTokenIndexes.first())
            assertEquals(0, state.activePlayerIndex)
            assertEquals(it + 1, state.consecutiveSixes)
        }

        state = LudoEngine.roll(state, AlwaysSixRandom)

        assertEquals(1, state.activePlayerIndex)
        assertNull(state.dice)
        assertEquals(0, state.consecutiveSixes)
        assertTrue(state.message.contains("three consecutive sixes"))
    }
}
