# Offline visual layer graph

The visual layer graph is the deterministic bridge between the editable timeline and a future pixel compositor. It is intentionally Android-graphics-independent so the same layer ordering, clip-local animation, masks, blend-mode intent and effect parameters can be tested on the JVM and consumed by preview/export renderers.

## Plan capabilities

- Video, image, shape and particle source kinds.
- Explicit z-order and blend-mode intent: normal, add, multiply, screen, overlay, darken and lighten.
- Normalized masks and per-layer effect stacks.
- Clip-local keyframes for position, scale, rotation, opacity, crop, blur and mask feather.
- Validated effect controls for colour grade, blur, chroma key, glow, drop shadow, vignette, film grain, outline and warp.
- Deterministic active-layer filtering and source-time mapping from the composition frame planner.

## Resource and input bounds

- 64 visual layers per frame plan.
- 16 effects and 8 masks per layer.
- 2,000 keyframes per animation through the shared animation validator.
- Normalized crop/mask bounds and effect-specific finite numeric ranges.
- Duplicate layer/effect IDs and missing active layer definitions are reported before rendering.

## Implementation boundary

This milestone produces draw commands; it does not yet draw pixels, implement blend shaders, animate particles, or connect a new GPU compositor to final MP4 export. Media3 exposes GPU effect, overlay, frame-processor and multi-input video-compositor building blocks. Composition supports multiple sequences and picture-in-picture. The next renderer milestone must consume this plan in preview and export, then be tested on real Android GPUs.

Everything here is local and deterministic. No AI service, network API or cloud backend is used.
