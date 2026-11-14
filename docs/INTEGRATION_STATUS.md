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

Exact-head GitHub Actions run #2383 passed on commit `ed73399553a69a15e28e6b284172c2926e61de5f`: JVM unit tests, Android debug APK assembly and APK artifact upload all succeeded. The uploaded APK artifact is #14063081542; the extracted APK is 11,957,559 bytes with SHA-256 `d558bf9576d6ac637b7a22d83fdae68ab27c8b9738c3df20a7f506a5b984f871`. The changes were squash-merged into `main` at `3e805b9ff8dfd83c9fda855d2c37c3bc32998056`. No emulator or physical-phone testing was performed; the user will handle physical-device checks. This is a reliability hardening increment, not a claim of measured speed improvement or release certification.
## Group D — Receiving and File Management — PR #152 (13 November 2026)

The current received-file implementation adds a durable, bounded catalog and more defensive storage handling:

- A versioned catalog stores the user's display name, on-disk relative filename, byte size, modification time and NeaRer app-bundle classification. Unicode display names are preserved.
- Startup rebuilds the visible received-file list only from regular files that actually exist inside the app-private `files/received` directory. Catalog paths are leaf names only; missing files, stale entries and path traversal are not restored.
- Files received before the catalog existed remain discoverable through a compatibility scan. The catalog is rebuilt from the actual files on disk.
- Nearby Connections and the legacy Wi-Fi Direct receiver use the same catalog-registration path. New stored filenames include random suffixes to prevent same-name transfers from colliding.
- The received-file list and catalog are bounded to the latest 200 entries; older private files are not automatically deleted.
- Completed-file notifications are posted only when Android notification permission is already granted. The app does not unexpectedly prompt; received files remain visible in-app if notifications are disabled.
- Downloads export checks available shared-storage space, copies through the bounded streaming/SHA-256 helper, validates the copied byte count, and publishes the MediaStore item only after the copy completes. Failed exports remove the pending shared-storage entry and retain the private original.
- Incoming resumable streams, Nearby FILE fallback copies and legacy direct-transfer writes preflight available space. The legacy direct receiver also checks space as chunks arrive and removes incomplete files when a write fails.
- NeaRer app bundles with the `.nearerapp` extension are recognized as installable bundles after restart.

JVM tests cover catalog round trips (including Unicode names), duplicate-path replacement, traversal rejection, corrupt/oversized catalog input, the 200-entry bound and storage-space boundary conditions.

This supersedes the earlier release-boundary note above that described the HyperTransfer Nearby STREAM call-site work as still awaiting integration: Group C is merged on `main`, and Group D builds on that integrated transport plus the later receive-lock and journal-repair hardening. PR #152 is merged into `main` at `f7fdd80bb6c2cb9cc5de2bf9b7a38a624eb1125e`. Exact synchronized PR head `eb1a21416a27ef1685c5178fd0c88813fbb3fa37` passed GitHub Actions run #2392: JVM unit tests, debug APK assembly and artifact upload. The APK artifact is #14062314264 (11,422,816 bytes). No physical-device validation is claimed; Android 8–16 storage-provider behavior, notification permission states and low-storage recovery still require the user's phone tests.


## Phase 1 consolidation audit — 14 November 2026

### Original NeaRer colour restoration

The original app palette is the design baseline again:

- Midnight: `#101A2B`
- Panel: `#1A2940`
- Mint: `#69E0C0`
- Muted text: `#A8B6C9`
- Soft white: `#F6F8FC`
- Brand blue: `#2563EB`
- Gold: `#FFD12D`

The shared `NeaRerPalette` design-system tokens and Android window/splash backgrounds already use the original values. The main Compose theme and File Centre were aligned to the same palette. The Ludo screen's surrounding panels, controls, borders and typography were also aligned to the shared palette. Traditional Ludo player colours and board markings were deliberately preserved; this is a brand-colour restoration, not a redesign of the game board or its rules.

The palette regression test now asserts the original shared values. No feature, navigation, transport, storage or game-rule logic was intentionally changed by this colour pass.

### Open-branch / PR consolidation findings

