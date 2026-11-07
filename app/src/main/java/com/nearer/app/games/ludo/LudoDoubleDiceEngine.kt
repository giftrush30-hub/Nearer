package com.nearer.app.games.ludo

import kotlin.random.Random

/**
 * Turn-level coordinator for the Royal Double Dice ruleset.
 *
 * It deliberately keeps the existing LudoState as the canonical board state,
 * while tracking the two dice and their remaining movement allowances here.
 */
data class LudoDoubleDiceState(
    val game: LudoState,
    val turn: LudoTwoDiceTurn? = null,
    val extraTurnPending: Boolean = false
) {
    val dice: LudoDicePair? get() = turn?.dice
    val awaitingDieSelection: Boolean get() = turn != null && turn.selected == null
    val awaitingTokenSelection: Boolean get() = turn?.selected != null
    val isFinished: Boolean get() = game.isFinished
}

object LudoDoubleDiceEngine {

    /** Roll two independent dice and calculate which results can legally be spent. */
    fun roll(state: LudoDoubleDiceState, random: Random = Random.Default): LudoDoubleDiceState {
        if (state.game.isFinished || state.turn != null) return state
        val pair = LudoDicePair(random.nextInt(1, 7), random.nextInt(1, 7))
        val turn = LudoTwoDiceTurn(pair)
        val playable = LudoTwoDiceRules.playableDice(state.game.activePlayer, turn)
        if (playable.isEmpty()) {
            val doubleBonus = pair.isDoubleSix
            val nextIndex = if (doubleBonus) state.game.activePlayerIndex
                else (state.game.activePlayerIndex + 1) % state.game.players.size
            val nextPlayer = state.game.players[nextIndex]
            val message = if (doubleBonus) {
                "${state.game.activePlayer.name} rolled double six but has no legal move. Roll again!"
            } else {
                "${state.game.activePlayer.name} has no legal move. ${nextPlayer.name}'s turn"
            }
            return state.copy(
                game = state.game.copy(
                    activePlayerIndex = nextIndex,
                    dice = null,
                    legalTokenIndexes = emptyList(),
                    message = message,
                    turnNumber = state.game.turnNumber + if (doubleBonus) 0 else 1,
                    lastAction = "Rolled ${pair.first} + ${pair.second} • no legal move"
                ),
                turn = null,
                extraTurnPending = false
            )
        }
        return state.copy(
            game = state.game.copy(
                dice = null,
                legalTokenIndexes = emptyList(),
                message = "${state.game.activePlayer.name}, choose a die, then a highlighted token",
                lastAction = "Rolled ${pair.first} + ${pair.second}"
            ),
            turn = turn.copy(remaining = playable),
            extraTurnPending = pair.isDoubleSix
        )
    }

    fun selectDie(state: LudoDoubleDiceState, slot: LudoDieSlot): LudoDoubleDiceState {
        val turn = state.turn ?: return state
        if (turn.selected != null) return state
        val selected = LudoTwoDiceRules.selectDie(state.game.activePlayer, turn, slot) ?: return state
        val legal = LudoTwoDiceRules.legalTokenIndexes(state.game.activePlayer, selected)
        return state.copy(
            turn = selected,
            game = state.game.copy(
                dice = selected.dice.value(slot),
                legalTokenIndexes = legal,
                message = "Move a highlighted token by ${selected.dice.value(slot)}"
            )
        )
    }

    /** Apply exactly one die, then re-check the other die against the changed board. */
    fun move(state: LudoDoubleDiceState, tokenIndex: Int): LudoDoubleDiceState {
        val turn = state.turn ?: return state
        val slot = turn.selected ?: return state
        val playerIndex = state.game.activePlayerIndex
        val player = state.game.activePlayer
        val dieValue = turn.dice.value(slot)
        val legal = LudoEngine.legalMoves(player, dieValue)
        if (tokenIndex !in legal) return state

        val singleDieInput = state.game.copy(dice = dieValue, legalTokenIndexes = legal)
        val moved = LudoEngine.move(singleDieInput, tokenIndex)
        if (moved.isFinished) return state.copy(game = moved, turn = null, extraTurnPending = false)

        val action = moved.lastAction
        // Royal Double Dice awards a bonus roll for double six only.
        // Captures and ordinary sixes do not override this mode's explicit rule.
        val earnedExtraTurn = state.extraTurnPending || turn.dice.isDoubleSix

        // LudoEngine.move advances the player after an ordinary single-die move.
        // In a two-dice turn we retain the same player until both dice are resolved.
        val samePlayerBoard = moved.copy(
            activePlayerIndex = playerIndex,
            dice = null,
            legalTokenIndexes = emptyList()
        )
        val afterDie = turn.copy(
            remaining = turn.remaining - slot,
            selected = null
        )
        val playableRemaining = LudoTwoDiceRules.playableDice(
            samePlayerBoard.activePlayer,
            afterDie
        )
        val nextTurn = afterDie.copy(remaining = playableRemaining)

        if (playableRemaining.isNotEmpty()) {
            return state.copy(
                game = samePlayerBoard.copy(
                    message = "Use your remaining die",
                    lastAction = action
                ),
                turn = nextTurn,
                extraTurnPending = earnedExtraTurn
            )
        }

        val getsAnotherTurn = earnedExtraTurn || turn.dice.isDoubleSix
        val nextIndex = if (getsAnotherTurn) playerIndex else (playerIndex + 1) % moved.players.size
        val nextPlayer = moved.players[nextIndex]
        return state.copy(
            game = samePlayerBoard.copy(
                activePlayerIndex = nextIndex,
                message = if (getsAnotherTurn) "${player.name} gets another turn"
                    else "${nextPlayer.name}'s turn",
                turnNumber = samePlayerBoard.turnNumber + if (getsAnotherTurn) 0 else 1,
                lastAction = action
            ),
            turn = null,
            extraTurnPending = false
        )
    }

    fun legalTokenIndexes(state: LudoDoubleDiceState): List<Int> {
        val turn = state.turn ?: return emptyList()
        return LudoTwoDiceRules.legalTokenIndexes(state.game.activePlayer, turn)
    }

    fun canRoll(state: LudoDoubleDiceState): Boolean =
        !state.game.isFinished && state.turn == null
}
