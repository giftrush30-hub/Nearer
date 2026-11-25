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
        audioMuted: Boolean = false
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
        }
        val duration = maxOf(
            positionedVideos.maxOfOrNull { it.timelineEndMs } ?: 0L,
            if (audioUri.isNullOrBlank()) 0L else audioDurationMs.coerceAtMost(86_400_000L)
        )
        return StudioMultiTrackProject(tracks, duration.coerceAtMost(86_400_000L))
    }

    fun moveItem(project: StudioMultiTrackProject, trackId: String, itemId: String, newStartMs: Long): StudioMultiTrackProject? {
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
        if (track.locked || newStartMs !in 0L..86_400_000L) return null
        if (track.items.none { it.id == itemId }) return null
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
        if (track.items.none { it.id == itemId }) return null
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

    private fun withDuration(tracks: List<StudioTimelineTrack>): StudioMultiTrackProject {
        val duration = tracks.flatMap { it.items }.maxOfOrNull { it.timelineEndMs } ?: 0L
        return StudioMultiTrackProject(tracks, duration.coerceAtMost(86_400_000L))
    }
}

/** Per-movie, offline multi-track state. The JSON codec keeps the format portable and versioned. */
internal class StudioMultiTrackProjectStore(context: Context, projectId: String) {
    private val preferences = context.getSharedPreferences("nearer_studio_multitrack_$projectId", Context.MODE_PRIVATE)

    fun read(): StudioMultiTrackProject = StudioMultiTrackCodec.decode(preferences.getString(KEY, null))

    fun write(project: StudioMultiTrackProject) {
        check(preferences.edit().putString(KEY, StudioMultiTrackCodec.encode(project)).commit()) {
            "Could not save the movie's multi-track timeline"
        }
    }

    fun exists(): Boolean = preferences.contains(KEY)

    private companion object { const val KEY = "multi_track_v1" }
}
