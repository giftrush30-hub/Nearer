# Offline multichannel audio downmix

The offline movie exporter decodes source audio to a stereo Float PCM cache before timeline mixing. The decoder now performs a deterministic channel-aware downmix rather than keeping only the first two channels.

## Channel handling

- Mono is duplicated to left and right.
- Stereo is preserved as-is.
- Android channel-position masks are interpreted in ascending bit order, matching the platform's documented PCM frame layout.
- When a decoder omits a usable mask, canonical layouts for two through eight channels are used.
- Front-center/dialogue is sent to both stereo channels at approximately -3 dB.
- Back and side surrounds are folded into their matching stereo side at approximately -3 dB.
- Back-center and LFE are folded into both sides at -6 dB.
- The decoder does not hard-clip the combined downmix. The existing timeline PCM mixer applies the final soft-knee limiter after all clips and music have been summed.

Input samples are checked for non-finite values and constrained to the normalized PCM range before downmixing. Output staging remains disk-backed and bounded.

## Timestamp handling

The decoder also passes the extractor's original sample timestamp to MediaCodec without forcing negative timestamps to zero. The timestamp aligner can therefore trim pre-zero samples and preserve the true media-time origin.

## Tests

JVM tests cover mono, stereo, canonical 5.1, explicit 5.1 channel masks, non-clipping intermediate sums, and incomplete frames.

## Platform references

- Android documents that channel-position masks define sample order by ascending mask bit: https://developer.android.com/reference/android/media/AudioFormat
- MediaFormat exposes optional KEY_CHANNEL_MASK metadata: https://developer.android.com/reference/android/media/MediaFormat
- MediaCodec output presentation timestamps derive from the timestamps supplied with corresponding input buffers: https://developer.android.com/reference/android/media/MediaCodec.BufferInfo

## Remaining limitations

- The downmixer supports standard positional channel layouts through 7.1. It is not an Ambisonics or object-audio renderer.
- If a decoder omits the channel mask, the canonical layout for its channel count is assumed.
- Real-device codec output layouts and listening quality still require handset validation.
