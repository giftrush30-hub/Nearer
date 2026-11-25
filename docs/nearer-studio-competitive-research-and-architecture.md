# NeaRer Studio: competitive research and product architecture

Research baseline: 24 November 2026  
Status: architecture proposal; not a claim that the listed features are implemented.

## Executive direction

NeaRer Studio should combine the reachability and speed of a beginner mobile editor with the depth of a professional post-production and 3D pipeline. It must not reproduce the complexity of desktop software on a phone.

Product promise: **make the first good movie easy; keep the professional ceiling high.**

Hard constraints:
- No AI features, models, inference services or AI dependency.
- Core import, editing, learning, saving, preview and export must work offline.
- No account, cloud project, internet connection or subscription required for core editing.
- Beginner controls use plain language. Technical controls are optional and grouped under “More control”.
- No feature may be called complete until it works on real supported Android devices and has tests.
- Studio must not make Messaging, Nearby, HyperTransfer or the rest of NeaRer unstable.

## Competitive benchmark: lessons to adopt, not interfaces to copy

### Mobile editors

**CapCut**
- Strength to learn from: fast template-led creation, accessible effects, social aspect ratios and a short path from footage to a finished clip.
- NeaRer response: offline scene templates, reusable effect recipes, simple story-first creation and clean export without requiring online assets or AI.

**VN**
- Strength to learn from: approachable multitrack editing and a useful balance between speed and direct timeline control.
- NeaRer response: make a simple clip strip the default; expose tracks only when a user chooses “More control”.

**InShot**
- Strength to learn from: a low-friction beginner flow and quick framing for common destinations.
- NeaRer response: ask “Where will this movie be watched?” in plain language, then set frame shape and safe areas automatically.

**KineMaster**
- Strength to learn from: layers, chroma key, keyframes and accessible mobile compositing.
- NeaRer response: use a layer stack with human-readable names (“Person”, “Background”, “Fire”, “Words”), with simple presets before manual keyframes.

**LumaFusion**
- Strength to learn from: a serious multitrack timeline and precision controls on a mobile device.
- NeaRer response: retain an advanced timeline as an optional workspace, never make it the first screen a novice sees.

### Professional post-production

**DaVinci Resolve**
- Strength to learn from: editing, colour, visual effects and audio post-production treated as connected stages of one production workflow.
- NeaRer response: one project and one shared media library across Movie, Effects, Colour and Sound departments. Avoid isolated copies of the same media.

**Blender**
- Strength to learn from: an integrated 3D pipeline spanning modelling, rigging, animation, simulation, rendering, compositing, motion tracking and video editing.
- NeaRer response: support a full 3D production path, but hide technical modelling and rigging behind ready-made characters, animation clips, sets and guided scene templates.

### Platform technologies

**Android Media3 Transformer**
- Relevant capabilities: trim, scale, rotate, overlays, filters, HDR and slow-motion processing; hardware codec and OpenGL paths; asynchronous export, progress reporting and cancellation.
- Architecture decision: keep Media3 as the first candidate for native video assembly and export where its composition model supports the required edit. Build a separate, tested compositor path for effects it cannot express. Do not silently claim crossfades or arbitrary effects are supported when the chosen pipeline cannot render them.

**Godot Android library**
- Relevant capabilities: engine can be embedded into an existing Android app and communicate with the host app; official sample demonstrates rendering glTF models.
- Important constraints to validate: one engine instance per Android process; orientation/configuration changes need careful handling.
- Architecture decision: run a focused prototype in a separate Studio-only screen/host, measure cold start, resident memory, APK/AAB size, device compatibility and clean unload before committing to a 3D runtime.

**Google Filament**
- Relevant capabilities: physically based real-time rendering with a stated focus on high-quality rendering and efficiency on Android.
- Architecture decision: benchmark Filament for a lightweight model/scene preview or custom renderer against Godot. Filament is a renderer, not a complete film-production editor; animation, scene authoring, asset management and export would still need engineering.

**Unity as a Library**
- Strength: embed an established 3D runtime into a native Android app.
- Critical trade-off: documented integration limitations include full-screen rendering and a retained memory footprint after unload. That conflicts with NeaRer's small, responsive, low-memory host-app goals unless a measured prototype proves acceptable.

