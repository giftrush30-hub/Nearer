package com.nearer.app

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaFormat
import android.net.Uri

internal data class StudioMovieAudioSourceInfo(
    val hasAudioTrack: Boolean,
    val durationMs: Long
)

/**
 * Reads local container metadata without decoding the media. Track presence comes from
 * MediaExtractor; duration falls back to MediaMetadataRetriever when a track omits KEY_DURATION.
 */
internal object StudioMovieAudioSourceProbe {
    fun inspect(context: Context, uri: Uri): StudioMovieAudioSourceInfo {
        require(uri.scheme in setOf("content", "file", "android.resource")) {
            "Only local offline movie audio sources are supported."
        }
        val extractor = MediaExtractor()
        var hasAudio = false
        var durationUs = 0L
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) hasAudio = true
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = maxOf(durationUs, runCatching {
                        format.getLong(MediaFormat.KEY_DURATION)
                    }.getOrDefault(0L))
                }
            }
        } finally {
            extractor.release()
        }

        var durationMs = (durationUs / 1_000L).coerceAtLeast(0L)
        if (durationMs <= 0L) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            } finally {
                runCatching { retriever.release() }
            }
        }
        return StudioMovieAudioSourceInfo(hasAudio, durationMs)
    }
}
