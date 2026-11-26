# GPU multi-layer blend foundation

## Purpose
This module defines a deterministic contract for a future OpenGL ES multi-input video compositor. It is not yet a working Media3 compositor. Media3 exposes VideoCompositor as the multi-input texture-combination interface, but the public MultipleInputVideoGraph.Factory accepts a VideoFrameProcessor.Factory and does not expose a custom compositor factory. A custom VideoGraph or lower-level composition path is therefore needed to inject a custom compositor safely.

## Implemented in this slice
- Stable shader mode codes for Normal, Add, Multiply, Screen, Overlay, Darken and Lighten.
- A bounded planner for up to eight simultaneous inputs.
- Deterministic z-order with input-ID tie-breaking.
- Preflight checks for duplicate IDs, opacity, dimensions, pixel budgets and non-negative timestamps.
- A 1 ms timestamp tolerance before inputs can be composited into the same output frame.
- An ES 1.00 GLSL blend pass implementing straight-alpha source-over with the selected blend function. Multi-layer composition must ping-pong between two output textures for each additional input and must never sample from the texture currently attached as the render target.

## Not yet integrated
- The shader is a pass primitive, not a registered Media3 VideoCompositor.
- The graph still needs a tested GL-thread owner, input-frame queue keyed by presentation timestamp, texture-producer release callbacks, bounded pending-frame queues, output texture delivery and failure-safe teardown.
- Timeline gaps need a transparent/black fill policy; late input handling needs deterministic drop/wait behavior.
- Preview and export must use the same graph/compositor implementation.
- Physical GPU tests must measure frame drops, memory, battery/thermal behavior and color correctness on low/mid-range Android phones.

## Offline constraints
The planner and shader are local-only. No AI, cloud rendering, network asset fetch, or backend dependency is introduced. The CPU reference compositor remains available for deterministic small-image tests, not as a full-resolution video fallback.
