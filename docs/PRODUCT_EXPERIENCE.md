# NeaRer product experience: familiar messaging, offline-first advantage

**Product promise:** NeaRer should feel immediately familiar to anyone who has used a modern messaging app, while making nearby communication and sharing useful when mobile data or internet access is unavailable.

This is a product and integration contract for the Android experience. It guides parallel implementation work; it does not replace or overwrite active messaging pull requests.

## 1. The first five minutes

1. Keep onboarding short, clear and skippable where information is optional. A phone number is optional; the local NeaRer ID remains the primary identity.
2. Explain nearby permissions in plain language when needed. Explain what Bluetooth, nearby-device, Wi-Fi and optional contacts access are used for.
3. After onboarding, land directly in **Chats**. Do not make users pass through a feature dashboard before they can message.
4. Show a useful empty state: start a nearby connection, find someone by their NeaRer ID when discovered, or open an existing conversation.
5. Keep the app useful offline. A saved ID is not a global online directory, and a queued message is not delivered.

## 2. Information architecture

Primary destinations:
- **Chats** — default destination and conversation inbox.
- **People** — nearby discovery, trusted people and locally saved NeaRer IDs.
- **Share** — File Centre, HyperTransfer, OpenSend and AppShare.
- **Games** — Game Zone and Ludo.
- **Communities** — local groups and their member conversations.

Preserve working routes while parallel PRs are integrated; converge on this hierarchy deliberately rather than replacing navigation wholesale. Mesh is infrastructure, not a competing destination. Show its state simply; keep routing jargon out of ordinary user flows.

## 3. Familiar Chats experience

- Conversation list with recognizable avatars, names, last-message preview, timestamp and accurate unread indicator.
- Incoming/outgoing bubbles, readable timestamps, consistent spacing and a composer anchored to the bottom.
- Familiar attachment entry points for supported photos, videos, documents and other files.
- Conversation-level actions for identity/profile, connection status, attachments and history.
- Preserve local history when a person is offline. Never silently redirect an offline conversation to another peer.
- Make group conversations visually distinct and show member identity consistently.
- Do not fabricate cloud backup, global presence, read receipts, voice calls or internet delivery when they are not implemented.

## 4. Honest delivery language

Distinguish:
- **Sending** — a local send attempt is underway.
- **Queued** — saved on this phone and waiting for a usable connection.
- **Sent** — handed to the selected peer transport; not proof of durable receipt unless the protocol confirms it.
- **Delivered** — the intended peer acknowledged durable local receipt.
- **Not verified** — delivery cannot be confirmed.

A delivery acknowledgement is not a read receipt. Never display "Seen" without a separate read-receipt protocol and user control. For group messages, show independent per-member state or an honest aggregate (for example, "2 delivered · 1 queued"). One member's ACK must never imply that every member received the message.

## 5. NeaRer-only advantages

- Nearby discovery explains what the user should enable and why; show chosen NeaRer identities, not raw phone model names.
- Prefer supported nearby transports without pretending Wi-Fi Direct, hotspot and Bluetooth are interchangeable when Android requires a specific action.
- Show connection recovery in plain language with visible retry/cancel actions.
- Support large files through bounded streaming, integrity checks and clear progress. Do not invent an arbitrary app-level size cap or promise unmeasured speeds.
- Mesh relay remains disabled until identity, forwarding, recovery, privacy and physical multi-hop release gates pass. Explain relay participation and battery/privacy implications and obtain informed consent.
- Keep Communities and Games accessible without turning the Chats landing page into a cluttered feature feed.

## 6. Visual and interaction system

- Retain NeaRer's blue + gold identity; use gold as an accent, not a flood of text or backgrounds.
- Use calm, high-contrast surfaces, consistent typography, predictable iconography and comfortable touch targets.
- Motion communicates state: nearby peer, connecting, sending, progress, success, retry and game actions. Avoid decorative motion that hides status or blocks interaction.
- Design for narrow Android screens and Android 8/API 26 as the intended minimum. Respect font scaling, touch accessibility and permission-denied states.
- Design empty, loading, offline, permission-denied, error and success states. Never leave users staring at an endless spinner.

## 7. Parallel-work integration rules

- Shared GitHub `main` is the source of truth. Inspect main and open PRs before editing shared files.
- Messaging PRs own Chats, People, onboarding and conversation flows. Complementary work must not rewrite those files without coordination.
- Develop File Centre/HyperTransfer, transport compatibility, mesh safety, Game Zone and visual consistency in isolated, reviewable slices.
- Prefer additive changes and selective forward-ports over wholesale merges of stale branches.
- Preserve existing working transport, profile, onboarding, Ludo and file-transfer behavior. Replacements require feature parity and a migration path.
- Report source-implemented, CI-validated, merged and physically tested work separately. A debug APK build is not device certification.

## 8. Release acceptance checklist

- [ ] Fresh install completes onboarding and lands directly in Chats.
- [ ] Returning user opens the inbox with local history intact.
- [ ] User can discover/trust a nearby person and open the correct conversation.
- [ ] Offline conversations remain visible; queued messages stay tied to the intended stable peer identity.
- [ ] Direct and group delivery states are truthful and independently tracked.
- [ ] Attachments can be selected in a conversation and use the verified transfer engine.
- [ ] Permission denial, disabled radios, disconnect, app restart and no-internet states have useful recovery guidance.
- [ ] Share, Communities and Games remain accessible without competing with Chats as the first destination.
- [ ] JVM tests and debug APK assembly pass on the exact proposed commit.
- [ ] Physical-device checks precede any field-reliability claim; emulator-only results do not substitute for them.

**Non-goal:** making NeaRer a visual clone of WhatsApp. The goal is familiar interaction quality with an honest, privacy-conscious, offline-first identity.
