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
| Incoming durable staging | NearbyIncomingMessageInbox.kt | Encrypted SharedPreferences inbox keyed by normalized peer ID plus message ID; synchronous commit; idempotent duplicate put; bounded at 200 entries with no eviction; failed writes roll back in-memory mutation; unreadable whole-store ciphertext is preserved and blocks further writes. Individual malformed/invalid records inside otherwise decryptable JSON are skipped during restore, so a later successful write can omit them. | Messaging delivery persistence behind an injected store port |
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

The actual user-facing outgoing path inspected is `MainActivity.sendChatMessage()`. It first adds the outgoing message to local chat history and requires `persistMessages()` to succeed before attempting transport. When a selected recipient has a `MeshRecipient` entry, it tries `NearbyConnectionsTransport.sendMeshMessage()`; if that recipient is directly connected and the recipient-encrypted route cannot start, it falls back only to the same peer's direct Nearby `send()`. It deliberately refuses to redirect a vanished remote recipient to some other connected phone. For an ordinary directly connected chat, it sends through the authenticated Nearby endpoint. A send failure marks the pending bubble failed and persists that state.

The legacy Wi-Fi Direct socket chat path is explicitly blocked: `LegacySocketChatPolicy.mayUseSocketChat()` and `mayAcceptSocketChatFrame()` both return false, with focused tests. MainActivity's remaining socket branch states that no plaintext socket-chat implementation remains. This is a privacy/security guard, not a missing fallback to re-enable casually.

`ChatTransportRouter` is a separate pure helper whose local tests show Nearby-first/Wi-Fi-Direct-second behaviour, but no reference to it was found in the inspected MainActivity or NearbyConnectionsTransport. Likewise, no reference to `DirectChatOutbox` was found in those coordinators. Their full production caller graph remains unconfirmed; do not treat either as part of the active outgoing send path based only on its focused test.

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

The shared `LocalDataCipher` currently uses Android Keystore AES-256-GCM with a `v1:` Base64 envelope containing the 12-byte IV followed by ciphertext. Its decrypt helper returns `null` for malformed/unsupported ciphertext and decryption failures; it also calls `getOrCreateKey()`, which creates a new alias key if the expected alias is absent. Therefore, a missing/invalidated old key can be indistinguishable from other decrypt failures at this API boundary, and attempting decrypt can create a new key that still cannot unlock old ciphertext. Keep the original value/backup untouched and design explicit key-availability/version handling before using this helper for a history migration. The inspected source tree has no dedicated `LocalDataCipher` test file. The current key alias is named `nearer.local.outbox.aes.v1`, and the helper does not supply per-record or per-store AES-GCM associated data. Before reusing it for the full chat-history store, decide whether history needs a dedicated key alias and/or authenticated store/version context so ciphertext cannot be silently transplanted between storage domains.

There is a second recovery concern: if parsing/restoration throws, `restoreLocalData()` clears the in-memory `chatMessages` collection. The catch path inspected does not overwrite the original SharedPreferences value, so the persisted raw string remains, but the app can present an empty in-memory history on that launch and repeat the failure on a later launch. This must not be confused with a successful repair or a confirmed permanent deletion.

The `commit()` call is deliberate for synchronous durability before some delivery-related state transitions. Any migration must preserve the required durability ordering, not replace it with an asynchronous `apply()` by assumption.

**Decision:** Treat this as a high-priority Messaging security and migration task, separate from package organisation. Before changing storage:
1. Define a versioned encrypted-history format and the exact encryption/key availability lifecycle.
2. Preserve the existing plaintext value as a recoverable backup until the encrypted value has been written, decrypted, parsed and verified successfully.
3. Make migration idempotent across process death and storage failures; do not mark migration complete before verified commit.
4. Test empty history, 500-message retention, malformed legacy JSON, unavailable/rotated encryption keys, partial writes and restart during migration.
5. Keep a recovery path that never silently replaces an unreadable existing history with an empty collection.

Do not silently clear history or change the storage format during a package-only refactor. Do not claim chat history is encrypted at rest until the migration and verification are implemented.

