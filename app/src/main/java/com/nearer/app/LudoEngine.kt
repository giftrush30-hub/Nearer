package com.nearer.app

import kotlin.random.Random

enum class LudoDifficulty { EASY, NORMAL, HARD }
enum class LudoMoveSpeed(val stepDurationMs: Int) { SLOW(180), NORMAL(105), FAST(55) }

enum class LudoColor(val startOffset: Int) {
    RED(0), GREEN(13), YELLOW(26), BLUE(39)
}

data class LudoToken(val steps: Int = -1) {
    init { require(steps in -1..57) }
    val isInYard: Boolean get() = steps == -1
    val isFinished: Boolean get() = steps == 57
}

data class LudoPlayer(
    val color: LudoColor,
    val name: String,
    val isComputer: Boolean,
    val tokens: List<LudoToken> = List(4) { LudoToken() }
) {
    val finishedTokens: Int get() = tokens.count { it.isFinished }
}

data class LudoGameState(
    val players: List<LudoPlayer>,
    val currentPlayerIndex: Int = 0,
    val diceValue: Int? = null,
    val winner: LudoColor? = null,
    val turnNumber: Long = 1L,
    val consecutiveSixes: Int = 0
) {
    val currentPlayer: LudoPlayer get() = players[currentPlayerIndex]
    val isFinished: Boolean get() = winner != null
}

data class LudoMoveResult(
    val state: LudoGameState,
    val captured: List<Pair<LudoColor, Int>>,
    val tokenFinished: Boolean
)

object LudoEngine {
    // Four coloured start squares plus the four star squares.
    private val safeSquares = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    fun newGame(
        names: Map<LudoColor, String> = emptyMap(),
        computerPlayers: Set<LudoColor> = emptySet(),
        playerColors: List<LudoColor> = LudoColor.entries
    ): LudoGameState = LudoGameState(
        players = playerColors.distinct().map { color ->
            LudoPlayer(
                color = color,
                name = names[color]?.trim()?.take(20)?.ifBlank { color.name.lowercase().replaceFirstChar { it.uppercase() } }
                    ?: color.name.lowercase().replaceFirstChar { it.uppercase() },
                isComputer = color in computerPlayers
            )
        }
    )

    /**
     * Creates a same-device local table. One human plays against three computer
     * opponents; selecting two to four humans enables pass-and-play on one phone.
     */
    fun newLocalGame(humanPlayers: Int): LudoGameState {
        require(humanPlayers in 1..4) { "A local Ludo table needs one to four human players." }
        val colors = LudoColor.entries
        val names = colors.mapIndexed { index, color ->
            color to if (humanPlayers == 1 && index == 0) "You" else
                if (humanPlayers == 1) listOf("Nova", "Atlas", "Mira")[index - 1] else "Player ${index + 1}"
        }.toMap()
        val computers = if (humanPlayers == 1) colors.drop(1).toSet() else emptySet()
        return newGame(names, computers, colors)
    }
    fun rollDice(state: LudoGameState, value: Int = Random.nextInt(1, 7)): LudoGameState {
        require(!state.isFinished) { "The game has already ended." }
        require(state.diceValue == null) { "Move or pass before rolling again." }
        require(value in 1..6) { "A Ludo die must show 1 through 6." }
        val sixes = if (value == 6) state.consecutiveSixes + 1 else 0
        return state.copy(diceValue = value, consecutiveSixes = sixes)
    }

    fun legalMoves(state: LudoGameState): List<Int> {
        val roll = state.diceValue ?: return emptyList()
        if (state.isFinished || state.consecutiveSixes >= 3) return emptyList()
        return state.currentPlayer.tokens.indices.filter { index ->
            val steps = state.currentPlayer.tokens[index].steps
            when {
                steps == -1 -> roll == 6 && !pathBlocked(state, state.currentPlayer.color, 0, 0)
                steps == 57 -> false
                else -> steps + roll <= 57 && !pathBlocked(state, state.currentPlayer.color, steps + 1, (steps + roll).coerceAtMost(51))
            }
        }
    }

    private fun pathBlocked(state: LudoGameState, movingColor: LudoColor, fromStep: Int, toStep: Int): Boolean {
        if (fromStep > toStep) return false
        val opponentSquares = state.players.filter { it.color != movingColor }.flatMap { opponent ->
            opponent.tokens.filter { it.steps in 0..51 }
                .groupBy { (opponent.color.startOffset + it.steps) % 52 }
                .filterValues { it.size >= 2 }.keys
        }.toSet()
        return (fromStep..toStep).any { relativeStep ->
            ((movingColor.startOffset + relativeStep) % 52) in opponentSquares
        }
    }