## Recommended product architecture

### 1. One project, several simple rooms

- **Start**: “Make a movie”, “Edit a video”, “Make a 3D movie”, “Learn”.
- **Movie room**: clip order, cut, trim, crop, speed, titles, captions, transitions, sound.
- **Effects room**: background replacement, keying, masks, tracking, particles, compositing.
- **Character room**: ready-made human/animal/creature characters, appearance presets, animation clips.
- **World room**: ready-made locations and a guided set builder.
- **3D movie room**: place actors, select actions, set camera and light, direct the scene, render.
- **Sound room**: narration, music, effects, voice processing and mixing.
- **Film School**: offline step-by-step lessons and short practice projects.

All rooms operate on one versioned project model and refer to the same source media. The simple view is the default; advanced timeline, layer and parameter controls are optional.

### 2. Scene recipes, not a game editor

A scene recipe is a reusable, editable arrangement:
- environment/set reference;
- character references;
- animation/action clips;
- camera movement;
- lighting setup;
- effect stack;
- sound cues;
- duration and shot sequence.

Example: “Hero lands in a street” loads a set, a ready-made character, a landing animation, a camera move, dust and sound. The user changes the character, location, timing or effect intensity. The recipe remains editable and does not require scripting or rigging.

### 3. Ready-made assets first

Ship only a small optimized starter set. Keep optional asset packs separate and downloadable only when the user explicitly chooses to obtain them. Once downloaded, packs work offline. Use open, documented asset formats and maintain a clear license/attribution record for every bundled model, texture, sound and font. Do not bundle scraped or unlicensed assets.

Asset tiers:
- Lite: low-poly or reduced texture, low memory and fast preview.
- Standard: balanced mobile quality.
- High: high-resolution textures and more detailed models for capable devices.

Use the same asset identity across tiers so users do not have to rebuild scenes.

### 4. Render and export pipeline

Separate project editing from final rendering:
- A project stores edit instructions, asset IDs and durable source references.
- Preview uses reduced resolution, proxy frames and capped effect quality.
- Export runs as a visible, cancellable job with progress and clear storage estimates.
- Render to a temporary output; validate it; then atomically publish the completed file.
- Preserve original source media.
- Recover or clean abandoned temporary exports after process death.
- Never block the UI thread with decode, render, file copy or export work.

### 5. Save and recovery system

- Save small project-state changes locally without rendering video.
- Debounce rapid slider updates; flush on lifecycle pause and explicit save.
- Use atomic writes or a transactional database for project records.
- Keep versioned snapshots and a recovery journal.
- Detect missing or revoked source-file permissions and offer “Find file” or “Remove clip”; never silently discard a project.
- Keep generated proxy/cache files separate from source media and safely purge regenerable cache.
- Provide project duplicate, rename, archive and delete actions with clear confirmation.

### 6. Device-aware performance

No single resolution, frame rate, model detail or effect stack is safe on every Android device. Detect codec, GPU and memory capabilities, then set a conservative starting quality. Let the user choose a higher quality when supported.

Measure:
- cold-open time for Studio and the 3D room;
- peak and steady-state PSS/RSS and native/GPU memory;
- dropped preview frames and time-to-first-frame;
- export speed, output validity, thermal throttling and battery use;
- app size and optional pack sizes;
- recovery after force-stop, low storage and process recreation.

Release decoders, player instances, textures, framebuffers and engine resources when their room closes. Do not keep the 3D runtime resident while the user chats or transfers files.

## Signature ideas to investigate

1. **Movie in Minutes** — guided templates that assemble a first scene from ready-made pieces.
2. **One Scene, Many Worlds** — change the background, weather, lighting and atmosphere while preserving the chosen subject and shot setup.
3. **Movie Rescue** — recover interrupted edits, locate missing media and explain storage problems in simple words.
4. **Director Camera** — choose “follow”, “close-up”, “orbit”, “wide shot” or “crane” rather than editing camera transforms.
5. **Effect Recipes** — combine and save effect stacks as named presets; edit each layer when desired.
6. **Shot Continuity Helper** — show side-by-side reference frames and warn when a reused character, costume or scene setup has changed between shots. This is a conventional comparison/checklist tool, not AI.
7. **Offline Foley Bench** — build footsteps, cloth, impacts, ambience and other sounds from licensed bundled recordings and manual layering.
8. **Take Selector** — compare alternate takes and choose the best one without destroying the originals.
9. **Story Strip** — arrange scenes as simple cards first; open the timeline only when precision is needed.
10. **Project Health Check** — explain missing files, unsupported formats, low space, heavy effects and export estimates before they cause failure.

