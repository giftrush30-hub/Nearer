package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

/** A renderer-independent description of one editable timeline lane. */
internal enum class StudioTrackKind { VIDEO, AUDIO, TEXT, EFFECT }

internal data class StudioTimelineItem(
    val id: String,
    val sourceUri: String?,
    val timelineStartMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val volume: Float = 1f,
    val opacity: Float = 1f
) {
    val durationMs: Long get() = (sourceOutMs - sourceInMs).coerceAtLeast(0L)
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

internal data class StudioTimelineTrack(
    val id: String,
    val name: String,
    val kind: StudioTrackKind,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val items: List<StudioTimelineItem> = emptyList()
)

internal data class StudioMultiTrackProject(
    val tracks: List<StudioTimelineTrack>,
    val durationMs: Long
)

/**
 * Versioned edit-decision model for the future multi-track renderer.
 * Stores source references and edit instructions, never decoded media bytes.
 */
internal object StudioMultiTrackCodec {
    private const val VERSION = 1
    private const val MAX_TRACKS = 32
    private const val MAX_ITEMS = 500
    private const val MAX_ID = 128
    private const val MAX_NAME = 80
    private const val MAX_DURATION_MS = 86_400_000L

    fun encode(project: StudioMultiTrackProject): String {
        val tracks = JSONArray()
        project.tracks.take(MAX_TRACKS).forEach { track ->
            val items = JSONArray()
            track.items.take(MAX_ITEMS).filter { isValid(it) }.forEach { item ->
                items.put(JSONObject()
                    .put("id", item.id)
                    .put("sourceUri", item.sourceUri)
                    .put("timelineStartMs", item.timelineStartMs)
                    .put("sourceInMs", item.sourceInMs)
                    .put("sourceOutMs", item.sourceOutMs)
                    .put("volume", item.volume.coerceIn(0f, 2f).toDouble())
                    .put("opacity", item.opacity.coerceIn(0f, 1f).toDouble()))
            }
            tracks.put(JSONObject()
                .put("id", track.id)
                .put("name", track.name.trim().take(MAX_NAME))
                .put("kind", track.kind.name)
                .put("muted", track.muted)
                .put("locked", track.locked)
                .put("items", items))
        }
        return JSONObject()
            .put("version", VERSION)
            .put("durationMs", project.durationMs.coerceIn(0L, MAX_DURATION_MS))
            .put("tracks", tracks)
            .toString()
    }

    fun decode(raw: String?): StudioMultiTrackProject {
        if (raw.isNullOrBlank()) return empty()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) return empty()
            val trackArray = root.optJSONArray("tracks") ?: JSONArray()
            var remainingItems = MAX_ITEMS
            val tracks = buildList {
                for (index in 0 until minOf(trackArray.length(), MAX_TRACKS)) {
                    val value = trackArray.optJSONObject(index) ?: continue
                    val id = value.optString("id", "").trim()
                    val name = value.optString("name", "").trim().take(MAX_NAME)
                    val kind = runCatching { StudioTrackKind.valueOf(value.optString("kind", "")) }.getOrNull()
                    if (!validText(id, MAX_ID) || name.isBlank() || kind == null) continue
                    val itemArray = value.optJSONArray("items") ?: JSONArray()
                    val items = buildList {
                        for (itemIndex in 0 until minOf(itemArray.length(), remainingItems)) {
                            val item = itemArray.optJSONObject(itemIndex) ?: continue
                            val decoded = StudioTimelineItem(
                                id = item.optString("id", "").trim(),
                                sourceUri = item.optString("sourceUri", "").takeIf { it.isNotBlank() && it != "null" },
                                timelineStartMs = item.optLong("timelineStartMs", -1L),
                                sourceInMs = item.optLong("sourceInMs", -1L),
                                sourceOutMs = item.optLong("sourceOutMs", -1L),
                                volume = item.optDouble("volume", 1.0).toFloat().coerceIn(0f, 2f),
                                opacity = item.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f)
                            )
                            if (isValid(decoded)) add(decoded)
                        }
                    }
                    remainingItems -= items.size
                    add(StudioTimelineTrack(
                        id, name, kind,
                        value.optBoolean("muted", false),
                        value.optBoolean("locked", false),
                        items
                    ))
                }
            }
            val calculatedDuration = tracks.flatMap { it.items }.maxOfOrNull { it.timelineEndMs } ?: 0L
            val requestedDuration = root.optLong("durationMs", calculatedDuration).coerceIn(0L, MAX_DURATION_MS)
            StudioMultiTrackProject(tracks, maxOf(requestedDuration, calculatedDuration.coerceAtMost(MAX_DURATION_MS)))
        }.getOrDefault(empty())
    }

    private fun isValid(item: StudioTimelineItem): Boolean =
        validText(item.id, MAX_ID) &&
            item.timelineStartMs in 0L..MAX_DURATION_MS &&
            item.sourceInMs >= 0L &&
            item.sourceOutMs > item.sourceInMs &&
            item.sourceOutMs <= MAX_DURATION_MS &&
            item.timelineEndMs <= MAX_DURATION_MS &&
            item.volume.isFinite() && item.opacity.isFinite()

    private fun validText(value: String, max: Int) = value.isNotBlank() && value.length <= max
    private fun empty() = StudioMultiTrackProject(emptyList(), 0L)
}