- **PR #4 — Royal Electric redesign:** still a draft and targets `feature/nearer-premium-blue-gold-ludo`, not `main`. Its head is substantially diverged from current main. The inspected Ludo double-dice engine and its tests are byte-identical to main; the inspected Ludo screen's meaningful differences were the old colour definitions and UI colour literals, now superseded by the original-palette restoration. Do not merge this PR or its old MainActivity wholesale. Continue reviewing only any independently unique, still-missing files before porting.
- **PR #65 — phone-free identity / Fast Share upload:** still open and targets an old UI branch, not main. Its identity primitives are already present in main. Current main also already contains an authenticated local browser-upload route using HTTP PUT, an explicit uploads-off-by-default control, bounded upload admission and private-file publication. PR #65's older POST-based upload implementation is therefore not a safe wholesale merge. Its remaining tests or isolated hardening changes should be compared against the current PUT implementation before any selective port.
- The old Ludo engine and test files inspected in PR #4 match current main; no duplicate Ludo engine merge is required from that branch.
- The branch inventory spans two pages (more than 160 branches). Branch count is not a merge list: many are historical snapshots, overlapping experiments or already-integrated work. Each candidate must be compared against current main and checked for unique code before integration.

### Current validation boundary

- The repository's Android workflow runs JVM unit tests and assembles/uploads a debug APK on pushes to `main`.
- The GitHub status endpoint currently exposes no status checks for the colour-restoration commits, and the available workflow-run lookup is limited to pull-request-triggered runs. The exact latest main-head build result therefore remains unverified here; do not claim a fresh APK build passed.
- Per the user's instruction, physical-device and human testing remain deferred to the user. This does not waive automated JVM tests or APK assembly checks.
- Keep relay disabled. Do not enable mesh forwarding or make speed, unlimited-size, or production-reliability claims as part of this consolidation pass.

### Phase 1 next engineering actions

1. Complete the branch-by-branch unique-change audit without merging stale branches wholesale.
2. Verify the latest main-head JVM test and debug APK workflow result.
3. Selectively forward-port only reviewed missing features and their tests, preserving the latest SmartConnect, messaging, HyperTransfer and Ludo implementations.
4. Keep this document current after each integration batch so parallel development chats have one shared source of truth.


### Additional focused branch checks — 14 November 2026

The following comparisons were checked against the current `main` source, rather than assuming that a branch's ahead/behind count means its features are missing:

- `feature/messaging-forwarding-voice-foundations`: `ChatForwardPolicy`, `VoiceNotePolicy` and both corresponding JVM test files are byte-identical to main. No port is needed.
- `feature/messaging-inbox-search-pins`: the inspected delivery-label, edit, inbox, inbox-state, reaction, reply policy files, `PeopleScreen.kt` and `PremiumChatsScreen.kt` are byte-identical to main. No duplicate port is needed.
- `feature/messaging-people-chat-inbox` and `feature/people-discovery-screen`: the inspected `PeopleScreen.kt` and `PremiumChatsScreen.kt` content is already present on main. Their older MainActivity changes are not to be copied over the newer SmartConnect activity.
- `integration/brand-logo-refresh`: the inspected launcher vector, logo vector, animated splash resource and icon-background colour resource are byte-identical to main. No logo rollback or duplicate port is needed.
- `feature/game-zone-ludo`: its Ludo engine and screen are older, smaller snapshots than the current main implementation. Do not merge this branch over the newer double-dice board and game logic.
- `feature/nearer-ui-polish`: its MainActivity and transport are an older product snapshot. Do not merge it wholesale over current messaging, SmartConnect and transfer work.
- `feature/fast-share-multiple-files`, `feature/fast-share-browser-upload`, `feature/fastshare-browser-upload` and `feature/fastshare-download-all-archive` are older, diverged transfer branches. Current main already has multi-file Fast Share, download-all ZIP and an explicit opt-in browser-upload flow. Compare any proposed isolated hardening against the current PUT implementation before porting.

These checks eliminate several duplicate/stale merge candidates. They do not yet constitute a full review of every branch in the 160+ branch inventory.


### Cross-screen palette completion — 14 November 2026

The audit found that Chats and People still had hard-coded blue surfaces and action accents even after the first theme pass. Those screens have now been aligned to the shared original palette too:

- Chats: conversation cards, incoming/outgoing bubbles, action surfaces, status accents, and muted text now use shared midnight/panel/raised-panel/mint/gold tokens. Semantic danger and success indicators remain distinguishable.
- People: contact cards, avatar backplates, buttons, and action labels now use the shared panel/brand-blue/mint tokens.
- Ludo: its outer controls and surfaces use the shared palette; the four traditional player colours, board markings, dice highlights and gameplay status colours remain distinct.
- The palette test now verifies that the main Activity tokens and shared design-system tokens remain identical.

