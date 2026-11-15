package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

internal data class StudioVideoClipState(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val inMs: Long,
    val outMs: Long
)

internal data class StudioVideoProjectState(
    val clips: List<StudioVideoClipState>,
    val backgroundAudioUri: String?,
    val backgroundAudioName: String
)

/** Versioned local timeline codec. Invalid clip entries are skipped safely. */
internal object StudioVideoProjectCodec {
    private const val VERSION = 1

    fun encode(project: StudioVideoProjectState): String {
        val root = JSONObject().put("version", VERSION)
            .put("backgroundAudioUri", project.backgroundAudioUri)
            .put("backgroundAudioName", project.backgroundAudioName)
        val clips = JSONArray()
        project.clips.take(12).forEach { clip ->
            clips.put(JSONObject()
                .put("uri", clip.uri)
                .put("name", clip.name)
                .put("durationMs", clip.durationMs.coerceAtLeast(0L))
                .put("inMs", clip.inMs.coerceAtLeast(0L))
                .put("outMs", clip.outMs.coerceAtLeast(0L)))
        }
        return root.put("clips", clips).toString()
    }

    fun decode(raw: String?): StudioVideoProjectState {
        if (raw.isNullOrBlank()) return StudioVideoProjectState(emptyList(), null, "")
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) return StudioVideoProjectState(emptyList(), null, "")
            val items = root.optJSONArray("clips") ?: JSONArray()
            val clips = buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val uri = item.optString("uri", "").takeIf { it.isNotBlank() } ?: continue
                    val duration = item.optLong("durationMs", 0L).coerceAtLeast(0L)
                    if (duration <= 500L) continue
                    val inMs = item.optLong("inMs", 0L).coerceIn(0L, duration - 500L)
                    val outMs = item.optLong("outMs", duration).coerceIn(inMs + 500L, duration)
                    add(StudioVideoClipState(uri, item.optString("name", "Video clip"), duration, inMs, outMs))
                }
            }.take(12)
            StudioVideoProjectState(
                clips = clips,
                backgroundAudioUri = root.optString("backgroundAudioUri", "").takeIf { it.isNotBlank() && it != "null" },
                backgroundAudioName = root.optString("backgroundAudioName", "")
            )
        }.getOrDefault(StudioVideoProjectState(emptyList(), null, ""))
    }
}
