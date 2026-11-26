# Multi-layer video compositor layout

This module adds a graphics-independent layout planner and a Media3 VideoCompositorSettings adapter for composition-sequence layout.

## Supported layout operations

- Normalized rectangle placement in Media3 normalized device coordinates (-1..1).
- Independent X/Y scale derived from rectangle bounds.
- Rotation normalized into 0..360 degrees.
- Alpha/opacity from 0..1.
- Deterministic z-index ordering in the pure plan.
- Bounded to 32 visual inputs, with duplicate-ID and invalid geometry rejection.

The Media3 adapter maps each sequence input ID to StaticOverlaySettings, including frame anchor, background anchor, scale, rotation and alpha. Unknown input IDs fall back to the default full-frame placement.

## GPU mask effect

Per-layer rectangle, ellipse and polygon masks now have a separate Media3 GlEffect. It creates one combined alpha texture per input-size configuration and samples that texture in the GPU shader for every frame. Up to eight masks intersect in one pass, with inversion and feathering supported. This mask pass is wired through StudioVisualRenderBridge for the same per-item preview/export effect chain.

## Important integration boundary

The adapter is not a general custom multi-input compositor. It still uses source-over alpha composition for independent video sequences. The GPU mask effect operates on one sequence at a time; it does not implement Multiply, Screen, Overlay, Add, Darken or Lighten between separate video layers. Those blend modes remain available in the CPU reference compositor and require a custom multi-input GPU compositor path.

This uses Media3 1.11.1 VideoCompositorSettings, which Android documents as beta and subject to API changes. A caller must not assume z-index ordering changes Media3 sequence registration order automatically.

All planning and rendering are local. No AI, network, cloud, or backend services are used.
