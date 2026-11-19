# NeaRer Department and Package Ownership Plan — 17 November 2026

**Baseline:** `main` at `71268ba3ddeb3002a54711e53cd6c74966633332`  
**Purpose:** define safe technical ownership for the current Android source tree while preserving NeaRer's approved Messaging-first product architecture. This is a target map, not a claim that these packages already exist.

## 1. Product hierarchy and ownership principles

NeaRer's product architecture is **Messaging-first**. Messaging is the central product experience and the reason the other spaces belong together. The approved product blueprint in `docs/PRODUCT_ARCHITECTURE.md` is authoritative for user-facing hierarchy and navigation.

- **Chats is the default and central experience.** Conversations, identity, contact discovery and communication are the app's primary purpose.
- **Share extends communication.** Lightweight attachments belong in conversations; substantial send/receive workflows live in the dedicated Share destination and return naturally to Chats.
- **Games extends social interaction.** Invitations, game chat and results connect back to conversations; Games is not a separate social network.
- **Communities support communication and group participation.** They must not become a competing messaging system or fork direct-chat delivery semantics.
- **Other workspaces** (Studio, Academy, Library, Business, Movies/Video and Flash) are supporting destinations reached through the app shell. Their technical ownership does not make them equal product pillars to Messaging.
- **Nearby and Mesh are infrastructure, not product destinations.** They enable message and transfer delivery; users should not need to manage radio technologies to chat.

The three primary destinations in the approved product blueprint are Chats, Share and Games, in that order. Other workspaces remain secondary. Do not infer product hierarchy from package names or file counts.

Technical ownership rules:

1. Every production class has one primary technical owner.
2. UI belongs to the feature that presents it; domain policy belongs to the feature whose rules it enforces.
3. Platform adapters live at the edge and expose narrow contracts.
4. Shared core contains only genuinely cross-feature capabilities.
5. Core must not import feature packages. Features should not reach into another feature's implementation.
6. Keep the existing `:app` Gradle module while proving package boundaries. Package moves are reversible and lower-risk than premature Gradle modularisation.
7. Do not move a class based on its filename alone. Inspect imports, callers, persistence ownership and tests first.

## 2. Proposed technical package ownership

These packages describe code responsibility, **not equal product status**. `feature.messaging` is the central product domain; Share and Games are first-class extensions of that experience, while the remaining workspaces are supporting destinations.

| Package | Owns | Existing source examples | Must not own |
|---|---|---|---|
| `app` | Application entry, Activity lifecycle, app composition, navigation root | `NeaRerApplication`, `MainActivity` (temporary composition root) | Feature business rules, protocol implementation, repository internals |
| `core.designsystem` | Shared theme tokens and reusable visual primitives | `NeaRerDesignSystem` | Feature-specific screen state or navigation |
| `core.security` | Cryptographic primitives, trusted identity, key binding and security contracts | `LocalDataCipher`, `SecurePeerIdentity`, `PeerSessionCrypto`, `RecipientEncryptionIdentity`, `TrustedPeerKeyStore` | UI, transport retries, feature navigation |
| `core.storage` | Shared durable-storage abstractions and file-system safety primitives | Shared storage contracts only after ownership review | Messaging/game/business-specific persistence rules |
| `core.nearby` | Android Nearby SDK adapter, discovery/connection lifecycle and platform permissions | `NearbyTransportService`, SDK-facing part of `NearbyConnectionsTransport`, `NearbyPermissionPolicy` | Message business rules, mesh route selection, transfer resume policy |
| `core.transfer` | Reusable transfer contracts, stream/range copy, integrity and checkpoint primitives | `ByteRange`, `ChunkedStreamCopy`, `FileTransferIntegrity`, common resume primitives | FastShare UI or Messaging acknowledgement policy |
| `feature.messaging` | NeaRer's central communication domain: conversations, direct/group chat experience, message identity, outbox/inbox semantics, delivery/read receipts, voice notes and chat notification policy | `PremiumChatsScreen`, `DirectChatOutbox`, `Chat*Policy`, `VoiceNoteComposer` | Nearby SDK lifecycle, radio management, generic file-transfer engine or community roster implementation |
| `feature.communities` | Community membership, roster and community-specific coordination, integrated with the central Messaging experience | `Community*Policy` and community state components currently embedded in app coordinator | A competing direct-message system, duplicated conversation/outbox semantics or transport SDK |
| `feature.mesh` | Mesh route, forwarding proof, hop acknowledgement and relay policy | `MeshRouteTable`, `MeshForwardingProofChain`, `MeshRelayPolicy`, `MeshReceiptOutbox` | Direct chat UI or Nearby SDK connection lifecycle |
| `feature.files` | File Centre, local file catalogue, app share and file browser UI | `FileCentre`, `FileHubWorkspaceScreen`, `PhoneFolderBrowser`, `AppShareCatalog`, `AppShareInstaller` | Nearby transport lifecycle or HTTP protocol internals |
| `feature.transfer` | HyperTransfer coordination, transfer queues, resume and transfer metrics | `TransferResumeCoordinator`, `TransferResumeStore`, `SequentialTransferQueue`, `HyperTransferTuning` | General-purpose file browsing or chat read receipts |
| `feature.opensend` | OpenSend hotspot, HTTP server/session, QR and local web transfer | `OpenSendForegroundService`, `OpenSendHttpServer`, `OpenSendSessionManager`, `FastShareController` | Nearby Connections SDK adapter |
| `feature.games.ludo` | Ludo rules, AI, room authority/session, codecs and game UI; game invitations, in-game communication and results integrate with Messaging through contracts | Existing `games.ludo.*` package | A separate social graph or duplicated general-purpose chat system |
| `feature.studio.audio` | Audio recording engine, projects, tracks, effects and audio UI | `StudioOboeEngine`, `StudioAudioProjectLibrary`, `StudioAudioMixerScreen`, `StudioPitchCorrectionProcessor` | Generic file transfer or global design tokens |
| `feature.studio.video` | Video editing, timeline, captions, grading and transitions | `StudioVideoTimelineScreen`, `StudioVideoProjectCodec`, `StudioVideoTransitionPlanner` | Audio engine internals or movie library ownership |
| `feature.movies` | Movie library and playback surfaces | `MoviesWorkspaceScreen`, `MoviesLibraryCodec` | Video editor project state |
| `feature.business` | Business listings, drafts and business media | `BusinessCenterWorkspaceScreen`, `BusinessDraftCodec`, `BusinessPhotoStorage` | General file browser or payment backend |
| `feature.bible` | Bible reading, study, academy and library content | `LibraryReaderScreens`, `LibraryWorkspaceScreen`, `AcademyWorkspaceScreen` | Global storage implementation |
| `feature.flash` | Flash signalling and call-screening capability | `FlashActivity`, `FlashCallScreeningService`, `FlashSignalPattern` | Direct Messaging transport |

