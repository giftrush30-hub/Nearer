package com.nearer.app.games.ludo

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoDoubleDiceEngineTest {
    private class PairRandom(private val values: List<Int>) : Random() {
        private var index = 0
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(from: Int, until: Int): Int {
            val value = values[index++]
            require(value in from until until)
            return value
        }
    }

    private fun game(tokens: List<Int>, secondTokens: List<Int> = listOf(-1, -1, -1, -1)) =
        LudoState(
            players = listOf(
                LudoPlayer("red", "Hope", LudoColor.RED, false, tokens),
                LudoPlayer("green", "Guest", LudoColor.GREEN, false, secondTokens)
            )
        )

    @Test fun combinedDiceMoveMovesOneTokenByTheTotal() {
        val rolled = LudoDoubleDiceEngine.roll(
            LudoDoubleDiceState(game(listOf(4, 55, -1, -1))),
            PairRandom(listOf(6, 2))
        )
        val combined = LudoDoubleDiceEngine.selectDie(rolled, LudoDieSlot.BOTH)
        assertEquals(8, combined.game.dice)
        assertEquals(listOf(0), LudoDoubleDiceEngine.legalTokenIndexes(combined))
        val moved = LudoDoubleDiceEngine.move(combined, 0)
        assertEquals(12, moved.game.players[0].tokens[0])
        assertNull(moved.turn)
        assertEquals(1, moved.game.activePlayerIndex)
    }

    @Test fun rollingCreatesTwoVisibleIndependentResults() {
        val state = LudoDoubleDiceEngine.roll(
            LudoDoubleDiceState(game(listOf(-1, -1, -1, -1))),
            PairRandom(listOf(3, 6))
        )
        assertEquals(LudoDicePair(3, 6), state.dice)
        assertEquals(setOf(LudoDieSlot.SECOND), state.turn?.remaining)
        assertTrue(state.awaitingDieSelection)
    }

    @Test fun selectingDieExposesOnlyMovesForThatValue() {
        val rolled = LudoDoubleDiceEngine.roll(
            LudoDoubleDiceState(game(listOf(-1, -1, -1, -1))),
            PairRandom(listOf(3, 6))
        )
        val selected = LudoDoubleDiceEngine.selectDie(rolled, LudoDieSlot.SECOND)
        assertEquals(6, selected.game.dice)
        assertEquals(listOf(0, 1, 2, 3), LudoDoubleDiceEngine.legalTokenIndexes(selected))
        assertTrue(selected.awaitingTokenSelection)
    }

    @Test fun afterFirstMoveSecondDieIsReevaluatedAndUsedBySamePlayer() {
        val rolled = LudoDoubleDiceEngine.roll(
            LudoDoubleDiceState(game(listOf(4, 20, -1, -1))),
            PairRandom(listOf(2, 5))
        )
        val first = LudoDoubleDiceEngine.selectDie(rolled, LudoDieSlot.FIRST)
        val afterFirst = LudoDoubleDiceEngine.move(first, 0)
        assertEquals(0, afterFirst.game.activePlayerIndex)
        assertEquals(1, afterFirst.game.turnNumber)
        assertEquals(setOf(LudoDieSlot.SECOND), afterFirst.turn?.remaining)
        val second = LudoDoubleDiceEngine.selectDie(afterFirst, LudoDieSlot.SECOND)
        assertEquals(5, second.game.dice)
        assertEquals(listOf(0, 1), LudoDoubleDiceEngine.legalTokenIndexes(second))
        val complete = LudoDoubleDiceEngine.move(second, 1)
        assertNull(complete.turn)
        assertEquals(1, complete.game.activePlayerIndex)
        assertEquals(2, complete.game.turnNumber)
        assertEquals(6, complete.game.players[0].tokens[0])
        assertEquals(25, complete.game.players[0].tokens[1])
    }

    @Test fun doubleSixGrantsAnotherTurnAfterBothDiceAreSpent() {
        val rolled = LudoDoubleDiceEngine.roll(
            LudoDoubleDiceState(game(listOf(4, -1, -1, -1))),
            PairRandom(listOf(6, 6))
        )
        val first = LudoDoubleDiceEngine.selectDie(rolled, LudoDieSlot.FIRST)
        val afterFirst = LudoDoubleDiceEngine.move(first, 0)
        assertEquals(0, afterFirst.game.activePlayerIndex)
        assertEquals(1, afterFirst.game.turnNumber)
        assertEquals(setOf(LudoDieSlot.SECOND), afterFirst.turn?.remaining)
        val second = LudoDoubleDiceEngine.selectDie(afterFirst, LudoDieSlot.SECOND)
        val complete = LudoDoubleDiceEngine.move(second, 0)
        assertNull(complete.turn)
        assertEquals(0, complete.game.activePlayerIndex)
        assertEquals(1, complete.game.turnNumber)
        assertTrue(complete.game.message.contains("another turn"))
    }

    @Test fun unusableDieDoesNotDiscardThePlayableOne() {
        val rolled = LudoDoubleDiceEngine.roll(
            LudoDoubleDiceState(game(listOf(55, -1, -1, -1))),
            PairRandom(listOf(2, 1))
        )
        assertEquals(setOf(LudoDieSlot.SECOND), rolled.turn?.remaining)
        val selected = LudoDoubleDiceEngine.selectDie(rolled, LudoDieSlot.SECOND)
        assertEquals(listOf(0), LudoDoubleDiceEngine.legalTokenIndexes(selected))
        assertFalse(LudoDoubleDiceEngine.canRoll(selected))
    }

    @Test fun noLegalMovePassesTurnUnlessDoubleSixEarnsAnotherRoll() {
        val blocked = LudoDoubleDiceEngine.roll(
            LudoDoubleDiceState(game(listOf(55, 55, 55, 55))),
            PairRandom(listOf(2, 3))
        )
        assertNull(blocked.turn)
        assertEquals(1, blocked.game.activePlayerIndex)
        val doubleSix = LudoDoubleDiceEngine.roll(
            LudoDoubleDiceState(game(listOf(55, 55, 55, 55))),
            PairRandom(listOf(6, 6))
        )
        assertNull(doubleSix.turn)
        assertEquals(0, doubleSix.game.activePlayerIndex)
        assertTrue(doubleSix.game.message.contains("Roll again"))
    }
}
