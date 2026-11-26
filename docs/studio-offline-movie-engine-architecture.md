# NeaRer Studio offline movie engine architecture

Status: implementation plan and engineering constraints, 2026-11-26.

## Product contract

- Core editing, preview, project storage, lessons, effects, character assets and export must work without internet, account, cloud storage or AI.
- Network access may be used only for optional purchases or user-initiated acquisition of assets; the core editor never depends on it.
- The editor must explain tools in ordinary language and expose advanced controls progressively.
- Do not silently flatten, drop or pretend to render a track. Preflight must name unsupported content before export.
- Keep the base app lean. Optional 3D runtimes and large asset packs must be isolated and loaded only when a project uses them.
- Preserve original media. Work from URI permissions and generated intermediates; publish an export only after container and stream validation.
- The current supported project baseline is Android 8+; physical device performance remains unverified until tested on target phones.

## Engine split

### 1. Timeline and project model

The timeline is the source of truth. Each track owns ordered items with:
- Stable item and track IDs.
- Source URI and persisted read permission.
- Source in/out timestamps.
- Timeline start/end timestamps.
- Track mute/lock/visibility state.
- Volume/opacity and animated properties.
- Optional mask, effect, transition and asset references.

All time is stored in integer milliseconds in the project model. Renderer adapters convert to microseconds using checked arithmetic. Preview and export must consume the same immutable render/frame plan. The renderer must never rebuild its own timing rules from UI widgets.

### 2. 2D video renderer

Use Media3 Transformer for reliable decode, encode, trim, rotate, crop, scaling, image clips, common effects, text/image overlays and standard export. Use custom Media3 GL effects where a single stream can be transformed in place.

Media3 Composition can combine sequential sequences and background audio, but its documented Transformer limitations include video/audio crossfades. CompositionPlayer supports layered preview work but multi-asset preview is still in active development. Therefore:
- Do not advertise native overlapping video crossfades as complete through stock Transformer.
- Keep the renderer-independent timeline/frame plan independent of Media3.
- Implement a dedicated multi-input compositor/transition renderer for overlapping picture layers and crossfades.
- Use Media3 for decode/encode/mux where the output path can accept frames from the custom compositor; prototype and benchmark this bridge before replacing the existing export path.
- Keep the existing single-sequence export as a safe fallback for projects that use only supported operations.

### 3. Masks, keyframes and tracking

- Store mask geometry in normalized 0..1 coordinates, not pixel coordinates.
- Keep rectangle, ellipse and polygon masks as editable vector data; rasterize only at the active preview/export resolution.
- Cache low-resolution preview mask textures; do not allocate full-resolution alpha planes repeatedly.
- Sample keyframes against clip-local timestamps so moving or trimming a clip does not corrupt its animation.
- Motion tracking is an offline point/patch tracker, not AI. Expose confidence and lost-track status; never silently extrapolate a lost subject.
- The GPU compositor should combine mask alpha, chroma-key alpha, opacity and transforms in a documented order, with tests for edge cases.

### 4. 3D and character performance

Filament is a candidate runtime because it is a real-time physically based renderer with Android and glTF support. It must be an optional/lazy-loaded capability, not a mandatory base dependency until APK size, native ABI footprint, memory use, GPU compatibility and Android 8 device tests are measured.

Use GLB/glTF 2.0 assets with:
- Local URI, SHA-256, byte size, provenance and licence metadata.
- Skeleton/rig identity and named animation clips.
- Optional morph targets and prepared facial/lip-sync clips.
- Explicit actor transforms, camera, lights and render dimensions.

Prepared actions (walk, run, jump, fall, dance, fight, fly, swim, climb, talk) are selected and timed by the user. Practical effects (fire, smoke, sparks, lightning, explosions, water, dust) are local layered assets or procedural shaders. No generative AI is involved.

A prepared 3D character can be swapped for another prepared rigged character and animated. Turning arbitrary live-action footage of a real person into any animal or different person is not a one-click deterministic non-AI operation; it requires manual roto/masks, tracked compositing, a matched replacement performance and substantial artist work. The UI must state this honestly.

### 5. Audio and voice

The audio engine must mix independent local tracks with source trims, gain automation, equal-power fades, pan, mute/solo, and a final limiter. Voice recording and processing can use deterministic DSP: EQ, compression, de-essing, noise gating, pitch shift and formant processing. A natural result depends on source quality and CPU; do not label a basic pitch shifter as studio-grade autotune.

Use bounded PCM buffers, streaming decode, reusable arrays and explicit sample-rate/channel conversion. Avoid loading full songs or films into RAM. Preserve 16-bit PCM / channel compatibility where required by Media3 composition export.

### 6. Practical VFX and overlays

Prefer prepared, licence-tracked, offline assets and procedural shaders for fire, smoke, rain, snow, sparks, lightning, muzzle flashes, explosions and stylized transformations. Every asset has a provenance/license record and a preview thumbnail. Advanced effects must be optional downloads or imported local packs; core lessons and starter assets remain bundled and usable offline.

Text, subtitles, logos, stickers and picture-in-picture are independent timeline layers with timing and transform keyframes. Export preflight reports any layer that has no renderer implementation; it must never silently omit it.

### 7. Beginner workflow, lessons and delivery

The beginner route is: import clips → arrange → trim → add words/music → choose a look/effect → preview → export. Advanced tools live under clear categories (Layers, Green Screen, Masks, Motion, 3D, Sound, Effects). Lessons are local, short and tied to real controls.

Export presets should be explicit (720p/1080p, frame rate, quality) and validated against available storage and device encoders. Use temporary output files, cancellation cleanup, progress and final MP4 stream/container checks. Never promise a specific speed or HD result without device-specific measurement.

## Performance and safety gates

Before enabling an engine or effect by default:
1. JVM tests for timing, geometry, state migration, invalid input and cancellation.
2. Android manifest/lint/build checks.
3. Benchmark on low-memory Android 8 class hardware and current devices.
4. Measure sustained preview frame pacing, export speed, peak memory, thermal throttling, output dimensions and audio/video sync.
5. Compare output against reference clips and inspect actual MP4 video/audio tracks.
6. Keep feature flags/fallbacks for devices whose GPU/codec path fails.

## Current engineering state

The repository has a multi-track project model, render-plan validation, chroma-key shader, mask rasterizer, keyframe evaluator, offline point tracker, GLB scene manifest/validator, a layered preview path with a multiple-input video graph, and export safety handling.

The current PR adds a common microsecond timeline adapter, deterministic frame sampling, transition-window planning, audio mix scheduling, and prepared-character/dialogue/practical-effect cue validation. These are foundations, not the completed renderer. Next steps are to connect the frame plan to actual preview and export, implement the GPU multi-layer compositor and PCM renderer, then integrate the optional 3D runtime behind measured compatibility gates.
