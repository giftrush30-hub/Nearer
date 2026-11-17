# NeaRer Messaging Source Boundaries and Ownership Audit

**Date:** 2026-11-17  
**Repository:** giftrush30-hub/Nearer  
**Baseline:** `main` at `71268ba3ddeb3002a54711e53cd6c74966633332` (17 November 2026).  
**Purpose:** Establish a source-grounded ownership map before package moves or runtime refactoring.

> This is an engineering audit, not a claim that every listed capability is complete. File names are inventory clues; ownership is assigned only where implementation was inspected. Remaining inventory is explicitly marked for further review.

## 1. Product rule

NeaRer is a messaging app first. Chats is the central/default experience. Share extends communication with substantial file-transfer workflows. Games extends social interaction through invites and in-game communication. Communities support group participation. Nearby and Mesh are underlying delivery infrastructure, not competing product destinations.

This product hierarchy is not the technical dependency graph. Messaging should own conversation and message meaning; Nearby should own the radio/session adapter; Mesh should own route and relay protocol; app composition should connect those capabilities without making the UI the owner of every subsystem.

## 2. Inspection scope

The current source tree contains **69 filename-matched production candidates** and **55 filename-matched JVM test candidates** across chat, message, conversation, community, mesh, nearby, receipt, identity, crypto and transfer-resume terms. These are inventory counts, not proof that every candidate belongs to Messaging. Some identity and transfer files serve more than one feature.

Implementation files opened and inspected directly:

- MainActivity.kt
- NearbyTransportService.kt
- NearbyConnectionsTransport.kt
- NearbyIncomingMessageInbox.kt
- NearbyInboxCommitPolicy.kt
- DirectChatOutbox.kt
- ChatTransportRouter.kt
- ChatInboxPolicy.kt
- CommunityIngressPolicy.kt
- MeshRouteTable.kt
- MeshRelayPolicy.kt
- NearbyMessageCrypto.kt
- PeerSessionCrypto.kt

Tests inspected directly:

- NearbyInboxCommitPolicyTest.kt
- ChatTransportRouterTest.kt

The tree also shows focused JVM tests for many chat policies, mesh protocol objects, identity, handshake and community policies. The inspected `NearbyInboxCommitPolicyTest` exercises only the pure boolean predicate; it does not exercise the service callback ordering, the direct transport ACK side effect, mesh final-receipt persistence, or process-restart replay. Those orchestration behaviours need dedicated seam-level regression tests before the service/transport boundary is refactored. Their existence is evidence of test targets, not proof that every runtime path is covered.

## 3. Confirmed responsibility map