These are proposals to prototype and test, not implemented features.

## Delivery gates

### Gate A — reliable 2D foundation
- Multiple named projects and safe project storage.
- Correct source permissions and missing-file recovery.
- Beginner movie flow with reorder, trim, sound, titles and transitions.
- Export progress, cancellation, validation and recovery.
- Automated codec tests and real-device export tests.

### Gate B — compositing and effects
- Layer model, transforms, keying, masks, manual rotoscoping and motion tracking.
- Reusable effects and deterministic offline rendering.
- Compare preview with final export to prevent surprises.

### Gate C — 3D feasibility prototype
- Evaluate Godot and Filament with the same sample scene and glTF character.
- Measure app bundle delta, startup, memory, rendering quality and release behaviour on low-, mid- and high-tier Android phones.
- Select one engine only after evidence; keep it isolated from the app's main process if necessary.
- Demonstrate a ready-made character, one animation, one set, camera, light and offline video export before expanding.

### Gate D — 3D movie production
- Character and environment packs, action library, scene recipes, virtual cameras, lights, audio and rendering.
- Ready-made path first; custom modelling and rigging remain optional advanced tools.

### Gate E — quality and ecosystem
- Compatibility matrix, accessibility, tutorials, licensing review, crash recovery, thermal tests and long-project tests.
- Cross-device project portability only where media and asset licenses allow it; no cloud dependency for local use.

## November 2026 research update — rendering, compositing and offline assets

### What the current Android media stack can and cannot do

The official Android Media3 documentation confirms that Transformer can build multi-item compositions from video, image and audio inputs; apply effects at clip or composition level; and export the result asynchronously. The current Composition documentation explicitly lists sequential editing, background audio, overlays and effects as supported use cases. It also explicitly lists crossfading video or audio tracks as not yet supported in Composition. Do not present a crossfade button as a real crossfade until NeaRer has a separately validated implementation. The current editor's cut and fade-through-black choices are honest interim options.

- Composition guide: https://developer.android.com/media/media3/transformer/composition
- Transformer API: https://developer.android.com/reference/androidx/media3/transformer/Transformer
- Editing app guide: https://developer.android.com/media/implement/editing-app

Engineering implications:
- Keep Media3 as the native Android editing/export foundation for the 2D timeline; do not introduce a second full video engine just to add basic editing.
- Build the editor's project/timeline model independently of the renderer. That allows a later rendering backend to support richer overlays, transitions, masks and compositing without rewriting project files.
- Treat preview/export parity as a release gate. The same effect parameters must drive both preview and export wherever the platform APIs allow.
- Export must expose progress and cancellation, release scarce codec resources, validate the temporary output, and only then publish it as a finished movie. A failed or cancelled export must not be shown as complete.
- Test codec and format combinations on real devices. Successful API construction is not proof that every phone's hardware encoder can render every source.

### 3D asset delivery and realism

Khronos glTF is a royalty-free, standardised runtime format designed for efficient loading. A GLB can package scene structure, meshes, materials, textures, skins and animation. Its PBR material model supports physically based surface properties, and KTX 2.0/Basis Universal texture delivery can reduce texture storage and GPU memory costs when supported by the runtime.

- Khronos glTF overview: https://www.khronos.org/gltf/
- glTF registry/specification: https://registry.khronos.org/glTF/
- PBR material guide: https://www.khronos.org/gltf/pbr

This strengthens the case for a **GLB-first asset pipeline** for ready-made characters, props and sets, with compressed textures, level-of-detail variants, reusable animation clips and explicit asset manifests. It does not prove that a full 3D engine should be bundled in the base APK. Engine selection still requires an Android benchmark with the same scene and assets.

### Asset sourcing and legal safety

