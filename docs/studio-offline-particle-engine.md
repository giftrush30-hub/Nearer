# Offline deterministic particle engine

The particle evaluator produces seekable, reproducible particle instances from local timeline time and a fixed seed. It does not require network access, AI, a cloud service, or simulation of previous frames.

## Built-in effect families

- Fire
- Smoke
- Explosion
- Sparks
- Lightning

Each emitter has a bounded spawn rate, lifetime, particle count, origin, velocity, spread, gravity and size range. Frame generation is deterministic for a given seed and timestamp, so scrubbing and offline export can request frames in any order.

## Safety limits

- 16 emitters per frame plan
- 10,000 particles per emitter
- 50,000 particle instances per output frame
- Finite-value and range validation before generation
- Invalid plans return errors and no partial particle frame

## Integration boundary

This is the procedural particle simulation and draw-instance layer. A GPU/CPU particle rasterizer still needs to consume these instances and composite their pixels into preview/export. The engine does not claim that particle effects are already visible in the Film Studio UI or final MP4.