| Area | Current source | Responsibility established by inspection | Target owner |
|---|---|---|---|
| App composition and chat experience | MainActivity.kt | Binds nearby service, attaches UI listener, holds substantial UI/connection state, and composes chat, mesh-recipient, transfer and other home state. | App shell/composition root; feature UI behind contracts |
| Nearby lifecycle | NearbyTransportService.kt | Foreground service owns long-lived NearbyConnectionsTransport, receives events without Activity, persists incoming messages, replays staged messages on attachment/restart, forwards events to attached listener. | core.nearby service/lifecycle adapter |
| Radio/session transport | NearbyConnectionsTransport.kt | Uses Google Nearby Connections; owns endpoint/stable-ID mapping, authenticated peer handshake/session state, message callbacks, mesh state and file-payload machinery. | Split across core.nearby, core.security, feature.mesh and feature.share adapters |
| Incoming durable staging | NearbyIncomingMessageInbox.kt | Encrypted local inbox keyed by normalized peer ID plus message ID; commits synchronously; preserves unreadable ciphertext rather than overwriting it with an empty queue. | Messaging delivery persistence |
| ACK and retirement predicate | NearbyInboxCommitPolicy.kt | Pure boolean checks for inbox/dedupe readiness and inbox/dedupe/domain-commit retirement. The service invokes the transport callback after inbox commit; the callback itself persists dedupe before sending the direct encrypted ACK (or persists the mesh final receipt). The predicate result is used for the later retirement gate; it is not itself the network-ACK sender. | Messaging delivery semantics |
| Direct legacy fallback queue | DirectChatOutbox.kt | Encrypted durable queue for Wi-Fi Direct socket chat fallback; binds entries to peer identity and restores defensively. Not evidence of a universal outbox for every route. | Messaging outbox adapter; legacy fallback decision remains open |
| Private route choice policy | ChatTransportRouter.kt | Its implementation prefers Nearby Connections, then Wi-Fi Direct, and never broadcasts private chat through mesh. Its runtime caller has not yet been established from the inspected coordinators. | Messaging delivery coordinator using Nearby route contract |
| Conversation list projection | PremiumChatsScreen.kt calls ChatInboxPolicy.kt | Pure conversation-ID projection, latest-message grouping, recipient association and filtering/order logic; the screen applies archived/unread state and persists local pin/archive preferences. | Messaging presentation policy; keep UI state in the Chats screen/state owner |
| Community packet gate | MainActivity.kt calls CommunityIngressPolicy.kt before handleCommunityPacket() | Validates supported group and Ludo packet structure and authenticated transport identity; roster membership and live game-state authorization remain in stateful handlers. The gate is currently invoked from the app coordinator. | Community/game protocol ingress behind a narrow service/use-case contract |
| Mesh route protocol | MeshRouteTable.kt | Verifies signed route advertisements, binds next hop to authenticated direct sender, validates path vectors and route freshness/withdrawal. | feature.mesh |
| Runtime mesh participation gate | MeshRelayParticipationPolicy.kt, called from NearbyConnectionsTransport.kt | Controls whether third-party packets/receipts may be accepted or forwarded and whether routes may be advertised. The transport also hard-forces `meshTransitEnabled = false` when asked to enable transit, so this runtime path keeps third-party relay disabled while still allowing this phone's own destination/origin traffic. | feature.mesh policy, called by Nearby adapter |
| Legacy/community-prefix relay predicate | MeshRelayPolicy.kt | Pure predicate that accepts only `NR_COMMUNITY:` bodies with bounded TTL/hops; no production callsite was found in the inspected MainActivity or NearbyConnectionsTransport. Its unit tests do not establish that it governs the current encrypted MeshRelayPacketV1 runtime. | Candidate/legacy policy pending full-tree caller confirmation |

## 4. Behaviour that must be protected

### 4.1 Incoming message durability

The current service path has a deliberate sequence:

1. Resolve the authenticated stable peer identity; do not use a temporary endpoint ID as durable conversation identity.
2. Construct an inbox entry and synchronously commit it to the encrypted `NearbyIncomingMessageInbox`.
3. If that durable write fails, release the pending dedupe reservation and do not invoke the transport acknowledgement callback.
4. After the inbox commit, invoke the callback supplied by the transport. For a direct Nearby message, that callback persists the peer-scoped message-ID dedupe record and sends the encrypted ACK only if the dedupe write succeeds. For a mesh destination message, the callback persists dedupe and the signed final mesh receipt; it reports success only when both are committed.
5. The service passes the callback result into `dispatchInboxEntry`. The `NearbyInboxCommitPolicy` pure predicate is used for the direct branch's retirement readiness; the mesh branch passes its receipt/dedupe result directly. The predicate itself does not send a network ACK.
6. Dispatch the staged event to the Activity listener. The listener's completion callback represents the existing chat/community store commit, not merely UI display.
7. Retire the staged inbox entry only after the listener reports successful domain persistence and the required dedupe/receipt result is true.
8. On listener reattachment or service recovery, replay staged entries and re-establish dedupe plus direct ACK or mesh final-receipt state before retirement.

Direct-message and mesh-message branches have distinct receipt handling. Do not flatten them into one generic acknowledgement path without proving semantics remain correct.

### 4.2 Outgoing direct route

ChatTransportRouter is intentionally narrow. It tries Nearby Connections when ready and falls back to Wi-Fi Direct only when that route is ready. If neither succeeds, it returns no route so the message remains queued. This is a private direct-route selector, not a mesh route selector. However, a focused search of the inspected MainActivity and NearbyConnectionsTransport sources did not find a reference to ChatTransportRouter or DirectChatOutbox. Their production caller graph must be traced across the full source tree before treating either as part of the active send path. The tests prove their local behaviour, not their integration.

### 4.3 Identity and cryptographic trust

