# NeaRer Source Ownership Map — 17 November 2026

**Baseline:** `main` at `9cd557ce3cf1fb4de65a9985cf0ef1bdd42f0270`  
**Purpose:** current responsibility map for safe organisation work. Line ranges are approximate navigation aids; declarations and call sites must be rechecked before each extraction.

## 1. MainActivity.kt

**Current size:** 5,655 lines, approximately 323 KB.  
**Role it should ultimately own:** Android Activity lifecycle, app composition, permission launchers, service binding and top-level navigation. It currently owns substantially more.

| Approx. lines | Current responsibility | Intended owner / boundary | Risk and guardrail |
|---|---|---|---|
| 129–753 | Activity service connection callbacks; nearby discovery, connection, message, mesh and file event callbacks | Thin app event adapter + feature event handlers | Preserve callback order, acknowledgement timing and UI-thread handoff |
| 754–1238 | Activity creation, permission result, resume/pause/destroy, foreground-service binding, state reconciliation and always-ready discovery | App lifecycle coordinator + Nearby service boundary | Keep Android lifecycle and permission launchers at Activity edge; do not duplicate service ownership |
| 1239–1715 | Profile edits, nearby identity/presence, permission helpers, discovery, Wi-Fi Direct peer connect and approval socket lifecycle | Profile/presence repository; Nearby discovery coordinator; isolated legacy file-transfer adapter | Do not revive plaintext legacy chat/community/game transport; preserve permission and radio fallback behaviour |
| 1716–1861 | Restore local state, received-file cataloguing and nearby transfer history | Feature repositories / local data sources | Preserve durable writes and received-file visibility |
| 1862–2468 | Direct chat edits, reactions, receipts, typing, delete, history, persistence and send path | Messaging domain policies + repository + conversation coordinator | Preserve authenticated sender authority, dedupe and durable ACK boundaries |
| 2469–3097 | Community persistence, roster sync, group delivery, packet ingress and group messaging | Community coordinator/repository | Preserve verified stable peer identity and per-recipient delivery receipts |
| 3098–3337 | Ludo invite/session state, moves, reconnect, rematch and in-game chat | Games/Ludo session coordinator | Keep game packets on authenticated transport; preserve reconnect/state validation |
| 3338–4185 | App-bundle install, external share intents, open/forward/save files, folder enumeration, send queues and legacy socket reader | App-share boundary + File/Transfer feature coordinators | Preserve URI grants, path safety, hashes and rule that unauthenticated socket ACKs never mark chat delivered |
| 4186–5414 | `NeaRerHome` Compose screen and related home UI | Feature screen packages + navigation host | Move composables with state contracts intact; no giant UI rewrite |
| 5415–approximately 5525 | `CommunityHub` Compose UI | Community UI package | Keep rendering separate from group persistence/transport |
| Remaining tail | Small models, formatters, Activity/helper components | Feature model/util packages or Android component files | Check whether earlier pure helpers are already extracted before duplicating |

### MainActivity extraction order

1. Confirm any pure helper still embedded and not already extracted by PR #234/#250.
2. Move UI-only composables with no lifecycle or transport ownership.
3. Move repository/persistence slices only after tests define durable-write semantics.
4. Move feature coordinators behind interfaces while MainActivity remains the composition root.
5. Keep permission launchers, Activity result APIs, service binding and navigation composition at the Android edge until a proven replacement exists.

## 2. NearbyConnectionsTransport.kt

**Current size:** 3,298 lines, approximately 182 KB.  
**Role it should ultimately own:** adapter to Google Nearby Connections plus a narrow transport-facing API. It currently contains multiple domain engines.

| Approx. lines | Current responsibility | Intended owner / boundary | Risk and guardrail |
|---|---|---|---|
| 20–323 | Listener contract, identity/session state, peer and transfer state models | Transport API + narrow state models | Keep public callbacks stable during extraction |
| 324–594 | Durable outgoing message outbox, retry, dedupe and ACK tracking | Message delivery engine / durable outbox | Preserve encrypted persistence, stable message IDs and authenticated peer binding |
| 595–864 | Nearby SDK discovery, connection and payload callbacks | Nearby SDK adapter | Keep SDK callback mapping thin; preserve endpoint-generation checks |
| 865–1110 | Peer hello/proof, identity binding and authenticated session completion | Peer authentication/session manager | Highest security sensitivity; no format change during structural extraction |
| 1111–1833 | Mesh route advertisements/withdrawals, route table, hop ACKs, receipts, encrypted packet delivery and forwarding | Mesh routing engine | Relay remains disabled; preserve replay protection, receipt ordering and bounded forwarding |
| 1834–2116 | HyperTransfer control, completion receipts, stream metadata/proposals, retry, resume and stream processing | HyperTransfer session coordinator | Preserve authenticated recipient binding, checkpoint ownership and fallback rules |
| 2117–2392 | Control/data frame dispatch, authenticated message processing, file finalization, digest and safe filename | Protocol dispatcher + message/file receive pipelines | Preserve wire compatibility, integrity validation and durable ACK ordering |
| 2393–3298 | Advertising/start-stop, identity/peer snapshots, inbox dedupe/ack recovery and public send/receive APIs | Nearby lifecycle adapter + narrow public facade | Avoid moving SDK lifecycle and durable message recovery as one untested batch |

