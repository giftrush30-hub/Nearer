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

## Important integration boundary

The adapter is not yet wired into the Film Studio preview and export Composition.Builder calls. The next integration change must use the same input ordering and layout settings in both paths. A caller must not assume z-index ordering changes Media3 sequence registration order automatically.

This uses Media3 1.11.1 VideoCompositorSettings, which Android documents as beta and subject to API changes. The adapter currently supports the compositor's normal source-over alpha composition only. Multiply, screen, overlay, add, custom masks, and particle layers require a custom compositor/shader path and are not falsely reported as implemented here.

All planning is deterministic and local. No AI, network, cloud, or backend services are used.
