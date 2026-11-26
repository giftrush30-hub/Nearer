# Deterministic 3D animation export timeline

## Purpose

Studio3DAnimationExportPlan is the timebase contract between the movie timeline and a future Filament off-screen renderer. It converts the movie frame index into an exact presentation timestamp and the animation-local time for every actor active on that frame.

## Scheduling rules

- Supported frame rates are 24–60 fps.
- Movie duration is bounded to 30 minutes; at most 100 actor-animation tracks are accepted.
- A track has an inclusive start and exclusive end on the movie timeline.
- Animation playback speed is bounded to 0.05x–8x.
- Looping tracks wrap by the source animation duration; non-looping tracks stop at the final source time.
- Two tracks for the same actor may not overlap. Different actors may animate concurrently.
- Frame timestamps are computed from the integer frame index (index * 1,000,000 / fps), not accumulated floating-point deltas. The final partial frame is retained by rounding frame count upward.
- frameAt calculates one frame on demand; it does not allocate a list for every frame of a long movie.

## Renderer hand-off still required

For each scheduled frame, the future renderer must:
1. Load the already validated local GLB asset and create/restore its Filament scene on a dedicated GL/render thread.
2. Apply each actor's transform and call the GLB animator with animationTimeUs / 1,000,000.0.
3. Render into a bounded off-screen target with alpha preserved.
4. Hand the resulting frame to the Media3 video pipeline at the exact presentationTimeUs.
5. Release render targets and GL resources on cancellation, export completion, or failure.

Filament documents off-screen RenderTarget rendering and HardwareBuffer-backed textures in its Android samples. Media3 exposes VideoFrameProcessor / FrameProcessor.Factory integration points, but the bridge must be implemented and device-tested before claiming that a 3D character is rendered into the final movie.

## Validation boundary

The JVM tests validate scheduling, loop/clamp behavior, playback speed, overlap rejection, frame-rate bounds and timestamp determinism. They do not instantiate Filament, allocate a GPU target, encode a movie, or establish physical-device performance. This module is offline-only and contains no AI, network asset loading or cloud rendering.
