# NeaRer secure mesh relay protocol — design gate

Status: engineering prototype in a draft branch. Relay participation remains disabled; this is not a release or a claim of physical multi-hop validation.

## Why direct-chat E2E keys are not enough

The current direct-chat ECDH session keys are derived separately for each live connection. A key shared by A and B cannot decrypt a packet later delivered by C to D. Multi-hop delivery therefore needs recipient-addressed encryption that A can create for D without giving B or C the decryption key.

The relay must never decrypt, rewrite, or re-encrypt the message body. It should forward an opaque, origin-authenticated envelope that only the destination can open.

## Identity keys required

Keep the existing device-bound P-256 signing identity separate from encryption keys.

- Signing identity: proves possession of the device signing key and signs route advertisements and immutable message headers.
- Recipient encryption identity: a distinct P-256 ECDH keypair, with a stable public key associated with the signing identity.
- Private ECDH material must be encrypted at rest using the app's Android Keystore-backed LocalDataCipher. If saved ciphertext cannot be unlocked, preserve it and fail closed; never silently generate a replacement key over unreadable state.
- A signed identity announcement must bind the recipient ECDH public key to the signing identity. Peers pin both together. A changed signing key or encryption key must be treated as a key-change event, not silently accepted.
- First-contact TOFU still does not prove a person's real-world identity. The UI must make this limitation clear.

## Recipient-addressed message envelope

Version 1 immutable envelope fields:

| Field | Purpose |
|---|---|
| version | Protocol version |
| packetId | Random unique ID used for deduplication |
| originId | Stable sender NeaRer ID |
| destinationId | Stable recipient NeaRer ID |
| createdAt / expiresAt | Bounded message lifetime |
| hopLimit | Maximum allowed relay count (1–4) |
| ephemeralPublicKey | Fresh sender-side P-256 ECDH public key |
| nonce | Fresh 96-bit AES-GCM nonce |
| ciphertext | Recipient-only encrypted message payload |
| originSignature | Signature over a canonical header and ciphertext hash |

A fresh ephemeral ECDH key and the recipient's pinned public encryption key derive a one-message AES-256-GCM key using HKDF-SHA256. Associated data binds protocol version, packet ID, origin ID, destination ID, timestamps and hop limit. Only the destination's private encryption key can open the ciphertext. Relays see routing metadata and ciphertext length/timing, but not message contents.

The origin signature protects the immutable envelope. Relays may add hop-by-hop forwarding receipts, but must not modify the signed envelope. The receiver validates the origin signature against a previously trusted signing key (or presents an explicit first-contact trust decision); a public key carried in an envelope is not automatically trusted merely because its signature verifies.

## Route advertisements

Route state is learned only from authenticated direct neighbors. An advertisement must carry:
- destination ID and destination encryption-key fingerprint;
- next-hop neighbor ID;
- hop count and route sequence number;
- issued-at and expiry timestamps;
- advertiser identity and signature.

A route sequence is scoped to its advertiser; sequence values from different neighbors are not directly comparable. The local route table compares sequence values only for the same authenticated next hop, and uses the signed advertisement freshness timestamp (then path length) when comparing a different advertiser. A received route must never point back to the local device or to the advertising neighbor itself.

Each node signs only its own next-hop assertion. A node must not treat an unsigned claim from another peer as a verified route. Route freshness, sequence monotonicity, loop prevention, and expiry are mandatory. Route updates are bounded and rate-limited to prevent gossip storms.

## Dead Drop / store-and-forward

A relay stores only the opaque, signed recipient envelope. It must not store plaintext or the sender's direct-chat session key.

Required rules:
- encrypted-at-rest storage using LocalDataCipher;
- bounded total bytes, per-origin quota, and per-packet size;
- expiry enforcement on restore and before every forwarding attempt;
- deduplication by packet ID;
- durable state transitions: received, queued, forwarding, awaiting-hop-ack, delivered, expired, rejected;
- retry with bounded exponential backoff and a maximum attempt count;
- do not delete the only durable copy until a valid destination delivery receipt is received;
- a receipt must be signed by the destination and bound to packet ID and origin/destination IDs;
- restart recovery must restore queued/awaiting state without losing or duplicating delivery.

A hop acknowledgement means only that the next hop durably accepted the packet. It is not proof that the final destination received it. The final delivery receipt is distinct.

## Relay forwarding policy

- Relay is off by default and remains disabled until the entire protocol is implemented and physically tested.
- Participation must be disclosed during onboarding and require explicit user consent plus Android permissions. No background bypass or secret enrollment.
- A relay forwards only to an authenticated next-hop connection and only when the route is fresh.
- Enforce hopsTaken < hopLimit, packet expiry, and a persistent packet-ID cache before forwarding.
- Never forward a packet back to the previous hop. Do not expose message plaintext to relay devices.
- Battery, data (if any), storage, queue size and relay pause controls must be visible to the user.

