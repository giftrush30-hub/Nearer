# Messaging Integration Register

Last reviewed: 2026-11-17
Repository: `giftrush30-hub/Nearer`

## Integration rule

`main` is the only source of truth. Feature branches are evidence and work queues, not parallel product truth. Never merge a stale branch wholesale just because its title sounds complete. Compare its file-level changes with current `main`, preserve already-merged functionality, port only genuinely missing behavior, and test the combined implementation.

Substantial functional changes should land through a reviewed pull request after JVM tests, Android manifest processing, and the relevant regression checks pass. APK assembly is opt-in and remains disabled unless explicitly requested.

## Repository inventory

- 269 branches were enumerated across all three GitHub branch-list pages.
- 65 branch names matched Messaging, identity, nearby connection, mesh/community, profile, audio, video, Bible, or game keywords.
- This is a branch-name and targeted compare audit, not a claim that every line in all 269 branches has been manually reviewed.
- The Messaging-focused candidates below were compared against `main`; high-conflict stale branches are not merge candidates without selective porting.

## Already represented in `main`

| Area | Evidence / status | Integration decision |
|---|---|---|
| People and Chats experience | PR #133 merged | Keep current People/Chats implementation; do not re-import the old branch. |
| Trusted-peer reconnect | PR #136 merged | Reuse current reconnect tracker and pinned-key checks. |
| Nearby permission policy | PR #134 merged | Keep `NearbyPermissionPolicy`; Android 8–15 behavior is already in main. |
| Group B discovery/reconnect hardening | PR #145 merged | Reuse current discovery generation, timing, and reconnect policies. |
| Restart delivery-state recovery | PR #184 merged | `ChatMessageRecoveryPolicy` / `ChatRestartRecoveryPolicy` already exist in main. |
| Messaging actions and searchable inbox | PR #150 merged | `ChatInboxPolicy`, reply/reaction/edit/delivery-label policies already exist in main. PR #140 is closed and must not be remerged. |
| Forwarding and voice-note metadata foundations | PR #153 merged | `ChatForwardPolicy` and `VoiceNotePolicy` already exist in main; verify user-facing recording/playback integration separately before calling voice notes complete. |
| Chat history bounding and metadata persistence | Existing policy files are present in main | Do not import old `fix/bounded-chat-history-v1` or metadata-only branches wholesale; compare any new behavior first. |
| Message ID history and retry policy | `MessageIdHistoryPolicy` and `MessageRetryPolicy` exist in main | Preserve these as the baseline. The current service branch adds peer-scoped dedupe rather than replacing the existing policy. |

## Current service-owned Nearby module

Working branch: `work/messaging-service-owned-nearby`.

Current implementation includes:

- `NearbyTransportService` owns the Nearby Connections transport independently of the Activity.
- `MainActivity` binds as a UI client and reattaches listeners rather than constructing a second transport.
- `NearbyIncomingMessageInbox` durably stages incoming direct and mesh messages before acknowledgement.
- Inbox entries are removed only after the existing chat/community persistence callback succeeds and the corresponding dedupe/receipt commit is durable.
- Incoming dedupe is scoped by authenticated peer ID plus message ID, with a compatibility lookup for legacy message-history entries.
- Mesh final delivery receipt recovery reuses the exact persisted signed receipt and can recover from a packet being retired after the inbox commit.
- File and HyperTransfer callbacks are forwarded through the service listener, so the service refactor does not create a second file-transfer transport.
- Service restart discovery is gated by onboarding, required runtime permissions, available Bluetooth/Wi-Fi, and legacy Android location-service readiness.
- Activity-side service startup exceptions are caught and surfaced as status text.

Current branch adds `NearbyServiceStartPolicy` and JVM tests for the readiness gate. The service entry is registered in `AndroidManifest.xml` with the connected-device foreground-service type and permission.

### What this module does not prove yet

