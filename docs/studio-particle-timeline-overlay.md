# Particle timeline overlay

The Film Studio timeline now has a timestamp-driven particle overlay that is used by the same Media3 effect list for live preview and final Transformer export.

## Presets

- Fire: rising warm embers and glow
- Smoke: slower, expanding grey plumes
- Explosion: outward-moving hot particles
- Sparks: short bright streaks
- Lightning: fast narrow flashes

Each preset is built from the deterministic offline particle engine. Its frame is regenerated from the current presentation timestamp, so seeking does not depend on rendering earlier frames. All simulation parameters are local and saved with the timeline's project preferences.

The CanvasOverlay draws directly to the video-sized overlay canvas. This avoids allocating a full-resolution CPU pixel buffer for every export frame. The separate rasterizer remains available for pixel compositing and other offline image workflows.

## Scope

This first UI integration applies the selected particle preset across each clip's rendered duration. Per-emitter position keyframes, individual emitter tracks, particle collision/physics and a GPU-native particle shader remain future improvements. The feature requires Android Media3 frame processing and must be validated on physical devices for performance.
