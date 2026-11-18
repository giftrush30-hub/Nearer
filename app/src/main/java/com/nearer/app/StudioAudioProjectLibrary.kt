package com.nearer.app

import org.json.JSONArray
import org.json.JSONObject

internal data class StudioAudioProjectLibraryEntry(
    val id: String,
    val name: String,
    val updatedAtMs: Long,
    val tracks: List<StudioAudioProjectTrackState>,
    val bpm: Int = StudioAudioBeatGridPolicy.DEFAULT_BPM,
    val beatDivision: Int = 2,
    val snapEnabled: Boolean = true
)

/** Bounded, versioned local library for independent Sound Desk arrangements. */
internal object StudioAudioProjectLibraryCodec {
    private const val VERSION = 2
    private const val LEGACY_VERSION = 1
    const val MAX_PROJECTS = 20
    const val MAX_NAME_LENGTH = 48

    fun normalizeName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).ifBlank { "Untitled Mix" }

    /**
     * Deletes the selected arrangement but guarantees the editor always has a project
     * to display. Removing the last project creates a fresh empty starter project.
     */
    fun delete(
        projects: List<StudioAudioProjectLibraryEntry>,
        projectId: String,
        replacementId: String,
        updatedAtMs: Long
    ): List<StudioAudioProjectLibraryEntry> {
        val remaining = projects.filterNot { it.id == projectId }
        if (remaining.isNotEmpty()) return remaining
        return listOf(
            StudioAudioProjectLibraryEntry(
                id = replacementId.trim().ifBlank { "studio-default" }.take(80),
                name = "My First Mix",
                updatedAtMs = updatedAtMs.coerceAtLeast(0L),
                tracks = emptyList()
            )
        )
    }

    fun rename(
        projects: List<StudioAudioProjectLibraryEntry>,
        projectId: String,
        rawName: String,
        activeTracks: List<StudioAudioProjectTrackState>,
        updatedAtMs: Long
    ): List<StudioAudioProjectLibraryEntry> {
        val safeTimestamp = updatedAtMs.coerceAtLeast(0L)
        return projects.map { project ->
            if (project.id == projectId) project.copy(
                name = normalizeName(rawName),
                updatedAtMs = safeTimestamp,
                tracks = activeTracks
            ) else project
        }
    }

    fun encode(projects: List<StudioAudioProjectLibraryEntry>): String {
        val items = JSONArray()
        projects.distinctBy { it.id.trim() }.take(MAX_PROJECTS).forEach { project ->
            val id = project.id.trim().take(80)
            if (id.isNotBlank()) items.put(JSONObject()
                .put("id", id)
                .put("name", normalizeName(project.name))
                .put("updatedAtMs", project.updatedAtMs.coerceAtLeast(0L))
                .put("bpm", StudioAudioBeatGridPolicy.normalizeBpm(project.bpm))
                .put("beatDivision", StudioAudioBeatGridPolicy.normalizeBeatDivision(project.beatDivision))
                .put("snapEnabled", project.snapEnabled)
                .put("tracksJson", StudioAudioProjectCodec.encode(project.tracks)))
        }
        return JSONObject().put("version", VERSION).put("projects", items).toString()
    }

    fun decode(raw: String?): List<StudioAudioProjectLibraryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("version", -1)
            if (version != VERSION && version != LEGACY_VERSION) return emptyList()
            val items = root.optJSONArray("projects") ?: return emptyList()
            buildList {
                val ids = HashSet<String>()
                for (index in 0 until items.length()) {
                    if (size >= MAX_PROJECTS) break
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id", "").trim().take(80)
                    if (id.isBlank() || !ids.add(id)) continue
                    add(StudioAudioProjectLibraryEntry(
                        id = id,
                        name = normalizeName(item.optString("name", "Untitled Mix")),
                        updatedAtMs = item.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
                        tracks = StudioAudioProjectCodec.decode(item.optString("tracksJson", null)),
                        bpm = StudioAudioBeatGridPolicy.normalizeBpm(
                            if (version >= VERSION) item.optInt("bpm", StudioAudioBeatGridPolicy.DEFAULT_BPM)
                            else StudioAudioBeatGridPolicy.DEFAULT_BPM
                        ),
                        beatDivision = StudioAudioBeatGridPolicy.normalizeBeatDivision(
                            if (version >= VERSION) item.optInt("beatDivision", 2) else 2
                        ),
                        snapEnabled = if (version >= VERSION) item.optBoolean("snapEnabled", true) else false
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }
}
