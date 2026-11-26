# Offline movie export pipeline

The video timeline's export action now uses one offline-only coordinator instead of asking Media3 to produce the final file with its own separate music sequence.

## Export stages

1. **Probe local sources.** StudioMovieAudioSourceProbe uses Android MediaExtractor track formats to identify audio tracks and read media duration. It falls back to MediaMetadataRetriever when duration metadata is absent.
2. **Plan audio cues.** StudioMovieAudioCuePlanner maps each video's source trim to the same timeline offset as its picture. It omits silent or muted clip audio and splits a short music bed into bounded loop cues until the movie duration.
3. **Decode and mix.** StudioOfflinePcmMixPipeline decodes unique local audio sources to disk-backed stereo Float PCM and mixes in bounded blocks. The minimum duration option also produces a silent PCM master when every source is silent or muted, so the final movie still has a valid AAC track.
4. **Encode AAC.** StudioAndroidAacAudioExporter encodes the mixed PCM to an AAC-only MP4 with absolute-frame timestamps.
5. **Render video-only.** Media3 Transformer renders the visual composition with audio removed from each video item. Original clip audio is therefore not accidentally included a second time.
6. **Mux final MP4.** StudioFinalMovieMuxer interleaves the video and AAC samples by presentation timestamp without decoding or recompressing either stream.

## Safety and lifecycle

- All media sources are local content:, file: or android.resource: URIs; no network or cloud service is used.
- Audio decoding, mixing, AAC encoding and final remux run off the UI thread. Media3 Transformer is created and started on the Android main thread.
- Cancellation is propagated to the active stage. Cancelling during Transformer rendering calls Transformer.cancel().
- Temporary video, PCM and AAC assets are removed in finally; failed final output is deleted. Only the validated pending MP4 is published to the project export library.
- Audio mixing uses 48 kHz stereo PCM and bounded 4,096-frame blocks. The PCM pipeline rechecks available storage after each unique source has been decoded.
- Silent video clips are valid: the source probe skips their audio cue rather than failing the entire export.
- Music looping is bounded to 512 cue segments. Extremely short loop sources that would exceed that limit are rejected with a clear preflight error.

## Important technical boundary

Android documents MediaExtractor.getTrackFormat() as the source of per-track metadata, including KEY_DURATION and audio format keys. Media3 documents EditedMediaItem.Builder.setRemoveAudio(true) for stripping audio before a video-only render. The final remuxer preserves the encoded track timestamps; it does not resample or time-stretch video.

This implementation is a software integration milestone, not proof of playback on every handset. Physical-device tests are still required for codec availability, audio/video sync, storage pressure, cancellation behavior, chroma-key composition, and long exports. No claim of measured export speed or device compatibility is made here.

## Research references

- Android MediaExtractor API: https://developer.android.com/reference/android/media/MediaExtractor
- Media3 EditedMediaItem.Builder API: https://developer.android.com/reference/androidx/media3/transformer/EditedMediaItem.Builder
- Media3 Transformer transformations guide: https://developer.android.com/media/media3/transformer/transformations
