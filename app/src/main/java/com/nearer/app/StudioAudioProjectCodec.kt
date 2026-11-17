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
    val fadeOutMs: Long,
    val lowGainDb: Float = 0f,
    val midGainDb: Float = 0f,
    val highGainDb: Float = 0f,
    val reverbMix: Float = 0f,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = durationMs,
    val startOffsetMs: Long = 0L,
    val tempoPoints: List<StudioTempoPoint> = emptyList()
)

/** Versioned local project codec. Older projects migrate with flat EQ and dry audio. */
internal object StudioAudioProjectCodec {
    private const val VERSION = 5

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
                .put("fadeOutMs", track.fadeOutMs.coerceAtLeast(0L))
                .put("lowGainDb", track.lowGainDb.coerceIn(-12f, 12f).toDouble())
                .put("midGainDb", track.midGainDb.coerceIn(-12f, 12f).toDouble())
                .put("highGainDb", track.highGainDb.coerceIn(-12f, 12f).toDouble())
                
                .put("reverbMix", track.reverbMix.coerceIn(0f, 0.35f).toDouble())
                .put("clipStartMs", track.clipStartMs.coerceAtLeast(0L))
                .put("clipEndMs", track.clipEndMs.coerceAtLeast(track.clipStartMs))
                .put("startOffsetMs", track.startOffsetMs.coerceIn(0L, 30_000L))
                .put("tempoPoints", JSONArray().apply {
                    if (track.tempoPoints.isNotEmpty()) {
                        StudioTempoAutomationPolicy.normalize(track.tempoPoints, track.durationMs).forEach { point ->
                            put(JSONObject().put("timeMs", point.timeMs).put("speed", point.speed.toDouble()))
                        }
                    }
                }))
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
                    val durationMs = item.optLong("durationMs", 0L).coerceAtLeast(0L)
                    val clipStartMs = item.optLong("clipStartMs", 0L).coerceIn(0L, durationMs)
                    val clipEndMs = if (durationMs > 0L) item.optLong("clipEndMs", durationMs).coerceIn((clipStartMs + 1L).coerceAtMost(durationMs), durationMs) else 0L
                    add(StudioAudioProjectTrackState(
                        uri = uri,
                        name = name,
                        durationMs = durationMs,
                        muted = item.optBoolean("muted", false),
                        gain = item.optDouble("gain", 1.0).toFloat().coerceIn(0f, 1f),
                        pan = item.optDouble("pan", 0.0).toFloat().coerceIn(-1f, 1f),
                        fadeInMs = item.optLong("fadeInMs", 0L).coerceAtLeast(0L),
                        fadeOutMs = item.optLong("fadeOutMs", 0L).coerceAtLeast(0L),
                        lowGainDb = item.optDouble("lowGainDb", 0.0).toFloat().coerceIn(-12f, 12f),
                        midGainDb = item.optDouble("midGainDb", 0.0).toFloat().coerceIn(-12f, 12f),
                        highGainDb = item.optDouble("highGainDb", 0.0).toFloat().coerceIn(-12f, 12f),
                        reverbMix = item.optDouble("reverbMix", 0.0).toFloat().coerceIn(0f, 0.35f),
                        clipStartMs = clipStartMs,
                        clipEndMs = clipEndMs,
                        startOffsetMs = item.optLong("startOffsetMs", 0L).coerceIn(0L, 30_000L),
                        tempoPoints = if (version >= 5) decodeTempoPoints(item, durationMs) else emptyList()
                    ))
                }
            }.take(8)
        }.getOrDefault(emptyList())
    }

    private fun decodeTempoPoints(item: JSONObject, durationMs: Long): List<StudioTempoPoint> {
        val array = item.optJSONArray("tempoPoints") ?: return emptyList()
        if (array.length() == 0) return emptyList()
        val points = buildList {
            for (index in 0 until array.length()) {
                val point = array.optJSONObject(index) ?: continue
                add(StudioTempoPoint(
                    timeMs = point.optLong("timeMs", 0L),
                    speed = point.optDouble("speed", 1.0).toFloat()
                ))
            }
        }
        return StudioTempoAutomationPolicy.normalize(points.take(StudioTempoAutomationPolicy.MAX_KEYFRAMES), durationMs)
    }
}
