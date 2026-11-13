# Mesh forwarding proof chain — Batch Two protocol work

## Purpose

The existing hop acknowledgement proves that one authenticated next hop durably accepted a packet from its immediate predecessor. It does not, by itself, give a later node a verifiable ordered history of earlier acceptances.

Batch Two introduces a bounded proof-chain primitive. Each record is signed by the accepting peer and commits to the SHA-256 hash of the preceding record. The current primitive binds every record to one packet ID, origin, and the 32-byte SHA-256 digest of the immutable origin-signed packet (including its origin signature), enforces sequential hop indexes, previous-hop continuity, unique peers, monotonic acceptance times, and a maximum of five acceptance records (four relay forwards plus the final destination).

## Network-scale semantics

The five-record limit is a **v1 packet-path implementation bound**, not a limit on the number of phones that may participate in a NeaRer mesh. A mesh may contain many more peers than any single packet's route. The current MeshRelayPacketV1 still restricts one packet to four relay-forward operations; do not describe this as support for 100-hop delivery. A future negotiated envelope must define a per-packet TTL, bounded proof encoding, loop prevention, route freshness, and congestion/power controls before longer paths are enabled.

## Trust model

A proof contains no self-asserted public key. Verification requires the caller to resolve each accepting peer's signing key from an independently trusted identity store. Missing keys fail closed. A valid chain therefore proves that each trusted signer signed its specific acceptance statement and that the statements form one ordered hash-linked chain.

## Important limitation

The proof record now includes and signs a 32-byte SHA-256 digest of the canonical immutable origin-signed packet bytes, including the origin signature. The transport caller must compute `MeshRelayPacketV1.immutablePacketDigest()` from the received packet and supply it to chain verification. A matching packet ID without a matching digest is rejected.

This is a protocol primitive and regression-tested model, **not yet connected to NearbyConnectionsTransport, packet forwarding, or durable Dead Drop records**. It does not prove physical radio transmission, prevent a trusted signer from lying, or prove that a relay actually forwarded the payload beyond its signed durable-acceptance claim. The transport integration must persist the chain atomically with packet state, append only after durable acceptance, transmit it within the Nearby payload budget, and verify it before accepting downstream state.

Relay remains disabled until that integration, key-distribution rules, recovery semantics, and physical-device validation are complete.
