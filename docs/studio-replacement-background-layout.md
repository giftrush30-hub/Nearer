# Replacement background layout controls

The green-screen background sequence can now be laid out in two modes:

- Full frame: the replacement fills the complete frame behind the keyed subject.
- Picture-in-picture: the replacement appears in a lower-right inset behind the keyed subject.

The foreground sequence remains input 0 and the replacement remains input 1. The foreground is kept on top so transparent chroma-key pixels reveal the replacement image/video. The selected layout is saved with the local project and the same VideoCompositorSettings are used by the CompositionPlayer preview and Transformer export.

Picture-in-picture is specifically a replacement-background inset visible through transparent keyed regions. It is not yet an arbitrary independent third video track. General-purpose multiple overlay tracks and user-draggable transform handles remain future work.