### Risk F — ListenerAdapter has an implicit acknowledge-by-default behaviour

The inspected `NearbyConnectionsTransport.ListenerAdapter.onMessage()` implementation invokes `acknowledge()` unless a subclass overrides it. This convenience default can turn an unhandled incoming message into a transport acknowledgement without proving that a durable inbox or domain store accepted it. The current service listener does override the callback and uses the encrypted inbox, but the base class remains a hazardous extension point.

**Decision:** Do not change this default as a side effect of package organisation. First enumerate all implementations and call sites, add a regression test for the intended no-handler behaviour, then migrate to an explicit opt-in acknowledgement contract. Preserve compatibility deliberately rather than silently changing delivery semantics.

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

### 6.1 Current dependency violations and mixed contracts found in source

The target diagram above is not the current dependency graph. The following concrete couplings were verified in the inspected source:

| Current edge | Evidence in current source | Why it matters | Safe target boundary |
|---|---|---|---|
| Nearby foreground service -> Messaging persistence | `NearbyTransportService` constructs and calls `NearbyIncomingMessageInbox` directly, and uses `NearbyInboxCommitPolicy` to decide retirement. | The service owns Android lifecycle but also knows a concrete chat inbox implementation and its domain durability policy. | Define a small incoming-delivery store/commit contract. Keep the service responsible for lifecycle/replay; inject the store implementation from the app composition root. |
| Nearby foreground service -> Messaging notification policy | `NearbyTransportService` calls `ChatNotificationPolicy.isUserVisibleMessageBody()` and `PrivateMessageNotification.show()`. | A core transport lifecycle component makes product-specific message visibility and notification decisions. | Inject a notification/presentation port or publish a typed event; let the Messaging/platform notification adapter decide whether and how to notify. Keep generic service-status notifications in Nearby. |
| Nearby foreground service -> Activity class | Its foreground notification creates a `PendingIntent` targeting `MainActivity`. | This is an app-shell navigation dependency, not a domain dependency. | Supply an app-provided notification intent factory or navigation PendingIntent at composition time. Do not make the service depend on a feature screen. |
| Nearby service -> combined transport listener | The binder attaches a `NearbyConnectionsTransport.Listener`; the same listener contains discovery, connection, direct/mesh message, message-status, file progress, file received and file status callbacks. | Messaging and Share are coupled through one broad event surface. | Split into typed nearby-session, message-delivery and file-transfer event ports, with a compatibility adapter during migration. |
| Nearby transport -> Mesh and Share implementation | `NearbyConnectionsTransport` owns mesh route/receipt stores and state as well as file metadata, payload and stream state. | One SDK coordinator owns three different domains and their recovery rules. | Keep the SDK connection adapter narrow; inject Mesh protocol and Share payload collaborators behind stable contracts. Extract only after tests characterize lifecycle and callback order. |
| App shell -> every feature | `MainActivity.kt` remains the listener and composition point for chat messages, community packets, game-related packet handling, file events and many other screens/states. | The Activity is acting as both composition root and feature implementation host. | Retain it as Android entry point, then delegate by feature-specific state holders/use cases. Do not move the Activity wholesale. |

**Important callback hazard:** `NearbyConnectionsTransport.ListenerAdapter.onMessage()` currently calls the supplied `acknowledge()` callback by default. That is not a safe generic default for a listener that has not durably stored a message. The service-owned listener explicitly overrides the message callbacks and stages messages in the encrypted inbox, but future listeners that inherit the adapter without overriding message handling could accidentally acknowledge without a durable domain commit. Treat this as a high-priority contract/test issue. Before changing the default, enumerate all Listener and ListenerAdapter implementations and test compatibility; the safe eventual contract should make acknowledgement opt-in and explicit.

The inbox store itself has several useful durability properties that must be retained: `put()` normalizes the peer ID and keys by peer plus message ID; an exact duplicate is idempotent; a conflicting duplicate returns false; the 200-entry cap rejects new entries rather than evicting old ones; and a failed synchronous write rolls back the in-memory insertion/removal. On whole-store decrypt/parse failure it sets a restore-failed guard and leaves the encrypted preference untouched. One narrower recovery caveat remains: if the JSON decrypts and parses but contains an invalid individual record, restore skips that record; a later successful write serializes only retained valid entries. Before schema evolution, decide whether invalid records should be quarantined or whether a versioned migration should preserve their raw representation rather than silently dropping them.

