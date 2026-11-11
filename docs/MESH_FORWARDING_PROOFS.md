# Mesh forwarding proof chain — Batch Two protocol work

## Purpose

The existing hop acknowledgement proves that one authenticated next hop durably accepted a packet from its immediate predecessor. It does not, by itself, give a later node a verifiable ordered history of earlier acceptances.

Batch Two introduces a bounded proof-chain primitive. Each record is signed by the accepting peer and commits to the SHA-256 hash of the preceding record. The chain binds every record to one packet ID and origin, enforces sequential hop indexes, previous-hop continuity, unique peers, monotonic acceptance times, and a maximum of four acceptances.

## Trust model

A proof contains no self-asserted public key. Verification requires the caller to resolve each accepting peer's signing key from an independently trusted identity store. Missing keys fail closed. A valid chain therefore proves that each trusted signer signed its specific acceptance statement and that the statements form one ordered hash-linked chain.

## Important limitation

This is a protocol primitive and regression-tested model, **not yet connected to NearbyConnectionsTransport, packet forwarding, or durable Dead Drop records**. It does not prove physical radio transmission, prevent a trusted signer from lying, or prove that a relay actually forwarded the payload beyond its signed durable-acceptance claim. The transport integration must persist the chain atomically with packet state, append only after durable acceptance, transmit it within the Nearby payload budget, and verify it before accepting downstream state.

Relay remains disabled until that integration, key-distribution rules, recovery semantics, and physical-device validation are complete.
