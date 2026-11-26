# Media3 visual transform compiler

The offline visual graph now has a renderer adapter for the transform subset that Media3 1.11.1 can implement with built-in GPU effects.

## Implemented effect mapping

| Visual transform | Media3 effect | Behaviour |
| --- | --- | --- |
| Normalized crop | `Crop` | Converts top-left normalized bounds to normalized device coordinates |
| X/Y scale | `ScaleAndRotateTransformation` | Applies independent scale factors |
| Rotation | `ScaleAndRotateTransformation` | Normalizes negative and multi-turn angles into 0–360 degrees |
| Blur | `GaussianBlur` | Applies a bounded blur radius |
| Opacity | `AlphaScale` | Scales source alpha from 0 to 1 |

The bridge combines the effect-node compiler and transform compiler into one ordered chain for a single video item. It returns no partial chain if any requested operation is unsupported.

The video timeline's shared `videoEffects(clipIndex)` path now creates a draw command for the selected clip and invokes this bridge. That function is used when configuring both the live ExoPlayer preview and each Transformer export item, so supported per-clip visual nodes pass through the same validation and effect ordering in both paths.

## Explicitly unsupported

- X/Y translation: requires a custom matrix transform/compositor pass.
- Feathered masks and raster mask application: require a custom mask shader/compositor pass.
- Inter-layer blend modes and multi-source compositing: owned by the composition compositor, not this per-item effect chain.
- Particle simulation and procedural shape rendering: not implemented by this adapter.

These features are represented in the graphics-independent visual plan but must not be advertised as rendered until their renderer exists.

## Safety and validation

- Non-finite values fail immediately.
- Scale, rotation, opacity, crop, blur and mask-feather inputs are range-checked.
- Unsupported translation and feathering are errors, not silent no-ops.
- The compiler is local and deterministic; it makes no network or AI calls.

## Verification boundary

JVM tests verify the pure transform plan, normalized crop coordinates, scale/rotation values, opacity/blur decisions, identity no-op and explicit unsupported-operation failures without invoking Android graphics stubs. The Media3 adapter then materializes the plan as Crop, ScaleAndRotateTransformation, GaussianBlur and AlphaScale effects; CI verifies that adapter compiles and packages. Actual GPU output, alpha compositing quality, device-specific performance and final-export appearance still require Android instrumentation or physical-device validation.