The current `NearbyTransportService` path has two separate callbacks with the same Kotlin type `() -> Boolean` but different meanings:
- the transport-supplied callback, invoked by the service after encrypted inbox persistence, establishes the peer-scoped dedupe/transport ACK (or mesh receipt) boundary;
- the callback passed from the service to the Activity listener is invoked only after the Activity has successfully persisted chat/community state, and then retires the staged inbox entry.

Keep these two stages separately named in any future interface. A generic `acknowledge` name obscures the difference between sender transport acknowledgement and local domain-commit completion.

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
2. **Enumerate every transport listener implementation.** Find all `NearbyConnectionsTransport.Listener` and `ListenerAdapter` implementations/call sites. Specifically characterize the adapter's acknowledge-by-default method before altering it; an unhandled message must not be acknowledged without durable acceptance.
3. **Name the two incoming completion stages separately.** One callback establishes transport dedupe/ACK or a mesh final receipt after inbox commit. The second reports successful local chat/community persistence and permits inbox retirement. Replace ambiguous callback names only behind compatibility tests.
4. **Map callers and persistence.** For every candidate class, identify production callers, persistent keys/files, Android dependencies and test coverage. Do not infer ownership from filename alone.
5. **Extract dependency ports before moving implementations.** Define incoming-delivery storage, notification, message-event and file-transfer event contracts. Inject implementations from app composition so Nearby lifecycle does not depend on Messaging/Share concrete classes or Activity screens.
6. **Extract pure Messaging policies.** Start with classes that have no Android or UI dependencies and already have focused JVM tests. Move only after tests prove behaviour unchanged and model dependencies are not pulled back into the app root.
7. **Define route and delivery interfaces.** Keep Nearby Connections behind an authenticated session/route contract. Keep the legacy socket-chat guard in place; do not revive Wi-Fi Direct chat as a fallback. Preserve the “no route, keep queued” result and never substitute a different recipient.
8. **Formalise inbox/outbox ownership.** Separate message semantics from Android storage implementation without changing encryption format, keys, commit timing or retry identity. Treat plaintext history encryption as a separate, versioned migration with recovery.
9. **Separate Mesh protocol from transport.** Extract signed route/packet/receipt policy and persistence behind explicit contracts. Keep relay disabled until independent safety gates pass.
10. **Separate Share payload operations.** Only after sender binding, integrity, completion receipt and recovery are mapped and covered.
11. **Reduce MainActivity gradually.** Extract one state owner/use case at a time; keep navigation and app composition stable.
12. **Only then introduce package boundaries or Gradle modules.** Start with package-level boundaries in the existing app module; consider modules only when dependencies are demonstrably acyclic and build/test costs justify them.

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

## 9. First extraction decision and required test gate

**No production class is approved for a blind package move yet.** The inspected candidates have dependencies or integration gaps that make a cosmetic move misleading:

- `ChatInboxPolicy` is a good pure presentation policy and is called by `PremiumChatsScreen`, but it consumes app-root `ChatMessage` and `MeshRecipient` models. Move those contracts first or deliberately keep the policy at the app boundary; do not create a feature-to-app dependency.
- `NearbyInboxCommitPolicy` is pure, but its caller is the foreground `NearbyTransportService`. Moving it directly into Messaging would make the Nearby infrastructure depend on a feature implementation. First define the cross-layer delivery contract and its owner.
- `CommunityIngressPolicy` is called from `MainActivity` and validates both group and Ludo packet vocabularies. Its future owner/contract must account for the Communities and Games split rather than moving it wholesale.
- `MeshRelayParticipationPolicy` is actively called from `NearbyConnectionsTransport`; moving it directly into `feature.mesh` would create a transport-to-feature dependency unless the gate is injected through an explicit contract.
- `ChatTransportRouter` and `DirectChatOutbox` have focused source/test files, but no production caller was found in the inspected MainActivity or NearbyConnectionsTransport. Do not move or advertise them as the active outgoing direct-message path until the full production caller graph is established.
- The tree contains focused tests for `NearbyInboxCommitPolicy`, but no correspondingly named focused test for `NearbyIncomingMessageInbox` or `DirectChatOutbox`. This does not prove there is no indirect coverage; it means their storage/recovery guarantees are not demonstrated by a dedicated test file in the inspected tree.
- `MeshRelayPolicy` has a focused test but no production callsite was found in the inspected coordinators. It must not be confused with the active `MeshRelayParticipationPolicy` gate.

