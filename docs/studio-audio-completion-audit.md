# Studio Audio five-part completion — stacked implementation

This branch is stacked on the parallel workstream's feat/studio-media3-111-final branch. It does not push to or alter that branch. The base supplies the Media3 1.11.1 / Android toolchain work needed for pitch-preserving speed parameters.

## Work status

1. **Vocal Pro quality:** smoothed sibilance envelope, Nyquist-safe filter cutoffs, compressor/de-esser/stereo/low-rate regression coverage. Listening acceptance remains.
2. **Timeline navigation:** 1×/2×/4× zoom and horizontal scrolling are copied from merged main PR #275.
3. **Tempo automation:** per-track start/end speed ramp, 0.50×–2.00×, 20 ms sampling, pitch-preserving Media3 speed parameters, bounded project codec v5 persistence, and output-duration calculation for fades.
4. **Crossfades:** equal-power sine fade-in and cosine fade-out curves; matching overlapping track fades maintain constant combined power in the ideal aligned case. Fade lengths are clamped to clip duration.
5. **Mix export verification:** .m4a AAC-in-MP4 output; completed file is inspected off the UI thread and only exposed when it is readable, has an audio track and has positive duration.

## Automation details

- Automation keyframes are source-clip time in milliseconds.
- Maximum 32 keyframes, bounded to the first 30 seconds.
- Keyframe speeds are clamped to 0.50×–2.00×.
- A two-point start/end ramp is exposed in Sound Desk controls.
- The speed provider samples a linearly interpolated curve every 20 ms, which creates a deterministic stepped approximation.
- SpeedParameters.shouldMaintainPitch is explicitly true.
- Rendered clip duration is calculated using the same 20 ms samples and supplied to the gain/fade processor so fades use output time, not source time.
- Existing BPM/beat division remains placement snapping only; it does not silently alter audio speed.

## Compatibility and verification

- Project codec version 5 stores tempo keyframes; versions 1–4 load with no tempo automation and remain normal speed.
- Imported source recordings are not modified.
- The branch is stacked on the Media3 upgrade branch; final validation must run after the base branch is reconciled with current main.
- Exact-head JVM tests, manifest validation, Android lint and installable debug APK build are required.
- Physical device listening at 0.5×, 1×, 1.5× and 2× is still required. No physical test or studio-grade guarantee is claimed.

## References

- Media3 release notes: https://developer.android.com/jetpack/androidx/releases/media3
- EditedMediaItem speed API: https://developer.android.com/reference/androidx/media3/transformer/EditedMediaItem.Builder
- SpeedProvider contract: https://developer.android.com/reference/androidx/media3/common/audio/SpeedProvider
- SpeedParameters source: https://github.com/androidx/media/blob/8c6678b657ede1e7883fc164ef73ed483c7796c3/libraries/common/src/main/java/androidx/media3/common/SpeedParameters.java
