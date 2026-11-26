# Shared preview and export compositor integration

The Film Studio timeline now attaches the same StudioMedia3VideoCompositorSettings to both the layered CompositionPlayer preview and the final Transformer Composition when a replacement background is active.

## Sequence contract

- Sequence/input 0 is the keyed foreground video and is registered first.
- Sequence/input 1 is the replacement background and is registered second.
- Both currently fill the output frame.
- The foreground's chroma-key effect creates transparent pixels; the compositor's source-over alpha composition reveals the background behind them.
- The settings adapter is shared by preview and export so the two paths do not drift in placement, scale, rotation or opacity defaults.

Media3's sequence registration order determines source-over ordering. The zIndex values in the pure plan are metadata for deterministic planning; they do not reorder Media3 sequences by themselves.

## Current limits

This connects the full-frame replacement-background composition to the real Media3 compositor. It does not yet expose picture-in-picture, split-screen or independent per-layer layout controls in the editor UI. The adapter supports source-over alpha only; non-normal blend modes and custom masks still need the GPU compositor path. The CPU pixel compositor remains a correctness/reference implementation for raster layers.

The existing single-video preview fallback remains unchanged. This integration is local and offline.