### Next safe engineering slice

Before package moves, add a testable seam around the service/transport inbox callback and cover these observable invariants:

1. If encrypted inbox persistence fails, the transport acknowledgement callback is never invoked and the pending dedupe reservation is released.
2. For direct Nearby delivery, the encrypted sender ACK is emitted only after the peer-scoped dedupe record is durably committed.
3. For destination mesh delivery, the final signed receipt is durably queued before the callback reports success.
4. If the chat/community domain commit fails, the staged inbox entry remains available for replay.
5. If the process dies after inbox persistence or after receipt persistence, replay is idempotent and does not create duplicate chat messages or duplicate final receipts.
6. A failed inbox removal does not erase the only recoverable copy of the incoming event.

The test seam should use fake persistence and transport collaborators; it must not alter packet formats, SharedPreferences keys, encryption, ACK timing, retry identity or Android service lifecycle. Once these tests characterize the current behaviour, the first implementation refactor can introduce narrow interfaces without changing semantics.

This is the next task, not a claim that the tests or interfaces already exist.

## 10. Explicit non-changes in this audit

- No production Kotlin file was changed.
- No class or package was moved.
- No Gradle module was added.
- No message format, encryption key, SharedPreferences key, acknowledgement timing, retry policy or mesh protocol was changed.
- No feature was declared physically tested or complete based on CI.
- No relay safety guard was relaxed.

**Next engineering task:** complete the caller/persistence/test matrix for the candidate inventory, then propose the first small, pure-policy extraction with existing and additional regression tests. Do not start with MainActivity or NearbyConnectionsTransport surgery.


## 11. Transport listener implementation inventory — confirmed scope

A focused source inspection of the three production coordinators confirms the following concrete listener objects on the current `main` baseline:

| Source | Implementation | Message callbacks | Acknowledgement behaviour | Other event families |
|---|---|---|---|---|
| `NearbyTransportService.kt` | `serviceListener = object : NearbyConnectionsTransport.ListenerAdapter()` | Overrides `onMessage`, `onMessageWithReply`, `onMeshMessage`, `onMeshMessageWithReply` and `onMessageStatus`. | Non-typing direct and mesh messages are staged in `NearbyIncomingMessageInbox`; transport callback is invoked only after successful `inbox.put`. The Activity receives a separate completion callback; the inbox is retired only after the Activity reports successful domain commit and the dedupe/receipt boundary is satisfied. | Forwards discovery, connection, mesh-route and file events to the currently attached UI listener. |
| `MainActivity.kt` | `nearbyListener = object : NearbyConnectionsTransport.Listener` | Overrides `onMessage`, `onMessageWithReply` and `onMessageStatus`; the service normally owns the transport and calls this listener only after inbox staging/replay. | Processes message/control/community content on the UI thread. It invokes the callback supplied by the service only when the relevant handler reports safe storage. This callback is the local-domain-commit/inbox-retirement stage, not the original transport ACK. | Handles status, discovery, connection state, Mesh route changes, file progress/receipt/status and other app-shell state. |
| `NearbyConnectionsTransport.kt` | `Listener` interface and `ListenerAdapter` base class | Interface requires `onMessage`; reply and mesh callbacks default-bridge to simpler message callbacks. Adapter's `onMessage` invokes `acknowledge()` by default. | The adapter default is an implicit ACK and is unsafe for a future subclass that does not override message handling and durably accept the event. | One combined contract covers discovery, connection, message, Mesh route and file-transfer events. |

