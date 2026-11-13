# NeaRer

**No Data? We Are NeaRer.**

NeaRer is a native Android app for nearby communication, file sharing and local community features. Its core nearby discovery and direct communication are designed to work without an internet connection.

## Current integration source milestone: 0.5.5

### Nearby communication
- Kotlin + Jetpack Compose Android app.
- Nearby Connections discovery, connection requests and approval.
- Direct one-to-one chat with encrypted message packets and encrypted delivery acknowledgements after the authenticated key exchange.
- Group and nearby broadcast messages are encrypted separately for each connected peer. Group fan-out currently uses best-effort sends; the durable per-recipient retry/acknowledgement queue is implemented for direct one-to-one chat, not yet for group fan-out.
- Local recent-message history and an encrypted-at-rest pending-message queue.
- Nearby Connections file transfer, progress, cancellation and integrity checks.
- Share Hub / OpenSend foundations for local file and app sharing.

### Community and game features
- Local group creation and joining with a six-digit code.
- Locally stored group membership and message history.
- Nearby group messaging to currently connected members.
- Ludo includes a traditional four-colour board, visible dice, token movement, safe squares, captures, computer opponents and nearby multiplayer foundations. Same-device offline pass-and-play supports two to four human players; solo mode supports three computer opponents. Nearby multiplayer still requires physical two-phone verification. Internet multiplayer and live voice calling are not claimed as complete.

## Mesh relay milestone — Batch One

The recipient-encrypted mesh and Dead Drop foundations have been integrated into `main` (commit `1a6006463f3e206941e2195e69d7aed818831fa1`). The implementation includes recipient-addressed encryption, signed route advertisements, encrypted store-and-forward persistence, authenticated hop acknowledgements, destination-signed delivery receipts, route withdrawals and retry handling. This is source integration, not proof of production readiness.

Batch Two adds signed, hash-linked per-hop acceptance proofs, compact packet/proof framing and atomic encrypted Dead Drop persistence for packet plus proof chain. The transport code is now integrated into `main` (coordinated merge commit `16a6a51a6cf11480a80f9536a0a9ec97f9098f37`). Exact-head JVM tests and debug APK assembly passed on the PR head before merge; the post-merge `main` workflow is running separately. The relay remains **disabled** pending security review, trust/key-rotation and recovery checks, and physical A → B → C → D validation. Do not treat a hop proof as proof of honest forwarding or a destination delivery receipt.

## Security boundaries and current limitations

- The current Nearby handshake uses each phone's long-term P-256 signing identity to authenticate a fresh ephemeral P-256 ECDH exchange. HKDF-SHA256 derives directional AES-256-GCM session keys.
- Chat bodies and delivery acknowledgements are encrypted and authenticated at the application layer. The message ID is authenticated as associated data; the receiver's durable deduplication prevents a replayed message from being stored twice.
- First-contact trust uses trust-on-first-use key pinning. This detects a changed key on later connections, but it does **not** prove a real-world person's identity or prevent a first-contact man-in-the-middle attack.
- File metadata and resume-control packets are authenticated by the direct Nearby connection but are not yet protected by the new end-to-end chat packet layer.
- Multi-hop mesh relay is **disabled**. Do not enable it until recipient routing, encrypted store-and-forward packets, authenticated hop handling, delivery/expiry rules and multi-phone tests are complete.
- Durable HyperTransfer checkpoints are not yet integrated with the actual interrupted Nearby FILE payload lifecycle. Do not claim process-death recovery or fully resumable transfers.
- Real-device testing is still required for two-phone discovery, chat, group messaging, large-file transfers, interrupted transfers, OpenSend hotspot compatibility, app installation and game behavior.
- Delivery acknowledgements are not read receipts. Internet multiplayer and live voice calls are not implied by the offline game foundation.
- File transfers have no fixed application-level size cap, but actual transfer size and speed remain constrained by device storage, Android providers, hardware and connection stability.

## Build and release policy

GitHub Actions validates JVM unit tests, debug APK assembly and APK artifact upload. Hosted emulator jobs are deferred as requested; this is not evidence of physical-device compatibility. A passing build does not replace two-physical-phone testing. Only an artifact from the exact validated commit should be considered current. Source integration does not certify a release APK or authorize enabling mesh relay.

## Suggested two-phone test

