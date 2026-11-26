# Shared preview and export compositor integration

The Film Studio timeline uses the same per-item StudioVisualRenderBridge effect chain for layered CompositionPlayer preview and final Transformer export. The shared composition settings are also applied to both paths when replacement-background layouts are active.

## Per-item GPU effect chain

- Chroma key and supported colour effects are compiled into Media3 GlEffects.
- Up to eight rectangle, ellipse or polygon masks are rasterized into one cached alpha plane when the input resolution is configured, uploaded as a single-channel OpenGL ES texture, and sampled by a GPU fragment shader for each frame.
- Multiple masks intersect by multiplying alpha; inverted and feathered mask geometry is retained.
- Crop, scale/rotation, blur and opacity are appended after the mask stage.

The GPU mask stage is a per-stream effect. It does not composite independent video layers and does not implement non-normal inter-layer blending.

## Composition sequence contract

- Sequence/input 0 is the keyed foreground video and is registered first.
- Sequence/input 1 is the replacement background and is registered second.
- Both currently fill the output frame unless the selected replacement-background layout changes placement.
- The foreground chroma-key effect creates transparent pixels; the compositor's source-over alpha composition reveals the background behind them.
- The same StudioMedia3VideoCompositorSettings instance is shared by preview and export so placement, scale, rotation and opacity do not drift.

Media3 sequence registration order determines source-over ordering. zIndex in the pure plan does not reorder Media3 sequences by itself.

## Remaining limits

The standard Media3 compositor remains source-over. Custom Multiply, Screen, Add, Overlay, Darken and Lighten blending between independent video layers still requires a multi-input GPU compositor implementation. The CPU pixel compositor remains the deterministic reference for those modes. Translation and fully independent multi-layer track layout also remain custom-compositor work.

The integration is local and offline. Physical GPU preview/export testing remains required.
