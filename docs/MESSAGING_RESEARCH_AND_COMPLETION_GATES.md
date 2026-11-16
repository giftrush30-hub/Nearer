# NeaRer Messaging: research, architecture decisions and completion gates

Status: active development workstream
Working branch: `work/messaging-service-owned-nearby`
Main remains unchanged. Do not merge this work until the gates below pass.

## 1. Product research and lessons

Research reviewed on 2026-11-16:

- WhatsApp security overview: https://www.whatsapp.com/security/advisories
- WhatsApp multi-device engineering: https://engineering.fb.com/2021/07/14/security/whatsapp-multi-device/
- WhatsApp delivery/read receipt semantics: https://faq.whatsapp.com/665923838265756/
- Telegram FAQ and feature model: https://telegram.org/faq
- Telegram privacy model: https://telegram.org/privacy
- Signal Double Ratchet specification: https://signal.org/docs/specifications/doubleratchet/
- Signal X3DH specification: https://signal.org/docs/specifications/x3dh/
- Android background Bluetooth communication guidance: https://developer.android.com/develop/connectivity/bluetooth/ble/background
- Google Nearby Connections overview: https://developers.google.com/nearby/connections/android

### WhatsApp lessons to adopt

- Keep delivery states distinct: locally queued/sending, delivered to a recipient device, read, and (for voice) played. A transport send callback is not a recipient delivery receipt.
- Make privacy the default. WhatsApp documents end-to-end encryption based on the Signal Protocol; its multi-device engineering article describes separate pairwise sessions per sender/recipient device and encrypted history/state synchronization.
- Make connection lifecycle resilient. WhatsApp's engineering write-up describes reliability trade-offs when a single phone process is the source of truth; NeaRer must not make an Activity the owner of an ongoing nearby connection.
- Group delivery/read state must be recipient-aware, rather than pretending one group-wide boolean proves every member received/read a message.
- Provide optional read receipts and privacy controls. A read receipt is a separate event from successful delivery.

### Telegram lessons to adopt

- Strong conversation organization: pinned chats, archive, folders, search, replies, mentions, and (later) group topics.
- Useful group controls: clear member roles, admin permissions, pinned information and notification controls.
- Message management: editing, deletion, auto-delete timers and rich media controls should have explicit, predictable semantics.
- Do not copy Telegram's security labels inaccurately. Telegram documents that ordinary Cloud Chats are client-server/server-client encrypted, while Secret Chats are end-to-end encrypted and device-specific. NeaRer should preserve its local-first, end-to-end design and must not claim cloud-style multi-device sync when it does not provide it.

### NeaRer's differentiator

- Core direct chat works without mobile data or a cloud account.
- Nearby Connections is the radio abstraction; it can use Bluetooth, BLE and Wi-Fi and exchange bytes, files and streams.
- Messages must survive temporary disconnects through encrypted local queues and explicit delivery acknowledgements.
- Relaying remains disabled until end-to-end identity, trust, replay protection, receipt recovery and multi-phone security tests pass.

## 2. Current implementation baseline

Main already contains direct chat, Nearby discovery/handshake, E2E payload encryption, durable outgoing queue, acknowledgements, local history, reply/edit/reaction flows, and community/group foundations.

The service-owned transport implementation was previously developed in PR #243. Its original branch had diverged from current main (16 commits behind). The relevant five files have been ported to a fresh branch based on current main so unrelated newer main changes are retained.

The active branch adds:
- `NearbyTransportService` to own the Nearby client outside the Activity lifecycle.
- An encrypted bounded incoming-message inbox.
- UI listener attach/detach and peer/message replay on reattachment.
- Service foreground notification and connected-device service declaration.
- Activity state rehydration and recovery hooks.
- Peer-scoped duplicate-protection policy (new work in this branch).

## 3. Important security gap to resolve

The current direct-chat crypto in `NearbyMessageCrypto` uses an authenticated ephemeral P-256 ECDH handshake and AES-GCM session keys. This is useful protection, but it is not the Signal Double Ratchet.

Signal's documented Double Ratchet derives a fresh message key for each message, advances sending/receiving chains, incorporates DH ratchet steps, and supports bounded skipped-message keys for out-of-order delivery. NeaRer must not advertise Signal Protocol parity, forward secrecy per message, or WhatsApp-equivalent security until an independently reviewed protocol/library decision and tests justify those claims.

