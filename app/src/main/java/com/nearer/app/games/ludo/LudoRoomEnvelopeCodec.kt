package com.nearer.app.games.ludo

import org.json.JSONObject

/**
 * Room-bound snapshot envelope. The payload is accepted only when its room,
 * host/guest identities and two human player IDs agree with the decoded board.
 */
data class LudoRoomStateEnvelope(
    val roomId: String,
    val hostPeerId: String,
    val guestPeerId: String,
    val sequence: Long,
    val state: LudoDoubleDiceState
)

object LudoRoomEnvelopeCodec {
    private const val VERSION = 1
    private const val MAX_LENGTH = 15_500

    fun encode(envelope: LudoRoomStateEnvelope): String? {
        if (!valid(envelope)) return null
        val state = LudoRoomStateCodec.encode(envelope.state) ?: return null
        return try {
            JSONObject()
                .put("version", VERSION)
                .put("roomId", envelope.roomId)
                .put("hostPeerId", envelope.hostPeerId)
                .put("guestPeerId", envelope.guestPeerId)
                .put("sequence", envelope.sequence)
                .put("state", state)
                .toString()
                .takeIf { it.length <= MAX_LENGTH }
        } catch (_: Exception) { null }
    }

    fun decode(raw: String): LudoRoomStateEnvelope? {
        if (raw.isBlank() || raw.length > MAX_LENGTH) return null
        return try {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) return null
            val roomId = root.optString("roomId")
            val host = root.optString("hostPeerId")
            val guest = root.optString("guestPeerId")
            val sequence = root.optLong("sequence", -1L)
            val stateRaw = root.optString("state")
            val state = LudoRoomStateCodec.decode(stateRaw) ?: return null
            val envelope = LudoRoomStateEnvelope(roomId, host, guest, sequence, state)
            envelope.takeIf { valid(it) }
        } catch (_: Exception) { null }
    }

    private fun valid(value: LudoRoomStateEnvelope): Boolean {
        if (value.roomId.isBlank() || value.roomId.length > 64 ||
            value.hostPeerId.isBlank() || value.hostPeerId.length > 64 ||
            value.guestPeerId.isBlank() || value.guestPeerId.length > 64 ||
            value.hostPeerId == value.guestPeerId || value.sequence < 0L) return false
        val players = value.state.game.players
        return players.size == 2 &&
            players.map { it.id } == listOf(value.hostPeerId, value.guestPeerId) &&
            players.none { it.isComputer }
    }
}

/** Compact, strict action codec used for guest-to-host move requests. */
object LudoRoomActionCodec {
    private const val MAX_LENGTH = 100

    fun encode(action: LudoRoomAction): String? {
        if (!valid(action)) return null
        return try {
            JSONObject()
                .put("action", action.type.name)
                .put("dieSlot", action.dieSlot?.name ?: JSONObject.NULL)
                .put("tokenIndex", action.tokenIndex ?: JSONObject.NULL)
                .toString()
                .takeIf { it.length <= MAX_LENGTH }
        } catch (_: Exception) { null }
    }

    fun decode(raw: String): LudoRoomAction? {
        if (raw.isBlank() || raw.length > MAX_LENGTH) return null
        return try {
            val json = JSONObject(raw)
            val type = try { LudoRoomActionType.valueOf(json.optString("action")) }
                catch (_: Exception) { return null }
            val die = if (json.isNull("dieSlot")) null else try {
                LudoDieSlot.valueOf(json.optString("dieSlot"))
            } catch (_: Exception) { return null }
            val token = if (json.isNull("tokenIndex")) null else json.optInt("tokenIndex", -1)
            LudoRoomAction(type, die, token).takeIf { valid(it) }
        } catch (_: Exception) { null }
    }

    private fun valid(action: LudoRoomAction): Boolean = when (action.type) {
        LudoRoomActionType.ROLL -> action.dieSlot == null && action.tokenIndex == null
        LudoRoomActionType.SELECT_DIE -> action.dieSlot != null && action.tokenIndex == null
        LudoRoomActionType.MOVE -> action.dieSlot == null && action.tokenIndex != null && action.tokenIndex in 0..3
    }
}