- No physical two-phone test has been performed by this automation.
- OEM process-kill, Android force-stop, battery-optimization, Bluetooth/Wi-Fi toggle, and permission-revocation behavior still require device validation.
- It does not claim Signal Double Ratchet or WhatsApp-level forward secrecy. Existing authenticated ECDH/AES-GCM behavior needs a separate cryptographic audit before stronger claims are made.
- Outgoing outbox restart/retry and acknowledgement reconciliation remain a separate substantial Messaging batch, even though the existing durable outbox is reused by the service-owned transport.

## Outstanding branches and pull requests to selectively port

| Branch / PR | Findings | Planned handling |
|---|---|---|
| `feature/messaging-a-sync` / PR #216 (open draft) | Adds direct read-receipt and typing policies, plus community receipt work; it is significantly behind current main. | Port as one coherent delivery/presence feature after service transport is integrated. Reconcile direct and community receipt semantics; typing hints must remain ephemeral and outside the durable outbox. |
| `feature/chat-background-notifications` / PR #210 (open draft) | Adds notification policy and UI wiring; it also contains read-receipt code overlapping PR #216. | Do not merge independently. Consolidate notification wiring with the read-receipt/presence port, remove duplicate policy definitions, and ensure alerts fire only after durable message persistence. |
| `feature/messaging-inbox-search-pins` / PR #140 (closed unmerged) | Contains older inbox/search/pin and message-action code. Most of its policy files are already in main through the later forward-port. | Treat as historical reference only; selectively recover only any UI behavior proven absent from main. |
| `feature/messaging-forwarding-voice-foundations` / PR #153 (merged) | The policy foundations are present in main. | Do not re-import. Audit the actual voice-note recorder, file payload, playback and permission flow as a separate feature-completion check. |
| `fix/chat-retry-while-peer-remains-connected` and `improve/chat-retry-backoff-v1` | Older retry policy and transport variants. | Compare algorithm and backoff behavior against main `MessageRetryPolicy`; port only a demonstrated defect fix. |
| `fix/durable-chat-dedupe-v1` | Older message-history dedupe implementation. | Do not replace peer-scoped transport dedupe with a global message-ID key. Check compatibility with `MessageIdHistoryPolicy`. |
| `fix/save-chat-before-send-v1/v2` | Older MainActivity-only changes. | Verify current send path persists before dispatch; no wholesale cherry-pick. |
| `feature/messaging-service-owned-nearby` / PR #242 (closed draft) | Earlier service-owned transport attempt. | Historical predecessor; do not reopen or merge. |
| `feature/messaging-service-owned-nearby-v2` / PR #243 (open draft) | Stale service-owned transport proposal with earlier implementation and APK-producing validation. | Supersede with the current branch's reviewed PR; close the stale draft once replacement PR is open. |
| `feature/messaging-onboarding-port`, `feature/chat-share-product-upgrade`, `feature/people-discovery-screen`, `feature/premium-onboarding-identity` | Older identity/onboarding/people UI variants. | Check against current onboarding/profile screens; coordinate with Profiles rather than creating duplicate identity or people stores. |
| `security/require-encrypted-nearby-chat` | Earlier policy for disabling legacy unencrypted socket chat. | Verify current transport paths and remove any remaining plaintext fallback only with regression coverage. |
| `security/batch-two-identity-validation`, `security/community-identity-roster`, `security/mesh-routing-foundation` | Cross-cutting identity, mesh proof and community identity changes; some branches are very far behind and touch multiple departments. | Review by security/community owners and port only missing, compatible invariants. Do not merge these branches wholesale into Messaging. |
| `identity/phone-free-nearer-id` | Large, older branch spanning identity, outbox, transport, file transfer, key stores and file centre. | Use as a reference for any missing identity/outbox behavior; reconcile file-by-file because it overlaps many newer modules. |

## Cross-department interfaces

