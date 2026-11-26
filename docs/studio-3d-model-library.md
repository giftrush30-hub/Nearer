# Remembered local 3D model workflow

The 3D Scene Studio remembers the last selected GLB model on the device when Android's document provider grants persistable read access. On reopening the 3D scene screen, the saved URI is loaded through the same local GLB validator and Filament loader.

If a document provider does not grant persistent access, the model still loads for the current session and the app does not save a stale URI. Users can change or clear the remembered model from the 3D scene screen.

The workflow uses Android's Storage Access Framework and local app preferences. No remote asset library, network fetch, cloud storage or AI is involved.

The current viewer supports embedded GLB 2.0 assets and their embedded animations. It does not yet create a movie timeline clip from a rendered 3D scene, export a transparent character pass, or offer rig-level pose editing. Those require a timestamp-controlled offscreen Filament renderer and an explicit export bridge.