Do not scrape characters, movie footage, sound effects, music, fonts or 3D models from competitors. Every bundled or downloadable pack needs a source record, exact license, attribution requirements, modification permissions and commercial-use status. Kenney's own support page says its asset-page packs are CC0/public domain, but each selected pack still needs its included license checked before bundling. Keep a credits/attribution manifest even where attribution is not mandatory.

- Kenney licensing FAQ: https://kenney.nl/support

### Low-memory and device-aware operation

Android's current memory guidance identifies bitmap allocation, redundant copies and poor resource release as important causes of memory pressure. Studio should decode preview thumbnails at display size, reuse bounded caches, stream source media rather than loading full-resolution frames into memory, and unload preview/3D resources when leaving their workspace.

- Android memory guidance: https://developer.android.com/topic/performance/memory.html
- Bitmap optimisation guidance: https://developer.android.com/topic/performance/memory/guide/bitmaps

### Updated engineering decision

For the next implementation stage, prioritise a renderer-independent edit decision model, durable multi-project persistence, recoverable/cancellable export and real-device benchmarks. Keep the advanced 3D room isolated and lazy-loaded. The editor should grow in capability through replaceable modules rather than shipping a huge always-resident engine to every user.

## Sources consulted

- CapCut/VN/InShot/LumaFusion mobile comparison: https://pixflow.net/blog/best-mobile-video-editing-apps/
- Android editor feature comparison: https://gadgets.beebom.com/guides/best-video-editing-apps-on-android-and-iphone
- KineMaster and Android mobile workflow comparison: https://www.techrepublic.com/de/article/news-best-video-editing-apps-android-2026/
- Blackmagic Design DaVinci Resolve: https://www.blackmagicdesign.com/products/davinciresolve
- Blender feature overview: https://www.blender.org/features/
- Android Media3 Transformer: https://developer.android.com/media/media3/transformer
- Android Media3 editing guide: https://developer.android.com/media/implement/editing-app
- Godot Android library: https://docs.godotengine.org/en/stable/tutorials/platform/android/android_library.html
- Godot embedded Android project guide: https://docs.godotengine.org/en/latest/tutorials/platform/android/embedding_in_android_projects.html
- Google Filament: https://google.github.io/filament/main/filament.html
- Unity as a Library for Android: https://docs.unity.com/en-us/engine/6000.0/manual/platform-specific/android/developing/unityasa-library
- Android memory guidance: https://developer.android.com/topic/performance/memory.html
- Android bitmap memory guidance: https://developer.android.com/topic/performance/memory/guide/bitmaps

## Research limitations

This is a desk-research and architecture synthesis, not hands-on testing of every competitor. Third-party app comparison pages can be inaccurate or outdated. Verify current capabilities, regional availability, license terms and device support directly before using them as engineering requirements. Benchmark competing apps on the same physical devices and source clips before making superiority claims.


## 25 November 2026 implementation update — multi-track editor integration

The next implementation batch now connects the renderer-independent timeline model to each named movie project.

Implemented on the integration branch:
- A per-project local multi-track store, separate from source media and independent of cloud services.
- Four visible lanes: main video, background music, titles/captions, and visual effects.
- Existing ordered video clips are represented as sequential items with source in/out points and timeline positions.
- Captions and the main title are represented as timed items on the text lane; colour-grade adjustments are represented on the effects lane.
- Music has a persisted mute state that is honoured by the final export composition.
- The main video lane can be locked; while locked, clip add, remove, reorder and trim controls are disabled.
- Shared editing operations support moving, trimming, muting, locking, adding tracks and removing items, with bounds checks and unit tests.
- Multi-track writes are debounced during continuous control changes to avoid excessive small writes.

Important boundary:
- The four-lane project representation is real and saved locally, but this is not yet a general-purpose compositor for arbitrary overlapping video tracks, masks or 3D scenes.
- Media3's documented MP4 output is limited to one video and one audio track. Visual layers must ultimately be composited into the video stream, and audio lanes mixed into the audio stream by a validated renderer.
- Existing clip sequence, captions, colour adjustments, and background music remain on the current Media3 rendering path. New arbitrary lane combinations must not be shown as export-ready until the renderer implements and tests them.
- No physical Android phone playback/export or thermal/memory benchmark is claimed by this implementation update.
