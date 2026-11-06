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

    @Test fun tokenFinishesOnTheExactFinalHomeSquare() {
        val players = LudoEngine.newGame().players.toMutableList()
        players[0] = players[0].copy(tokens = listOf(LudoToken(56), LudoToken(), LudoToken(), LudoToken()))
        val state = LudoEngine.rollDice(LudoGameState(players = players), 1)
        val result = LudoEngine.moveToken(state, 0)
        assertEquals(57, result.state.players[0].tokens[0].steps)
        assertTrue(result.tokenFinished)
    }

    @Test fun overshootingHomeIsNotLegal() {
        val players = LudoEngine.newGame().players.toMutableList()
        players[0] = players[0].copy(tokens = listOf(LudoToken(56), LudoToken(), LudoToken(), LudoToken()))
        val state = LudoEngine.rollDice(LudoGameState(players = players), 2)
        assertTrue(LudoEngine.legalMoves(state).isEmpty())
    }

    @Test fun landingOnUnsafeOpponentSquareCapturesSingleTokenAndGrantsBonusTurn() {
        val players = LudoEngine.newGame().players.toMutableList()
        players[0] = players[0].copy(tokens = listOf(LudoToken(4), LudoToken(), LudoToken(), LudoToken()))
        // Green's start offset is 39; relative step 17 shares Red's global square 4.
        players[1] = players[1].copy(tokens = listOf(LudoToken(17), LudoToken(), LudoToken(), LudoToken()))
        val state = LudoEngine.rollDice(LudoGameState(players = players), 1)
        val result = LudoEngine.moveToken(state, 0)
        assertEquals(LudoToken(), result.state.players[1].tokens[0])
        assertEquals(listOf(LudoColor.GREEN to 0), result.captured)
        assertEquals(0, result.state.currentPlayerIndex)
    }

    @Test fun safeSquareDoesNotCapture() {
        val players = LudoEngine.newGame().players.toMutableList()
        players[0] = players[0].copy(tokens = listOf(LudoToken(7), LudoToken(), LudoToken(), LudoToken()))
        players[1] = players[1].copy(tokens = listOf(LudoToken(21), LudoToken(), LudoToken(), LudoToken()))
        val state = LudoEngine.rollDice(LudoGameState(players = players), 1)
        val result = LudoEngine.moveToken(state, 0)
        assertEquals(LudoToken(21), result.state.players[1].tokens[0])
        assertTrue(result.captured.isEmpty())
    }

    @Test fun aPairOnOneSquareCannotBeCaptured() {
        val players = LudoEngine.newGame().players.toMutableList()
        players[0] = players[0].copy(tokens = listOf(LudoToken(3), LudoToken(), LudoToken(), LudoToken()))
        players[1] = players[1].copy(tokens = listOf(LudoToken(16), LudoToken(16), LudoToken(), LudoToken()))
        val state = LudoEngine.rollDice(LudoGameState(players = players), 1)
        assertTrue(LudoEngine.legalMoves(state).isEmpty())
        assertEquals(LudoToken(16), state.players[1].tokens[0])
        assertEquals(LudoToken(16), state.players[1].tokens[1])
    }

    @Test fun thirdConsecutiveSixForfeitsTurn() {
        var state = LudoEngine.newGame()
        state = LudoEngine.moveToken(LudoEngine.rollDice(state, 6), 0).state
        state = LudoEngine.moveToken(LudoEngine.rollDice(state, 6), 1).state
        state = LudoEngine.rollDice(state, 6)
        assertTrue(LudoEngine.legalMoves(state).isEmpty())
        state = LudoEngine.passTurn(state)
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(0, state.consecutiveSixes)
    }
}