    fun moveToken(state: LudoGameState, tokenIndex: Int): LudoMoveResult {
        require(!state.isFinished) { "The game has already ended." }
        val roll = state.diceValue ?: throw IllegalStateException("Roll the dice first.")
        require(tokenIndex in 0..3) { "Choose one of your four tokens." }
        require(tokenIndex in legalMoves(state)) { "That token cannot move with this roll." }

        val player = state.currentPlayer
        val oldSteps = player.tokens[tokenIndex].steps
        val newSteps = if (oldSteps == -1) 0 else oldSteps + roll
        val tokens = player.tokens.toMutableList()
        tokens[tokenIndex] = LudoToken(newSteps)
        val updatedPlayer = player.copy(tokens = tokens)
        val players = state.players.toMutableList()
        players[state.currentPlayerIndex] = updatedPlayer

        val captured = mutableListOf<Pair<LudoColor, Int>>()
        if (newSteps in 0..51) {
            val landingSquare = (player.color.startOffset + newSteps) % 52
            if (landingSquare !in safeSquares) {
                players.indices.filter { it != state.currentPlayerIndex }.forEach { opponentIndex ->
                    val opponent = players[opponentIndex]
                    val occupants = opponent.tokens.withIndex().filter { (_, token) ->
                        token.steps in 0..51 &&
                            (opponent.color.startOffset + token.steps) % 52 == landingSquare
                    }
                    // A pair is a blockade: it cannot be captured as a single token.
                    if (occupants.size == 1) {
                        val capturedIndex = occupants.single().index
                        val resetTokens = opponent.tokens.toMutableList()
                        resetTokens[capturedIndex] = LudoToken()
                        players[opponentIndex] = opponent.copy(tokens = resetTokens)
                        captured += opponent.color to capturedIndex
                    }
                }
            }
        }

        val winner = updatedPlayer.takeIf { it.tokens.all(LudoToken::isFinished) }?.color
        val earnsBonus = roll == 6 || captured.isNotEmpty() || newSteps == 57
        val nextIndex = when {
            winner != null -> state.currentPlayerIndex
            earnsBonus -> state.currentPlayerIndex
            else -> (state.currentPlayerIndex + 1) % players.size
        }
        val nextState = state.copy(
            players = players,
            currentPlayerIndex = nextIndex,
            diceValue = null,
            winner = winner,
            turnNumber = if (nextIndex == state.currentPlayerIndex) state.turnNumber else state.turnNumber + 1,
            consecutiveSixes = if (nextIndex == state.currentPlayerIndex && roll == 6) state.consecutiveSixes else 0
        )
        return LudoMoveResult(nextState, captured, newSteps == 57)
    }

    fun passTurn(state: LudoGameState): LudoGameState {
        require(!state.isFinished) { "The game has already ended." }
        require(state.diceValue != null) { "Roll the dice before passing." }
        require(legalMoves(state).isEmpty()) { "A legal token move is available." }
        val nextIndex = if (state.consecutiveSixes < 3 && state.diceValue == 6) {
            state.currentPlayerIndex
        } else {
            (state.currentPlayerIndex + 1) % state.players.size
        }
        return state.copy(
            currentPlayerIndex = nextIndex,
            diceValue = null,
            turnNumber = if (nextIndex == state.currentPlayerIndex) state.turnNumber else state.turnNumber + 1,
            consecutiveSixes = if (nextIndex == state.currentPlayerIndex) state.consecutiveSixes else 0
        )
    }

    fun chooseComputerMove(state: LudoGameState, difficulty: LudoDifficulty = LudoDifficulty.NORMAL): Int? {
        val moves = legalMoves(state)
        if (moves.isEmpty()) return null
        if (difficulty == LudoDifficulty.EASY) return moves.random()
        val player = state.currentPlayer
        val roll = state.diceValue ?: return null
        return moves.maxWithOrNull(compareBy<Int> { index ->
            val old = player.tokens[index].steps
            val destination = if (old == -1) 0 else old + roll
            val landingSquare = if (destination in 0..51) (player.color.startOffset + destination) % 52 else -1
            val base = when {
                destination == 57 -> 10_000
                old == -1 -> 120
                destination in 52..56 -> 400 + destination
                else -> 200 + destination
            }
            val capture = if (destination in 0..51 && wouldCapture(state, player.color, destination)) 650 else 0
            val safe = if (landingSquare in safeSquares) 90 else 0
            val danger = if (difficulty == LudoDifficulty.HARD && destination in 0..51 && landingSquare !in safeSquares) {
                state.players.filter { it.color != player.color }.sumOf { opponent ->
                    opponent.tokens.count { token ->
                        token.steps in 0..51 && run {
                            val opponentSquare = (opponent.color.startOffset + token.steps) % 52
                            val distance = (landingSquare - opponentSquare + 52) % 52
                            distance in 1..6
                        }
                    } * 55
                }
            } else 0
            base + capture + safe - danger
        }.thenBy { -it })
    }

    private fun wouldCapture(state: LudoGameState, color: LudoColor, steps: Int): Boolean {
        val square = (color.startOffset + steps) % 52
        if (square in safeSquares) return false
        return state.players.any { opponent ->
            opponent.color != color && opponent.tokens.count { token ->
                token.steps in 0..51 && (opponent.color.startOffset + token.steps) % 52 == square
            } == 1
        }
    }
}

data class LudoNetworkMatch(
    val matchId: String,
    val state: LudoGameState,
    val localColor: LudoColor,
    val isHost: Boolean,
    val opponentName: String,
    val ready: Boolean,
    val revision: Long = 0L,
    val remotePlayers: Map<LudoColor, String> = emptyMap()
)