Before public release, review:
- Key rotation and post-compromise recovery.
- Replay and duplicate handling across reconnects.
- Out-of-order delivery and bounded skipped-message handling.
- Identity key changes and re-pairing.
- Multi-device identity and group sender-key design.
- Secure storage, backup policy and key-loss behavior.

Do not invent a bespoke ratchet as a quick patch. Evaluate maintained, audited implementations and platform compatibility before changing the wire protocol.

## 4. Peer-scoped duplicate protection

Incoming dedupe and in-flight state must be keyed by authenticated peer identity plus message ID. A message ID alone is not a safe namespace across peers.

This branch introduces `NearbyMessageDedupePolicy` and JVM tests for:
- Different peers using the same message ID remain independent.
- Peer IDs are normalized.
- Invalid identities/IDs are rejected.
- Legacy unscoped message-history entries remain recognized during compatibility transition.

The legacy fallback is temporary compatibility behavior: old records did not retain sender identity, so they cannot be retroactively attributed with certainty. New records must always be peer-scoped.

## 5. Crash-safe mesh final-receipt recovery

- Destination replay checks the encrypted dead-drop queue for a still-pending packet and completes the final receipt path before retiring the inbox event.
- Final receipt creation is idempotent by packet ID: if the receipt outbox already contains a signed receipt, retries reuse that exact receipt rather than creating a new timestamp/signature for the same packet ID.
- The destination retires its local encrypted packet only after the final receipt has been durably enqueued.
- If receipt persistence fails, the packet and durable inbox event remain available for retry. If the packet has already expired or been retired after the inbox commit, the authenticated encrypted inbox is sufficient to recreate the final signed receipt.
- This closes the crash window between receipt persistence and local packet retirement, including packet expiry after a durable inbox commit. It does not replace multi-device or real-radio testing.

## 6. Completion order

1. Reconcile service-owned transport with current main and review startup/stop/bind/rebind lifecycle.
2. Verify incoming durable inbox commit, acknowledgement ordering, retry and dedupe semantics.
3. Verify outgoing outbox restoration, endpoint re-resolution, retries, ACK retirement and interruption labels.
4. Verify permission denial/revocation, radios off/on, Activity recreation, service restart, process death and force-stop limitations.
5. Review encryption, identity pinning, replay protection and metadata privacy.
6. Complete one-to-one messaging UX: delivery/read states, message info, reply/edit/reaction/delete semantics, voice notes and media.
7. Complete group messaging: per-member delivery/read receipts, membership changes, moderation, mentions, pinned information and notification controls.
8. Run JVM tests without building an APK; add instrumentation/physical-device acceptance checks separately.
9. Review and merge to main only after all required gates pass.

## 7. Required test matrix

| Scenario | Expected behavior |
|---|---|
| Activity recreated while peers connected | Service remains owner; UI reattaches and receives a fresh peer snapshot |
| App moved to background with service active | Connection may continue under Android policy; visible foreground-service notification explains activity |
| Incoming message while no Activity is bound | Encrypted inbox commit happens before delivery acknowledgement |
| UI history commit fails | Inbox entry remains and can be replayed |
| Sender retries after lost ACK | Receiver deduplicates and returns acknowledgement without a second bubble |
| Same message ID from two peers | Each peer's message is processed independently |
| Service process is killed and restarted by Android | Durable outbox/inbox restore; no guarantee is made for force-stop |
| Nearby permission revoked | Discovery stops/fails safely; no permission bypass |
| Bluetooth and Wi-Fi unavailable | No false connected/ready state; user receives actionable status |
| Group message to several peers | Each recipient's delivery/read state is tracked independently |
| Old app version sends a legacy message | Compatibility behavior is explicit and tested |

## 8. No-APK rule

Do not run `assembleDebug`, `assembleRelease`, or any workflow that packages/uploads an APK during this workstream unless the owner explicitly authorizes it. Prefer code review, static inspection and JVM-only validation. The existing repository workflow currently runs both JVM tests and `assembleDebug` on selected PR targets, so do not open a PR against a triggering target until the APK workflow has been adjusted or the owner approves it.