The restored theme therefore covers the main shell, Chats, People, File Centre, Ludo, Android window bars and splash background. The logo's original brand-blue and mint artwork remains unchanged.


A deeper PR #4 Ludo check also confirmed that `LudoRoomProtocol`, `LudoRoomAuthority`, `LudoRoomEnvelopeCodec`, `LudoRoomStateCodec`, `LudoRoomSession`, `LudoSoundEffects`, `LudoTwoDiceRules` and `LudoAiEngine` are byte-identical between that draft branch and main. The draft's Ludo screen was an older visual snapshot; its colour differences are superseded by the shared-palette update. No Ludo protocol or game-engine merge is needed from PR #4.


### Continued consolidation audit — 14 November 2026

A further focused review checked trusted reconnect and the older Fast Share implementation branches against the current main-head source:

- `feature/trusted-peer-auto-reconnect` is one commit ahead but 61 commits behind main and changes only `MainActivity.kt`. Its auto-reconnect behavior is already present on main in a newer form: main delegates duplicate-request suppression and request lifecycle cleanup to `trustedPeerReconnectTracker`, clears the tracker through the transport request callback, and clears it when transport is unavailable. The old branch uses a mutable endpoint-ID set and direct request call. Do not merge or cherry-pick it; doing so would replace the newer tracker implementation with older state handling.
- `feature/fastshare-download-all-archive` contains an `FastShareArchiveWriter.kt` whose blob SHA is identical to main. The branch's download catalog, HTTP request parser and HTTP session are older snapshots than main. In particular, the old parser supports only GET/HEAD, while main has the explicit PUT upload route; main's session validates bounded HTTP headers, requires a declared Content-Length for PUT, rejects chunked bodies and rejects request bodies on download routes. No archive-writer port is needed and the older parser/session must not overwrite main.
- `feature/fast-share-browser-upload` also contains the same archive-writer blob as main. Its upload receiver is an older self-contained file-publication implementation; main instead separates bounded stream copying from the current authenticated HTTP session and publication/admission path. Its session/parser snapshots are older and do not include the current PUT route. Do not port these files wholesale.
- `feature/fast-share-multiple-files` has older catalog/request/session snapshots than main. Current main's catalog and HTTP server are further evolved; no unique safe improvement was established in this pass.
- The compare endpoint returned 404 for `feature/offline-folder-sharing`; this is an unavailable ref, not evidence that the feature is integrated or absent. Revisit only if the branch name/ref can be confirmed.

### Latest main-head CI lookup

- Re-queried combined commit status for `7a60a082c7b3def1c8988755a64441a28a0347aa`: no status checks were returned.
- Re-queried workflow runs for that commit. The available repository wrapper returned an empty list and is documented to return only pull-request-triggered runs, so it cannot confirm the push-triggered `main` workflow.
- Therefore exact-head JVM tests and debug APK assembly remain unverified for this latest main commit. Earlier passing PR-head runs recorded above do not validate later colour and integration commits.

### Consolidation decision

No code was copied from these candidates because the reviewed main implementations are newer or already contain the useful functionality. This is deliberate: avoiding a stale merge that silently regresses trusted reconnect or browser-upload security is more valuable than increasing the number of merged branches. Continue with source-level audits of remaining unique candidates, then obtain an exact-main-head CI result through a workflow-run source that includes push events. Relay stays disabled and no physical-device tests are claimed.


### HyperTransfer and mesh branch follow-up — 14 November 2026

- The older HyperTransfer development branches (`feature/hypertransfer-engine`, `feature/hypertransfer-nearby-stream-bridge`, `feature/hypertransfer-nearby-transport-final`, `feature/hypertransfer-nearby-transport-integration`, `feature/hypertransfer-stream-coordinator` and `feature/hypertransfer-validated-sender-stream`) are all deeply diverged historical stages. Main already contains the later STREAM transport, durable resume integration and subsequent concurrency/journal hardening. Do not merge these earlier stages over main.
- `fix/hypertransfer-receipt-retention-cleanup` has no unique transport change: its `NearbyConnectionsTransport.kt`, completion receipt codec and codec test blobs are identical to main.
- `fix/hypertransfer-recipient-completion-receipt` is an earlier snapshot. Its receipt handler removes the outgoing stream after a successful receipt but does not cancel and clear the retention timeout. Main now cancels `retentionTimeout` and clears the reference before removing the completed stream, preventing a stale timeout callback from acting on a completed transfer. Its completion receipt codec and test are already identical to main. Do not cherry-pick the older handler.
- Both `security/mesh-routing-foundation` and `backup/batch-one-mesh-before-main-integration` are 139 commits behind main with no commits ahead. No merge action is needed; their shared ancestry is already incorporated or superseded.