### Transport extraction order

1. Reconfirm existing extracted helpers and tests before doing any work.
2. Prefer small pure codecs/formatters/policies that do not own session state.
3. Extract file finalization or transfer metadata policy only where the complete call graph and existing integrity tests are clear.
4. Define internal interfaces for authentication, message delivery, HyperTransfer and mesh without changing packet formats.
5. Extract mesh routing last; keep relay disabled until security review and multi-device acceptance.

## 3. Other high-concentration files to map next

| File | Current size (approx.) | Likely ownership concern |
|---|---:|---|
| FileCentre.kt | 66 KB | File catalog, SAF/provider access, selection, copy/move and UI/state boundaries |
| PremiumChatsScreen.kt | 59 KB | Conversation UI, message actions, state and presentation separation |
| games/ludo/LudoGameScreen.kt | 58 KB | Game rendering, rules/state, input and multiplayer UI boundaries |
| FastShareController.kt | 48 KB | HTTP session lifecycle, authorization, upload/download and transfer coordination |
| StudioAudioWorkspaceScreen.kt | 44 KB | Compose UI vs recording engine, routing, persistence and export |
| StudioVideoTimelineScreen.kt | 38 KB | Timeline UI vs media model/rendering |
| LudoGamePanel.kt | 38 KB | Repeated game UI/state ownership with Ludo screen |
| BusinessCenterWorkspaceScreen.kt | 34 KB | Catalogue UI vs persistence and media handling |
| MoviesWorkspaceScreen.kt | 29 KB | Library UI vs Media3 playback and provider state |

These are candidates for inspection, not pre-approved refactors. The owning feature workstream must be preserved.

## 4. Non-negotiable refactoring guardrails

- No protocol or packet-format changes bundled with structural refactoring.
- No message ACK before the recipient's durable commit.
- No unauthenticated legacy socket chat, community or game frames.
- No mesh relay enablement as a side effect of extraction.
- No removal of transfer checkpoints, integrity checks or safe filename handling.
- No migration of stored data without explicit backward-compatibility tests.
- Every moved responsibility keeps or gains focused tests.
- Run JVM tests, manifest validation, lint diagnostics and APK assembly on the exact PR head.
- Record physical-device verification separately from automated checks.
- Do not create duplicate helpers already present on main or another integration branch.

## 5. Current package direction

The immediate step is package organisation within the existing `:app` module:

- `com.nearer.app.core.common`
- `com.nearer.app.core.designsystem`
- `com.nearer.app.core.security`
- `com.nearer.app.core.storage`
- `com.nearer.app.core.nearby`
- `com.nearer.app.core.transfer`
- `com.nearer.app.feature.messaging`
- `com.nearer.app.feature.communities`
- `com.nearer.app.feature.games.ludo`
- `com.nearer.app.feature.files`
- `com.nearer.app.feature.business`
- `com.nearer.app.feature.studio`
- `com.nearer.app.feature.bible`
- `com.nearer.app.feature.movies`

Package names are a proposed destination, not an instruction to move everything immediately. Keep shared models in core only when they are truly feature-neutral. Do not allow core to import feature packages.

## 6. Next source audit

Before the first production-code move, map these call graphs and test ownership:
1. MainActivity -> NearbyTransportService -> NearbyConnectionsTransport lifecycle and listener registration.
2. MainActivity -> chat repositories/outbox/inbox -> authenticated delivery acknowledgement.
3. MainActivity -> community roster and per-recipient delivery receipt persistence.
4. MainActivity -> file queue -> Nearby/HyperTransfer/legacy transfer adapter.
5. NearbyConnectionsTransport -> peer handshake -> message outbox and incoming durable inbox.
6. NearbyConnectionsTransport -> HyperTransfer resume coordinator -> sender/receiver journals.

The first extraction should be selected only after the call graph and exact test suite are known.