## Implementation sequence

1. Implement persistent recipient ECDH identity and signed binding to the existing signing identity.
2. Add pure, testable recipient-envelope canonicalization, signature verification and AES-GCM encryption/decryption.
3. Add signed route advertisements and route-table freshness/loop policies.
4. Add encrypted Dead Drop storage, quota, expiry, retry and crash recovery.
5. Connect forwarding and signed hop/final receipts while keeping the feature flag hard-disabled.
6. Test two phones first, then A → B → C, then A → B → C → D, including disconnects, app restarts, duplicate packets, tampered ciphertext/signatures, changed keys and expired packets.
7. Only after all tests pass, request explicit approval to enable relay for a controlled field test.

## Implementation status on this draft branch

### Implemented in source (not yet field-validated)

- A stable P-256 recipient ECDH keypair persisted as LocalDataCipher ciphertext; unreadable existing key material fails closed rather than silently generating a replacement.
- A signed binding associates the recipient ECDH public key with the already pinned device signing identity. The Nearby handshake exchanges and verifies this binding and rejects stale/replayed binding sequences.
- Recipient-only ECDH + HKDF-SHA256 + AES-256-GCM packet encryption, separate from live direct-chat session keys.
- An origin-signed opaque packet envelope with immutable signed header/ciphertext, expiry, bounded hop count and a 32 KiB Nearby BYTES wire cap.
- Signed path-vector route advertisements. The receiver derives its next hop from the authenticated direct advertiser, checks destination-key fingerprints, rejects loops, bounds route state and rate-limits advertisements. Signed withdrawals remove routes only when issued by their current next hop.
- Encrypted-at-rest Dead Drop storage with queue and per-origin quotas, packet-ID collision checks, previous-hop tracking, durable forwarding leases, retry backoff, and persisted once-per-hop counter increments.
- Signed hop acknowledgements are accepted only from the expected authenticated next hop and only after the receiver has durably stored the packet.
- Destination-signed final receipts and a separate encrypted-at-rest receipt outbox. Receipt relays persist before acknowledging and retain receipts until the next authenticated hop acknowledges them.
- The transport has a mesh-send API and the normal Chats UI now exposes a trusted-recipient selector, route/hop status, recipient-specific message history, and an explicit unreachable state. File sharing remains limited to a direct connection.
- Route availability is exposed to the UI only when the destination's signing and recipient-encryption keys are already pinned on this phone. This is a deliberate fail-closed trust rule: current NeaRer IDs are random identifiers, not cryptographic fingerprints. A relayed public key alone is not sufficient proof that it belongs to the claimed ID. Remote first-contact recipients therefore cannot yet be safely added to the send list.
- A duplicate final receipt arriving through a new authenticated neighbor refreshes its stored ingress path so route recovery does not keep forwarding it toward a stale previous hop.
- JVM tests cover packet integrity, recipient encryption, route signatures/path vectors/withdrawals, hop ACKs, receipts and the Nearby payload cap. Android instrumentation covers encrypted queue persistence, hop-counter recovery, quotas and receipt-outbox persistence.

### Still incomplete / release blockers

- The random legacy NeaRer ID is not cryptographically bound to the signing key. A secure, backwards-compatible identity migration or equivalent independently verifiable identity certificate is required before enabling first-contact multi-hop delivery. Do not trust a destination key supplied by an intermediate relay.
- The mutable hop counter is not a globally verifiable chained forwarding trace. A malicious authenticated relay can lie about earlier hops before downstream nodes observe the packet. A chained, independently verifiable per-hop trace is still required before hostile-network release.
- Receipt verification and deletion at intermediate relays is incomplete when that relay has not directly pinned the destination signing key; such copies may remain until expiry.
- Queue retry currently runs only while the Nearby transport instance is alive. WorkManager/foreground-service scheduling for process death, Doze and reboot recovery is not yet implemented.
- Withdrawal tombstones are in-memory only and do not survive process restart. Route cache and withdrawal recovery need further adversarial testing.
- Queue limits, expiry, retry policy and battery/storage controls need product-level review.
- No physical A → B → C → D test, forced process-death/reboot test, multi-manufacturer test, or adversarial field test has passed. Android 35 instrumentation recently crashed before its tests completed; the latest workflow is being rerun with clean emulator snapshots and retained test reports.
- Relay remains disabled. This draft PR is not release-ready and must not be merged or advertised as complete.

The draft PR must pass exact-head CI and code review before integration. The user must explicitly approve any merge or controlled relay field test.
