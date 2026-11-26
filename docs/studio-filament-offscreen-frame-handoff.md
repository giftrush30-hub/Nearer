# Filament off-screen frame hand-off contract

## Implemented
The 3D animation timeline now has a second deterministic layer: Studio3DOffscreenFramePlan. It validates the output surface dimensions and creates one RGBA8 frame request at a time. Each request carries the exact movie presentation timestamp, frame index, width, height, row stride, byte count and the animation samples for that timestamp.

- 24–60 FPS is inherited from the animation timeline plan.
- Dimensions are bounded to 3840x2160 and 16 megapixels.
- Each output frame is RGBA8 with a checked row stride and 64 MiB maximum frame buffer.
- Frame requests are computed on demand; raw movie frames are not accumulated in memory.
- Animation samples are taken from the existing deterministic track plan, so preview/export can share the same timebase.

## Required runtime adapter
A device-side Filament renderer still needs to:
1. Create one Engine and off-screen View/RenderTarget per export session, not per frame.
2. Attach RGBA8 color and depth textures to the RenderTarget.
3. For each request, update every actor's animation time and transform on the Filament render thread.
4. Render the scene into the target and perform an explicit GPU-to-encoder hand-off.
5. Preserve the exact presentation timestamp through the Media3 frame input path.
6. Release frame buffers/textures only after the downstream consumer has finished with them.
7. Destroy Filament objects in dependency order on the owning thread, including cancellation/failure paths.

Filament documents View.setRenderTarget(RenderTarget) for off-screen rendering and sample applications demonstrate rendering a scene to a texture. The actual render-target allocation, readback or HardwareBuffer bridge, Media3 integration, alpha/color-space handling and encoder synchronization are not implemented by this scheduling contract.

## Validation boundary
JVM tests cover timestamp generation, actor activation windows, RGBA buffer arithmetic, dimension limits and on-demand frame requests. They do not validate GL output or GPU timing. Physical-device tests remain required before claiming 3D movie export.
