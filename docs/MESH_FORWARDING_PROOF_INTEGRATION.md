# Mesh forwarding-proof transport integration plan

Status: design and protocol primitive only. This document is not evidence that live forwarding currently carries proof chains.

## Current transport behavior observed in this branch

1. The origin creates an origin-signed `mesh_packet_v1` envelope.
2. A receiving peer validates the embedded origin signature and packet expiry.
3. The receiver stores the opaque packet in encrypted Dead Drop storage.
4. The receiver sends a separate signed `mesh_hop_ack_v1` to its previous hop after storage succeeds.
5. A relay increments the mutable `hopsTaken` field once in persistent storage before sending the packet to its selected next hop.
6. The next hop repeats the durable-store and ACK sequence.

The separate hop ACK is useful for reliable retry, but it is not a transferable end-to-end chain of all relay acceptances. The `MeshForwardingProofChain` class currently added in Batch Two is not part of this packet format or storage lifecycle.

## Required v2 envelope

**Critical binding requirement:** Batch Two now signs a 32-byte SHA-256 digest of the canonical immutable origin-signed packet bytes, including the origin signature. The transport caller must calculate `MeshRelayPacketV1.immutablePacketDigest()` and pass it into chain verification. A packet-ID match with a different digest must fail closed.

A future envelope must carry:

- The exact immutable origin-signed packet bytes (or a canonical immutable-packet digest), cryptographically bound by every proof record.
- A bounded ordered proof chain, with no public keys accepted from the chain itself.
- A protocol version and explicit chain length.
- A total encoded size that remains below Nearby Connections' 32 KiB BYTES limit, including JSON/base64 overhead.

Do not silently reinterpret `mesh_packet_v1` as v2. Version negotiation must fail closed when peers cannot agree on a supported format.

## Required relay transaction

For each accepted packet:

1. Authenticate the immediate Nearby peer using the existing secure handshake.
2. Validate the origin signature and packet lifetime.
3. Validate every existing proof using keys resolved from the local trusted-identity store. If a required signer key is unavailable, do not claim a fully verified chain.
4. Persist the packet and current chain together in one encrypted durable record.
5. Only after the durable commit, sign a new acceptance proof whose previous-hop ID is the authenticated sender and whose previous-proof hash is the hash of the last chain element (or genesis hash).
6. Persist the appended chain before acknowledging the previous hop.
7. Forward packet plus chain to the selected next hop. Retries must reuse the exact persisted proof chain; they must not append a second proof for the same local acceptance.
8. At the destination, validate the entire chain against independently pinned signer keys and the expected origin/destination before marking the chain as verified.

## Important semantics

- A signed acceptance proof attests to the signer's statement that it durably accepted the packet from the named previous hop. It does not prove physical radio transmission or that the signer behaved honestly.
- A malicious trusted relay can sign a false acceptance statement. The protocol must not describe signatures as proof of honest forwarding.
- Proof-chain timestamps are diagnostic and replay-hardening inputs, not a trusted global clock. Packet expiry remains authoritative.
- Proof records include every receiving peer, including the final destination. `hopsTaken` counts relay forwarding operations, not receiving peers: with `hopLimit = 4`, four intermediate relays may each forward once and a fifth receiving peer is the destination. The chain therefore allows at most five acceptance records (`hopLimit + 1`). This is a bound derived from the current v1 forwarding loop; preserve it in v2 golden vectors and tests.
- Duplicate packet arrival through another neighbor must not replace a better verified chain with an unverified or rollback chain.
- A destination receipt must remain a distinct end-to-end delivery statement; a hop proof is never a Delivered signal.

## Release gates

- Wire-format codec and golden vectors.
- Atomic encrypted Dead Drop persistence of packet plus chain.
- Idempotent append across retries, app restarts and duplicate arrivals.
- Trust-store lookup and key-rotation behavior.
- Nearby transport integration with strict 32 KiB budget tests.
- Tamper, missing-key, replay, duplicate-hop, rollback and process-death tests.
- Physical A → B → C → D test with forced disconnects and app restarts.

Relay remains disabled until these gates and the separately tracked first-contact identity and recovery gates are satisfied.
