package com.nearer.app

import org.json.JSONObject

internal data class StudioVocalControls(
    val warmth: Float = 0f,
    val presence: Float = 0f,
    val deEss: Float = 0f,
    val compression: Float = 0f,
    val ambience: Float = 0f,
    val outputGain: Float = 1f
) {
    fun normalized() = copy(
        warmth = warmth.coerceIn(-1f, 1f),
        presence = presence.coerceIn(-0.2f, 0.25f),
        deEss = deEss.coerceIn(0f, 0.5f),
        compression = compression.coerceIn(0f, 4f),
        ambience = ambience.coerceIn(0f, 0.15f),
        outputGain = outputGain.coerceIn(0.6f, 1.4f)
    )
}

internal object StudioVocalControlsCodec {
    private const val VERSION = 1

    fun encode(controls: StudioVocalControls): String {
        val safe = controls.normalized()
        return JSONObject()
            .put("version", VERSION)
            .put("warmth", safe.warmth.toDouble())
            .put("presence", safe.presence.toDouble())
            .put("deEss", safe.deEss.toDouble())
            .put("compression", safe.compression.toDouble())
            .put("ambience", safe.ambience.toDouble())
            .put("outputGain", safe.outputGain.toDouble())
            .toString()
    }

    fun decode(raw: String?): StudioVocalControls {
        if (raw.isNullOrBlank()) return StudioVocalControls()
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version", -1) != VERSION) return StudioVocalControls()
            StudioVocalControls(
                warmth = json.optDouble("warmth", 0.0).toFloat(),
                presence = json.optDouble("presence", 0.0).toFloat(),
                deEss = json.optDouble("deEss", 0.0).toFloat(),
                compression = json.optDouble("compression", 0.0).toFloat(),
                ambience = json.optDouble("ambience", 0.0).toFloat(),
                outputGain = json.optDouble("outputGain", 1.0).toFloat()
            ).normalized()
        }.getOrDefault(StudioVocalControls())
    }
}
