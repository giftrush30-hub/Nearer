# NeaRer Sound Desk tempo automation

## Design

Tempo automation is stored per track as source-media keyframes:

- Speed range: 0.50× to 2.00×.
- At most 32 keyframes per track.
- Keyframe times are snapped to a 20 ms grid and bounded to the first 30 seconds of a clip.
- Between keyframes, the target speed is linearly interpolated; Media3's `SpeedProvider` receives deterministic 20 ms speed samples.
- A two-point ramp is exposed in the phone UI as start speed and end speed. Reset returns the track to normal 1.00× playback.
- The project's BPM and beat subdivision remain separate: they still control timeline snapping, not audio playback speed.

## Pitch preservation and export

The export uses Media3 1.11.1's `SpeedParameters(speedProvider, shouldMaintainPitch = true)` on each edited audio item. The speed provider is attached to the item, not the entire composition, so each track can have an independent curve. The implementation does not also add a second speed-changing audio effect to the same item.

The output-duration policy uses the same 20 ms sampled speed values as the provider. The gain/fade processor receives the estimated rendered duration, and fade lengths are bounded to that duration. This keeps fade-out timing aligned when a clip is sped up or slowed down. Normal-speed and constant-speed tracks use O(1) duration calculations rather than walking the sample grid, avoiding unnecessary work during recomposition for long recordings.

The curve changes timing, not source files. Imported recordings remain unchanged; tempo automation is applied only during the local render. The project codec moves to version 5 and reads versions 1–4 with an empty (normal-speed) automation curve.

## Limits and acceptance

- Automation is bounded to the first 30 seconds of each source clip in this first implementation.
- Speed range is intentionally conservative (0.50×–2.00×) to avoid extreme artifacts.
- The 20 ms provider sampling is a stepped approximation of the linearly interpolated keyframe curve. It must be listened to for zippering, transient smearing and time-stretch artifacts.
- The API's pitch-maintenance flag is enabled, but it does not guarantee artifact-free results on every voice, drum or music source.
- Exact-head JVM tests, manifest validation, lint and a debug APK build are required before merge.
- Physical-device listening is still required at 0.5×, 1×, 1.5× and 2×, including voice, drums, sustained instruments and mixed tracks. No studio-grade guarantee or device test is claimed here.

## AndroidX Media3 references

- Media3 1.11.1 release notes (pitch-maintaining time stretch): https://developer.android.com/jetpack/androidx/releases/media3
- `EditedMediaItem.Builder.setSpeed(SpeedParameters)`: https://developer.android.com/reference/androidx/media3/transformer/EditedMediaItem.Builder
- `SpeedProvider` timestamp contract: https://developer.android.com/reference/androidx/media3/common/audio/SpeedProvider
- `SpeedParameters` source: https://github.com/androidx/media/blob/8c6678b657ede1e7883fc164ef73ed483c7796c3/libraries/common/src/main/java/androidx/media3/common/SpeedParameters.java
