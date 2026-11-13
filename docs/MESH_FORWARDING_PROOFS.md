# Mesh forwarding proof chain — Batch Two protocol work

## Purpose

The existing hop acknowledgement proves that one authenticated next hop durably accepted a packet from its immediate predecessor. It does not, by itself, give a later node a verifiable ordered history of earlier acceptances.

Batch Two introduces a bounded proof-chain primitive. Each record is signed by the accepting peer and commits to the SHA-256 hash of the preceding record. The current primitive binds every record to one packet ID, origin, and the 32-byte SHA-256 digest of the immutable origin-signed packet (including its origin signature), enforces sequential hop indexes, previous-hop continuity, unique peers, monotonic acceptance times, and a maximum of five acceptance records (four relay forwards plus the final destination).

## Network-scale semantics

There are two different scale dimensions:

- **Mesh population:** how many distinct phones can participate in the wider nearby mesh. NeaRer's product target is at least 100 participating phones, subject to radio range, discovery capacity, Android resource limits, routing freshness and congestion controls. This is not a 100-device simultaneous direct-connection promise.
- **Per-message path length:** how many forwarding steps one packet takes from its origin to its destination. The current `MeshRelayPacketV1` restricts one packet to four relay-forward operations and the current proof codec allows five acceptance records including the destination. These are temporary v1 implementation bounds, not product-wide network limits.

Do not claim that the current code supports 100-hop delivery. A future negotiated envelope must replace the fixed v1 hop bound with an explicit per-packet TTL and a separately versioned, scalable proof representation. The future representation must define bounded proof-sidecar storage and transport, chunk framing/reassembly under the 32 KiB Nearby BYTES frame ceiling, loop prevention, route freshness, replay handling, congestion and battery controls. It must reject peers that do not negotiate the same version and limits. A larger mesh population must not imply that every packet floods every participant.

## Trust model

A proof contains no self-asserted public key. Verification requires the caller to resolve each accepting peer's signing key from an independently trusted identity store. Missing keys fail closed. A valid chain therefore proves that each trusted signer signed its specific acceptance statement and that the statements form one ordered hash-linked chain.

## Important limitation

The proof record includes and signs a 32-byte SHA-256 digest of the canonical immutable origin-signed packet bytes, including the origin signature. The transport caller must compute `MeshRelayPacketV1.immutablePacketDigest()` from the received packet and supply it to chain verification. A matching packet ID without a matching digest is rejected.

This is a protocol primitive and regression-tested model, **not yet connected to NearbyConnectionsTransport, packet forwarding, or durable Dead Drop records**. It does not prove physical radio transmission, prevent a trusted signer from lying, or prove that a relay actually forwarded the payload beyond its signed durable-acceptance claim. The transport integration must persist the chain atomically with packet state, append only after durable acceptance, transmit it within the negotiated framing budget, and verify it before accepting downstream state.

Relay remains disabled until that integration, key-distribution rules, recovery semantics, and physical-device validation are complete.
