package com.nearer.app

import org.json.JSONObject

internal data class StudioVideoGrade(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f
) {
    fun normalized() = copy(
        brightness = brightness.coerceIn(-0.5f, 0.5f),
        contrast = contrast.coerceIn(-0.5f, 0.5f),
        saturation = saturation.coerceIn(-50f, 50f)
    )
}

internal object StudioVideoGradeCodec {
    private const val VERSION = 1

    fun encode(grade: StudioVideoGrade): String {
        val safe = grade.normalized()
        return JSONObject()
            .put("version", VERSION)
            .put("brightness", safe.brightness.toDouble())
            .put("contrast", safe.contrast.toDouble())
            .put("saturation", safe.saturation.toDouble())
            .toString()
    }

    fun decode(raw: String?): StudioVideoGrade {
        if (raw.isNullOrBlank()) return StudioVideoGrade()
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version", -1) != VERSION) return StudioVideoGrade()
            StudioVideoGrade(
                brightness = json.optDouble("brightness", 0.0).toFloat(),
                contrast = json.optDouble("contrast", 0.0).toFloat(),
                saturation = json.optDouble("saturation", 0.0).toFloat()
            ).normalized()
        }.getOrDefault(StudioVideoGrade())
    }
}
