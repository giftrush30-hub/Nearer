package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

internal data class StudioAudioProjectTrackState(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val muted: Boolean,
    val gain: Float,
    val pan: Float,
    val fadeInMs: Long,
    val fadeOutMs: Long
)

/** Versioned local project codec. Version 1 projects migrate with centred pan. */
internal object StudioAudioProjectCodec {
    private const val VERSION = 2

    fun encode(tracks: List<StudioAudioProjectTrackState>): String {
        val root = JSONObject().put("version", VERSION)
        val items = JSONArray()
        tracks.forEach { track ->
            items.put(JSONObject()
                .put("uri", track.uri)
                .put("name", track.name)
                .put("durationMs", track.durationMs.coerceAtLeast(0L))
                .put("muted", track.muted)
                .put("gain", track.gain.coerceIn(0f, 1f).toDouble())
                .put("pan", track.pan.coerceIn(-1f, 1f).toDouble())
                .put("fadeInMs", track.fadeInMs.coerceAtLeast(0L))
                .put("fadeOutMs", track.fadeOutMs.coerceAtLeast(0L)))
        }
        return root.put("tracks", items).toString()
    }

    fun decode(raw: String?): List<StudioAudioProjectTrackState> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("version", -1)
            if (version !in 1..VERSION) return emptyList()
            val items = root.optJSONArray("tracks") ?: return emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val uri = item.optString("uri", "").takeIf { it.isNotBlank() } ?: continue
                    val name = item.optString("name", "Audio track").ifBlank { "Audio track" }
                    add(StudioAudioProjectTrackState(
                        uri = uri,
                        name = name,
                        durationMs = item.optLong("durationMs", 0L).coerceAtLeast(0L),
                        muted = item.optBoolean("muted", false),
                        gain = item.optDouble("gain", 1.0).toFloat().coerceIn(0f, 1f),
                        pan = item.optDouble("pan", 0.0).toFloat().coerceIn(-1f, 1f),
                        fadeInMs = item.optLong("fadeInMs", 0L).coerceAtLeast(0L),
                        fadeOutMs = item.optLong("fadeOutMs", 0L).coerceAtLeast(0L)
                    ))
                }
            }.take(8)
        }.getOrDefault(emptyList())
    }
}