Temporary Nearby endpoint IDs are not durable user identities. The inspected Activity and transport use stable NeaRer identities, pinned peer trust and a handshake/session layer. Future package boundaries must keep separate:

- stable app identity;
- endpoint/session identity;
- signing-key trust and key continuity;
- recipient encryption keys;
- per-session message encryption state.

A connected Nearby endpoint must not automatically be treated as a trusted contact. A successful Nearby connection is not, by itself, proof that end-to-end encrypted multi-hop messaging is complete.

### 4.4 Mesh is not a private-chat shortcut

The inspected private-chat route selector never broadcasts private messages through mesh. The current encrypted mesh runtime uses `MeshRelayParticipationPolicy` at the transport boundary to gate third-party packet/receipt acceptance, forwarding and route advertisement. In addition, `NearbyConnectionsTransport.setMeshTransitEnabled(true)` explicitly forces the runtime flag back to false and reports that multi-hop transit is paused pending proof-chain, trust/key-rotation, recovery, security and physical multi-phone gates. The current code therefore keeps transit relay disabled while preserving direct delivery for this phone's own messages.

A separate `MeshRelayPolicy` checks the legacy `NR_COMMUNITY:` body prefix and TTL/hop bounds, but no production callsite was found in the inspected MainActivity or NearbyConnectionsTransport. Do not treat that helper or its unit tests as proof that the current encrypted MeshRelayPacketV1 path uses that allow-list. Full-tree caller confirmation remains open.

Do not remove the hard-disable guard or broaden relay participation as part of organisation work.

### 4.5 Share is adjacent, not owned by Messaging

NearbyConnectionsTransport currently contains substantial file payload and transfer state alongside messaging callbacks. This is historical implementation coupling, not a reason for Messaging to own Share. Target boundary:

- Messaging owns chat message semantics and lightweight attachment references.
- Share owns file selection, transfer jobs, progress, integrity, resume and received-file workflows.
- Nearby provides the underlying authenticated connection/payload capability.
- App composition passes transfer commands/events between Share and Nearby.

No file-transfer protocol should be moved or rewritten until metadata, sender binding, integrity, completion receipt and recovery tests are mapped.

## 5. Confirmed architecture risks

### Risk A — MainActivity carries too many responsibilities

The current file is approximately 323 KB. The inspected portions show service binding, nearby listener handling, discovery/connection state, mesh-recipient state and composition of chat and transfer data into the home UI. The file also contains large amounts of UI and application state.

**Decision:** Keep MainActivity as Android entry point and temporary composition root. Do not move the whole Activity or rewrite it. Extract feature-facing state holders and use-case boundaries in small, tested slices. A package move alone will not solve this coupling.

### Risk B — NearbyConnectionsTransport is a mixed-responsibility coordinator

The class owns Nearby SDK client/session state, peer authentication and message crypto sessions, mesh route/receipt/dead-drop state, and file payload/stream state. This is a high-risk concentration point.

**Decision:** Do not split the class by search-and-replace or move the entire file. First define stable interfaces and characterize callback ordering. Extract pure protocol policies and independent stores before extracting SDK lifecycle or transfer machinery.

### Risk C — Service and Activity share an implicit callback contract

The foreground service is responsible for durable staging, but the Activity listener is responsible for confirming that the chat/community store has committed the event. This is an important cross-layer contract.

**Decision:** Make the contract explicit before changing packages: delivery event, durable staging result, dedupe/receipt result, domain commit result, and inbox retirement result. Do not let a UI callback accidentally become an acknowledgement of mere display.

### Risk D — Chat history persistence is not encrypted by the current writer

The current `MainActivity.persistMessages()` implementation serializes up to the last 500 locally retained chat messages as JSON and writes the plaintext string to the `nearer_local` SharedPreferences key `messages` using synchronous `commit()`. The corresponding `restoreLocalData()` reads the same raw string into a `JSONArray`. The stored fields include message bodies, conversation IDs/names, reply previews, read/delivery state and reactions. Neither inspected direction passes this history payload through `LocalDataCipher`.

This is distinct from `NearbyIncomingMessageInbox` and `DirectChatOutbox`, which do encrypt their own persisted payloads. Those encrypted transport queues do not make the main chat-history store encrypted at rest.

