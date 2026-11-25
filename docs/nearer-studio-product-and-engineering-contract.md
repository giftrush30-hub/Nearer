# NeaRer Studio — product and engineering contract

Status: product direction agreed on 24 November 2026. This document separates the agreed vision from features already implemented.

## Product promise

NeaRer Studio should feel like a simple movie-making studio on a phone, not like a desktop 3D game editor squeezed onto a small screen.

**Simple on the outside. Powerful on the inside.**

A first-time user should be able to make a short movie using plain-language actions and ready-made resources. Advanced controls remain available but are never required for ordinary tasks.

## Non-negotiable rules

1. **No AI.** No AI model, cloud inference, AI editing service, or AI-only feature may be required by Studio. Character, effects, tracking, cutouts, animation and transformations must use conventional graphics, signal processing, compositing and manual controls.
2. **Offline-first core.** Importing local media, editing, project saving, lessons, effects included with the app, previewing and exporting must work without an internet connection. Network access may be used only for an explicitly optional paid service or feature, never as a hidden requirement.
3. **Beginner language.** Use words such as “Add video”, “Cut”, “Join”, “Move”, “Add music”, “Put words on screen” and “Make video”. Explain technical controls only when the user opens advanced tools.
4. **Ready-made first.** Prefer ready-made characters, movement clips, environments, effects and scene templates. Do not make users model, rig, script or build a game-like scene just to make a movie.
5. **Fast, safe project saving.** Save small project changes locally and often. Saving an edit must not trigger a full video export. Preserve original media and recover project state after an interrupted session.
6. **Protect the rest of NeaRer.** Studio must be isolated from Messaging and HyperTransfer failures, loaded only when opened, and must not keep large render buffers or 3D assets resident when it is closed.
7. **Be honest about device limits.** Check memory, graphics and codec support. Offer lower-cost previews and export settings. Do not promise a resolution or effect that the phone cannot process.

## Agreed Studio departments

- Movie Editor: trim, split, join, reorder, crop, speed, titles, captions, transitions and colour adjustments.
- Visual Effects: compositing, particles, fire, lightning, smoke, water, explosions, glow and other conventional effects.
- Character Workshop: ready-made people, animals and creatures, plus optional character appearance controls.
- Movie World Workshop: ready-made locations and a simple way to arrange or customize sets.
- 3D Movie Studio: complete 3D scenes with characters, animation, virtual cameras, lights, environments and sound.
- Sound Studio: voice recording, sound effects, music, mixing and voice processing.
- Scene Director: guided, template-based scene assembly with plain-language steps.
- NeaRer Film School: short offline lessons and practice tasks built into Studio.

The 3D system should support realistic and stylized looks. Motion capture may be an optional advanced input; ready-made animation clips are the default because they are simpler and faster.

## Technical direction to validate

- Keep the existing Android/Compose application shell and current Media3-based video/audio capabilities.
- Keep video decoding and export on Android media APIs where they meet the need; do not add a large dependency without measuring its APK, memory and performance cost.
- Build effects as bounded, reusable processing stages. Prefer GPU rendering for live previews when supported, with a lower-cost fallback.
- Treat 3D as a separately loaded engine/runtime with optional asset packs. Do not load the 3D engine or large asset libraries on the app home screen.
- Store project instructions and references to source media, not duplicate copies of every source video. Use a versioned project format and safe local writes.
- Generate preview-quality frames or proxy media only when needed; release temporary files and graphics resources when a project closes.
- Use device capability checks and conservative defaults before enabling expensive effects or export settings.
- Keep Film School text and illustrations bundled locally. Do not embed large tutorial videos in the base APK.

## Delivery order

### Foundation — current work
- Make the Studio home screen easier for beginners to understand.
- Add an offline Film School with short lessons and local progress.
- Preserve and build on the existing video timeline, editor, audio tools and project codecs.

### Next: dependable movie projects
- Multiple named projects rather than one shared timeline.
- Safe autosave and recovery after process death.
- Clear project list, duplicate/rename/delete and media-missing recovery.
- More complete clip-level controls and predictable preview/export behaviour.

### Next: beginner editing workflow
- Guided first-movie flow.
- Simple clip ordering and trimming.
- Music, titles, captions and transitions with plain-language controls.
- Helpful export quality choices based on the phone.

### Next: visual effects
- Layered composition, chroma key, manual masks/rotoscoping, motion tracking and reusable effect presets.
- Build and test each effect as a real offline feature; never present a decorative mockup as a working effect.

### Next: ready-made 3D filmmaking
- Separate 3D engine evaluation and Android device benchmark.
- Small starter library of optimized, rigged characters, animation clips, sets, cameras and lights.
- Scene templates that can be changed without game-development knowledge.
- Optional packs and memory-aware loading.
- Only after the engine and export pipeline pass real-device tests should the full 3D workflow be advertised.

## Acceptance checks for every Studio feature

- Works with airplane mode enabled, unless the feature is explicitly marked as an optional online service.
- Adds no AI dependency.
- Uses plain-language labels and has a beginner path.
- Does not modify source media unless the user explicitly chooses to overwrite it.
- Saves project settings without starting a full export.
- Handles missing files, denied permissions, low storage and process recreation safely.
- Has unit tests for codecs and editing rules, plus device tests for playback, GPU use, memory, heat and export.
- Does not block or destabilize messaging, nearby connections or file transfer.
