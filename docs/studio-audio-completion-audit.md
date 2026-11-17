# Studio Audio five-part completion — stacked implementation

This integration branch is rebuilt on the parallel workstream's current PR #284 head (`feat/studio-audio-route-media3-main-sync`). It preserves the latest Media3 1.11.1, API 36 build compatibility, verified headphone-only live-monitor routing, ambiguous-output confirmation and per-write route checks. It does not modify the parallel workstream's branch.

## Work status

1. **Vocal Pro quality:** retain key-aware monophonic pitch correction with optional LPC formant compensation, then apply offline vocal polish (low-cut, presence, de-essing, gate, compression, air, stereo width, saturation, room ambience and output limiting). This pass improves the sibilance envelope, Nyquist-safe filter cutoffs and DSP regression coverage. The noise-reduction control is a downward gate/expander, not a spectral denoiser; pitch correction is for a single lead vocal, not chords. Listening acceptance remains.
2. **Timeline navigation:** 1×/2×/4× zoom and horizontal scrolling are copied from merged main PR #275.
3. **Tempo automation:** per-track start/end speed ramp, 0.50×–2.00×, 20 ms sampling, pitch-preserving Media3 speed parameters, bounded project codec v5 persistence, and output-duration calculation for fades.
4. **Crossfades:** equal-power sine fade-in and cosine fade-out curves; matching overlapping track fades maintain constant combined power in the ideal aligned case. Fade lengths are clamped to clip duration.
5. **Mix export verification:** .m4a AAC-in-MP4 output; completed file is inspected off the UI thread and only exposed when it is readable, explicitly reports `METADATA_KEY_HAS_AUDIO=yes`, and has positive duration. JVM coverage rejects `no`, null and empty audio-track metadata.

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
- This branch is based on PR #284's current head; exact-head validation must run here before integration. The parallel PR #284 remains independently owned.
- Exact-head JVM tests, manifest validation, Android lint and installable debug APK build are required.
- Physical device listening at 0.5×, 1×, 1.5× and 2× is still required. No physical test or studio-grade guarantee is claimed.

## References

- Media3 release notes: https://developer.android.com/jetpack/androidx/releases/media3
- EditedMediaItem speed API: https://developer.android.com/reference/androidx/media3/transformer/EditedMediaItem.Builder
- SpeedProvider contract: https://developer.android.com/reference/androidx/media3/common/audio/SpeedProvider
- SpeedParameters source: https://github.com/androidx/media/blob/8c6678b657ede1e7883fc164ef73ed483c7796c3/libraries/common/src/main/java/androidx/media3/common/SpeedParameters.java
