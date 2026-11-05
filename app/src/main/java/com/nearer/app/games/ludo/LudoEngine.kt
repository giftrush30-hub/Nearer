package com.nearer.app.games.ludo

import kotlin.random.Random

enum class LudoColor(val label: String, val hex: Long, val startIndex: Int) {
    RED("Red", 0xFFE85D68, 0),
    GREEN("Green", 0xFF58C99B, 13),
    YELLOW("Yellow", 0xFFFFD166, 26),
    BLUE("Blue", 0xFF5B91F2, 39)
}

data class LudoPlayer(
    val id: String,
    val name: String,
    val color: LudoColor,
    val isComputer: Boolean,
    val tokens: List<Int> = List(4) { -1 }
) {
    val finishedTokens: Int get() = tokens.count { it == 56 }
}

data class LudoState(
    val players: List<LudoPlayer>,
    val activePlayerIndex: Int = 0,
    val dice: Int? = null,
    val legalTokenIndexes: List<Int> = emptyList(),
    val winnerId: String? = null,
    val message: String = "Red rolls first",
    val turnNumber: Int = 1,
    val lastAction: String = "Match ready"
) {
    val activePlayer: LudoPlayer get() = players[activePlayerIndex]
    val isFinished: Boolean get() = winnerId != null
    val canRoll: Boolean get() = !isFinished && dice == null
}

object LudoEngine {
    // Common traditional Ludo safe squares: four starts and four star squares.
    private val safeSquares = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    fun newGame(
        humanName: String,
        playerCount: Int = 4,
        computerNames: List<String> = listOf("NeaRer Bot", "NeaRer Bot 2", "NeaRer Bot 3"),
        computerOpponents: Boolean = true
    ): LudoState {
        val count = playerCount.coerceIn(2, 4)
        val colors = when (count) {
            2 -> listOf(LudoColor.RED, LudoColor.YELLOW)
            3 -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW)
            else -> LudoColor.values().toList()
        }
        val players = colors.mapIndexed { index, color ->
            LudoPlayer(
                id = "player-$index",
                name = if (index == 0) humanName.ifBlank { "You" } else computerNames.getOrElse(index - 1) { "Player ${index + 1}" },
                color = color,
                isComputer = index != 0
            )
        }
        return LudoState(players = players, message = "${players.first().name} rolls first")
    }

    fun roll(state: LudoState, random: Random = Random.Default): LudoState {
        if (!state.canRoll) return state
        val value = random.nextInt(1, 7)
        val legal = legalMoves(state.activePlayer, value)
        if (legal.isEmpty()) {
            val next = if (value == 6) state.activePlayerIndex else (state.activePlayerIndex + 1) % state.players.size
            val player = state.players[next]
            return state.copy(
                activePlayerIndex = next,
                dice = null,
                legalTokenIndexes = emptyList(),
                message = if (value == 6) "${state.activePlayer.name} rolled 6 but has no legal move. Roll again!" else "${state.activePlayer.name} has no move. ${player.name}'s turn",
                turnNumber = state.turnNumber + if (value == 6) 0 else 1,
                lastAction = "Rolled $value • no legal move"
            )
        }
        return state.copy(
            dice = value,
            legalTokenIndexes = legal,
            message = if (legal.size == 1) "Move the highlighted token" else "${state.activePlayer.name}, choose a token",
            lastAction = "Rolled $value"
        )
    }

    fun legalMoves(player: LudoPlayer, dice: Int): List<Int> {
        if (dice !in 1..6) return emptyList()
        return player.tokens.indices.filter { index ->
            val progress = player.tokens[index]
            when {
                progress == -1 -> dice == 6
                progress in 0..55 -> progress + dice <= 56
                else -> false
            }
        }
    }

    fun move(state: LudoState, tokenIndex: Int): LudoState {
        val dice = state.dice ?: return state
        if (tokenIndex !in state.legalTokenIndexes) return state
        val current = state.activePlayer
        val oldProgress = current.tokens[tokenIndex]
        val newProgress = if (oldProgress == -1) 0 else oldProgress + dice
        val movedTokens = current.tokens.toMutableList().also { it[tokenIndex] = newProgress }
        var players = state.players.toMutableList()
        players[state.activePlayerIndex] = current.copy(tokens = movedTokens)

        var captured = 0
        if (newProgress in 0..51) {
            val landing = globalSquare(current.color, newProgress)
            if (landing !in safeSquares) {
                players = players.map { opponent ->
                    if (opponent.id == current.id) opponent
                    else {
                        val reset = opponent.tokens.map { progress ->
                            if (progress in 0..51 && globalSquare(opponent.color, progress) == landing) {
                                captured++
                                -1
                            } else progress
                        }
                        opponent.copy(tokens = reset)
                    }
                }.toMutableList()
            }
        }

        val updatedPlayer = players[state.activePlayerIndex]
        val won = updatedPlayer.tokens.all { it == 56 }
        val action = when {
            won -> "${updatedPlayer.name} wins!"
            captured > 0 -> "${updatedPlayer.name} captured $captured token(s)"
            newProgress == 56 -> "${updatedPlayer.name} brought a token home"
            oldProgress == -1 -> "${updatedPlayer.name} brought a token onto the board"
            else -> "${updatedPlayer.name} moved"
        }
        val extraTurn = dice == 6 || captured > 0 || newProgress == 56
        val nextIndex = if (won || extraTurn) state.activePlayerIndex else (state.activePlayerIndex + 1) % players.size
        val nextPlayer = players[nextIndex]
        return state.copy(
            players = players,
            activePlayerIndex = nextIndex,
            dice = null,
            legalTokenIndexes = emptyList(),
            winnerId = if (won) updatedPlayer.id else null,
            message = if (won) "${updatedPlayer.name} has won the game!" else if (extraTurn) "${updatedPlayer.name} gets another turn" else "${nextPlayer.name}'s turn",
            turnNumber = state.turnNumber + if (extraTurn || won) 0 else 1,
            lastAction = action
        )
    }

    fun chooseComputerMove(state: LudoState): Int? {
        val player = state.activePlayer
        val dice = state.dice ?: return null
        return state.legalTokenIndexes.maxByOrNull { tokenIndex ->
            val old = player.tokens[tokenIndex]
            val next = if (old == -1) 0 else old + dice
            var score = next * 2
            if (old == -1) score += 30
            if (next == 56) score += 1000
            if (next in 0..51) {
                val landing = globalSquare(player.color, next)
                if (landing !in safeSquares && state.players.any { opponent ->
                    opponent.id != player.id && opponent.tokens.any { it in 0..51 && globalSquare(opponent.color, it) == landing }
                }) score += 250
                if (landing in safeSquares) score += 25
            }
            score
        }
    }

    fun globalSquare(color: LudoColor, progress: Int): Int =
        (color.startIndex + progress) % 52

    fun isSafeSquare(globalIndex: Int): Boolean = globalIndex in safeSquares
}
