# GPU mask pass for Media3 video effects

## What is integrated

StudioGpuMaskEffect is a Media3 GlEffect for one input stream. It builds a combined 8-bit alpha plane from up to eight normalized rectangle, ellipse or polygon masks, uploads that plane once as a single-channel OpenGL ES 2.0 texture per input-size configuration, then samples the mask texture in a fragment shader for every video frame. Multiple masks intersect by multiplying their alpha values. Inverted and feathered masks are retained through the existing mask rasterizer.

The visual render bridge now includes this GPU mask pass in the same per-item effect chain used by Media3 preview and Transformer export. It does not copy full-resolution video frames to the CPU. CPU work is limited to generating the mask texture when the frame dimensions change.

## Limits and validation

- Mask count is bounded to 8 per layer and raster dimensions to the existing 16-megapixel limit.
- The alpha texture is recreated when the input resolution changes and released with the shader program.
- The GPU shader path is exercised by Android build/CI, while mask geometry/intersection is covered by JVM tests against the deterministic rasterizer.
- Physical GPU preview/export and sustained performance tests remain necessary.
- This pass applies masks to one layer; it does not yet implement inter-layer Multiply, Screen, Add, Overlay, Darken or Lighten GPU blending. Those require a multi-input compositor path, not a per-stream effect.
- Offline-only; no AI, cloud, network asset loading or backend services.
