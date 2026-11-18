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
- When a clip is trimmed after setting the visible two-point start/end ramp, the editor retimes the ramp endpoint to the new clip duration while preserving both selected speed targets. Imported multi-keyframe curves retain their internal timing and are safely bounded. Regression tests cover both shorter and longer trims.
- A two-point start/end ramp is exposed in Sound Desk controls.
- The speed provider samples a linearly interpolated curve every 20 ms, which creates a deterministic stepped approximation.
- SpeedParameters.shouldMaintainPitch is explicitly true.
- Rendered clip duration is calculated using the same 20 ms samples and supplied to the gain/fade processor so fades use output time, not source time.
- Existing BPM/beat division remains placement snapping only; it does not silently alter audio speed.

## Compatibility and verification

- Project codec version 5 stores tempo keyframes; versions 1–4 load with no tempo automation and remain normal speed.
- Imported source recordings are not modified.
- The project-library delete policy defensively creates a fresh empty starter arrangement if called with the final project; regression tests cover that fallback and deleting one project while preserving the others. The current UI already disables the delete action when only one project remains, so this is defensive protection rather than a user-reachable crash fix.
- Project trim decoding computes its minimum legal end position without `clipStartMs + 1` overflow; malformed `Long.MAX_VALUE` trim values are covered by a regression test.
- Offline track export converts source duration and fade lengths to PCM frames using overflow-safe millisecond conversion; extreme metadata saturates at `Long.MAX_VALUE` rather than wrapping to a negative or tiny frame count.
- The vocal-polish processor now resets its filter history, dynamics envelopes, limiter state and room-delay buffer on Media3 `flush()` as well as full reset. This prevents a reused processor from carrying the previous take's audio state into the next take; a same-processor reuse regression test compares consecutive flushed renders. Exact-head CI remains required.
- Added direct `StudioTrackGainProcessor` regression tests for chunk-boundary invariance (the gain/fade envelope must not reset between Media3 input buffers), mono-to-stereo duplication, hard-left/right balance, and rejection of unsupported channel layouts. A lifecycle review also found that the processor reset its frame clock on full reset but not on Media3 `flush()`; it now resets on both, with a reuse regression test proving a second input stream starts its fade at frame zero. These tests are committed but still need exact-head CI execution.
- Live-monitor route refresh preserves the selected headphone when another eligible device is added; if the selected output disappears, monitoring stops and a remaining output is selected for a fresh explicit restart. Unit tests cover preservation, fallback and no-device cases.
- PR #284 was merged into `main` at merge commit `690183a830eec5172c83b7a57286e44a06616590`. This branch is now retargeted directly to `main`; the shared PR #284 feature head remains an ancestor, so its Media3 and safe-monitor changes are retained.
- Exact-head validation is being rerun after the PR #284 merge and retarget to `main`. Required gates are JVM tests, manifest validation, Android lint diagnostics and installable debug APK build/upload. The Android validation workflow also runs on pushes to `studio/five-parts-main-sync`, with that branch included in APK build and artifact-upload conditions. The existing repository lint baseline is not yet a clean-lint certification.
- Physical device listening at 0.5×, 1×, 1.5× and 2× is still required. No physical test or studio-grade guarantee is claimed.

## References

- Media3 release notes: https://developer.android.com/jetpack/androidx/releases/media3
- EditedMediaItem speed API: https://developer.android.com/reference/androidx/media3/transformer/EditedMediaItem.Builder
- SpeedProvider contract: https://developer.android.com/reference/androidx/media3/common/audio/SpeedProvider
- SpeedParameters source: https://github.com/androidx/media/blob/8c6678b657ede1e7883fc164ef73ed483c7796c3/libraries/common/src/main/java/androidx/media3/common/SpeedParameters.java