1. Install the same validated build on two Android phones.
2. Enable Bluetooth and Wi-Fi on both phones and grant NeaRer's nearby-device permissions.
3. Discover each other, compare the connection authentication code and approve the connection.
4. Send direct messages in both directions and verify delivery after reconnecting.
5. Create a local group, join from the second phone and test group messages.
6. Send a file, interrupt a transfer and verify the current recovery behavior.
7. Repeat with Ludo and other available game modes; record any feature that does not work on real devices.

## Technical references

- [Nearby Connections overview](https://developers.google.com/nearby/connections/overview)
- [Nearby Connections: exchange data](https://developers.google.com/nearby/connections/android/exchange-data)
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)

## SmartConnect — Phase 1 implementation

The Phase 1 SmartConnect branch adds automatic Nearby Connections startup after first-run permissions are granted and whenever the app returns to the foreground. It uses the flexible multi-peer cluster strategy by default, so users do not need to configure a matching one-to-one speed mode. Nearby person cards open the connection flow with one tap. Peers whose signing and recipient-encryption keys were previously pinned can reconnect without repeating first-contact approval; the authenticated handshake still verifies key continuity before application data is accepted. First-time peers still require explicit approval and a security-code comparison.

SmartConnect does not silently enable Bluetooth or Wi-Fi and does not bypass Android permissions. Discovery currently remains dependent on Android radio availability and foreground/app-process lifecycle; continuous background discovery and two-physical-phone verification are separate release gates. The existing Android minimum is API 26 (Android 8.0).

Hosted Android emulator launch jobs are currently deferred because their startup/boot reliability has been inconsistent. The CI validation path focuses on JVM unit tests, debug APK assembly and artifact upload. Emulator checks are not claimed as passed, and physical-device testing remains separate.


## Coordinated integration checkpoint

Phase 1 SmartConnect is integrated into `main`. The coordinated Phase 2 branch carries the Batch Two mesh identity/proof-chain transport work and the Batch Three Ludo pass-and-play updates while retaining the SmartConnect `MainActivity.kt` changes. The existing CI workflow is kept from `main` (JVM tests + debug APK build + artifact upload; no emulator launch job). The coordinated PR #119 has been merged into `main` at `16a6a51a6cf11480a80f9536a0a9ec97f9098f37` after exact-head CI passed. The original conflicting PR #117 was closed as superseded; its source branch remains available. Relay remains disabled.


## Post-merge coordination update — 13 November 2026

- Coordinated PR #119 (SmartConnect + Batch Two mesh proof-chain/Dead Drop integration + Ludo foundations) was merged into `main` as `16a6a51a6cf11480a80f9536a0a9ec97f9098f37`.
- Exact PR-head workflow for `ceb48ad6382d55102dddd9f3b21e81edd464d599` passed JVM unit tests, debug APK assembly and artifact upload before merge.
- A separate push workflow for the resulting `main` merge commit was started; its final result must be checked before describing the post-merge main build as validated.
- Phase 1 SmartConnect, coordinated mesh proof-chain foundations and Ludo pass-and-play foundations are now source-integrated in `main`. This does not mean multi-hop relay, internet multiplayer, nearby multiplayer, or physical-device behavior is field-verified.
- Relay remains hard-disabled. Next engineering focus is a focused Phase 2 audit and completion of the live secure-message delivery path, then transfer lifecycle integration; do not claim full Phase 2 completion based only on the foundations already present.
- Android minimum remains API 26 (Android 8.0). Hosted emulator checks remain deferred; physical-device compatibility is unverified.


## Phase 2 messaging audit — 13 November 2026

The live direct one-to-one path has these integrated foundations: signed fresh ECDH handshake, directional AES-GCM session keys, encrypted message packets and acknowledgements, persist-before-send encrypted outbox, duplicate suppression committed before ACK, bounded retry attempts, and retry on a later authenticated reconnect. The acknowledgement means the recipient committed the message locally; it is not a read receipt. First-contact trust remains trust-on-first-use and depends on users comparing the in-person security code.

### Group fan-out reliability gap

The current group/broadcast methods encrypt a separate packet for each connected peer, but do not put each recipient's copy into the durable outbox or map its acknowledgement to a pending delivery. They are best-effort while peers are connected. This is a known Phase 2 gap, not a completed reliable group-messaging feature. The next implementation must track delivery independently per recipient, persist each recipient's queue entry before send, retry after reconnect, and clear only that recipient's copy after its authenticated ACK.

### Process lifecycle boundary

The direct outbox is restored when the transport is recreated and retries when the matching trusted peer authenticates again. This is reconnect-driven recovery, not a guarantee of background delivery while the app is stopped or the phone is in Doze. Android background execution and a durable scheduled retry mechanism remain separate work.
