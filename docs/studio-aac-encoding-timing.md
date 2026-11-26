# NeaRer Studio — AAC encoding and mux timing contract

This milestone adds a platform-independent planning layer for the next Android MediaCodec AAC-LC encoder integration. It does not claim to encode AAC or produce MP4 by itself.

## Rules enforced by the planner

- Restrict requested AAC-LC output to MPEG-4 audio sample rates and mono/stereo channels.
- Validate a conservative bitrate range before allocating codec resources.
- Convert codec input capacity into complete interleaved PCM16 frames; never submit a partial sample frame.
- Derive every input presentation timestamp from the absolute PCM frame position. The clock is not advanced by repeatedly adding a rounded packet duration, avoiding cumulative timestamp drift.
- Check arithmetic overflow for very long projects.
- Validate the actual encoder output MIME type, sample rate, channel count and codec-specific data before adding the audio track to an MP4 muxer.
- Reject negative, duplicate and out-of-order per-track sample timestamps.

## Android encoder integration sequence

1. Configure MediaCodec with AAC-LC, selected sample rate, channel count, bitrate and PCM16 input.
2. Start the encoder and inspect the output format event. Do not invent the mux track format; use the encoder's returned format and codec-specific data.
3. Add the AAC track to MediaMuxer only after that format is available. Add the video track too before starting the muxer.
4. Feed PCM16 frames in bounded chunks using planInputChunk; set each input buffer timestamp from the returned absolute-frame timestamp.
5. Drain output buffers promptly, copy each encoded sample and its BufferInfo, and write samples in chronological order per track. Release every codec output buffer promptly.
6. Signal input EOS, drain encoder EOS, stop and release codec/muxer safely. Write to a temporary output and publish only after muxer stop succeeds; delete partial output on cancellation or failure.

## Important integration caveats

- AAC encoder output is device-dependent. The actual encoder may expose a different input PCM format or buffer capacity; the Android adapter must inspect and verify capabilities rather than assume.
- Codec output buffers and codec-specific data have ownership/lifetime rules. Copy encoded bytes before releasing an output buffer if writing is deferred.
- MediaMuxer requires all tracks to be registered before start, and each track's samples must be written in chronological order.
- Audio packet PTS and video frame PTS must share the same movie time origin. The eventual export coordinator must reconcile source trims, timeline offsets, frame-rate timestamps and the final movie duration.
- This pure JVM layer is deliberately testable without a device. Actual AAC encoding, muxing, cancellation under codec load, and playback compatibility still require Android integration and physical-device testing.

## Research

- Android MediaCodec: synchronous buffer ownership, queue/dequeue and prompt output-buffer release.
- Android MediaMuxer: add tracks before start; write encoded samples in chronological order; use codec-provided output format.
- Android media format support: AAC-LC and MP4 support vary by Android version/device; check actual codec capabilities at runtime.
