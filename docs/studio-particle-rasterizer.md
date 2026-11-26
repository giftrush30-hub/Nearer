# Particle rasterization and raster-layer handoff

The deterministic particle simulation can now be rasterized into an RGBA frame. The resulting frame is represented as a StudioPixelLayer and can be composited with the existing offline pixel engine, including its masks, opacity and blend modes.

## Rendering behavior

- Particle positions are interpreted as normalized device coordinates (-1..1).
- Fire, smoke and explosion particles render as soft radial sprites.
- Sparks and lightning particles render as narrow rotated streaks.
- Per-particle color, opacity, size and rotation are preserved.
- Output is deterministic for a given particle frame and output size.

## Safety

- Reuses the pixel compositor's 16-megapixel limit.
- Enforces the simulation's 50,000-particle frame limit.
- Rejects non-finite or invalid particle render values.
- Limits total raster work to 100 million candidate pixels per frame.
- Invalid or over-budget frames return no partial raster output.

## Integration boundary

The rasterized frame can already be passed to StudioPixelCompositor as a raster layer. It is not yet connected to the Film Studio timeline UI or to a Media3 TextureOverlay / final video export. That GPU upload and timestamped export integration is a separate stage.