- **Profiles / onboarding:** `nearer_local` identity and onboarding preferences are shared. Messaging must use the existing stable NeaRer identity and pinned key stores; it must not invent a second profile or phone-number identity.
- **Notifications:** notification policy consumes persisted incoming message events. It must not notify for duplicates, edits, reactions, failed inbox commits, or messages while the chat is actively visible.
- **Communities / mesh:** community messages use the same service-owned transport but have separate durable receipt/fanout stores. Direct-chat peer IDs and community member IDs must not be conflated.
- **HyperTransfer / Share:** file payload callbacks are forwarded by `NearbyTransportService`; preserve the existing transfer controller, resumable transfer journal, and file centre rather than adding a competing transfer engine.
- **Audio / Video:** voice notes should be ordinary bounded audio-file payloads with metadata and playback UI; they must not create a separate network transport. Future call/media sessions need their own permission and foreground-service design.
- **Bible / Games / Business Center:** no second messaging store or direct transport should be introduced by these departments. Any future chat surface should use the shared conversation and transport interfaces.

## Research and platform constraints

- Google Nearby Connections is a peer-to-peer API that can discover, connect and exchange data without internet; it abstracts Bluetooth, BLE and Wi-Fi transport selection: https://developers.google.com/nearby/connections/overview
- Android 14+ requires a declared foreground-service type and the relevant permission/runtime prerequisites. Connected-device foreground services require one of the documented network/Bluetooth/NFC/USB conditions: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Android documents foreground-service launch failures when required permissions or runtime prerequisites are missing: https://developer.android.com/develop/background-work/services/fgs/troubleshooting
- WorkManager is the recommended API for persistent deferrable work, but it is not a replacement for a live Nearby connection; it can support independent cleanup/reconciliation tasks when appropriate: https://developer.android.com/reference/androidx/work/WorkManager.html
- WhatsApp multi-device engineering is a useful model for not making the Activity the source of truth: https://engineering.fb.com/2021/07/14/security/whatsapp-multi-device/
- Signal's Double Ratchet is a separate cryptographic protocol. NeaRer must not claim equivalent forward secrecy or post-compromise recovery until such a protocol is correctly integrated and audited: https://signal.org/docs/specifications/doubleratchet/

## Continuous integration policy

- PR and push validation runs JVM unit tests plus Android manifest processing.
- The default validation workflow does not run `assembleDebug` or upload APK artifacts.
- APK generation is an explicit `workflow_dispatch` option and defaults to false.
- Passing JVM/manifest checks is not a substitute for physical device validation.

## Recent integration

- PR #258, “Make nearby messaging service-owned with durable incoming recovery”, was merged to `main` on 2026-11-17 as `02a7a2be138d4c8c06ea5ae4395482e94e9a2921`.
- The merged module includes service-owned Nearby transport, encrypted durable incoming inbox, peer-scoped dedupe, mesh final-receipt recovery, lifecycle readiness gates and no-APK CI validation.
- PR checks passed: JVM tests, manifest processing and diagnostic Android lint. APK build/upload steps were skipped.
- The diagnostic lint report recorded 110 project-wide errors and 64 warnings, with the first blocking issues in existing MainActivity permission calls, OpenSendHotspot, FlashActivity, StudioAudioWorkspaceScreen and manifest location declarations. The new inbox/service contributed two `ApplySharedPref` warnings because synchronous commits are required before ACK/service activation; those two intentional commits are now explicitly documented and suppressed in the outgoing-recovery branch.
- The original service branch was reset to the merged `main` commit; the next work branch is based on that same commit.

## Next integration sequence

1. Complete outgoing delivery ACK identity validation, endpoint-disconnect cleanup, and reconnect recovery on `work/messaging-outgoing-recovery`.
2. Validate and merge that outgoing-reliability module through a focused PR.
3. Port read receipts, typing hints and private background notifications as one coherent delivery/presence feature, reconciling PR #216 and #210.
4. Re-audit voice-note end-to-end functionality and inbox UI completeness without duplicating policies already in main.
5. Continue the same selective branch-recovery process for identity, community, HyperTransfer and other cross-department features.
