# Offline final MP4 mux stage

`StudioFinalMovieMuxer` combines a rendered video-only MP4 and an AAC-only MP4 using Android `MediaExtractor` and `MediaMuxer`.

## Guarantees implemented

- Reads local MP4 files only; no network, cloud or AI dependency.
- Requires exactly one video track in the video input and exactly one audio track in the AAC input.
- Requires the audio track MIME to be AAC (`audio/mp4a-latm`).
- Adds both output tracks before starting the muxer.
- Copies compressed samples without decoding or re-encoding.
- Interleaves the two streams by presentation timestamp, preserving each source track's timestamps and sample flags.
- Rejects negative, duplicate or decreasing timestamps within either track.
- Uses reusable direct buffers and rejects samples larger than 32 MiB.
- Supports cancellation and progress callbacks.
- Deletes partial output on any failure.
- Reopens the final MP4 and verifies the `ftyp` box and presence of both audio and video tracks.

## Integration boundary

This is a container remux stage, not a video renderer and not yet connected to the editor's export button. The caller must first render a video-only MP4 and encode a synchronized AAC-only MP4 whose timestamps share the same zero-based timeline. Source clip audio, background music, trims and fades must be mixed into that AAC master before this remuxer is used. The existing Media3 composition exporter remains in place until that end-to-end handoff is integrated and validated.

Physical-device codec/muxer compatibility and playback testing remain required.
