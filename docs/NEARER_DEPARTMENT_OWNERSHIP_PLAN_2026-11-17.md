# NeaRer Department and Package Ownership Plan — 17 November 2026

**Baseline:** `main` at `9cd557ce3cf1fb4de65a9985cf0ef1bdd42f0270`  
**Purpose:** define clear ownership for the current Android source tree before moving files. This is a target map, not a claim that these packages already exist.

## 1. Ownership principles

1. Every production class has one primary owning department.
2. UI belongs to the feature that presents it; domain policy belongs to the feature whose rules it enforces.
3. Platform adapters live at the edge and expose narrow contracts.
4. Shared core contains only genuinely cross-feature capabilities.
5. Core must not import feature packages. Features should not reach into another feature's implementation.
6. Keep the existing `:app` Gradle module while proving package boundaries. Package moves are reversible and lower-risk than premature Gradle modularisation.
7. Do not move a class based on its filename alone. Inspect imports, callers, persistence ownership and tests first.

## 2. Proposed package ownership

| Package | Owns | Existing source examples | Must not own |
|---|---|---|---|
| `app` | Application entry, Activity lifecycle, app composition, navigation root | `NeaRerApplication`, `MainActivity` (temporary composition root) | Feature business rules, protocol implementation, repository internals |
| `core.designsystem` | Shared theme tokens and reusable visual primitives | `NeaRerDesignSystem` | Feature-specific screen state or navigation |
| `core.security` | Cryptographic primitives, trusted identity, key binding and security contracts | `LocalDataCipher`, `SecurePeerIdentity`, `PeerSessionCrypto`, `RecipientEncryptionIdentity`, `TrustedPeerKeyStore` | UI, transport retries, feature navigation |
| `core.storage` | Shared durable-storage abstractions and file-system safety primitives | Shared storage contracts only after ownership review | Messaging/game/business-specific persistence rules |
| `core.nearby` | Android Nearby SDK adapter, discovery/connection lifecycle and platform permissions | `NearbyTransportService`, SDK-facing part of `NearbyConnectionsTransport`, `NearbyPermissionPolicy` | Message business rules, mesh route selection, transfer resume policy |
| `core.transfer` | Reusable transfer contracts, stream/range copy, integrity and checkpoint primitives | `ByteRange`, `ChunkedStreamCopy`, `FileTransferIntegrity`, common resume primitives | FastShare UI or Messaging acknowledgement policy |
| `feature.messaging` | Direct chat, outbox/inbox, delivery/read receipts, voice notes, chat notification policy | `PremiumChatsScreen`, `DirectChatOutbox`, `Chat*Policy`, `VoiceNoteComposer` | Nearby SDK lifecycle or community roster ownership |
| `feature.communities` | Community roster, group delivery and community receipts | `Community*Policy` and community state components currently embedded in app coordinator | Generic direct-chat persistence or transport SDK |
| `feature.mesh` | Mesh route, forwarding proof, hop acknowledgement and relay policy | `MeshRouteTable`, `MeshForwardingProofChain`, `MeshRelayPolicy`, `MeshReceiptOutbox` | Direct chat UI or Nearby SDK connection lifecycle |
| `feature.files` | File Centre, local file catalogue, app share and file browser UI | `FileCentre`, `FileHubWorkspaceScreen`, `PhoneFolderBrowser`, `AppShareCatalog`, `AppShareInstaller` | Nearby transport lifecycle or HTTP protocol internals |
| `feature.transfer` | HyperTransfer coordination, transfer queues, resume and transfer metrics | `TransferResumeCoordinator`, `TransferResumeStore`, `SequentialTransferQueue`, `HyperTransferTuning` | General-purpose file browsing or chat read receipts |
| `feature.opensend` | OpenSend hotspot, HTTP server/session, QR and local web transfer | `OpenSendForegroundService`, `OpenSendHttpServer`, `OpenSendSessionManager`, `FastShareController` | Nearby Connections SDK adapter |
| `feature.games.ludo` | Ludo rules, AI, room authority/session, codecs and game UI | Existing `games.ludo.*` package | Generic chat, transfer or app-wide navigation |
| `feature.studio.audio` | Audio recording engine, projects, tracks, effects and audio UI | `StudioOboeEngine`, `StudioAudioProjectLibrary`, `StudioAudioMixerScreen`, `StudioPitchCorrectionProcessor` | Generic file transfer or global design tokens |
| `feature.studio.video` | Video editing, timeline, captions, grading and transitions | `StudioVideoTimelineScreen`, `StudioVideoProjectCodec`, `StudioVideoTransitionPlanner` | Audio engine internals or movie library ownership |
| `feature.movies` | Movie library and playback surfaces | `MoviesWorkspaceScreen`, `MoviesLibraryCodec` | Video editor project state |
| `feature.business` | Business listings, drafts and business media | `BusinessCenterWorkspaceScreen`, `BusinessDraftCodec`, `BusinessPhotoStorage` | General file browser or payment backend |
| `feature.bible` | Bible reading, study, academy and library content | `LibraryReaderScreens`, `LibraryWorkspaceScreen`, `AcademyWorkspaceScreen` | Global storage implementation |
| `feature.flash` | Flash signalling and call-screening capability | `FlashActivity`, `FlashCallScreeningService`, `FlashSignalPattern` | Direct Messaging transport |

