# Offline 3D scene preview

NeaRer Studio now has a Filament-based Android scene preview component for local GLB assets.

## Runtime

- Google Filament 1.75.1 Android utilities, including the glTF loader and ModelViewer.
- glTF 2.0 binary (.glb) import from the Android document picker.
- Touch orbit and zoom controls from Filament's ModelViewer.
- Embedded animation playback and selection.
- Unit-cube normalization for imported models.
- All model bytes are read from the selected local document; no network asset fetch is used.

## Validation and safety

A local GLB 2.0 container validator checks the magic, version, declared file length, chunk boundaries, JSON-first ordering and glTF asset version before native loading. Invalid containers are rejected before reaching the native renderer.

## Limitations

This first runtime supports GLB binary assets only. External-resource .gltf folders, custom material compilation, scene compositing into the final 2D movie, audio-driven lip sync and full character rig controls are not yet integrated. The preview component must still be connected to the main Studio workspace navigation and tested on physical Android GPUs. Filament's renderer is real-time, but supported asset features and performance vary by device and driver.

The implementation is offline-only and uses no AI, cloud rendering or backend.
