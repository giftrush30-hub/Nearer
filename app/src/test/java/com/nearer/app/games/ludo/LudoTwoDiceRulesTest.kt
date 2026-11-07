package com.nearer.app.games.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoTwoDiceRulesTest {
    private fun player(tokens: List<Int>) =
        LudoPlayer("p1", "Player", LudoColor.RED, false, tokens)

    @Test fun eitherDieShowingSixCanReleaseAToken() {
        val yard = player(listOf(-1, -1, -1, -1))
        val turn = LudoTwoDiceTurn(LudoDicePair(3, 6))
        assertEquals(setOf(LudoDieSlot.SECOND), LudoTwoDiceRules.playableDice(yard, turn))
        val selected = LudoTwoDiceRules.selectDie(yard, turn, LudoDieSlot.SECOND)!!
        assertEquals(listOf(0, 1, 2, 3), LudoTwoDiceRules.legalTokenIndexes(yard, selected))
    }

    @Test fun playerCanChooseSeparateDiceOrOptionalCombinedTotal() {
        val player = player(listOf(4, 20, -1, -1))
        val turn = LudoTwoDiceTurn(LudoDicePair(2, 5))
        assertEquals(setOf(LudoDieSlot.FIRST, LudoDieSlot.SECOND, LudoDieSlot.BOTH), LudoTwoDiceRules.playableDice(player, turn))
        val selected = LudoTwoDiceRules.selectDie(player, turn, LudoDieSlot.FIRST)!!
        assertEquals(listOf(0, 1), LudoTwoDiceRules.legalTokenIndexes(player, selected))
        val combined = LudoTwoDiceRules.selectDie(player, turn, LudoDieSlot.BOTH)!!
        assertEquals(7, combined.dice.value(LudoDieSlot.BOTH))
        assertEquals(listOf(0, 1), LudoTwoDiceRules.legalTokenIndexes(player, combined))
    }

    @Test fun playerCanSpendBothDiceOnDifferentTokens() {
        val before = player(listOf(4, 20, -1, -1))
        val first = LudoTwoDiceRules.selectDie(before, LudoTwoDiceTurn(LudoDicePair(2, 5)), LudoDieSlot.FIRST)!!
        val afterFirst = before.copy(tokens = listOf(6, 20, -1, -1))
        val remaining = LudoTwoDiceRules.completeSelectedDie(afterFirst, first)
        assertEquals(setOf(LudoDieSlot.SECOND), remaining.remaining)
        val second = LudoTwoDiceRules.selectDie(afterFirst, remaining, LudoDieSlot.SECOND)!!
        assertEquals(listOf(0, 1), LudoTwoDiceRules.legalTokenIndexes(afterFirst, second))
    }

    @Test fun playerCanSpendBothDiceOnTheSameToken() {
        val before = player(listOf(4, -1, -1, -1))
        val first = LudoTwoDiceRules.selectDie(before, LudoTwoDiceTurn(LudoDicePair(2, 3)), LudoDieSlot.FIRST)!!
        val afterFirst = before.copy(tokens = listOf(6, -1, -1, -1))
        val remaining = LudoTwoDiceRules.completeSelectedDie(afterFirst, first)
        val second = LudoTwoDiceRules.selectDie(afterFirst, remaining, LudoDieSlot.SECOND)!!
        assertEquals(listOf(0), LudoTwoDiceRules.legalTokenIndexes(afterFirst, second))
    }

    @Test fun unusableDieIsDiscardedAndOtherDieCanStillBeUsed() {
        val nearHome = player(listOf(55, -1, -1, -1))
        val turn = LudoTwoDiceTurn(LudoDicePair(2, 1))
        assertEquals(setOf(LudoDieSlot.SECOND), LudoTwoDiceRules.playableDice(nearHome, turn))
        assertFalse(LudoTwoDiceRules.hasMovesRemaining(nearHome, LudoTwoDiceTurn(LudoDicePair(2, 2), emptySet())))
    }

    @Test fun onlyDoubleSixAwardsBonusTurn() {
        assertTrue(LudoTwoDiceRules.bonusTurnAfterPair(LudoDicePair(6, 6)))
        assertFalse(LudoTwoDiceRules.bonusTurnAfterPair(LudoDicePair(6, 5)))
        assertFalse(LudoTwoDiceRules.bonusTurnAfterPair(LudoDicePair(3, 3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun diceRejectValuesOutsideOneToSix() { LudoDicePair(0, 6) }
}