This review found no additional safe code to port. The current main implementations remain the source of truth for trusted reconnect, Fast Share upload admission, HyperTransfer STREAM lifecycle and mesh foundations.


### Share Hub phone-folder browser integration — 14 November 2026

A useful feature from draft PR #161 (`integration/phone-folder-browser`) was reviewed and selectively forward-ported into main rather than merging its old MainActivity snapshot.

Implemented on main:
- Added `PhoneFolderBrowser.kt`, using Android's Storage Access Framework. The user explicitly chooses a folder; NeaRer requests and persists read-only access only.
- Added folder navigation, current-folder search, multi-file selection across visited folders, image/video thumbnails on supported Android versions, and a bounded 2,000-entry listing with a visible truncation explanation.
- Added a Share Hub card that explains what the feature does and exposes the saved-folder permission's explicit “Forget saved folder access” action.
- Connected the selected document URIs to the existing `onSendFiles` pipeline. No separate transfer protocol or storage permission was introduced.
- Reused the original shared NeaRer palette tokens in the new browser. No brand colour or logo changes were made.
- Added feedback that distinguishes “files selected but no nearby peer is connected” from files submitted to the existing transfer queue.

Privacy and implementation notes:
- No broad storage permission is requested. The Android folder picker is the authority for access.
- Only selected file URIs are submitted; NeaRer does not recursively send an entire folder.
- Persisted read access is checked before reopening the saved folder and can be released by the user.
- The 2,000-entry limit is a display/listing safety bound, not a file-size or transfer-size cap.
- The feature is now present in main, but exact-head compilation, JVM tests and APK assembly have not yet been verified. No device test is claimed.

Related draft PRs #162 and #163 were not merged: their shared accent alias and Ludo palette-import changes are already resolved in main's current design-system references. Keep the original palette restoration as the source of truth.


### Additional transfer-reliability branch checks — 14 November 2026

- `feature/group-d-received-file-reliability`: the received-file catalog policy, received-file notification helper and resume stream writer blobs checked in this pass are identical to main. Its older MainActivity/transport snapshot is not a safe wholesale merge.
- `feature/fast-range-skip-performance`: `StreamRangeCopy.kt` is byte-identical to main; no port required.
- `feature/write-stall-watchdog-race`: both `SocketWriteStallGuard.kt` and its JVM test are byte-identical to main. The sibling `feature/slow-receiver-write-protection` contains an older guard implementation and must not overwrite the current race-hardened implementation.
- `feature/group-c-resume-integration-main` is an earlier, diverged HyperTransfer integration stage. Main already contains the newer stream bridge, coordinator and tests; do not merge the old stage wholesale.
- `fix/fast-share-header-safety`, `fix/fast-share-http-session-bounds`, `fix/incoming-file-metadata-peer-binding`, `fix/reject-unidentified-incoming-file-peer` and `fix/resume-incoming-files-after-reconnect` are historical diverged snapshots. Their affected files have moved substantially on main; do not cherry-pick whole files without a line-by-line security comparison.

These are selective source checks, not a claim that every repository branch has been exhaustively audited. The new phone-folder browser is a direct feature port with updated palette and current-main wiring; it is not a wholesale merge of PR #161.


### Palette compatibility correction — 14 November 2026

A final source check of draft PRs #162 and #163 found one real mainline omission that the earlier audit had incorrectly treated as already resolved: `NeaRerDesignSystem.kt` defined `NeaRerPalette.AccentBlue`, but did not expose the `BlueAccent` compatibility alias still referenced by separate Compose screens. The missing alias has now been added directly to main as `internal val BlueAccent = NeaRerPalette.AccentBlue`.

The Ludo screen's `NeaRerPalette` import and palette-token conversion from PR #162 are already present in main, so no duplicate Ludo change was copied. No palette values, gameplay behavior, navigation, or logo assets were changed by this compatibility fix. Exact-main-head JVM tests and APK assembly remain unverified because the available GitHub status/workflow lookup returned no records for the new commit.
