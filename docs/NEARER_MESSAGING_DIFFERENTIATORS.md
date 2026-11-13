# NeaRer Messaging: Competitor Research and Differentiation Plan

Research date: 2026-11-13

## Executive direction

NeaRer should not try to win by adding every chat feature at once. It should be the easiest familiar messenger to use when mobile data is expensive, unavailable, congested, or unnecessary — especially in hostels, campuses, markets, danfo journeys and local communities.

The product promise should be:

> **Your conversations stay with you. NeaRer helps you reach trusted people nearby, even when the internet is not available.**

This is a product direction, not a claim that every offline route or multi-hop relay is already working. The current transport keeps multi-hop transit disabled until its security and recovery gates pass.

## Competitor research

### WhatsApp
What it teaches us:
- Keep the everyday chat flow obvious: conversation list, familiar bubbles, simple composer.
- Replies, reactions, polls, voice messages, chat lock and group tools are now expected messenger basics.
- Users should not need to learn networking terminology to send a message.

NeaRer response:
- Keep Chats as the default destination after onboarding.
- Add message replies, reactions, edit/delete rules and voice notes in a deliberate sequence.
- Explain connection and delivery status in plain language, not transport jargon.

Official reference: https://faq.whatsapp.com/424198503229937/

### Telegram
What it teaches us:
- Search, pinned conversations, folders, replies, mentions, topics and group moderation help users manage busy conversations.
- Large-group features are useful, but can make a small messenger feel complicated if exposed too early.

NeaRer response:
- Search and pinned chats are already in the current messaging branch.
- Add simple chat organization only after core one-to-one interactions are dependable.
- Keep advanced group controls tucked away until a user actually needs them.

Official reference: https://telegram.org/faq

### Signal
What it teaches us:
- Privacy must be a default design discipline, not a decorative setting.
- Message interactions should not weaken identity, encryption or local data protection.

NeaRer response:
- NeaRer ID and verified cryptographic identity remain authoritative; a phone number stays optional.
- Never call a transport acknowledgement a read receipt.
- Any future edit/delete/reaction must be represented as authenticated message events and persisted safely.

Official reference: https://signal.org/

### Briar
What it teaches us:
- Direct peer-to-peer messaging can work without central servers.
- Bluetooth and Wi-Fi can keep contact synchronization available during internet outages.
- Offline communication still depends on compatible devices meeting, local radio range and battery/background behaviour.
- Briar distinguishes private messages from content that may be synchronized through contacts; this is an important privacy boundary.

NeaRer response:
- Make the route and its limitations visible.
- Do not imply that one phone can reach a distant person without a real path.
- Keep helper devices unable to read messages intended for someone else.
- Build a low-battery mode and honest reconnect states before advertising background mesh reliability.

Official references:
- https://www.briarproject.org/
- https://briarproject.org/how-it-works/
- https://briarproject.org/manual/

### Bridgefy and newer offline-messenger category
What they teach us:
- The simple story — turn on Bluetooth and talk to nearby people — is compelling.
- Store-and-forward relays can extend reach only when enough compatible devices are present and participating.
- A phone-only mesh does not create unlimited range; obstacles, device availability and radio behaviour matter.

NeaRer response:
- Avoid made-up range promises and avoid implying that every nearby phone is a relay.
- Show whether a person is directly nearby, reachable through an approved route, or currently unreachable.
- Explain what the user must turn on, and why, in one short instruction.

References:
- https://bridgefy.me/home/
- https://www.briarproject.org/how-it-works/

Research caution: third-party 2026 comparison pages were useful for discovering products and questions to investigate, but some are written by competing app makers. Product claims above are therefore anchored primarily in official product documentation. All range and capability claims must be independently verified on real devices before NeaRer repeats them.

## NeaRer's own differentiators

These are proposed product features, not claims that they are all implemented today.

### 1. Nearby Pulse — a useful local presence view
A simple, optional screen showing:
- People you have already trusted who are discoverable nearby.
- Whether they are directly reachable, recently seen, or currently unavailable.
- A plain connection-quality hint such as “Good nearby link” or “Moving in and out”.
- Last-seen age without exposing precise location.

Privacy rules:
- No map pin or precise location.
- No public list of strangers by default.
- Presence is opt-in and locally scoped.
- A person can pause visibility without deleting their conversations.

Why it is different: the app helps people understand who is actually reachable around them, rather than presenting a generic online dot that assumes internet access.

### 2. Data-Dead Mode — one clear switch for low-connectivity situations
A user-facing mode that:
- Prioritizes local nearby messaging and file sharing.
- Stops optional online-only work while active.
- Explains Bluetooth / nearby Wi-Fi requirements in plain language.
- Shows a clear “No route yet” state instead of pretending a message has been delivered.
- Offers a battery-saving setting for discovery frequency.

Safety: this mode must not silently change Android radio settings or bypass runtime permissions. Android remains in control and the user must approve required permissions.

### 3. Message Journey — honest delivery, not fake ticks
For every outgoing message, show only what the phone can prove:
- Saved on this phone.
- Waiting for the recipient or a valid route.
- Sent to the recipient's phone and saved by their NeaRer app.
- Not confirmed / not sent.

