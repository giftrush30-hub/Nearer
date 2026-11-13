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

1. **Partially addressed:** nearby group-chat fan-out now creates a distinct transport message ID per currently connected authenticated recipient, commits each copy to the encrypted-at-rest outbox before sending, retries it after that same stable peer reconnects, and retires only the matching recipient copy after its authenticated ACK. The sender's local group message is also persisted before fan-out. Remaining gap: community rosters store display names rather than stable peer IDs, so members who are offline at send time cannot yet be queued safely; broadcasts and Ludo/control fan-out remain ephemeral.
2. Retry recovery is triggered by transport initialization plus a later authenticated peer reconnect. It is not guaranteed background delivery while the process is stopped or Android Doze suspends the app. A lifecycle-safe scheduled recovery design is still required.
3. First-contact trust is TOFU. Signed key possession and the in-person security-code comparison reduce risk but do not independently identify a real-world person or prevent a compromised first-contact channel.
4. The direct messaging path has source-level implementation and unit-test foundations, but there is no current two-physical-phone end-to-end validation. Do not claim field reliability or production readiness.

### Phase 2 completion checklist

- [ ] Extend the new per-recipient durable group-chat queue to offline members by storing verified stable peer IDs in community rosters; add durable broadcast history/outbox and keep Ludo/control packets ephemeral unless their replay semantics are explicitly designed.
- [ ] Add process-death/reboot/Doze-aware retry recovery with bounded battery use and clear Android foreground/background behavior.
- [ ] Add focused tests for outbox corruption, duplicate sends, reconnect races, ACK from the wrong peer, queue persistence failure, and multiple recipients with independent delivery state.
- [ ] Validate direct and group chat in both directions on physical Android phones, including app restart and radio interruption.
- [ ] Update user-facing delivery states so queued, sent, locally delivered, and read (if ever added) are not conflated.

Phase 2 is therefore **partially implemented, not complete**. This audit is a source review; it does not substitute for device tests or a security review.


## Follow-up integration — 13 November 2026

The coordinated source work has advanced beyond the earlier PR #119 checkpoint:

- PR #120 is merged into `main`: bounded transfer-throughput samples remain capped at 512, recent/peak rates use deque endpoints instead of scanning the retained samples, and idle snapshots prune stale samples so recent speed falls to zero when progress stops. Its exact-head JVM tests, debug APK build and artifact upload passed.
- PR #121 is merged into `main`: the More/Profile area now opens a full local profile editor for display name, illustrated avatar and optional photo. Profile photos are decoded/downscaled before storage (maximum 512 px longest edge), and replaced/removed local photo files are cleaned up.
- PR #121 also scales the adaptive launcher foreground and monochrome artwork into Android's central safe area and uses the foreground artwork for the Android 12+ splash icon. Its exact-head JVM tests, debug APK build and artifact upload passed.
- The earlier onboarding/premium-Ludo PR #112 remains an old-base reference only. Its onboarding and File Centre files match current main; its profile editor and icon improvements were selectively forward-ported, while current main retains the newer SmartConnect and Ludo changes. Do not merge PR #112 wholesale.
- PRs #109 and #110 are stale parallel branches. Current main already contains the shared `TransferStreamCopy` implementation, one-pass SHA-256 staging/receive copy, zero-length bulk-read fallback, byte-count/integrity checks, and tests. Do not merge their old branches wholesale.
- The remaining transfer-speed optimization from PR #108 was selectively forward-ported through PR #120; do not merge the old PR #108 branch wholesale.

### Current release and test boundaries

- Source integration is ahead of physical-device certification. Exact-head CI passed on PR #119, #120, #121, #125 and #129, including JVM unit tests and debug APK assembly. PR #125 is merged as `25d643437e6e7a233f818e3febe3255e569c302f`; PR #129 is merged as `271e2bfabcffab9209af5aae23f10cc185bd2c92`. The CI artifacts are debug APKs, not Play Store release certification.
- No Android emulator launch checks were run, per the user's instruction. No physical-device test is claimed for this integration.
- Android minimum remains API 26 (Android 8.0); Android 10 is within the declared OS range but has not yet been certified on the user's Infinix handset.
- Mesh relay remains hard-disabled. No 100+ participant scale or A → B → C → D physical mesh test is claimed.
- HyperTransfer's durable process-death checkpoint/resume lifecycle remains unconnected to the actual Nearby FILE payload lifecycle; full resumability and unlimited-size performance are not certified.
- Next engineering gate: physical two-phone validation of SmartConnect, encrypted direct chat, file transfer and Ludo, then fix any device-specific issues. Mesh relay remains a separate later gate.


## Community group-message delivery update — 13 November 2026

The original connected-peer-only fan-out was superseded by the identity-bound implementation in PR #125 below. The current implementation is intended to:

