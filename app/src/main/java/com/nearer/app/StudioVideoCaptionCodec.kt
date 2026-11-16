package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

internal data class StudioVideoCaptionLayer(
    val id: String,
    val text: String,
    val clipIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val verticalPosition: Int = 2
) {
    fun normalized(clipCount: Int): StudioVideoCaptionLayer {
        val safeStart = startMs.coerceAtLeast(0L)
        return copy(
            id = id.take(64),
            text = text.take(120),
            clipIndex = clipIndex.coerceIn(0, (clipCount - 1).coerceAtLeast(0)),
            startMs = safeStart,
            endMs = endMs.coerceAtLeast(safeStart + 250L),
            colorArgb = if (colorArgb in intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD166.toInt(), 0xFF67E8C5.toInt())) colorArgb else 0xFFFFFFFF.toInt(),
            verticalPosition = verticalPosition.coerceIn(0, 2)
        )
    }
}

internal object StudioVideoCaptionCodec {
    private const val VERSION = 1
    private const val MAX_LAYERS = 4

    fun encode(layers: List<StudioVideoCaptionLayer>): String {
        val array = JSONArray()
        layers.take(MAX_LAYERS).forEach { layer ->
            val safe = layer.normalized(12)
            array.put(JSONObject()
                .put("id", safe.id)
                .put("text", safe.text)
                .put("clip", safe.clipIndex)
                .put("start", safe.startMs)
                .put("end", safe.endMs)
                .put("color", safe.colorArgb.toLong())
                .put("position", safe.verticalPosition))
        }
        return JSONObject().put("version", VERSION).put("layers", array).toString()
    }

    fun decode(raw: String?): List<StudioVideoCaptionLayer> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version", -1) != VERSION) return emptyList()
            val array = json.optJSONArray("layers") ?: return emptyList()
            buildList {
                for (index in 0 until minOf(array.length(), MAX_LAYERS)) {
                    val item = array.optJSONObject(index) ?: continue
                    add(StudioVideoCaptionLayer(
                        id = item.optString("id", "caption-$index"),
                        text = item.optString("text", ""),
                        clipIndex = item.optInt("clip", 0),
                        startMs = item.optLong("start", 0L),
                        endMs = item.optLong("end", 2_000L),
                        colorArgb = item.optLong("color", 0xFFFFFFFFL).toInt(),
                        verticalPosition = item.optInt("position", 2)
                    ).normalized(12))
                }
            }
        }.getOrDefault(emptyList())
    }
}


/** Keeps caption assignments attached to their original clip when the user reorders the sequence. */
internal fun remapStudioCaptionLayersForClipMove(
    layers: List<StudioVideoCaptionLayer>,
    fromIndex: Int,
    toIndex: Int
): List<StudioVideoCaptionLayer> {
    if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) return layers
    return layers.map { layer ->
        val nextIndex = when {
            layer.clipIndex == fromIndex -> toIndex
            fromIndex < toIndex && layer.clipIndex in (fromIndex + 1)..toIndex -> layer.clipIndex - 1
            fromIndex > toIndex && layer.clipIndex in toIndex until fromIndex -> layer.clipIndex + 1
            else -> layer.clipIndex
        }
        layer.copy(clipIndex = nextIndex)
    }
}

/** Removes captions belonging to a deleted clip and shifts later assignments left. */
internal fun removeStudioCaptionLayersForClip(
    layers: List<StudioVideoCaptionLayer>,
    removedIndex: Int
): List<StudioVideoCaptionLayer> = layers
    .filterNot { it.clipIndex == removedIndex }
    .map { layer -> if (layer.clipIndex > removedIndex) layer.copy(clipIndex = layer.clipIndex - 1) else layer }
