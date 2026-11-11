# NeaRer secure mesh relay protocol — design gate

Status: design only. This document does not enable relay or claim that store-and-forward works.

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

Foundations now present in source:
- A stable P-256 recipient ECDH keypair persisted as LocalDataCipher ciphertext; unreadable existing key material fails closed rather than being silently replaced.
- A signed binding that associates the recipient ECDH public key with the already pinned device signing identity.
- Recipient-only ECDH + HKDF-SHA256 + AES-256-GCM packet encryption, separate from the live direct-chat session keys.
- An origin-signed opaque packet envelope with immutable signed header/ciphertext, expiry, and bounded hop count.
- Signed route advertisement verification and a route table that derives its next hop from the authenticated direct advertiser, rather than trusting a remote next-hop claim.
- A durable encrypted-at-rest Dead Drop queue foundation with bounded storage, retry backoff, forwarding leases, and distinct hop-accepted vs final-delivery states.
- Destination-signed final receipt verification before a stored packet can be deleted.
- A bounded, signed hop-acceptance acknowledgement that binds packet ID, accepting next-hop ID, previous-hop ID and timestamp; Dead Drop now requires this proof from the expected next hop before recording hop acceptance.

Still not integrated:
- The v3 Nearby handshake now exchanges a fresh signed recipient-identity binding after the signing-key proof, verifies that it binds the exact recipient key from the handshake, pins the signing/encryption keys, and rejects stale/replayed binding sequence numbers.
- Authenticated Nearby peers now exchange signed route advertisements containing the destination encryption-key material, verify the advertiser against the pinned signing key and destination-key fingerprint, derive the next hop from the direct authenticated sender, and gossip materially new routes. Unchanged route refreshes do not trigger gossip loops. Route expiry, per-peer rate limits, withdrawal propagation and route persistence still need work.
- Dead Drop enqueue/claim/verified-hop-ack/retry/receipt methods are not yet connected to mesh packet send/receive callbacks or a background worker. The signed hop-ack codec and verification gate remain protocol primitives only.
- Receipt creation is a protocol primitive; no end-to-end receipt exchange exists.
- There is no A → B → C → D real-device test, no app-restart delivery test, and no adversarial field test.
- Storage quotas and retry policies need product-level review before field use.

The draft PR must pass the exact-head CI and code review before integration. Relay remains disabled.