- Commit the sender's local group message before fan-out.
- Persist separate encrypted-at-rest delivery copies for each eligible stable peer ID before the first send.
- Retry each recipient copy independently after that same pinned identity reconnects, including members offline at send time.
- Retire a recipient copy only after its authenticated ACK is received from the expected endpoint and the outbox removal is durably committed.
- Reject group messages from identities outside the saved roster and reject roster updates not sent by the pinned coordinator.
- Keep the Wi-Fi Direct fallback out of identity-sensitive group membership and group-chat operations; broadcasts and Ludo/control packets remain ephemeral.

These are source-level changes, not proof of physical-device delivery, process-death/Doze recovery, or production-secure group membership. See the combined PR #125 section below for trust and migration boundaries.


## Community identity-bound roster follow-up — PR branch `security/community-identity-roster`

The durable community fan-out follow-up replaces display-name recipient selection with stable peer identity:

- Community groups persist an owner/coordinator peer ID and a list of member peer IDs alongside display names. Display names are presentation only.
- A roster update is accepted only from the same pinned peer identity recorded as coordinator. A legacy group with no coordinator uses first-pinned-peer TOFU; the UI explicitly tells the user to confirm that peer is the trusted group-code sharer.
- Group messages are accepted only when the sender's authenticated stable peer ID is in the saved roster. A matching display name is not sufficient.
- Durable fan-out targets stable peer IDs, requires both identity keys to be pinned on this phone, and can save offline copies with no live endpoint. Those copies retry only after the same peer identity authenticates again.
- The legacy Wi-Fi Direct fallback has no equivalent stable peer identity binding; it is not used for group membership control or group chat.
- Old name-only group records remain visible but cannot send secure group messages. The owner must recreate the group using the identity-aware protocol and members must rejoin; the old display-name list cannot safely reveal who the original owner was.

Security limits remain explicit: the first trusted roster coordinator is TOFU rather than a cryptographically signed group invitation, and the six-digit group code is not an authentication secret. Users must compare the existing first-contact pairing code in person. A phone can only queue copies for member identities whose signing and recipient-encryption keys it has pinned; other members are skipped with a visible warning. PR #125 is merged and exact-head CI passed, but this is not a claim of production-secure group membership or physical-device validation.


## Combined Community reliability/security stack — PR #125 (merged)

PR #125 combined the durable per-recipient group fan-out from PR #124 with identity-bound community rosters. PR #124 and overlapping transport-only PR #123 were closed as superseded. The combined changes are integrated into main at commit `25d643437e6e7a233f818e3febe3255e569c302f`. Exact-head JVM tests and debug APK assembly passed. No physical-device test or background-delivery guarantee is claimed.


## Community delivery receipt follow-up — branch `feature/community-delivery-receipts`

The follow-up adds a transport-message-ID to community-message/recipient mapping so each group message can show separate per-member states:

- The transport returns the exact message IDs committed for each stable recipient and defers the first send until the caller has persisted the corresponding receipt mapping.
- A pinned member whose Nearby endpoint is discovered but whose handshake is not finished is still queued with no live endpoint; authentication completion retries that same member's copy.
- Community receipt references and per-peer delivery states are persisted locally and restored after app restart.
- A bounded 2,000-ID authenticated ACK history is committed atomically with outbox retirement, allowing direct and community message states to reconcile after a crash between transport ACK and UI receipt persistence.
- Authenticated ACK callbacks update only the mapped stable peer's status; one recipient's ACK does not mark the entire group delivered.
- The UI shows delivered, queued and not-verified recipient counts under each outgoing group message.
- If saving a receipt fails, the updated state remains in memory and the app retries the write on Activity pause/resume. If storage continues failing and the process is killed before a successful retry, that latest receipt may not survive the restart.

PR #129 is merged into main at commit `271e2bfabcffab9209af5aae23f10cc185bd2c92`; exact-head JVM tests and debug APK assembly passed. It has not been validated on physical phones. It does not add background delivery while the app process is stopped or Android Doze suspends execution.

## HyperTransfer restart-discovery foundation — 13 November 2026

The follow-up branch `feature/hypertransfer-restart-discovery` adds a bounded, restart-safe discovery operation to the existing local resume store:

- `TransferResumeStore.discoverVerifiedCandidates()` enumerates the journal's bounded checkpoint list after process recreation, reconstructs the item identity from each checkpoint, and re-verifies the on-disk committed prefix before returning a candidate.
- Missing, truncated, oversized, or digest-mismatched partial files are discarded together with their journal entry instead of being surfaced as resumable work.
- Results are deterministic (transfer ID, then relative path), so a future recovery UI/coordinator can restore a stable list.
- Tests simulate a new store instance after process death, multiple transfer discovery, and cleanup of missing/corrupt partials.

This is intentionally a storage/recovery foundation, not a claim that Nearby FILE payloads can yet resume across process death. A candidate must still be bound to the authenticated stable peer and confirmed by the sender's current source identity and prefix digest before a transport applies any offset. The actual Nearby FILE payload lifecycle is still not wired to the durable stream writer/checkpoint protocol. No emulator or physical-device test is claimed.

