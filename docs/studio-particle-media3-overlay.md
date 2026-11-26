# Timestamped Media3 particle overlay

The deterministic particle simulator and CPU rasterizer can be used as a real Media3 bitmap overlay. Media3 supplies the video-frame presentation timestamp; the overlay regenerates the matching particle frame, rasterizes it into a reusable ARGB bitmap and returns it to OverlayEffect.

## Behavior

- One reusable bitmap and one reusable IntArray per configured video size; no full-frame bitmap or pixel-buffer allocation for every frame.
- Particle simulation is evaluated directly from presentationTimeUs, so seeking and export do not depend on previously rendered frames.
- The same GlEffect can be attached to EditedMediaItem effects for live preview and Transformer export.
- Invalid simulation/raster plans fail the frame with a timestamped VideoFrameProcessingException rather than silently dropping an effect.
- Bitmap and GL texture resources are released through the Media3 overlay lifecycle.

## Safety and performance

- Reuses the 16-megapixel output cap and 100-million raster-operation budget.
- The particle engine enforces bounded emitter and active-particle counts.
- Rasterization is CPU work on Media3's frame-processing path. High particle counts or 4K output can reduce export/preview speed; device benchmarking is required before enabling high-density presets by default.

Media3's standard OverlayEffect uses source-over alpha composition. The custom multiply/screen/add layer modes remain available in the CPU reference compositor but are not claimed as supported by this GPU overlay.

This component is local and offline. It does not use AI, cloud rendering or network services.
