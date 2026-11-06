package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoEngineTest {
    @Test fun sixLetsTokenLeaveYardAndKeepsTurn() {
        val state = LudoEngine.rollDice(LudoEngine.newGame(), 6)
        assertEquals(listOf(0, 1, 2, 3), LudoEngine.legalMoves(state))
        val moved = LudoEngine.moveToken(state, 0).state
        assertEquals(0, moved.players[0].tokens[0].steps)
        assertEquals(0, moved.currentPlayerIndex)
    }

    @Test fun nonSixCannotLeaveYardAndPassesTurn() {
        val state = LudoEngine.rollDice(LudoEngine.newGame(), 4)
        assertTrue(LudoEngine.legalMoves(state).isEmpty())
        val passed = LudoEngine.passTurn(state)
        assertEquals(1, passed.currentPlayerIndex)
        assertEquals(null, passed.diceValue)
    }

    @Test fun tokenMustReachFinishWithExactCount() {
        val players = LudoEngine.newGame().players.toMutableList()
        players[0] = players[0].copy(tokens = listOf(LudoToken(57), LudoToken(), LudoToken(), LudoToken()))
        val state = LudoEngine.rollDice(LudoGameState(players = players), 1)
        val result = LudoEngine.moveToken(state, 0)
        assertEquals(58, result.state.players[0].tokens[0].steps)
        assertTrue(result.tokenFinished)
    }

    @Test fun landingOnUnsafeOpponentSquareCapturesToken() {
        val players = LudoEngine.newGame().players.toMutableList()
        players[0] = players[0].copy(tokens = listOf(LudoToken(4), LudoToken(), LudoToken(), LudoToken()))
        // Green's relative step 44 shares Red's global square 5.
        players[1] = players[1].copy(tokens = listOf(LudoToken(44), LudoToken(), LudoToken(), LudoToken()))
        val state = LudoEngine.rollDice(LudoGameState(players = players), 1)
        val result = LudoEngine.moveToken(state, 0)
        assertEquals(LudoToken(), result.state.players[1].tokens[0])
        assertEquals(listOf(LudoColor.GREEN to 0), result.captured)
    }

    @Test fun safeSquareDoesNotCapture() {
        val players = LudoEngine.newGame().players.toMutableList()
        players[0] = players[0].copy(tokens = listOf(LudoToken(7), LudoToken(), LudoToken(), LudoToken()))
        // Green's relative step 47 shares Red's safe global square 8.
        players[1] = players[1].copy(tokens = listOf(LudoToken(47), LudoToken(), LudoToken(), LudoToken()))
        val state = LudoEngine.rollDice(LudoGameState(players = players), 1)
        val result = LudoEngine.moveToken(state, 0)
        assertEquals(47, result.state.players[1].tokens[0].steps)
        assertTrue(result.captured.isEmpty())
    }
}
