package com.nearer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Editing operations shared by the timeline UI and future renderers. */
internal object StudioMultiTrackEditor {
    fun fromVideoSources(
        videos: List<StudioTimelineItem>,
        audioUri: String? = null,
        audioDurationMs: Long = 0L,
        audioMuted: Boolean = false,
        textItems: List<StudioTimelineItem> = emptyList(),
        effectItems: List<StudioTimelineItem> = emptyList()
    ): StudioMultiTrackProject {
        var cursor = 0L
        val positionedVideos = videos.map { item ->
            val safe = item.copy(timelineStartMs = cursor.coerceAtMost(86_400_000L))
            cursor = (cursor + safe.durationMs).coerceAtMost(86_400_000L)
            safe
        }
        val tracks = mutableListOf(
            StudioTimelineTrack("video-main", "Main video", StudioTrackKind.VIDEO, items = positionedVideos)
        )
        if (!audioUri.isNullOrBlank() && audioDurationMs > 0L) {
            tracks += StudioTimelineTrack(
                "audio-music", "Background music", StudioTrackKind.AUDIO,
                muted = audioMuted,
                items = listOf(StudioTimelineItem(
                    id = "background-music", sourceUri = audioUri, timelineStartMs = 0L,
                    sourceInMs = 0L, sourceOutMs = audioDurationMs.coerceAtMost(86_400_000L)
                ))
            )
        } else {
            tracks += StudioTimelineTrack("audio-music", "Background music", StudioTrackKind.AUDIO, muted = audioMuted)
        }
        tracks += StudioTimelineTrack("text-overlays", "Titles and captions", StudioTrackKind.TEXT, items = textItems)
        tracks += StudioTimelineTrack("visual-effects", "Visual effects", StudioTrackKind.EFFECT, items = effectItems)
        val duration = maxOf(
            positionedVideos.maxOfOrNull { it.timelineEndMs } ?: 0L,
            if (audioUri.isNullOrBlank()) 0L else audioDurationMs.coerceAtMost(86_400_000L)
        )
        return StudioMultiTrackProject(tracks, duration.coerceAtMost(86_400_000L))
    }

    fun moveItem(project: StudioMultiTrackProject, trackId: String, itemId: String, newStartMs: Long): StudioMultiTrackProject? {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        if (track.locked || newStartMs !in 0L..86_400_000L) return null
        val moving = track.items.firstOrNull { it.id == itemId } ?: return null
        if (newStartMs + moving.durationMs > 86_400_000L) return null
        val updated = project.tracks.map { lane ->
            if (lane.id != trackId) lane else lane.copy(items = lane.items.map { item ->
                if (item.id == itemId) item.copy(timelineStartMs = newStartMs) else item
            })
        }
        return withDuration(updated)
    }

    fun trimItem(
        project: StudioMultiTrackProject,
        trackId: String,
        itemId: String,
        sourceInMs: Long,
        sourceOutMs: Long
    ): StudioMultiTrackProject? {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        if (track.locked || sourceInMs < 0L || sourceOutMs <= sourceInMs || sourceOutMs > 86_400_000L) return null
        val trimming = track.items.firstOrNull { it.id == itemId } ?: return null
        if (trimming.timelineStartMs + (sourceOutMs - sourceInMs) > 86_400_000L) return null
        val updated = project.tracks.map { lane ->
            if (lane.id != trackId) lane else lane.copy(items = lane.items.map { item ->
                if (item.id == itemId) item.copy(sourceInMs = sourceInMs, sourceOutMs = sourceOutMs) else item
            })
        }
        return withDuration(updated)
    }

    fun setMuted(project: StudioMultiTrackProject, trackId: String, muted: Boolean): StudioMultiTrackProject? {
        if (project.tracks.none { it.id == trackId }) return null
        return project.copy(tracks = project.tracks.map { if (it.id == trackId) it.copy(muted = muted) else it })
    }

    fun setLocked(project: StudioMultiTrackProject, trackId: String, locked: Boolean): StudioMultiTrackProject? {
        if (project.tracks.none { it.id == trackId }) return null
        return project.copy(tracks = project.tracks.map { if (it.id == trackId) it.copy(locked = locked) else it })
    }

    fun addTrack(project: StudioMultiTrackProject, kind: StudioTrackKind, name: String): StudioMultiTrackProject? {
        if (project.tracks.size >= 32 || name.isBlank() || name.length > 80) return null
        val track = StudioTimelineTrack(UUID.randomUUID().toString(), name.trim(), kind)
        return project.copy(tracks = project.tracks + track)
    }

    fun removeItem(project: StudioMultiTrackProject, trackId: String, itemId: String): StudioMultiTrackProject? {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        if (track.locked || track.items.none { it.id == itemId }) return null
        val updated = project.tracks.map { lane ->
            if (lane.id != trackId) lane else lane.copy(items = lane.items.filterNot { it.id == itemId })
        }
        return withDuration(updated)
    }


