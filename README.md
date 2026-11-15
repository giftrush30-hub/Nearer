# NeaRer

**No Data? We Are NeaRer.**

NeaRer is a native Android app for nearby communication, file sharing and local community features. Its core nearby discovery and direct communication are designed to work without an internet connection.

## Current integration source milestone: 0.5.5

### Nearby communication
- Kotlin + Jetpack Compose Android app.
- Nearby Connections discovery, connection requests and approval.
- Direct one-to-one chat with encrypted message packets and encrypted delivery acknowledgements after the authenticated key exchange.
- Direct one-to-one chat uses an encrypted durable outbox with stable message IDs, reconnect retry and authenticated delivery acknowledgements. Verified community-group fan-out also queues encrypted per-recipient copies durably and records delivery receipts. Nearby broadcast messages remain best-effort to currently connected peers.
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

- The older Wi-Fi Direct line-protocol fallback is **not an authenticated, encrypted app-message transport**. Pairing-code approval alone does not provide application-layer confidentiality or authenticated delivery receipts. NeaRer therefore ignores legacy `MSG`, `ACK` and `COMMUNITY` frames and does not send private chat, community or game packets over that socket. Its separate compatible file-transfer path remains available; private chat and app messages require the authenticated, encrypted Nearby Connections session.

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

### Group fan-out reliability — source-integrated

The current group-message path now creates a separately encrypted durable outbox copy for each identity-verified recipient. It commits the complete outbox before the first send, commits the transport-ID → group-message/recipient receipt mapping before dispatch, retries a queued copy when that same pinned peer authenticates again, and retires only the copy acknowledged by that peer. Unverified roster members are not silently included. This is stronger than best-effort broadcast, but physical multi-phone and process-restart verification remain release gates.

Nearby broadcasts are still best-effort to currently connected peers; they do not yet use the durable per-recipient group-message queue.

### Process lifecycle and notification boundary

Direct and group outbox entries survive process recreation and retry when the matching trusted peer authenticates again. The app now also creates a private, content-hidden Android notification for a newly received direct message when its Activity is backgrounded, provided Android notification permission/channel settings allow it. Edits, reactions, duplicate packets and messages that fail local persistence do not create new-message alerts.

This notification is not a guarantee of delivery after Android kills the app: the Nearby transport is still owned by the Activity, and NeaRer does not yet keep a dedicated connected-device foreground service running for messaging. Doze, force-stop, app-process death and long periods in the background still require a dedicated service/lifecycle design and physical-device verification. A local delivery ACK means the message was committed on the recipient phone; it is not a read receipt.


## Messaging background-alert slice — 15 November 2026

- Added a private Android notification for newly persisted incoming direct messages while the app Activity is backgrounded.
- Notification text deliberately does not expose message content on the lock screen; the public notification version only says that NeaRer has a new message.
- Duplicate deliveries, local persistence failures, edits, reactions and community control packets do not trigger a new-message alert.
- Notification eligibility and stable notification IDs have JVM unit tests.
- The alert is conditional on Android notification permission and channel settings. It does not keep Nearby Connections alive after the Activity/process is stopped; a dedicated connected-device service remains future messaging work.
