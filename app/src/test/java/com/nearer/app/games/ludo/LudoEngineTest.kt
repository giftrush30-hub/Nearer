package com.nearer.app.games.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoEngineTest {
    private fun player(color: LudoColor, tokens: List<Int>, id: String = color.name) =
        LudoPlayer(id = id, name = color.label, color = color, isComputer = false, tokens = tokens)

    @Test fun tokenLeavesYardOnlyOnSix() {
        val red = player(LudoColor.RED, listOf(-1, -1, -1, -1))
        assertTrue(LudoEngine.legalMoves(red, 5).isEmpty())
        assertEquals(listOf(0, 1, 2, 3), LudoEngine.legalMoves(red, 6))
    }

    @Test fun tokenMustRollExactNumberToFinish() {
        val red = player(LudoColor.RED, listOf(55, 56, 56, 56))
        assertTrue(LudoEngine.legalMoves(red, 2).isEmpty())
        assertEquals(listOf(0), LudoEngine.legalMoves(red, 1))
    }

    @Test fun landingOnUnprotectedOpponentCapturesIt() {
        val red = player(LudoColor.RED, listOf(0, -1, -1, -1), "red")
        val green = player(LudoColor.GREEN, listOf(40, -1, -1, -1), "green")
        val state = LudoState(players = listOf(red, green), dice = 1, legalTokenIndexes = listOf(0))
        val result = LudoEngine.move(state, 0)
        assertEquals(-1, result.players[1].tokens[0])
        assertTrue(result.message.contains("another turn"))
    }

    @Test fun safeSquaresProtectOpponentTokens() {
        val red = player(LudoColor.RED, listOf(12, -1, -1, -1), "red")
        val green = player(LudoColor.GREEN, listOf(0, -1, -1, -1), "green")
        val state = LudoState(players = listOf(red, green), dice = 1, legalTokenIndexes = listOf(0))
        val result = LudoEngine.move(state, 0)
        assertEquals(0, result.players[1].tokens[0])
        assertFalse(result.lastAction.contains("captured"))
    }

    @Test fun exactFinalMoveWinsTheGame() {
        val red = player(LudoColor.RED, listOf(55, 56, 56, 56), "red")
        val green = player(LudoColor.GREEN, listOf(-1, -1, -1, -1), "green")
        val state = LudoState(players = listOf(red, green), dice = 1, legalTokenIndexes = listOf(0))
        val result = LudoEngine.move(state, 0)
        assertEquals("red", result.winnerId)
        assertTrue(result.isFinished)
    }

    @Test fun globalTrackWrapsAtFiftyTwo() {
        assertEquals(0, LudoEngine.globalSquare(LudoColor.BLUE, 13))
    }
}