    /** Split one source item at a source timestamp while preserving its timeline position. */
    fun splitItem(project: StudioMultiTrackProject, trackId: String, itemId: String, splitSourceMs: Long): StudioMultiTrackProject? {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        if (track.locked) return null
        val item = track.items.firstOrNull { it.id == itemId } ?: return null
        if (splitSourceMs <= item.sourceInMs || splitSourceMs >= item.sourceOutMs) return null
        val firstDuration = splitSourceMs - item.sourceInMs
        val second = item.copy(id = UUID.randomUUID().toString(),
            timelineStartMs = item.timelineStartMs + firstDuration, sourceInMs = splitSourceMs)
        if (second.timelineEndMs > 86_400_000L) return null
        val first = item.copy(sourceOutMs = splitSourceMs)
        val updated = project.tracks.map { lane ->
            if (lane.id != trackId) lane else lane.copy(items = lane.items.flatMap {
                if (it.id == itemId) listOf(first, second) else listOf(it)
            })
        }
        return withDuration(updated)
    }

    /** Duplicate an edit-decision item; original media bytes are not copied. */
    fun duplicateItem(project: StudioMultiTrackProject, trackId: String, itemId: String, newStartMs: Long): StudioMultiTrackProject? {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        if (track.locked || newStartMs !in 0L..86_400_000L || track.items.size >= 500) return null
        val item = track.items.firstOrNull { it.id == itemId } ?: return null
        if (newStartMs + item.durationMs > 86_400_000L) return null
        val copy = item.copy(id = UUID.randomUUID().toString(), timelineStartMs = newStartMs)
        return withDuration(project.tracks.map { lane ->
            if (lane.id == trackId) lane.copy(items = lane.items + copy) else lane
        })
    }

    fun setItemOpacity(project: StudioMultiTrackProject, trackId: String, itemId: String, opacity: Float): StudioMultiTrackProject? {
        if (!opacity.isFinite() || opacity !in 0f..1f) return null
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        if (track.locked || track.items.none { it.id == itemId }) return null
        return project.copy(tracks = project.tracks.map { lane ->
            if (lane.id != trackId) lane else lane.copy(items = lane.items.map {
                if (it.id == itemId) it.copy(opacity = opacity) else it
            })
        })
    }

    fun setItemVolume(project: StudioMultiTrackProject, trackId: String, itemId: String, volume: Float): StudioMultiTrackProject? {
        if (!volume.isFinite() || volume !in 0f..2f) return null
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        if (track.locked || track.kind != StudioTrackKind.AUDIO || track.items.none { it.id == itemId }) return null
        return project.copy(tracks = project.tracks.map { lane ->
            if (lane.id != trackId) lane else lane.copy(items = lane.items.map {
                if (it.id == itemId) it.copy(volume = volume) else it
            })
        })
    }

    fun renameTrack(project: StudioMultiTrackProject, trackId: String, name: String): StudioMultiTrackProject? {
        val safeName = name.trim()
        if (safeName.isBlank() || safeName.length > 80 || project.tracks.none { it.id == trackId }) return null
        return project.copy(tracks = project.tracks.map { if (it.id == trackId) it.copy(name = safeName) else it })
    }

    fun reorderTrack(project: StudioMultiTrackProject, trackId: String, newIndex: Int): StudioMultiTrackProject? {
        val oldIndex = project.tracks.indexOfFirst { it.id == trackId }
        if (oldIndex < 0 || newIndex !in project.tracks.indices) return null
        val reordered = project.tracks.toMutableList()
        val moving = reordered.removeAt(oldIndex)
        reordered.add(newIndex, moving)
        return project.copy(tracks = reordered)
    }

    private fun withDuration(tracks: List<StudioTimelineTrack>): StudioMultiTrackProject {
        val duration = tracks.flatMap { it.items }.maxOfOrNull { it.timelineEndMs } ?: 0L
        return StudioMultiTrackProject(tracks, duration.coerceAtMost(86_400_000L))
    }
}

/** Per-movie, offline multi-track state. The JSON codec keeps the format portable and versioned. */
internal class StudioMultiTrackProjectStore(context: Context, projectId: String) {
    private val preferences = context.getSharedPreferences("nearer_studio_multitrack_$projectId", Context.MODE_PRIVATE)

    fun read(): StudioMultiTrackProject = StudioMultiTrackCodec.decode(preferences.getString(KEY, null))

    fun write(project: StudioMultiTrackProject): Boolean = runCatching {
        preferences.edit().putString(KEY, StudioMultiTrackCodec.encode(project)).commit()
    }.getOrDefault(false)

    fun exists(): Boolean = preferences.contains(KEY)

    private companion object { const val KEY = "multi_track_v1" }
}