There is a second recovery concern: if parsing/restoration throws, `restoreLocalData()` clears the in-memory `chatMessages` collection. The catch path inspected does not overwrite the original SharedPreferences value, so the persisted raw string remains, but the app can present an empty in-memory history on that launch and repeat the failure on a later launch. This must not be confused with a successful repair or a confirmed permanent deletion.

The `commit()` call is deliberate for synchronous durability before some delivery-related state transitions. Any migration must preserve the required durability ordering, not replace it with an asynchronous `apply()` by assumption.

**Decision:** Treat this as a high-priority Messaging security and migration task, separate from package organisation. Before changing storage:
1. Define a versioned encrypted-history format and the exact encryption/key availability lifecycle.
2. Preserve the existing plaintext value as a recoverable backup until the encrypted value has been written, decrypted, parsed and verified successfully.
3. Make migration idempotent across process death and storage failures; do not mark migration complete before verified commit.
4. Test empty history, 500-message retention, malformed legacy JSON, unavailable/rotated encryption keys, partial writes and restart during migration.
5. Keep a recovery path that never silently replaces an unreadable existing history with an empty collection.

Do not silently clear history or change the storage format during a package-only refactor. Do not claim chat history is encrypted at rest until the migration and verification are implemented.

### Risk E — Source package does not express domain boundaries

The inspected files currently live in the broad com.nearer.app package. Ownership is not enforced by package structure. Moving files into feature packages without resolving dependencies could create cycles or accidental access to UI implementation details.

**Decision:** Treat package migration as a later mechanical step after dependency direction, public contracts and tests are established.

## 6. Target dependency direction

~~~text
Android app shell / MainActivity
        |
        +--> Messaging feature
        |       |
        |       +--> conversation/message policies and state
        |       +--> delivery coordinator / durable inbox and outbox contracts
        |                    |
        |                    +--> Nearby route contract
        |                    +--> Mesh delivery contract (only where explicitly supported)
        |
        +--> Share feature ------> Nearby payload/transfer contract
        |
        +--> Games feature ------> conversation/invite contracts
        |
        +--> Communities feature -> shared delivery contracts

core.nearby  = Android radio, discovery, authenticated connection lifecycle and endpoint adapter
feature.mesh = route advertisements, route table, relay eligibility, forwarding and mesh receipts
core.security = identity, trust, key and cryptographic primitives/contracts
~~~

This is target dependency direction, not a statement that these packages or interfaces already exist.

Rules:
- UI may call feature use cases; domain policies must not import Compose or Activity classes.
- Messaging must not depend on MainActivity or the Nearby SDK.
- Nearby must not decide conversation ordering, unread state, read receipts or chat UI behaviour.
- Mesh must not own private conversation history or bypass Messaging's message identity and receipt rules.
- Share must not invent a second peer identity or trust model.
- Games may reuse conversation/invite capabilities; it must not create a competing direct-message stack.
- Communities may have group-specific authorization and roster semantics, but should reuse shared identity and transport foundations.

## 7. Safe refactoring order

1. **Freeze and characterize current semantics.** Preserve the current JVM suite and add tests around the exact service/inbox callback contract before changing it.
2. **Map callers and persistence.** For every candidate class, identify production callers, persistent keys/files, Android dependencies and test coverage. Do not infer ownership from filename alone.
3. **Extract pure Messaging policies.** Start with classes that have no Android or UI dependencies and already have focused JVM tests. Move only after tests prove behaviour unchanged.
4. **Define route and delivery interfaces.** Keep Nearby Connections and Wi-Fi Direct behind a small adapter contract. Preserve route priority and the “no route, keep queued” result.
5. **Formalise inbox/outbox ownership.** Separate message semantics from Android storage implementation without changing encryption format, keys, commit timing or retry identity.
6. **Separate Mesh protocol from transport.** Extract signed route/packet/receipt policy and persistence behind explicit contracts. Keep relay disabled until independent safety gates pass.
7. **Separate Share payload operations.** Only after sender binding, integrity, completion receipt and recovery are mapped and covered.
8. **Reduce MainActivity gradually.** Extract one state owner/use case at a time; keep navigation and app composition stable.
9. **Only then introduce package boundaries or Gradle modules.** Start with package-level boundaries in the existing app module; consider modules only when dependencies are demonstrably acyclic and build/test costs justify them.

