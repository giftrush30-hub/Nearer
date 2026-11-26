# Offline PCM block mixer integration contract

Studio now has a deterministic audio schedule and a bounded PCM block mixer.

## Data path

1. The project/render-plan layer validates local content:, file: or android.resource: sources.
2. A decoder adapter implements StudioPcmStereoSource, seeking to a source frame and filling a caller-provided interleaved stereo Float buffer.
3. The export worker calls StudioPcmBlockMixer.mixBlock for consecutive output-frame ranges. Keep blocks at or below 4,096 frames; the mixer uses one reusable scratch block and never buffers an entire song.
4. The caller feeds mixed PCM to the selected Android encoder/muxer path and aligns audio presentation timestamps with the video output clock.

## Mixer guarantees

- Source trims are applied in sample frames, not rounded to whole milliseconds during each sample.
- Multiple active cues are summed in deterministic cue-list order.
- Muted and out-of-window cues do not request decoded samples.
- Per-cue gain, equal-power fade envelopes and stereo balance use the shared Studio audio math.
- A soft limiter is applied after summing, with an explicit configurable ceiling.
- Missing active decoders, invalid read counts, invalid output buffers and oversized blocks fail fast.
- Memory is bounded by one scratch block (32 KiB at the maximum block size), plus decoder buffers and the output block.

## Deliberate boundary

This is the reusable PCM mixing core, not yet the Android decoder/encoder/muxer adapter. It does not claim MP3/AAC/Opus input support by itself, does not write an MP4, and has not been measured on a physical Android device. Decoder and muxer integration must handle codec-specific output formats, end-of-stream, timestamp continuity, cancellation, storage permissions and partial-file cleanup. Preview should use the same gain/fade/pan rules so what the user hears matches export.

## Platform references

- Android MediaCodec is the low-level codec API used with MediaExtractor and MediaMuxer; codec I/O is asynchronous and buffer-driven: https://developer.android.com/reference/android/media/MediaCodec
- Android PCM Float is supported by modern AudioRecord and AudioTrack APIs, but device codec support must still be queried: https://developer.android.com/reference/android/media/AudioFormat
- Media3 Composition supports arranged media sequences and background audio, but documented Transformer composition limitations include audio/video crossfading: https://developer.android.com/media/media3/transformer/composition
