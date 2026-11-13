# Mesh forwarding-proof transport integration plan

Status: proof-sidecar framing and atomic encrypted Dead Drop acceptance have been implemented on the Batch Two branch. Relay remains hard-disabled pending CI, security review, recovery validation and physical multi-device testing.

## Current transport behavior observed in this branch

1. The origin creates an origin-signed `mesh_packet_v1` envelope.
2. A receiving peer validates the embedded origin signature and packet expiry.
3. The receiver stores the opaque packet in encrypted Dead Drop storage.
4. The receiver sends a separate signed `mesh_hop_ack_v1` to its previous hop after storage succeeds.
5. A relay increments the mutable `hopsTaken` field once in persistent storage before sending the packet to its selected next hop.
6. The next hop repeats the durable-store and ACK sequence.

The separate hop ACK is useful for reliable retry, but it is not a transferable end-to-end chain of all relay acceptances. The `MeshForwardingProofChain` class currently added in Batch Two is not part of this packet format or storage lifecycle.

## Required next protocol version

**Critical binding requirement:** Batch Two signs a 32-byte SHA-256 digest of the canonical immutable origin-signed packet bytes, including the origin signature. The transport caller must calculate `MeshRelayPacketV1.immutablePacketDigest()` and pass it into chain verification. A packet-ID match with a different digest must fail closed.

Do not silently reinterpret `mesh_packet_v1` as a newer version. A future envelope must negotiate an explicit protocol version and carry:

- The exact immutable origin-signed packet bytes or their canonical digest, cryptographically bound by every proof record.
- A bounded ordered proof sidecar, with no public keys accepted from the sidecar itself.
- An explicit packet TTL / maximum forwarding count negotiated by both peers.
- Strict total storage quotas, proof-sidecar length limits, chunk counts and reassembly timeouts.
- A total frame size below Nearby Connections' 32 KiB BYTES limit, including JSON/base64 overhead.

### Scale target and non-goals

The product target is a mesh population of 100+ participating phones, not 100 simultaneous direct links from one handset. Discovery and routing must be neighborhood-scoped and avoid global broadcast storms.

The current v1 limit of four relay-forward operations and five acceptance records is not a future product limit. The next protocol version must use a negotiated TTL rather than inherit those constants. If a route needs more proof bytes than one Nearby BYTES frame can carry, transfer the proof sidecar as independently framed, authenticated chunks with strict count/size/time limits and a final digest. Do not enlarge a single frame past the Nearby limit or allocate memory based on an untrusted declared chain length.

A 100-peer mesh and a 100-hop single-message path are different engineering requirements. The first is a network participation target; the second is a much harder route reliability and proof-scaling target. Neither is claimed as implemented until multi-device tests demonstrate it. The path design must support configurable longer routes without a hardcoded five-record codec, while keeping a conservative default TTL to protect battery, latency and storage.

## Required relay transaction

For each accepted packet:

1. Authenticate the immediate Nearby peer using the existing secure handshake.
2. Validate the origin signature and packet lifetime.
3. Validate every existing proof using keys resolved from the local trusted-identity store. If a required signer key is unavailable, do not claim a fully verified chain.
4. Persist the packet and current proof sidecar together in one encrypted durable record.
5. Only after the durable commit, sign a new acceptance proof whose previous-hop ID is the authenticated sender and whose previous-proof hash is the hash of the last chain element (or genesis hash).
6. Persist the appended proof before acknowledging the previous hop.
7. Forward packet plus proof sidecar to the selected next hop. Retries must reuse the exact persisted proof; they must not append a second proof for the same local acceptance.
8. At the destination, validate the complete proof sidecar against independently trusted signer keys and the expected origin/destination before marking the chain as verified.

## Important semantics

- A signed acceptance proof attests to the signer's statement that it durably accepted the packet from the named previous hop. It does not prove physical radio transmission or that the signer behaved honestly.
- A malicious trusted relay can sign a false acceptance statement. The protocol must not describe signatures as proof of honest forwarding.
- Proof-chain timestamps are diagnostic and replay-hardening inputs, not a trusted global clock. Packet expiry remains authoritative.
- In v1, proof records include every receiving peer, including the final destination. `hopsTaken` counts relay forwarding operations, not receiving peers: with `hopLimit = 4`, four intermediate relays may each forward once and a fifth receiving peer is the destination. The v1 codec therefore allows at most five acceptance records (`hopLimit + 1`). Do not carry this v1 constant into the scalable protocol.
- Duplicate packet arrival through another neighbor must not replace a better verified chain with an unverified or rollback chain.
- A destination receipt must remain a distinct end-to-end delivery statement; a hop proof is never a Delivered signal.

## Release gates

- Versioned wire-format codec and golden vectors for v1 and the scalable version.
- Atomic encrypted Dead Drop persistence of packet plus proof sidecar.
- Idempotent append across retries, app restarts and duplicate arrivals.
- Trust-store lookup and key-rotation behavior.
- Strict chunk framing/reassembly and total proof-sidecar budget tests.
- Tamper, missing-key, replay, duplicate-hop, rollback and process-death tests.
- Mesh population simulations at 10, 50 and 100+ identities with bounded discovery and route tables.
- Physical A → B → C → D tests with forced disconnects and app restarts, followed by longer multi-hop trials.

Relay remains disabled until these gates and the separately tracked first-contact identity and recovery gates are satisfied.
