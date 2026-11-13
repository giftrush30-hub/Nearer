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


## Coordinated Phase 1 / Phase 2 integration — 13 November 2026

- Phase 1 SmartConnect PR #118 was squash-merged into `main` at `c8c0400e9a2231ac02a7978242b6aad55c98face`. Its JVM unit tests and debug APK assembly passed; the artifact was uploaded. Emulator jobs were not run. This does not certify physical-device behavior.
- Batch Two / Batch Three work from PR #117 has been carried onto `integration/phase2-coordinated` without replacing the newer SmartConnect `MainActivity.kt` or reverting the current main-branch CI workflow. The coordinated branch contains the mesh proof-chain/envelope implementation, atomic Dead Drop proof persistence, associated tests, Ludo pass-and-play engine/panel and integration documentation.
- First-contact trust prompts were merged into the SmartConnect activity without discarding its automatic discovery and trusted-reconnect work.
- Next gate: run JVM tests and assemble the APK on the coordinated branch head, resolve every failure, then review the live message/mesh transport path before merging. Do not merge the original PR #117 as-is while it reports conflicts.
- Relay remains disabled. A successful compile does not prove remote key trust, multi-hop delivery, process-death recovery or 100+ device scale. Physical A → B → C → D validation remains a later release gate.


## Post-merge status correction — 13 November 2026

The earlier sections above are historical checkpoints and include pending gates that have since moved. The current source state is `main` at merge commit `16a6a51a6cf11480a80f9536a0a9ec97f9098f37`. PR #119 merged the coordinated SmartConnect, mesh proof-chain/Dead Drop, Ludo and documentation work after exact PR-head JVM unit tests, debug APK assembly and artifact upload all passed. The separate push-triggered workflow for the resulting main commit was still running at the time of this update.

Current interpretation:
- Phase 1 SmartConnect: source-integrated into main; physical device verification remains outstanding.
- Batch Two mesh security: proof-chain and atomic encrypted Dead Drop foundations source-integrated; multi-hop relay remains disabled and not field-validated.
- Ludo: traditional board/engine and same-device offline pass-and-play foundations source-integrated; nearby multiplayer still needs two-phone validation; internet multiplayer and live voice calling are not complete.
- Phase 2 secure messaging: encryption, key exchange, encrypted queue, retry/deduplication and acknowledgements have foundations in the existing code, but the full live end-to-end delivery path must be audited and validated before Phase 2 is called complete.
- File transfer: HyperTransfer durable checkpoints are not yet connected to the actual interrupted Nearby FILE payload lifecycle; do not claim process-death recovery or full resumability.
- Relay: hard-disabled until identity/trust binding, forwarding security, retry/recovery, security review and physical multi-hop tests are complete.
- Android minimum: API 26 (Android 8.0). No emulator or physical-device certification is claimed.


## Phase 2 direct messaging path audit — 13 November 2026

### Integrated source path

- Peer authentication uses a fresh P-256 ECDH key pair and nonces per Nearby connection. The long-term P-256 signing key authenticates the handshake transcript, including both device IDs, ephemeral keys and recipient-encryption keys.
- HKDF-SHA256 derives directional AES-256-GCM session keys. Secure chat payloads bind sender ID, recipient ID and message ID as authenticated associated data.
- Direct one-to-one sends persist an encrypted-at-rest outbox entry before attempting transmission. The sender retries with bounded backoff and retains the message as queued after the bounded attempt window.
- A later authenticated reconnect for the same stable peer reattaches the pending queue and retries it using the fresh session keys.
- The receiver suppresses duplicate message IDs, commits its bounded deduplication history before sending the encrypted ACK, and does not ACK when that commit fails.
- Sender-side ACK handling is bound to the expected endpoint for that pending direct message. The ACK means durable local receipt, not that a person read the message.

### Known gaps — do not mark Phase 2 complete yet

1. Group/broadcast fan-out encrypts a copy for each currently connected peer but bypasses the persistent per-recipient outbox and ACK tracking. A disconnected peer can miss the group message. Implement per-recipient queue identities, persist-before-send, per-peer retry, and authenticated per-peer ACK retirement.
2. Retry recovery is triggered by transport initialization plus a later authenticated peer reconnect. It is not guaranteed background delivery while the process is stopped or Android Doze suspends the app. A lifecycle-safe scheduled recovery design is still required.
3. First-contact trust is TOFU. Signed key possession and the in-person security-code comparison reduce risk but do not independently identify a real-world person or prevent a compromised first-contact channel.
4. The direct messaging path has source-level implementation and unit-test foundations, but there is no current two-physical-phone end-to-end validation. Do not claim field reliability or production readiness.

### Phase 2 completion checklist

- [ ] Add durable per-recipient group/community message queues and ACK tracking.
- [ ] Add process-death/reboot/Doze-aware retry recovery with bounded battery use and clear Android foreground/background behavior.
- [ ] Add focused tests for outbox corruption, duplicate sends, reconnect races, ACK from the wrong peer, queue persistence failure, and multiple recipients with independent delivery state.
- [ ] Validate direct and group chat in both directions on physical Android phones, including app restart and radio interruption.
- [ ] Update user-facing delivery states so queued, sent, locally delivered, and read (if ever added) are not conflated.

Phase 2 is therefore **partially implemented, not complete**. This audit is a source review; it does not substitute for device tests or a security review.