Some classes intentionally span more than one concern. For example, `NearbyConnectionsTransport` currently owns Nearby SDK callbacks plus message delivery, mesh and HyperTransfer paths. It must be decomposed behind contracts, not assigned wholesale to one package.

## 3. Important boundary decisions

### Messaging vs Nearby vs Mesh
- Messaging owns message identity, conversation policy, outbox/inbox semantics and delivery receipts.
- Nearby owns radio discovery, endpoint connection and payload delivery adapter.
- Mesh owns route selection, forwarding proof and hop receipts.
- A message may travel over Nearby or mesh, but neither transport should own chat UI or conversation business rules.

### File Centre vs HyperTransfer vs OpenSend
- File Centre owns file selection, browsing, catalogue and user-facing file operations.
- Transfer owns durable transfer jobs, checkpoints, stream integrity and resume semantics.
- OpenSend owns its local hotspot/HTTP session and upload/download protocol.
- Shared stream-copy and file safety primitives belong in core only when multiple owners use them without feature-specific assumptions.

### Studio Audio vs Studio Video vs Movies
- Studio Audio owns audio capture, audio DSP, audio project and mix/export.
- Studio Video owns video timeline, caption, grade and transition editing.
- Movies owns playback/library experience.
- Shared media primitives must be isolated from feature-specific project formats and editing state.

## 4. Dependency direction

Target direction:

```text
app (composition + navigation)
  ├── feature.*
  └── core.*

feature.* ───────> core.*
core.* ──────────> Android / Kotlin platform APIs
core.* -X-> feature.*
feature A -X-> feature B implementation
```

Feature-to-feature interaction should use app-level composition or narrow contracts. Do not make `core` a dumping ground for code that simply has many callers.

## 5. First safe migration sequence

1. Create a package inventory and compile/import graph for each proposed owner.
2. Move leaf-level pure policy/codec classes together with their tests; do not alter their algorithms.
3. Move design-system primitives separately from screens.
4. Extract UI-only screens and previews without changing state ownership.
5. Define contracts around Nearby lifecycle, Messaging delivery, Mesh routing and HyperTransfer.
6. Extract stateful engines one at a time with persistence compatibility tests.
7. Add dependency checks to prevent core-to-feature and feature-to-feature cycles.
8. Consider Gradle modules only after package boundaries, test seams and dependency rules remain stable.

## 6. Not yet authorised by this map

- No package moves have been made by this document.
- No Gradle module has been added.
- No persistence schema, packet format, encryption, permission, lifecycle or delivery semantics may change as part of a package-only move.
- No feature may be declared complete based on its target package existing.
