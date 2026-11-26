# NeaRer Studio — AAC encoding and mux timing

The offline audio pipeline now has an Android AAC-LC encoder stage in addition to the JVM-testable timing planner.

## Implemented

- Validates AAC-LC sample rate, mono/stereo output and bitrate before creating the codec.
- Reads the existing little-endian stereo Float PCM master from disk in bounded chunks; it does not load a whole song or movie soundtrack into memory.
- Converts normalized float samples to signed PCM16 with explicit clipping and the full asymmetric signed range.
- Feeds complete interleaved PCM frames into the Android AAC encoder, deriving each input timestamp from the absolute source frame position.
- Waits for the actual encoder output-format event, verifies MIME type, sample rate, channel count and codec-specific data, then registers the AAC track with MediaMuxer.
- Writes encoded AAC samples in chronological order with a per-track timestamp guard.
- Signals input EOS, drains encoder EOS, closes codec/muxer resources and deletes partial output on cancellation or failure.
- Produces an audio-only MP4 temporary asset for the next video/audio remux stage.

## Important boundary

This is real Android codec/muxer implementation code, but it has not yet been exercised on physical phones. Device encoder behavior, AAC playback compatibility, long-file cancellation and audio/video synchronization still need validation. The current result is an audio-only MP4 asset, not the final combined movie export. The PCM source length is checked against the declared stereo frame count before codec startup, and PCM16 conversion is covered by pure JVM clipping tests. The final coordinator must register both the video and AAC tracks before starting its muxer, then write both tracks in chronological order on a shared movie timebase.

## Platform constraints and research

- Android MediaCodec synchronous processing: obtain buffers by ID, queue input, drain output and release output buffers promptly.
- Android MediaMuxer: register tracks before start and write each track's samples in chronological order; AAC is supported in MP4.
- AAC codec-specific data belongs in the output MediaFormat supplied when adding the muxer track; it must not be copied as a normal encoded sample.
- Runtime codec capabilities must be checked on each phone rather than assuming every hardware encoder accepts the same profile, input PCM encoding or buffer size.

Official references:
- https://developer.android.com/reference/android/media/MediaCodec
- https://developer.android.com/reference/android/media/MediaMuxer
