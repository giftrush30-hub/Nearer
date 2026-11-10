package com.nearer.app.games.ludo

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded, versioned snapshot codec for the authoritative nearby Ludo host.
 * The receiving phone must still validate room identity and host authority
 * before applying a decoded snapshot.
 */
object LudoRoomStateCodec {
    private const val VERSION = 1
    private const val MAX_BYTES = 12_000

    fun encode(state: LudoDoubleDiceState): String? = try {
        val root = JSONObject()
            .put("version", VERSION)
            .put("players", JSONArray().apply {
                state.game.players.forEach { player ->
                    put(JSONObject()
                        .put("id", player.id)
                        .put("name", player.name.take(24))
                        .put("color", player.color.name)
                        .put("computer", player.isComputer)
                        .put("tokens", JSONArray(player.tokens))
                    )
                }
            })
            .put("activePlayerIndex", state.game.activePlayerIndex)
            .put("dice", state.game.dice ?: JSONObject.NULL)
            .put("legalTokenIndexes", JSONArray(state.game.legalTokenIndexes))
            .put("winnerId", state.game.winnerId ?: JSONObject.NULL)
            .put("message", state.game.message.take(120))
            .put("turnNumber", state.game.turnNumber)
            .put("consecutiveSixes", state.game.consecutiveSixes)
            .put("lastAction", state.game.lastAction.take(120))
            .put("extraTurnPending", state.extraTurnPending)
        val turn = state.turn
        if (turn == null) {
            root.put("turn", JSONObject.NULL)
        } else {
            root.put("turn", JSONObject()
                .put("first", turn.dice.first)
                .put("second", turn.dice.second)
                .put("remaining", JSONArray().apply { turn.remaining.forEach { put(it.name) } })
                .put("selected", turn.selected?.name ?: JSONObject.NULL)
            )
        }
        root.toString().takeIf { it.length <= MAX_BYTES }
    } catch (_: Exception) { null }

    fun decode(raw: String): LudoDoubleDiceState? {
        if (raw.isBlank() || raw.length > MAX_BYTES) return null
        return try {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) return null
            val playersJson = root.optJSONArray("players") ?: return null
            if (playersJson.length() !in 2..4) return null
            val players = (0 until playersJson.length()).map { index ->
                val item = playersJson.optJSONObject(index) ?: return null
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || id.length > 64 || name.isBlank() || name.length > 24) return null
                val color = try { LudoColor.valueOf(item.optString("color")) } catch (_: Exception) { return null }
                val tokenJson = item.optJSONArray("tokens") ?: return null
                if (tokenJson.length() != 4) return null
                val tokens = (0 until tokenJson.length()).map { tokenJson.optInt(it, Int.MIN_VALUE) }
                if (tokens.any { it !in -1..56 }) return null
                LudoPlayer(id, name, color, item.optBoolean("computer", false), tokens)
            }
            if (players.map { it.id }.toSet().size != players.size ||
                players.map { it.color }.toSet().size != players.size) return null
            val active = root.optInt("activePlayerIndex", -1)
            if (active !in players.indices) return null
            val dice = if (root.isNull("dice")) null else root.optInt("dice", -1).takeIf { it in 1..12 } ?: return null
            val legalJson = root.optJSONArray("legalTokenIndexes") ?: JSONArray()
            val legal = (0 until legalJson.length()).map { legalJson.optInt(it, -1) }
            if (legal.any { it !in 0..3 } || legal.toSet().size != legal.size) return null
            val turn = if (root.isNull("turn")) null else {
                val item = root.optJSONObject("turn") ?: return null
                val pair = LudoDicePair(item.optInt("first", -1), item.optInt("second", -1))
                val remainingJson = item.optJSONArray("remaining") ?: return null
                val remaining = (0 until remainingJson.length()).map {
                    try { LudoDieSlot.valueOf(remainingJson.optString(it)) } catch (_: Exception) { return null }
                }.toSet()
                if (remaining.size != remainingJson.length()) return null
                val selected = if (item.isNull("selected")) null else {
                    try { LudoDieSlot.valueOf(item.optString("selected")) } catch (_: Exception) { return null }
                }
                if (selected != null && selected != LudoDieSlot.BOTH && selected !in remaining) return null
                if (selected == LudoDieSlot.BOTH && !(LudoDieSlot.FIRST in remaining && LudoDieSlot.SECOND in remaining)) return null
                LudoTwoDiceTurn(pair, remaining, selected)
            }
            val winner = if (root.isNull("winnerId")) null else root.optString("winnerId").takeIf { id -> players.any { it.id == id } } ?: return null
            val turnNumber = root.optInt("turnNumber", 1)
            val sixes = root.optInt("consecutiveSixes", 0)
            if (turnNumber < 1 || sixes !in 0..2) return null
            val game = LudoState(
                players = players,
                activePlayerIndex = active,
                dice = dice,
                legalTokenIndexes = legal,
                winnerId = winner,
                message = root.optString("message").take(120),
                turnNumber = turnNumber,
                consecutiveSixes = sixes,
                lastAction = root.optString("lastAction").take(120)
            )
            LudoDoubleDiceState(game, turn, root.optBoolean("extraTurnPending", false))
        } catch (_: Exception) { null }
    }
}