Some classes intentionally span more than one concern. For example, `NearbyConnectionsTransport` currently owns Nearby SDK callbacks plus message delivery, mesh and HyperTransfer paths. It must be decomposed behind contracts, not assigned wholesale to one package.

## 3. Important boundary decisions

### Messaging is the product core; Nearby and Mesh are delivery infrastructure
- Messaging owns the user-facing communication experience and its domain guarantees: conversations, message identity, direct/group chat policy, durable outbox/inbox semantics, message-level delivery receipts and chat interaction state.
- Nearby owns radio discovery, endpoint connection, permission-aware lifecycle and the platform payload-delivery adapter. It must not decide who a conversation is with or what a delivered message means.
- Mesh owns route selection, forwarding proofs, hop acknowledgements and relay policy. It is a delivery mechanism beneath Messaging, not a separate user-facing communication product.
- The app composition layer wires Messaging to available delivery mechanisms. Messaging must not depend directly on Android radio SDK details, and Nearby/Mesh must not own chat UI or conversation business rules.
- A route or first-hop acknowledgement is not a message delivery receipt. Preserve the existing authenticated end-to-end message and durable recipient-acknowledgement semantics.

### Share and Games extend Messaging
- Share owns the dedicated high-capability file-transfer workspace, while chat attachments remain part of conversations. Both use shared transfer infrastructure without making the Messaging domain depend on File Centre UI.
- Games owns game rules and sessions. Invitations, in-game chat where implemented, and results should connect to existing conversations through narrow app-level contracts; do not create a parallel social identity or messaging stack.
- Communities owns membership and community-specific coordination, while respecting Messaging's central role and avoiding duplicated direct-chat delivery semantics.

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

The product hierarchy and code dependency graph are related but not identical. Messaging is the product core; this does **not** mean every feature should import Messaging implementation classes. Integrations use narrow contracts owned by the relevant domain and wired by the app composition root.

Target direction:

```text
app (product shell, navigation and composition)
  ├── feature.messaging  (central product experience)
  ├── feature.share / feature.files / feature.transfer
  ├── feature.games / feature.communities
  ├── supporting workspaces (studio, bible, business, media, flash)
  └── core.* (shared technical capabilities)

feature.* ───────> core.* and its own domain
app ─────────────> feature contracts (composition/integration)
core.* ──────────> Android / Kotlin platform APIs
core.* -X-> feature.*
feature A -X-> feature B implementation
```

Cross-feature journeys (for example, inviting a chat contact to a game or sending a large file from a conversation) are coordinated through app-level composition or explicit narrow contracts. Do not make `core` a dumping ground, and do not turn Messaging into a dependency for unrelated implementation details.

## 5. First safe migration sequence

1. Create a package inventory and compile/import graph for each proposed owner.
2. Move leaf-level pure policy/codec classes together with their tests; do not alter their algorithms.
3. Move design-system primitives separately from screens.
4. Extract UI-only screens and previews without changing state ownership.
5. Define contracts around Nearby lifecycle, Messaging delivery, Mesh routing and HyperTransfer.
6. Extract stateful engines one at a time with persistence compatibility tests.
7. Add dependency checks to prevent core-to-feature and feature-to-feature cycles.
8. Consider Gradle modules only after package boundaries, test seams and dependency rules remain stable.

## 6. Product hierarchy acceptance checks

- Chats remains the default launch destination and the central communication experience.
- Share remains a dedicated extension for substantial transfers; chat attachments and large transfers have clear, non-duplicated responsibilities.
- Games remains socially connected to existing conversations rather than creating a competing social network.
- Mesh and radio technologies remain beneath the user experience, not separate primary destinations.
- Supporting workspaces do not displace or fragment Messaging's central role.
- No package move may change message identity, authentication, encryption, persistence, deduplication, acknowledgement meaning, retry or reconnect behaviour.

## 7. Not yet authorised by this map

- No package moves have been made by this document.
- No Gradle module has been added.
- No persistence schema, packet format, encryption, permission, lifecycle or delivery semantics may change as part of a package-only move.
- No feature may be declared complete based on its target package existing.