### Important special case: ephemeral typing events

The service treats `NR_TYPING:` separately from durable chat messages. It forwards typing directly to the attached UI listener and does not stage it in the encrypted inbox. If there is no UI listener, or forwarding throws, the service currently invokes the supplied acknowledgement callback. The Activity's typing handler returns whether the event was accepted by its typing-state handler. This is an ephemeral, best-effort path and must not be described as having the same durable replay guarantee as ordinary messages.

The same direct-forwarding pattern exists for Mesh typing events. Any future listener split must preserve the explicit distinction between ephemeral typing signals and durable message/control events; tests should prove that a typing ACK cannot be confused with a persisted-message commit.

### What this inventory establishes — and what it does not

- Two concrete production listener objects were directly inspected: the service-owned adapter subclass and the Activity's direct interface implementation.
- The service is the transport's listener at construction time; the Activity is attached as a UI listener through the binder. The transport therefore has one active service listener, while the service delegates selected events to the attached Activity.
- The adapter's acknowledge-by-default implementation is confirmed in source, not merely inferred from its name.
- The service listener explicitly overrides all four message delivery callback variants, so the dangerous adapter default is not used for its ordinary direct/mesh message paths.
- This focused inspection is not yet a repository-wide proof that no other anonymous or named implementations exist in other production or test files. The complete all-source implementor search remains open; do not change the adapter default until that sweep and compatibility tests are complete.
- No dedicated test was found in the inspected test inventory that instantiates `ListenerAdapter` to assert the default ACK behaviour, or exercises the complete service -> encrypted inbox -> Activity commit -> inbox retirement sequence. Existing pure policy tests do not substitute for those integration-contract tests.

### Listener contract test cases to add before interface extraction

1. A bare `ListenerAdapter` subclass that does not override message handling demonstrates the current default ACK behaviour. Mark this as a compatibility hazard, not desired future semantics.
2. A listener that explicitly declines or cannot persist an ordinary message must not ACK it.
3. Direct and Mesh ordinary messages must not ACK before encrypted inbox persistence succeeds.
4. Activity/domain commit failure must leave the staged inbox event available for replay; successful commit may retire it only after the dedupe/receipt condition is met.
5. Direct and Mesh typing events remain ephemeral and are tested separately from durable message delivery.
6. A UI listener throwing during durable dispatch must not cause the durable inbox copy to be deleted.
7. Replaying an already staged event after rebind/restart must remain idempotent and must not duplicate the domain message or final Mesh receipt.

These are proposed regression cases; they are not claimed to exist or pass yet.


## 12. Durability-gate characterization tests added

A test-only change now exhaustively checks the existing pure `NearbyInboxCommitPolicy` truth tables:

- `canAcknowledge`: all four combinations of inbox-commit and dedupe-commit state.
- `canRetireInbox`: all eight combinations of inbox, dedupe and chat/community-store commit state.

The expected rule is explicit: acknowledgement requires both inbox and dedupe commits; inbox retirement requires all three commits. These tests characterize the existing policy and do not change the policy implementation or runtime callback order.

**Still not covered by these tests:** Android SharedPreferences/Keystore persistence, the actual Nearby SDK acknowledgement closure, service listener dispatch, Activity rebind/restart replay, ephemeral typing forwarding, or the default adapter acknowledgement hazard. Those require a dedicated seam and fake collaborators; this test addition must not be mistaken for end-to-end delivery coverage.


### Adapter default is now captured by a characterization test

A focused JVM test instantiates a bare `NearbyConnectionsTransport.ListenerAdapter` subclass and confirms that its inherited `onMessage` invokes the acknowledgement callback once. The test name and comments explicitly classify this as a hazardous legacy compatibility default, not the desired durable-delivery rule.

This test is a tripwire for future contract changes: if the default is later made opt-in, the test must be deliberately rewritten alongside the complete implementation inventory and migration of every listener. It does not prove that every listener implementation has been found, nor does it validate the service's Android persistence path.