The same recovery foundation now includes a guarded completion operation, `TransferResumeStore.finalizeVerifiedComplete()`:

- It accepts only a locally re-verified EOF checkpoint and checks the sender-provided whole-file SHA-256 before publishing.
- It publishes the completed partial using atomic same-filesystem hard-link creation, which fails if the destination already exists (including a competing writer winning a race). If hard links are unsupported, it fails closed and keeps the partial rather than exposing a half-copied destination.
- It refuses an existing destination, does not derive the destination from the untrusted manifest path, and removes the journal record after successful publication.
- Unit tests cover successful publication, incomplete data, digest mismatch, and destination collision.

This is still a storage primitive. The receive UI/transport must choose a safe destination and call it only after authenticated resume negotiation and complete stream validation. No Nearby payload integration or physical-device validation is claimed.

### Stable sender binding for restart recovery

The checkpoint format is now version 2 and stores the authenticated sender's stable NeaRer ID. Version-1 journal entries remain readable as legacy, unbound checkpoints, but are deliberately excluded from post-restart discovery because the previous Nearby endpoint ID is transient and cannot prove who owns the transfer.

- New stream checkpoints can persist the authenticated sender ID.
- Discovery returns only candidates bound to a valid stable sender ID.
- Resume validation requires an exact sender-ID match. A different authenticated peer gets a security rejection, and its attempt does not delete or overwrite the original peer's partial.
- The negotiation layer also requires the exact authenticated stable sender ID before it will propose a saved offset. Sender ownership is checked before manifest mismatch cleanup, so a different peer cannot invalidate the owner's partial by reusing transfer/item IDs with altered metadata.
- Codec tests cover version-1 migration and version-2 round trips; store tests cover unbound legacy exclusion, correct-peer acceptance, and wrong-peer rejection without data loss.

The current transport still does not pass its authenticated peer ID into the durable stream writer or route FILE payload bytes through that writer. This change establishes the identity boundary needed for that integration; it does not yet make Nearby process-death resume operational.


### Nearby STREAM transport call-site integration — 13 November 2026
- The transport integration branch now connects the authenticated Nearby BYTES/STREAM callbacks to the verified HyperTransfer coordinator and bridge.
- New peers advertise stream support after the signed identity handshake. A sender uses the resumable STREAM path only after the other peer advertises support; peers that do not answer the stream handshake fall back to the existing FILE transfer path.
- Sender metadata binds the payload ID, deterministic transfer identity, file name/path, size and whole-file SHA-256. The transfer identity is derived from the authenticated sender/receiver IDs and content identity so reselecting the same file after an app restart can find the receiver's saved partial.
- The receiver checks its peer-bound journal and sends either a saved-prefix proposal or an explicit no-resume response. The sender independently verifies the source prefix and sends the decision before providing the stream. The receiver correlates metadata, proposal/decision and STREAM callback regardless of callback order.
- Incoming STREAM bytes are drained on the file I/O executor, checkpointed by the durable writer, checked against the whole-file digest, and published through the no-overwrite finalizer. Interrupted transfers retain only verified checkpoints; reconnect creates a fresh Nearby payload ID and repeats the authenticated handshake.
- The legacy Nearby FILE path remains available for peers that do not advertise stream support and as a timeout fallback. Existing same-process FILE resume remains intact.
- This is source integration awaiting exact-head CI. No emulator or physical-device testing is claimed. The first real-phone gate must verify both directions, app process death/restart, same-file re-selection, interrupted transfer, zero-byte files, old-peer FILE fallback, and Android storage behavior (including hard-link publication).


## HyperTransfer reliability hardening — 13 November 2026

The follow-up branch `feature/hypertransfer-concurrency-journal-hardening` hardens the merged Nearby STREAM recovery path without changing the messaging-owned screens or received-file catalogue work:

- A single transfer/item can now have only one active durable receive writer. Duplicate concurrent Nearby STREAM callbacks for the same transfer are rejected before they can race over the same partial file; unrelated transfers remain independently concurrent.
- Receive locks are reference-counted and removed after use, so the guard does not accumulate an unbounded per-transfer lock map.
- Corrupt, oversized, mismatched and crash-leftover temporary checkpoint journal entries are removed during lookup/discovery instead of being rediscovered indefinitely.
- Journal discovery remains bounded to 10,000 valid entries, with each candidate decoded only after its on-disk size is checked.
- Regression tests cover duplicate concurrent receives, journal corruption, oversized records, misnamed records and crash-leftover temporary files.
- Existing transport selection already prefers authenticated, capability-advertised Nearby STREAM for supported peers and falls back to standard FILE when negotiation, staging or repeated stream attempts fail. This work preserves that compatibility route; it does not change the shared Nearby discovery strategy, which must remain compatible on both phones.

Validation is pending on this branch until its exact-head JVM tests and debug APK assembly finish. No emulator or physical-phone testing is performed here. The user will handle physical-device checks. This is a reliability hardening increment, not a claim of measured speed improvement or release certification.
