package com.nearer.app

import org.json.JSONObject

internal data class StudioVideoTextOverlay(
    val text: String = "",
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val verticalPosition: Int = 2
) {
    fun normalized() = copy(
        text = text.take(80),
        colorArgb = if (colorArgb in intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD166.toInt(), 0xFF67E8C5.toInt())) colorArgb else 0xFFFFFFFF.toInt(),
        verticalPosition = verticalPosition.coerceIn(0, 2)
    )
}

internal object StudioVideoTextOverlayCodec {
    private const val VERSION = 1

    fun encode(overlay: StudioVideoTextOverlay): String {
        val safe = overlay.normalized()
        return JSONObject()
            .put("version", VERSION)
            .put("text", safe.text)
            .put("color", safe.colorArgb.toLong())
            .put("position", safe.verticalPosition)
            .toString()
    }

    fun decode(raw: String?): StudioVideoTextOverlay {
        if (raw.isNullOrBlank()) return StudioVideoTextOverlay()
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version", -1) != VERSION) return StudioVideoTextOverlay()
            StudioVideoTextOverlay(
                text = json.optString("text", ""),
                colorArgb = json.optLong("color", 0xFFFFFFFFL).toInt(),
                verticalPosition = json.optInt("position", 2)
            ).normalized()
        }.getOrDefault(StudioVideoTextOverlay())
    }
}
