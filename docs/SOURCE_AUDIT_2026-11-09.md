# NeaRer Source Audit — Messaging-First Restructure
## Snapshot reviewed: main at 2026-11-09

**Scope:** Read-only review of the current README, Android Gradle configuration, manifest, `MainActivity.kt` and `NearbyConnectionsTransport.kt`. This is not a build, penetration test or real-device validation.

## 1. What is already present

- Native Android app using Kotlin, Jetpack Compose and Material 3.
- Nearby Connections discovery, connection requests, authentication-token display/approval and peer lifecycle callbacks.
- A Wi-Fi Direct discovery and custom socket fallback path.
- One-to-one text chat and local message persistence.
- Nearby Connections message acknowledgements, a bounded retry queue and deduplication IDs.
- File sending/receiving, progress callbacks, file metadata and SHA-256 file hashing.
- A queued multi-file sending flow for a connected Nearby Connections peer.
- Group/community packet handling and a multiplayer Ludo state machine/protocol in `MainActivity.kt`.
- Recently-seen peer records and editable nearby display name.
- Existing brand palette constants include midnight navy, blue and gold, though the app theme currently sets Material primary to mint.

## 2. Important gaps and discrepancies

### Product structure
- The current app source is still organised around a large `MainActivity` that owns transport lifecycle, chat, groups, games, file transfer, persistence and UI state.
- The desired product structure (Chats / Share / Games) is a product decision, not yet proven to exist as a clean navigation architecture.
- Status/Updates is deferred. Mesh must not become another primary destination.

### Identity
- Nearby Connections endpoint names currently carry a display name plus a six-character UUID-derived local identifier.
- That identifier is a device-local peer hint, not a proper NeaRer account identity, cryptographic public-key identity or recoverable user ID.
- A durable NeaRer ID, contact search, contact exchange and identity recovery design are still needed.

### Mesh
- The transport accepts a TTL field in the range 0..4, but its current message handler processes direct `kind=message` packets and does not forward them.
- The source comment explicitly says multi-hop forwarding remains disabled until recipient routing and end-to-end encryption exist.
- `MainActivity.onCreate` forces `mesh_relay_enabled` to false.
- `updateMeshRelayEnabled` briefly stores the requested value and then writes false again. Its in-memory UI state can therefore disagree with the persisted/effective relay state. This should be corrected when relay settings are next touched.
- Current peer discovery and direct connections are not proof of a working multi-hop mesh.
- There is no verified destination-addressed route discovery, multi-hop acknowledgement chain or relay store-and-forward path in the reviewed message handler.

### Security
- Nearby Connections has its own authenticated/encrypted peer-connection mechanisms, but that does not automatically provide end-to-end encryption across multiple relays.
- The custom Wi-Fi Direct socket path shown in `MainActivity` uses a six-digit comparison/approval handshake and Base64 message encoding. Base64 is not encryption. The reviewed code does not establish TLS or an end-to-end cryptographic session for that fallback path. Treat this as a security review blocker before broad private-chat claims.
- The local message history and pending outbox are stored in SharedPreferences as JSON/text in the reviewed code. No encryption-at-rest layer is visible in these paths.
- Group packets rely on local group membership/name checks; the README itself notes that group identity and relay privacy need further security work.
- Do not enable multi-hop forwarding of private messages until authenticated identities, E2E encryption, replay protection and safe relay queue handling are implemented and tested.

### File transfer
- The current source has more transfer machinery than a basic one-file demo: multiple selected files can be queued and sent sequentially to a connected Nearby Connections peer.
- Sequential file queueing is not simultaneous multi-recipient transfer.
- The transport source contains SDK payload resume handling for a reconnect to the same peer, while the README currently says interrupted transfers cannot resume. Reconcile the README with the exact end-to-end behaviour and real-device tests before advertising resumability.
- No arbitrary 200 MB application-level cap should be introduced. Practical storage/provider/transport constraints still apply.

### Games and documentation
- The current `MainActivity` contains Ludo protocol/state handling (`LudoNetworkMatch`, `LudoEngine`, dice/action/state packets).
- The README describes a two-player tic-tac-toe game. That documentation is inconsistent with the reviewed current source and must be corrected only after inspecting the game UI and running tests.
- A source implementation of Ludo rules/protocol does not prove that the shipped UI is visually complete or that real-device multiplayer works.

## 3. Recommended safe implementation order

1. Add a proper app-shell/navigation layer with exactly three destinations: Chats, Share, Games. Keep existing transport code untouched in this step.
2. Extract state and responsibilities out of `MainActivity` incrementally: app coordinator, chat repository, transfer coordinator, game coordinator and connection state.
3. Establish a real NeaRer identity and contact model before recipient-addressed routing.
4. Security review and repair of the custom Wi-Fi Direct fallback and local data storage. Define one consistent secure messaging envelope for both direct transports.
5. Build destination-addressed direct delivery and end-to-end encryption.
6. Implement multi-hop forwarding, deduplication, bounded TTL, acknowledgements and durable encrypted store-and-forward.
7. Correct relay preference/UI state and implement informed relay consent and battery controls.
8. Add the Living Mesh visualisation driven by real transport/routing events.
9. Give Share its own Send/Receive workspace and test sequential queueing, resumability and multiple receiving phones independently.
10. Reconcile game UI, README, build and real-device multiplayer tests.

## 4. Verification gates before claims

- Build the debug APK successfully and record the exact commit.
- Run unit tests for message encoding, identity, deduplication, TTL, route selection and Ludo rules.
- Test direct chat and file transfer on at least two physical Android phones.
- Test multi-hop only after the relay engine exists, using three or more physical devices; distinguish simulation from physical-device evidence.
- Test disconnect/reconnect, process death, phone restart, background restrictions and battery use.
- Measure transfer throughput and concurrent receiver performance instead of estimating them.
- Verify that the UI reports actual route/forward/delivery events and never fabricates a live path.

## 5. Immediate conclusion

NeaRer already has a meaningful nearby Android foundation: direct chat, direct peer discovery, file-transfer machinery, local groups and Ludo-related code. It does **not** yet have the complete messaging-first app shell or a verified multi-hop private messaging mesh.

The next safe code change should be the three-destination app shell, but only after locating and understanding the existing Compose screen composables and current navigation/state flow. Security work must precede turning on multi-hop relay.
