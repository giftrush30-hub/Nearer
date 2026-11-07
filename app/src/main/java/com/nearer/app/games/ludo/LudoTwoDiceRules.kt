package com.nearer.app.games.ludo

/** NeaRer Royal Double Dice: two independent movement allowances, never summed. */
enum class LudoDieSlot { FIRST, SECOND, BOTH }

data class LudoDicePair(val first: Int, val second: Int) {
    init { require(first in 1..6 && second in 1..6) { "Dice values must be between 1 and 6" } }
    fun value(slot: LudoDieSlot): Int = when (slot) { LudoDieSlot.FIRST -> first; LudoDieSlot.SECOND -> second; LudoDieSlot.BOTH -> first + second }
    val isDouble: Boolean get() = first == second
    val isDoubleSix: Boolean get() = first == 6 && second == 6
}

data class LudoTwoDiceTurn(
    val dice: LudoDicePair,
    val remaining: Set<LudoDieSlot> = setOf(LudoDieSlot.FIRST, LudoDieSlot.SECOND),
    val selected: LudoDieSlot? = null
) {
    val isResolved: Boolean get() = remaining.isEmpty() && selected == null
}

/** Pure two-dice rules shared by the game engine, UI and tests. */
object LudoTwoDiceRules {
    /** A die is playable only when at least one token can legally use its value. */
    fun playableDice(player: LudoPlayer, turn: LudoTwoDiceTurn): Set<LudoDieSlot> =
        turn.remaining.filterTo(linkedSetOf()) { slot ->
            LudoEngine.legalMoves(player, turn.dice.value(slot)).isNotEmpty()
        }.also { playable ->
            if (LudoDieSlot.FIRST in turn.remaining && LudoDieSlot.SECOND in turn.remaining &&
                LudoEngine.legalMoves(player, turn.dice.first + turn.dice.second).isNotEmpty()) {
                playable += LudoDieSlot.BOTH
            }
        }

    /** Select a playable die; unusable dice are automatically discarded. */
    fun selectDie(player: LudoPlayer, turn: LudoTwoDiceTurn, slot: LudoDieSlot): LudoTwoDiceTurn? {
        val playable = playableDice(player, turn)
        if (slot !in playable) return null
        return turn.copy(remaining = turn.remaining.filterTo(linkedSetOf()) { it != LudoDieSlot.BOTH }, selected = slot)
    }

    fun legalTokenIndexes(player: LudoPlayer, turn: LudoTwoDiceTurn): List<Int> {
        val selected = turn.selected ?: return emptyList()
        return LudoEngine.legalMoves(player, turn.dice.value(selected))
    }

    /** Re-evaluate the second die after the first move changes the board. */
    fun completeSelectedDie(playerAfterMove: LudoPlayer, turn: LudoTwoDiceTurn): LudoTwoDiceTurn {
        val selected = turn.selected ?: return turn
        val next = turn.copy(remaining = if (selected == LudoDieSlot.BOTH) emptySet() else turn.remaining - selected, selected = null)
        return next.copy(remaining = playableDice(playerAfterMove, next))
    }

    /** In this ruleset only double six grants a bonus roll, after both dice resolve. */
    fun bonusTurnAfterPair(dice: LudoDicePair): Boolean = dice.isDoubleSix

    fun hasMovesRemaining(player: LudoPlayer, turn: LudoTwoDiceTurn): Boolean =
        playableDice(player, turn).isNotEmpty()
}
