# Audio presentation-timestamp alignment

Android's MediaCodec output BufferInfo carries the presentation timestamp associated with the decoded output buffer. The PCM decoder now uses that timestamp instead of assuming the first decoded sample always belongs at media time zero.

## Implemented behavior

- Convert each decoded buffer's PTS to an absolute source-frame position.
- Resample using the absolute source-frame clock, avoiding cumulative per-buffer timestamp rounding.
- Insert silence when a decoded stream has a real timestamp gap instead of stretching the previous sample across the gap.
- Ignore overlapping duplicate boundary samples from adjacent decoder buffers.
- Preserve positive audio-track start offsets by writing leading silence into the disk-backed PCM cache. This keeps sourceInMs trims aligned with the original video timeline.
- Ignore decoded samples before timeline zero.
- Keep the existing bounded 4,096-frame PCM staging and file-backed random access.

## Tests

The pure JVM aligner tests cover a 100 ms initial offset, 2x resampling, timestamp gaps, overlapping buffer boundaries, and negative timestamps before timeline zero.

## Limits

The decoder still selects the first audio track in a source container and supports PCM_FLOAT or PCM_16BIT decoder output. Unsupported PCM formats fail explicitly. Device-specific decoder timestamp behavior, long-GOP video/audio sync and final playback must still be tested on real Android phones.

## Official API references

- MediaCodec.BufferInfo documents presentationTimeUs as the timestamp derived from the corresponding input buffer: https://developer.android.com/reference/android/media/MediaCodec.BufferInfo
- MediaCodec.queueInputBuffer accepts the input presentation timestamp: https://developer.android.com/reference/android/media/MediaCodec
