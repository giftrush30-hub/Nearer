# NeaRer Studio renderer decisions

Status: research-backed architecture; advanced GPU effects and 3D runtime are not yet shipped.

## Compositing
Media3 Composition supports multiple sequences for picture-in-picture/overlays and background audio. Transformer emits a single composited video stream and one audio stream; this does not limit editable source lanes. The current exporter already handles sequential clips, per-clip colour/text effects, timed captions and a looping music sequence. Arbitrary positioned lanes still need a tested adapter and custom frame processing.

## Green screen
Implement chroma key as a Media3 GlEffect/GlShaderProgram. Use key-colour distance in HSV or YCbCr, tolerance, edge softness, despill and alpha preservation. The keyed foreground must then be composited over a second visual sequence. Expose a simple colour picker and named controls. Do not promise perfect hair or transparent-fabric edges before real footage testing.

## Masks, tracking, keyframes and transitions
Use timestamped keyframes with hold, linear and smoothstep interpolation, storing positions in normalized coordinates. Start with rectangle, ellipse and polygon masks; feather through an alpha texture. Tracking is separate: user selects a point/region, the tracker estimates movement, and the user can correct samples. Media3 Composition documents no built-in video/audio crossfades; retain fade-through-black until a custom compositor is integrated and tested.

## 3D engine and assets
Evaluate Google Filament first for the embedded Android runtime, with glTF 2.0/GLB assets. Filament offers Android Java/JNI APIs, OpenGL ES 3.0, glTF loading, PBR materials, cameras and lighting, and is designed for efficient Android rendering. Load it only when the 3D workspace opens. Godot remains a candidate for an isolated prototype, but its Android compatibility renderer also requires OpenGL ES 3.0 and its complete editor/runtime stack is heavier for embedding.

Use licensed GLB assets with reusable rigs/animations, LOD meshes, compressed textures and explicit manifests. Keep starter assets offline and record provenance/licences. Do not scrape protected characters, films, music, fonts or models.

## Compatibility and performance gates
The app currently has minSdk 26 (Android 8). Keep it until whole-app tests prove a lower API is safe; Media3 Transformer alone supporting API 23 does not prove every app dependency does. Before enabling advanced compositing/3D by default, benchmark 720p/1080p render time, peak memory, dropped frames, heat and battery on representative Android 8/10 phones. Use preview-sized thumbnails, bounded caches, streamed media, and release GL/decoder/native resources when leaving the workspace. No physical-phone benchmark has been run; Filament is provisional pending same-scene device comparison.

References:
- https://developer.android.com/media/media3/transformer/composition
- https://developer.android.com/reference/androidx/media3/effect/GlEffect
- https://developer.android.com/reference/androidx/media3/effect/BaseGlShaderProgram
- https://google.github.io/filament/dup/intro.html
- https://google.github.io/filament/dup/android_samples.html
- https://docs.godotengine.org/en/stable/about/system_requirements.html
