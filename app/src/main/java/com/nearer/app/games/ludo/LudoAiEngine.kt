package com.nearer.app.games.ludo

import kotlin.random.Random

enum class LudoAiDifficulty(val label: String, val description: String) {
    EASY("EASY", "Relaxed opponent • simple choices"),
    MEDIUM("MEDIUM", "Balanced opponent • progress, safety and captures"),
    HARD("HARD", "Strategic opponent • threat awareness and token development")
}

object LudoAiEngine {
    fun chooseComputerMove(state: LudoState, difficulty: LudoAiDifficulty, random: Random = Random.Default): Int? {
        val dice = state.dice ?: return null
        val legal = state.legalTokenIndexes
        if (legal.isEmpty()) return null
        if (difficulty == LudoAiDifficulty.EASY) return legal.random(random)
        val player = state.activePlayer
        return legal.maxByOrNull { tokenIndex ->
            val old = player.tokens[tokenIndex]
            val next = if (old == -1) 0 else old + dice
            var score = next * 2
            if (old == -1) score += if (difficulty == LudoAiDifficulty.MEDIUM) 30 else 18
            if (next == 56) score += 1000
            if (next in 0..51) {
                val landing = LudoEngine.globalSquare(player.color, next)
                val enemies = state.players.filter { it.id != player.id }
                val captures = enemies.sumOf { enemy -> enemy.tokens.count { it in 0..51 && LudoEngine.globalSquare(enemy.color, it) == landing } }
                if (captures > 0) score += if (difficulty == LudoAiDifficulty.HARD) 420 * captures else 250 * captures
                if (LudoEngine.isSafeSquare(landing)) score += if (difficulty == LudoAiDifficulty.HARD) 75 else 25
                if (difficulty == LudoAiDifficulty.HARD) {
                    val danger = enemies.sumOf { enemy -> enemy.tokens.count { progress ->
                        progress in 0..51 && !LudoEngine.isSafeSquare(LudoEngine.globalSquare(enemy.color, progress)) &&
                            (1..6).any { step -> (LudoEngine.globalSquare(enemy.color, progress) + step) % 52 == landing }
                    } }
                    if (!LudoEngine.isSafeSquare(landing)) score -= danger * 95
                    val otherRunners = player.tokens.indices.count { it != tokenIndex && player.tokens[it] in 0..55 }
                    if (old == -1 && otherRunners == 0) score += 12
                    if (old in 0..51 && player.tokens.count { it in 0..55 } <= 1) score += 24
                    if (next in 48..55) score += 45
                }
            }
            score
        }
    }
}