Every production refactor must be a focused PR, pass JVM tests, manifest validation, lint review and debug APK build, and be reviewed against existing behaviour. Physical-device testing is a separate gate and must not be claimed from CI.

## 8. Candidate inventory requiring caller-level review

The tree contains these filename-matched source candidates. This is deliberately an inventory, not a final ownership assignment.

**Chat/conversation candidates:**  
ChatDeletePolicy.kt, ChatDeliveryLabelPolicy.kt, ChatDeliveryRecoveryPolicy.kt, ChatEditPolicy.kt, ChatForwardPolicy.kt, ChatHistoryPolicy.kt, ChatInboxPolicy.kt, ChatInboxStatePolicy.kt, ChatMessageRecoveryPolicy.kt, ChatNotificationPolicy.kt, ChatReactionPolicy.kt, ChatReadReceiptPolicy.kt, ChatReplyPolicy.kt, ChatRestartRecoveryPolicy.kt, ChatTransportRouter.kt, ChatTypingPolicy.kt, ConversationIndex.kt, DirectChatOutbox.kt, MessageIdHistoryPolicy.kt, MessageRetryPolicy.kt, OutgoingReceiptHistoryPolicy.kt, PremiumChatsScreen.kt, PrivateMessageNotification.kt.

**Community candidates:**  
CommunityDeliveryPolicy.kt, CommunityIngressPolicy.kt, CommunityPacketPolicy.kt, CommunityReadReceiptPolicy.kt, CommunityRosterPolicy.kt.

**Mesh candidates:**  
MeshDeliveryReceipt.kt, MeshForwardingProofChain.kt, MeshHopAcknowledgement.kt, MeshPacketEnvelopeV2.kt, MeshReceiptOutbox.kt, MeshReceiptRecoveryPolicy.kt, MeshRelayPacketV1.kt, MeshRelayParticipationPolicy.kt, MeshRelayPolicy.kt, MeshRoutePersistence.kt, MeshRouteTable.kt, MeshRouteWithdrawal.kt, MeshRouteWithdrawalStore.kt.

**Nearby, identity and security candidates:**  
NeaRerIdentity.kt, NearbyConnectionTimingMeter.kt, NearbyConnectionsTransport.kt, NearbyControlFramePolicy.kt, NearbyDiscoveryBudgetPolicy.kt, NearbyDiscoveryGenerationPolicy.kt, NearbyInboxCommitPolicy.kt, NearbyIncomingMessageInbox.kt, NearbyMessageCrypto.kt, NearbyMessageDedupePolicy.kt, NearbyPeerHandshake.kt, NearbyPermissionPolicy.kt, NearbyRadioReadinessPolicy.kt, NearbyServiceBindingPolicy.kt, NearbyServiceStartPolicy.kt, NearbyStreamExchangeController.kt, NearbyStreamPayloadBridge.kt, NearbyTransferStatusPolicy.kt, NearbyTransportService.kt, PeerSessionCrypto.kt, RecipientEncryptionIdentity.kt, RecipientIdentityBinding.kt, RecipientMessageCrypto.kt, SecurePeerIdentity.kt.

**Transfer-adjacent candidates requiring cross-feature review:**  
HyperTransferCompletionReceiptCodec.kt, HyperTransferIdentity.kt, LegacySocketChatPolicy.kt, TransferResumeMessageCodec.kt.

The corresponding test tree includes focused tests for many chat policies, community policies, mesh packet/route/receipt types, identity/handshake/crypto and nearby service policies. Exact source-to-test pairing and the production caller graph still need file-by-file verification.

## 9. Explicit non-changes in this audit

- No production Kotlin file was changed.
- No class or package was moved.
- No Gradle module was added.
- No message format, encryption key, SharedPreferences key, acknowledgement timing, retry policy or mesh protocol was changed.
- No feature was declared physically tested or complete based on CI.
- No relay safety guard was relaxed.

**Next engineering task:** complete the caller/persistence/test matrix for the candidate inventory, then propose the first small, pure-policy extraction with existing and additional regression tests. Do not start with MainActivity or NearbyConnectionsTransport surgery.
