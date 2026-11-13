# NeaRer integration checkpoint

Batch One source integration is complete. The consolidated tree is on `main` and `integration/nearer-complete-app` at commit `98adcd4a0d56e288a26ce71108d8184149838d6e`. This is an engineering milestone, not a release certification. Source integration was explicitly approved; relay activation and release publication remain separate decisions.

## Integrated into this branch

- Latest launcher crash fix, approved blue/gold branding, chat-history baseline and File Centre work from the current design candidate.
- First-run onboarding wired into app startup; display name, avatar choice, optional profile photo and onboarding answers are stored locally.
- More screen now displays the saved NeaRer profile, local identity ID, avatar choice and optional photo, with display-name editing.
- Premium animated Ludo board and visible dice UI.
- Premium Chats extracted into a dedicated Compose screen and used by the Chats navigation tab.
- HyperTransfer foundation classes: bundle manifest/codec/session, manifest fragments, bounded transfer policies, memory/thermal buffer tuning, resume checkpoints, checkpoint journal, prefix verification, resume negotiation codecs/policies and resumable stream writer.
- HyperTransfer unit-test suite and engineering notes copied into this branch.
- Receive stream-copy fix: handles providers that return zero from bulk reads, computes SHA-256 while copying, validates the staged byte count, and only deletes the provider payload after validation.
- Provider-backed outgoing files are now hashed during the staging copy so they do not need a second full read for hashing.
- Local security work added: Android Keystore AES-GCM data cipher, device-bound P-256 signing identity, trust-on-first-use peer-key pin store and encrypted pending-message outbox. The live Nearby handshake now signs a transcript containing both device identities, fresh nonces and fresh ephemeral P-256 ECDH public keys. Both phones derive directional AES-256-GCM session keys with HKDF-SHA256. Direct chat messages, group/broadcast chat packets and delivery acknowledgements are encrypted and authenticated per peer; message IDs are authenticated as associated data and the receiver's durable deduplication protects against replayed delivery. Queued messages remain encrypted at rest and are encrypted for the current session when retried. This provides session encryption after the first-contact trust decision; TOFU does not prove a real-world person's identity or prevent a first-contact man-in-the-middle attack.
- OpenSend/AppShare is now surfaced through a new Share Hub navigation destination: select files for a temporary local Wi-Fi/browser download, scan the Wi-Fi and download QR codes, list installed apps visible to Android, package and queue APK sets to a nearby NeaRer phone, share archives through Android's Sharesheet, and validate received app archives before invoking Android's installer. The foreground-service implementation and manifest registration are present, but the current screen owns its OpenSend session directly; background-survival behavior still needs device validation.

## Important integration work still required

- HyperTransfer resume/checkpoint primitives are present, but the durable checkpoint store and resume negotiation are still not connected to the actual interrupted Nearby FILE payload lifecycle. The current transport's in-memory Nearby SDK offset retry is separate from the durable HyperTransfer checkpoint engine. Do not describe process-death recovery or fully resumable transfers as complete.
- The authenticated ephemeral ECDH handshake and AES-GCM chat packet path are now wired into the live Nearby transport, but they have not yet passed a current-head build or two-phone test. Do not describe chat as field-verified or production-ready until those checks pass. File metadata/resume control packets are still link-authenticated rather than end-to-end encrypted; the Nearby FILE payload lifecycle and HyperTransfer durable resume integration remain separate unfinished work.
- Share Hub is connected to the main navigation. App discovery, archive staging, Nearby queueing, local hotspot/browser sharing and receive/install actions are surfaced, but the full flows and background-survival behavior still need real-device validation.
- Full integration of remaining Fast Share hardening and dependent PR changes is not complete.
- Run the complete unit-test/build workflow against this exact branch head, then resolve every failure. Current-head validation is pending; passing a prior commit does not validate these E2E changes.
- Real-device testing is still required for two-phone discovery, chat, large-file send/receive, interrupted transfers, OpenSend hotspot compatibility, AppShare installation and Ludo.

## Known limitations to keep visible

- Delivery acknowledgements are not read receipts.
- Multi-hop mesh foundations are integrated: recipient-addressed ECDH/HKDF/AES-GCM envelopes, origin signatures, signed path-vector routes and withdrawals, encrypted Dead Drop storage, retry/lease handling, authenticated hop acknowledgements, destination-signed delivery receipts, and an encrypted receipt outbox. Relay remains disabled. Outstanding release gates are cryptographic binding of legacy peer IDs to stable identities for remote first contact, independently verifiable chained per-hop forwarding proofs, intermediate receipt-copy cleanup when destination keys are not pinned, process-death/Doze/reboot retry scheduling, and physical A → B → C → D plus adversarial device validation.
- Internet multiplayer and live voice calls are not part of the current offline Ludo implementation.
- No speed or unlimited-size claims should be made without device measurements.
- AppShare's complete installed-app catalogue uses Android package-visibility access and may require Google Play policy review before Play Store publication.
- Android still controls installation confirmation and compatibility; NeaRer cannot silently install APKs.

## Release gate

Only publish a release APK after the exact-head build/validation workflow and real-device two-phone chat/file-transfer tests pass. Physical multi-hop relay tests and the mesh security gates above are additionally required before any relay feature is enabled. A source merge or a workflow run from an earlier commit is not evidence of release readiness.


## Latest validation feedback

An earlier Android validation attempt exposed integration compile errors in the extracted Chats screen, the Ludo avatar argument, and identity-ID validation calls. Those issues have been corrected on this branch. A fresh validation run against the corrected head is still required; no APK from the failed attempt is publishable.

## Phase 1 — SmartConnect source implementation (branch: phase-1-smartconnect)

Implemented on the Phase 1 branch:
- Nearby Connections starts automatically after onboarding permissions are granted and when the app returns to the foreground, provided Bluetooth or Wi-Fi is enabled.
- Permission results trigger the same automatic startup path; the app does not turn radios on or bypass Android permission prompts.
- The default flexible multi-peer strategy is used without requiring both users to enable and restart a separate Ultra Speed mode.
- Nearby person cards are tappable to initiate the chat connection flow.
- Previously trusted peers are automatically accepted for reconnection only when both signing-key and recipient-encryption-key pins exist; the live handshake still validates key continuity.
- First-contact peers still require user approval and security-code comparison.
- Deterministic SmartConnect policy tests cover startup gates and trusted reconnect requirements.

Not yet certified / remaining Phase 1 gates:
- Run JVM tests, APK assembly and emulator launch tests against the exact Phase 1 PR head.
- Verify discovery, one-tap connect, first-contact approval and trusted reconnect on two physical Android phones, including Android 10 hardware.
- Confirm discovery recovery when Bluetooth/Wi-Fi is toggled and after app background/foreground transitions.
- Continuous background discovery after the app is swiped away is not implemented by this activity-owned lifecycle change; Android foreground-service design and notification/Doze behavior require a separate implementation and device validation.
- Google Nearby Connections manages its supported Bluetooth/BLE/Wi-Fi transports; this does not mean NeaRer independently exposes or guarantees every vendor-specific Wi-Fi Direct/hotspot combination.

Do not mark Phase 1 release-complete until the exact-head CI checks and physical-device gates pass. Android minimum remains API 26 (Android 8.0); API 24/25 are not currently supported.