When a future verified multi-hop route is enabled, show a simple route summary such as “Reached through 2 trusted nearby helpers” only when backed by signed, validated route receipts. Never reveal helper message contents. Never equate delivery with reading.

### 4. Local Circles — temporary nearby group conversations
Use cases:
- Hostel floor or housemates.
- Class or study group.
- Market team or event volunteers.
- Friends travelling together.

Design:
- Invite-only, short setup, clear owner/admin.
- Optional expiry chosen by the creator.
- Local group history remains on each member's phone.
- Members can leave and owners can remove members.
- Membership changes are signed and reconciled safely after offline periods.

Do not release until group membership, key changes, per-member delivery and offline reconciliation are tested.

### 5. NeaRer Handoff — help a conversation survive a missed connection
A trusted device may eventually hold an encrypted, recipient-addressed packet until the intended person is reachable again. The helper must not be able to read it.

Requirements before launch:
- Explicit, understandable opt-in for helping.
- Android permission and battery controls.
- Signed forwarding and receipt proofs.
- Expiry, storage quotas, abuse limits and revocation.
- No claim of active multi-hop delivery until real multi-phone tests pass.

The current code deliberately disables transit relaying. This idea remains a roadmap item until the security gates are satisfied.

### 6. Offline-first setup and app handoff
A practical feature for Nigerian environments: when someone nearby does not have NeaRer installed, let the user share the app package locally over the existing file-sharing workflow, with clear Android install guidance. This is not automatic installation and must respect Android's install permission flow.

## Product sequence

### Now — make one-to-one chat dependable
Implemented in the current messaging PR (pending integration and physical-device validation):
1. Replies/quotes with message IDs and safe legacy packet compatibility.
2. Reactions with per-message, per-user state and replay-safe encrypted control events.
3. Copy message and delete-for-me with confirmation and local rollback on save failure.
4. Edit own messages with bounded revision numbers, authenticated sender checks, persistence and stale/duplicate update handling.
5. Search, pin, archive, local unread counts and plain-language delivery labels.

Still to build:
6. Message forwarding with clear original-sender attribution and privacy safeguards.
7. Delete-for-everyone only after signed tombstones and offline reconciliation are designed and tested.
8. Voice notes after audio storage, cancellation, transfer resume and playback are reliable.

### Next — make nearby use feel special
1. Nearby Pulse.
2. Data-Dead Mode.
3. Clear connection setup guidance and battery controls.
4. Local Circles.
5. Verified Message Journey and Handoff only after transport security gates.

### Later — richer communication
- Audio calls before video calls.
- Group voice only after battery and multi-device testing.
- Optional online mode only when the user chooses it; no cloud dependency for the offline MVP.

## Acceptance rules

A feature is not “done” because a screen or button exists. It must pass:
- Backward-compatible packet parsing where applicable.
- Persistence across app restart.
- Correct recipient and conversation binding.
- Duplicate/replay protection.
- Tests for malformed or oversized input.
- Honest delivery and privacy wording.
- Automated Android build and unit tests.
- Real-phone testing on at least two Android devices before release claims.

Keep the UI simple. The differentiators should make NeaRer easier to understand, not add a second settings manual.


## Implementation update — quoted replies

The messaging branch now includes the first end-to-end quoted-reply implementation over the authenticated Nearby Connections message path:

- Each message can be selected with a simple **Reply** action.
- The composer shows who is being replied to and a short quote; users can cancel before sending.
- The outgoing message stores the referenced message ID, bounded quote text and original author's display name.
- Quote metadata is carried inside the existing encrypted direct-message envelope and the recipient-encrypted message body.
- Incoming quote metadata is validated before it is accepted.
- Quote fields are saved with local chat history and restored after app restart.
- Older messages without quote fields continue to load normally. Older app versions can still read the message body, although they will not render the quote.

The reply preview is a snapshot of the original text (limited to 180 characters); it does not fetch or reveal the original message from another device. The reply reference remains scoped to the same conversation when created. Replies over the legacy line-based fallback are intentionally not sent because that older wire format cannot carry quote metadata safely.

This is code-level implementation; the latest Android workflow and real two-phone compatibility testing are still required before calling the feature release-ready. 

## Implementation update — message reactions and editing

The current messaging branch also implements:
- Six familiar reactions (👍 ❤️ 😂 😮 😢 🙏), with one active reaction per verified peer per message.
- Reaction updates are idempotent set/remove events, so a retried encrypted packet cannot accidentally toggle a reaction off.
- **Edit** is available only on messages sent by the local user. Each edit increments a bounded revision number and stores its edit time.
- Edits travel as control events inside the existing authenticated, encrypted nearby message path. A receiver only applies an edit to a message authored by that same verified peer in that conversation.
- Duplicate edits are safe, stale revisions cannot overwrite newer text, and skipped/conflicting revisions are rejected.
- Edit version and timestamp persist with local chat history; older stored messages default to revision zero.

Edits are limited to 3,500 characters so the control event remains within the existing message envelope limit. This is code-level implementation; Android CI and two-phone interoperability testing are still required before release claims.
