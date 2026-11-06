package com.nearer.app.games.ludo

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoEngineTraditionalRulesTest {
    private class AlwaysSixRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(from: Int, until: Int): Int {
            require(from <= 6 && until > 6)
            return 6
        }
    }

    @Test
    fun nonSafeLandingCapturesAnOpponentToken() {
        val red = LudoPlayer("red", "Hope", LudoColor.RED, false, listOf(4, -1, -1, -1))
        val green = LudoPlayer("green", "Guest", LudoColor.GREEN, false, listOf(44, -1, -1, -1))
        val state = LudoState(
            players = listOf(red, green), dice = 1, legalTokenIndexes = listOf(0)
        )

        val moved = LudoEngine.move(state, 0)

        assertEquals(-1, moved.players[1].tokens[0])
        assertTrue(moved.message.contains("another turn"))
        assertEquals(0, moved.activePlayerIndex)
    }

    @Test
    fun safeSquareProtectsOpponentTokenFromCapture() {
        val red = LudoPlayer("red", "Hope", LudoColor.RED, false, listOf(7, -1, -1, -1))
        val green = LudoPlayer("green", "Guest", LudoColor.GREEN, false, listOf(47, -1, -1, -1))
        val state = LudoState(
            players = listOf(red, green), dice = 1, legalTokenIndexes = listOf(0)
        )

        val moved = LudoEngine.move(state, 0)

        assertEquals(47, moved.players[1].tokens[0])
        assertEquals(1, moved.activePlayerIndex)
    }

    @Test
    fun tokenMustReachHomeWithAnExactRoll() {
        val red = LudoPlayer("red", "Hope", LudoColor.RED, false, listOf(55, -1, -1, -1))
        assertTrue(LudoEngine.legalMoves(red, 2).isEmpty())
        assertEquals(listOf(0), LudoEngine.legalMoves(red, 1))
    }

    @Test
    fun thirdConsecutiveSixForfeitsTheTurn() {
        var state = LudoEngine.newGame("Hope", playerCount = 2, computerOpponents = false)

        repeat(2) {
            state = LudoEngine.roll(state, AlwaysSixRandom())
            assertEquals(6, state.dice)
            assertTrue(state.legalTokenIndexes.isNotEmpty())
            state = LudoEngine.move(state, state.legalTokenIndexes.first())
            assertEquals(0, state.activePlayerIndex)
            assertEquals(it + 1, state.consecutiveSixes)
        }

        state = LudoEngine.roll(state, AlwaysSixRandom())

        assertEquals(1, state.activePlayerIndex)
        assertNull(state.dice)
        assertEquals(0, state.consecutiveSixes)
        assertTrue(state.message.contains("three consecutive sixes"))
    }
}
