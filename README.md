# NeaRer

**No Data? We Are NeaRer.**

NeaRer is a native Android app for nearby communication, file sharing and local community features. Its core nearby discovery and direct communication are designed to work without an internet connection.

## Current integration source milestone: 0.5.5

### Nearby communication
- Kotlin + Jetpack Compose Android app.
- Nearby Connections discovery, connection requests and approval.
- Direct one-to-one chat with encrypted message packets and encrypted delivery acknowledgements after the authenticated key exchange.
- Group and nearby broadcast messages are encrypted separately for each connected peer.
- Local recent-message history and an encrypted-at-rest pending-message queue.
- Nearby Connections file transfer, progress, cancellation and integrity checks.
- Share Hub / OpenSend foundations for local file and app sharing.

### Community and game features
- Local group creation and joining with a six-digit code.
- Locally stored group membership and message history.
- Nearby group messaging to currently connected members.
- Ludo and other in-app community/game foundations are part of the integration branch; individual multiplayer and offline/online modes must be checked against the actual build before being described as complete.

## Mesh relay milestone — Batch One

The recipient-encrypted mesh and Dead Drop foundations have been integrated into `main` (commit `1a6006463f3e206941e2195e69d7aed818831fa1`). The implementation includes recipient-addressed encryption, signed route advertisements, encrypted store-and-forward persistence, authenticated hop acknowledgements, destination-signed delivery receipts, route withdrawals and retry handling. This is source integration, not proof of production readiness.

Multi-hop relay remains **disabled**. Remote first-contact identity binding, independently verifiable per-hop forwarding proofs, intermediate receipt cleanup, process-death/reboot retry scheduling and physical A → B → C → D validation remain outstanding. No message relay should be enabled until these release gates are completed.

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

The integration branch has GitHub Actions checks for JVM unit tests, debug APK assembly and Android emulator launch tests on API 31 and API 35. A passing workflow is necessary but does not replace two-physical-phone testing. Only an artifact from the exact validated commit should be considered current. Batch One source integration into `main` was explicitly approved; that approval does not certify a release APK or authorize enabling mesh relay.

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
