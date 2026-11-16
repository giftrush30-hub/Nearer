package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

internal data class StudioCustomVocalPreset(
    val name: String,
    val profile: StudioVocalProfile,
    val controls: StudioVocalControls
)

internal object StudioCustomVocalPresetCodec {
    const val MAX_PRESETS = 12
    private const val MAX_RAW_LENGTH = 16_384
    const val MAX_NAME_LENGTH = 28
    private const val VERSION = 1

    fun normalizedName(raw: String): String = raw
        .filterNot { it.isISOControl() }
        .trim()
        .take(MAX_NAME_LENGTH)

    fun upsert(
        existing: List<StudioCustomVocalPreset>,
        rawName: String,
        profile: StudioVocalProfile,
        controls: StudioVocalControls
    ): List<StudioCustomVocalPreset>? {
        val name = normalizedName(rawName)
        if (name.isBlank()) return null
        val next = existing.filterNot { it.name.equals(name, ignoreCase = true) } +
            StudioCustomVocalPreset(name, profile, controls.normalized())
        return next.takeLast(MAX_PRESETS)
    }

    fun encode(presets: List<StudioCustomVocalPreset>): String {
        val array = JSONArray()
        presets.takeLast(MAX_PRESETS).forEach { preset ->
            val name = normalizedName(preset.name)
            if (name.isNotBlank()) {
                array.put(JSONObject()
                    .put("name", name)
                    .put("profile", preset.profile.name)
                    .put("controls", StudioVocalControlsCodec.encode(preset.controls)))
            }
        }
        return JSONObject().put("version", VERSION).put("presets", array).toString()
    }

    fun decode(raw: String?): List<StudioCustomVocalPreset> {
        if (raw.isNullOrBlank() || raw.length > MAX_RAW_LENGTH) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) return emptyList()
            val array = root.optJSONArray("presets") ?: return emptyList()
            val decoded = mutableListOf<StudioCustomVocalPreset>()
            for (index in 0 until minOf(array.length(), MAX_PRESETS)) {
                val item = array.optJSONObject(index) ?: continue
                val name = normalizedName(item.optString("name"))
                if (name.isBlank() || decoded.any { it.name.equals(name, ignoreCase = true) }) continue
                val profile = runCatching { StudioVocalProfile.valueOf(item.optString("profile")) }.getOrNull() ?: continue
                val controls = StudioVocalControlsCodec.decode(item.optString("controls"))
                decoded += StudioCustomVocalPreset(name, profile, controls)
            }
            decoded
        }.getOrDefault(emptyList())
    }
}
