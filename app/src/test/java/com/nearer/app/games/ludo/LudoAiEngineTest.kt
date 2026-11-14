package com.nearer.app.games.ludo

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoAiEngineTest {
    @Test fun easyChoosesOnlyLegalMove() {
        val base = LudoEngine.newGame("Human", 2, computerOpponents = true)
        val computer = base.players[1].copy(tokens = listOf(0, -1, -1, -1))
        val state = base.copy(players = listOf(base.players[0], computer), activePlayerIndex = 1,
            dice = 2, legalTokenIndexes = listOf(0))
        assertTrue(LudoAiEngine.chooseComputerMove(state, LudoAiDifficulty.EASY) == 0)
    }

    @Test fun mediumAndHardReturnLegalChoices() {
        val base = LudoEngine.newGame("Human", 2, computerOpponents = true)
        val computer = base.players[1].copy(tokens = listOf(0, 4, -1, -1))
        val state = base.copy(players = listOf(base.players[0], computer), activePlayerIndex = 1,
            dice = 2, legalTokenIndexes = listOf(0, 1))
        val medium = LudoAiEngine.chooseComputerMove(state, LudoAiDifficulty.MEDIUM)
        val hard = LudoAiEngine.chooseComputerMove(state, LudoAiDifficulty.HARD)
        assertNotNull(medium); assertNotNull(hard)
        assertTrue(medium in state.legalTokenIndexes); assertTrue(hard in state.legalTokenIndexes)
    }

    @Test fun aiDoesNotInventMovesWhenNoDieOrNoLegalToken() {
        val state = LudoEngine.newGame("Human", 2, computerOpponents = true)
        assertTrue(LudoAiEngine.chooseComputerMove(state, LudoAiDifficulty.HARD) == null)
        val rolled = state.copy(dice = 3, legalTokenIndexes = emptyList())
        assertTrue(LudoAiEngine.chooseComputerMove(rolled, LudoAiDifficulty.MEDIUM) == null)
    }
}
