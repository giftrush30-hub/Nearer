# Android offline audio decode and PCM staging pipeline

## Implemented path

- Selects the first local audio track with Android MediaExtractor.
- Decodes compressed audio with the platform MediaCodec decoder using synchronous input/output buffers.
- Converts mono/stereo and multichannel decoder output into stereo Float PCM.
- Resamples to the selected project sample rate with linear interpolation.
- Writes decoded PCM to temporary little-endian Float files rather than retaining complete recordings in memory.
- Exposes a bounded random-access source that feeds StudioPcmBlockMixer.
- Renders the complete timeline in blocks to a temporary stereo Float PCM master, with cancellation checks, progress callbacks, free-space preflight and cleanup.

## Important boundaries

The result is an intermediate PCM master, not yet a compressed AAC track or final MP4. The next export stage must encode PCM to AAC (or another device-supported codec) and mux it with video using presentation timestamps. It must also preserve cancellation, output-format changes, muxer track-start ordering, and temporary-file cleanup. No physical-device codec compatibility or performance claim is made here.

The decoder uses Android's codec-reported PCM encoding and accepts PCM_FLOAT or PCM_16BIT. Unsupported output formats fail explicitly instead of misreading samples. Codec support depends on the phone and selected source format.

## Official Android references

- MediaExtractor selects a track and supplies compressed access units: https://developer.android.com/reference/android/media/MediaExtractor
- MediaCodec synchronous mode requires dequeueing input/output buffers and releasing output buffers promptly: https://developer.android.com/reference/android/media/MediaCodec
- MediaFormat.KEY_PCM_ENCODING reports decoder PCM representation; float output must be confirmed from the decoder output format: https://developer.android.com/reference/android/media/MediaFormat#KEY_PCM_ENCODING
- MediaMuxer is the platform API for writing encoded samples into MP4/container tracks: https://developer.android.com/reference/android/media/MediaMuxer
