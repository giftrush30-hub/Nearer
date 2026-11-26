# CPU pixel compositor

A deterministic CPU reference compositor now handles local raster layers in z-order. It supports normal source-over, add, multiply, screen, overlay, darken and lighten blend modes, per-layer opacity, per-pixel alpha masks and integer placement offsets.

The compositor accepts the existing StudioAlphaMask produced by the rectangle, ellipse and polygon mask rasterizer. This gives masks and blend modes a testable pixel-level implementation for small preview assets, generated shapes and particle textures.

Safety limits match the visual graph's 64-layer cap and 16-megapixel mask/raster budget. Invalid dimensions, pixel counts, duplicate IDs, opacity values or mask sizes reject the composition without returning partial pixels.

This is a CPU reference path, not a claim of real-time full-resolution video rendering. Full movie export should use a GPU compositor or a tiled/native path to avoid copying large frame buffers on the UI thread.
