# NeaRer

NeaRer is an offline-first Android nearby chat and file-transfer app.

## HyperTransfer

- Nearby STREAM transfers use durable resume checkpoints, authenticated peer binding, SHA-256 verification and verified completion receipts.
- Adaptive LITE / FAST / TURBO policy selects bounded receive buffers from local memory, storage, thermal and peer capability signals. Unknown or legacy peers are capped conservatively.
- Multi-file selections are queued as separate verified transfers. The queue advances only after the recipient's verified completion receipt and a durable queue checkpoint.
- Selected folders are archived with streaming ZIP I/O, preserving nested paths without loading the archive into memory. The sender rejects duplicate/case-conflicting normalized paths, overlong paths and excessive entry counts; the receiver streams extraction into a new private browsable folder and rejects traversal, collisions, unsafe names and low-storage writes.
- Up to 60 outgoing HyperTransfer measurements are retained per authenticated phone pair. The app reports median end-to-end throughput and time-to-first-byte, measured from preparation through recipient verification. The chat history card accepts a manually measured Xender MiB/s reference for a same-file comparison; NeaRer does not claim a speed advantage without that measurement.

## Bundle manifest status

The versioned bundle manifest, codec, fragmentation and session validation components are implemented and tested as foundations. The live multi-file queue still sends separate per-file payloads; manifest-driven bundle negotiation and end-to-end bundle receipts remain future integration work. Folder sharing currently uses a single streamed ZIP archive.

## Validation

Automated JVM tests and Android APK builds run in GitHub Actions. Physical-device tests, two-phone interoperability, OEM background restrictions and comparative Xender benchmarks must be performed on real phones; source/build checks do not prove radio performance or background survival.
