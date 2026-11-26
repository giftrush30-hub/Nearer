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

### NeaRer Library

- Unified Library destination for the local document collection and NeaRer Word Bible/study workspace.
- Existing local collection supports user-selected files, search, filters, favourites, PDF rendering and saved reading position.
- Recovered Bible work adds offline Scripture packs, translation/book/chapter navigation, verse search, translation comparison, bookmarks, highlights, private notes, original-language word study, reading plans and user-imported offline audio.
- Bible content and lexical datasets are separate licensed packs; the base APK does not bundle large Scripture or audio corpora.
- General EPUB reading, a general-purpose dictionary, unified annotations across formats, publisher catalogue, paid book sales and sponsored reading remain planned work. Paid access is deferred until the approved Wallet/payment phase.
- See [NeaRer Library roadmap](docs/nearer-library-roadmap.md) and [NeaRer Word device checklist](docs/NEARER_WORD_DEVICE_SMOKE_TEST.md).

### Business Center
- Local-first business identity with category, service area, hours and optional public contact details.
- Bounded local product/service catalogue (up to 100 listings), availability labels, search/filter, private product-photo storage and local catalogue sharing through Android's share sheet.
- This is not yet a public marketplace, direct NeaRer Nearby catalogue delivery, online ordering or payment processing. See [Business Center implementation and roadmap](docs/business-center-roadmap.md).

### Movies / Video Center
- Offline local video library with favourites, search, continue-watching filters, periodic resume checkpoints, playback-speed controls and cached thumbnails for known local Android providers.
- Uses Android document permissions and Media3 for supported local formats; shared URI grants are checked against Library and Studio Video before revocation.
- This is not yet public creator publishing, licensed movie streaming, online discovery, hosting, transcoding or payments. See [Movies / Video Center implementation and roadmap](docs/movies-video-center-roadmap.md).

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

## Messaging — current integrated state (17 November 2026)

The following messaging capabilities are integrated into `main`. They are source- and CI-validated; physical-device acceptance is still required before calling the messaging department release-ready.

### Direct chat and delivery
- First-contact peers must approve the connection and compare the in-person security code. Previously pinned peers can reconnect after the authenticated identity/key-continuity checks.
- Direct messages use the authenticated encrypted Nearby Connections session, an encrypted durable outbox, stable message IDs, duplicate suppression and authenticated delivery acknowledgements.
- A delivery acknowledgement means the recipient committed the message to NeaRer's encrypted incoming inbox. It does **not** mean the recipient opened or read it.
- Pending outgoing messages retry when the same pinned peer authenticates again. Recovery is reconnect-driven and depends on Android allowing the service and radios to run; force-stop, radio shutdown, OEM battery restrictions and Doze can affect availability.

### Groups, receipts and chat controls
- Verified community-group messages are encrypted separately for each verified recipient, queued durably per recipient before dispatch, and acknowledged independently. Nearby broadcast messages remain best-effort.
- Direct and group read receipts are separate from delivery acknowledgements. Receipt events are bounded and checked against the authenticated peer identity; group receipts also require membership in the verified group roster.
- Users can disable read receipts. Local read state and delivery of a receipt are tracked separately; pending receipts can retry after reconnect.
- Direct-chat typing indicators are short-lived control packets. They are not stored in the durable message inbox or queued for later delivery.
- Delete-for-everyone is restricted to the original sender and eligible outgoing messages. The receiving copy accepts deletion only from the authenticated original sender. Local delete-for-me is separate. Deletion cannot erase screenshots, exports or copies made outside NeaRer.
- New persisted direct and verified group messages can trigger generic private notifications while NeaRer is backgrounded. Protocol control packets, duplicates and failed inbox writes must not create new-message alerts. Android notification permission and channel settings remain user-controlled.

### Voice notes and lifecycle
- Direct-chat voice notes are recorded locally and sent through the nearby file-transfer path. They are file attachments, not yet fully integrated inline audio-message bubbles or group voice notes.
- Active recording is stopped and released when the app backgrounds or the composer is disposed; incomplete recordings are discarded.
- The Nearby transport is owned by a connected-device foreground service rather than by the Activity alone. The service maintains an ongoing Android notification, stages incoming messages in an encrypted durable inbox before acknowledging them, and replays pending inbox entries when the UI reconnects.
- This service does not override Android permission, battery, force-stop or radio controls. Continuous availability across every device/OEM lifecycle state is not promised until verified on physical phones.

### Automated validation and release acceptance
- GitHub Actions on `main` runs JVM tests, Android manifest validation, diagnostic lint, debug APK assembly and artifact upload.
- The `main` build at commit `3bfafc51c815e21a334a1b70dad9507b872f98b1` passed all workflow steps on 17 November 2026. Its uploaded debug APK is approximately 17.3 MB.
- A passing CI workflow proves that the source tests and APK build succeeded. It does not prove radio discovery, Bluetooth/Wi-Fi interoperability, multi-phone delivery, notification behavior or background recovery on real devices.
- Android minimum remains API 26 (Android 8.0). Hosted emulator checks are deferred; no physical-device test is claimed as passed.

Before marking Messaging release-ready, test with at least two physical Android phones (and three for group fan-out):
1. Discover each other with Bluetooth/Wi-Fi enabled, compare the security code, approve, and exchange direct messages in both directions.
2. Confirm queued/sent and Delivered states; open the receiving chat and confirm Read appears only when read receipts are enabled.
3. Disable read receipts and verify no direct or group read receipt is sent.
4. Test typing, replies, edits, reactions and delete-for-everyone, including attempts by a non-sender and delayed edits/reactions after deletion.
5. Create a group, add a third phone, exchange messages, and verify per-recipient delivery and group read receipts.
6. Record and send a voice note; play the received attachment. Background the app during another recording and confirm it stops safely.
7. Background both phones and verify the foreground-service notification, message notifications and inbox replay. Repeat after disconnect/reconnect, process recreation and device restart; record force-stop and battery-optimization behavior separately.
8. Transfer files while connected and interrupt a transfer to document the current recovery behavior.

Do not enable multi-hop relay or claim complete messaging availability based only on these checks; relay remains separately disabled pending its security and A → B → C → D validation.
