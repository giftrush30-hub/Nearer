# CPU pixel compositor

A deterministic CPU reference compositor handles local raster layers in z-order. It supports normal source-over, add, multiply, screen, overlay, darken and lighten blend modes, per-layer opacity, per-pixel alpha masks and integer placement offsets.

The compositor accepts the existing StudioAlphaMask produced by the rectangle, ellipse and polygon mask rasterizer. This gives masks and blend modes a testable pixel-level implementation for small preview assets, generated shapes and particle textures.

The Film Studio video effect path now also has a separate GPU mask pass: it rasterizes the selected mask geometry once per input-size configuration, uploads one alpha texture, and samples that texture in a Media3 fragment shader for every frame. This GPU pass masks a single video layer; it does not perform inter-layer blend modes.

Safety limits match the visual graph's 64-layer cap, eight masks per GPU pass and 16-megapixel mask/raster budget. Invalid dimensions, pixel counts, duplicate IDs, opacity values or mask sizes reject the composition without returning partial pixels.

The CPU pixel compositor remains a correctness/reference implementation for inter-layer blend math. Full movie export still needs a custom multi-input GPU compositor for Multiply, Screen, Add, Overlay, Darken and Lighten; physical GPU performance must be validated on target phones.
