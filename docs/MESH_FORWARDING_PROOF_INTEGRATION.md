# Mesh forwarding-proof transport integration plan

Status: proof-sidecar framing and atomic encrypted Dead Drop acceptance have been implemented on the Batch Two branch. Relay remains hard-disabled pending CI, security review, recovery validation and physical multi-device testing.

## Current transport behavior observed in this branch

1. The origin creates an origin-signed immutable `MeshRelayPacketV1` and queues it with an empty proof prefix.
2. The sender frames packet bytes and proof bytes in `MeshPacketEnvelopeV2`, without Base64 expansion.
3. A receiving peer authenticates the Nearby endpoint and validates packet origin signature/expiry. On the first hop, it additionally requires the packet's embedded origin key to equal the pinned signing key of the authenticated direct sender; later hops require a valid trusted proof prefix.
4. The receiver checks that incoming proof count equals the packet's persisted hop counter, appends its own signed acceptance record, verifies the appended record using its local public key, then commits encrypted packet and proof sidecar together in one encrypted Dead Drop queue write.
5. Only after that durable commit does it send the separate signed `mesh_hop_ack_v1` to the previous hop.
6. A later forward attempt reads the exact persisted proof chain, increments the mutable `hopsTaken` field once in persistent storage, and sends packet plus unchanged proof sidecar to the selected next hop.
7. Duplicate arrivals are idempotent only when immutable packet content and the stored verified chain agree; a shorter chain cannot overwrite a stronger stored chain.

The hop ACK remains a reliable per-link retry signal; the proof sidecar carries the ordered acceptance claims across hops. Neither proves physical radio transmission or honest forwarding.

## Required next protocol version

**Critical binding requirement:** Batch Two signs a 32-byte SHA-256 digest of the canonical immutable origin-signed packet bytes, including the origin signature. The transport caller must calculate `MeshRelayPacketV1.immutablePacketDigest()` and pass it into chain verification. A packet-ID match with a different digest must fail closed.

Do not silently reinterpret `mesh_packet_v1` as a newer version. A future envelope must negotiate an explicit protocol version and carry:

- The exact immutable origin-signed packet bytes or their canonical digest, cryptographically bound by every proof record.
- A bounded ordered proof sidecar, with no public keys accepted from the sidecar itself.
- An explicit packet TTL / maximum forwarding count negotiated by both peers.
- Strict total storage quotas, proof-sidecar length limits, chunk counts and reassembly timeouts.
- A total binary frame size below Nearby Connections' 32 KiB BYTES limit, including packet and proof lengths; the current v1 frame reserves 5 KiB for the maximum proof chain.

### Scale target and non-goals

The product target is a mesh population of 100+ participating phones, not 100 simultaneous direct links from one handset. Discovery and routing must be neighborhood-scoped and avoid global broadcast storms.

The current v1 limit of four relay-forward operations and five acceptance records is not a future product limit. The next protocol version must use a negotiated TTL rather than inherit those constants. If a route needs more proof bytes than one Nearby BYTES frame can carry, transfer the proof sidecar as independently framed, authenticated chunks with strict count/size/time limits and a final digest. Do not enlarge a single frame past the Nearby limit or allocate memory based on an untrusted declared chain length.

A 100-peer mesh and a 100-hop single-message path are different engineering requirements. The first is a network participation target; the second is a much harder route reliability and proof-scaling target. Neither is claimed as implemented until multi-device tests demonstrate it. The path design must support configurable longer routes without a hardcoded five-record codec, while keeping a conservative default TTL to protect battery, latency and storage.

## Required relay transaction

For each accepted packet:

1. Authenticate the immediate Nearby peer using the existing secure handshake.
2. Validate the origin signature and packet lifetime.
3. Validate every existing proof using keys resolved from the local trusted-identity store. If a required signer key is unavailable, do not claim a fully verified chain.
4. Decode the incoming proof prefix and verify each record using keys from the local trusted-identity store.
5. Append the local signed acceptance record, then verify the resulting prefix with the local public key and trusted remote keys.
6. Persist the encrypted packet and appended proof sidecar together in one encrypted Dead Drop queue write.
7. Only after that durable commit, acknowledge the previous hop. Forward packet plus the exact persisted proof sidecar to the selected next hop; retries must not append a second local acceptance.
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
- CI and instrumentation coverage for atomic encrypted Dead Drop persistence of packet plus proof sidecar.
- Idempotent append across retries, app restarts and duplicate arrivals, including interrupted process writes.
- Trust-store lookup and key-rotation behavior.
- Strict chunk framing/reassembly and total proof-sidecar budget tests.
- Tamper, missing-key, replay, duplicate-hop, rollback and process-death tests.
- Mesh population simulations at 10, 50 and 100+ identities with bounded discovery and route tables.
- Physical A → B → C → D tests with forced disconnects and app restarts, followed by longer multi-hop trials.

Relay remains disabled until these gates and the separately tracked first-contact identity and recovery gates are satisfied.
