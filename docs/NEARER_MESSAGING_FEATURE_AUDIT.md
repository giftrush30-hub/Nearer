# NeaRer Messaging Product Audit

Date: 2026-11-13

## Product direction

NeaRer should feel immediately familiar to WhatsApp users, borrow Telegram's conversation organization and group tools, and adopt Signal's privacy discipline. Its differentiator is communication that can continue locally when mobile data or internet service is unavailable.

Do not claim that a local NeaRer ID is a global directory, that an offline message can reach a distant phone without a route, or that a relay can read end-to-end encrypted message content.

## Benchmark findings

| Product | Strong patterns worth learning from | NeaRer adaptation |
|---|---|---|
| WhatsApp | Simple chat list, status indicators, reactions, replies, edits, polls, voice notes, disappearing messages, locked chats, groups and communities | Familiar inbox and composer; optional privacy controls; offline-safe group and message features |
| Telegram | Powerful search, pinned chats, folders, saved messages, scheduled messages, topics, large groups and channels | Local search, pinned chats and filters first; later local saved notes, scheduled-on-reconnect, group topics |
| Signal | Default privacy posture, usernames/phone privacy, message details, edit/reply/reactions, disappearing messages, secure local backups, screen lock | Stable cryptographic identity, explicit trust, local encrypted backup, chat lock, transparent delivery state |
| Discord | Persistent voice rooms and structured communities | Optional nearby voice rooms / group voice only after reliable audio transport and battery testing |

Official product references:
- WhatsApp Help Center: reactions, polls, chat lock, lists and linked devices.
- Telegram FAQ: search, folders, pinned chats, saved messages, topics and scheduled messages.
- Signal Support: edit, reply, reactions, groups, stories, backups and phone-number privacy.

## Current implementation snapshot

Implemented in the current messaging branches (subject to integration and device validation):
- Chats-first home destination and a local conversation inbox.
- People discovery, local contacts, manually linked NeaRer IDs.
- Trusted-recipient chat selection and isolated conversation history.
- Nearby direct message transport with encryption and delivery acknowledgement foundations.
- Automatic reconnection for previously trusted identities.
- In-chat file picker using the existing transfer engine.
- Optional phone number stored locally and not treated as the primary identity.
- Inbox search by contact/display name, NeaRer ID and any message in the locally retained conversation history (up to the app's current 500-message timeline limit).
- Inbox filters for All, Nearby, Via others and Offline, using plain language in the screen.
- Locally persisted pinned conversations.
- Clear outgoing-message labels: waiting for reconnection, sending, delivered to the recipient's phone, interrupted/not confirmed, or not sent. Delivery confirmation is not a read receipt.

Not yet complete or not yet verified as a full end-to-end user feature:
- Reactions, edit, delete-for-everyone and message forwarding.
- Quoted replies are now implemented in this branch: reply action, composer quote preview/cancel, encrypted direct and recipient-encrypted message metadata, local persistence and restart restoration. Legacy messages remain readable; the legacy line-based fallback does not send replies.
- Voice notes, in-chat audio/video calling and live voice rooms.
- Group chats with membership, delivery, history and admin controls.
- Polls, scheduled messages, disappearing messages and stories/status.
- Chat lock, per-chat notification controls, mute/archive and unread counters.
- Search across media, and search older messages beyond the locally retained timeline limit.
- Export/restore of encrypted local chat history and media.
- Full multi-hop message transit; relay remains deliberately disabled until proof, trust and recovery requirements pass.
- Real-device interoperability, reconnect, duplicate suppression and app-process-death recovery validation.

## Roadmap and acceptance gates

### M1 — Reliable one-to-one messaging
- Durable outgoing queue and clear waiting / sending / delivered-to-phone / interrupted / not-sent states. Read receipts are a separate future feature; never label a transport ACK as a read receipt.
- Idempotent message IDs, duplicate suppression, retry/backoff and safe recovery after disconnect or app restart.
- Conversation history integrity and correct recipient binding.
- Test on two or more Android phones, including Android 8+ compatibility, radio loss, app background/force-stop, and reconnection.

### M2 — Everyday chat interactions
- Reply/quote and message reactions.
- Edit own message with visible edited marker and bounded edit window.
- Delete for me; add delete-for-everyone only when all recipients can receive and apply a signed deletion event.
- Forward with clear original-sender attribution and privacy safeguards.
- Copy, select, share and message details.

### M3 — Voice and rich communication
- Voice-note recording, playback, waveform/progress and cancellation.
- Local speech-to-text only as an optional on-device capability; never silently upload audio.
- Audio calls first, then video calls and group voice after battery, network and privacy gates.
- Link previews only when the user explicitly enables online fetching; offline-safe plain links remain supported.

### M4 — Groups and community
- Multi-member groups with signed membership state, invite/approval, admin roles and member removal.
- Group delivery acknowledgements and per-member delivery state.
- Reply, mentions, reactions, pinned announcements, polls and topic threads.
- Group messages remain end-to-end encrypted; relays only forward opaque packets.
- Offline group membership changes must reconcile safely when members meet again.

### M5 — Privacy and personal organization
- Disappearing messages with explicit sender/recipient policy and honest limitations for offline devices.
- Per-chat mute, archive, pin, unread/mark-unread and notification previews.
- Optional app/chat lock using Android biometric/device credential.
- Encrypted local backup/export with user-controlled recovery key and restore verification.
- Storage controls for message media, with no automatic deletion of user files without consent.

### M6 — NeaRer-native differentiators
- Nearby Pulse: an opt-in local presence card that shows who is discoverable nearby, approximate connection quality and last-seen age without exposing precise location.
- Store-and-forward inbox: queue encrypted messages for a trusted recipient and deliver when a valid route becomes available, with expiry and clear queued status.
- Relay receipt trail: show route/hop count and delivery confirmation without revealing message contents to relays.
- Local event rooms: temporary, invite-only nearby group chats for hostel/class/market situations, with explicit expiry and owner controls.
- Smart reconnect and low-battery mode with user-visible controls.

## Engineering rules

- No Firebase, Supabase or mandatory cloud service for the offline MVP.
- No phone-number-based identity assumptions; NeaRer ID plus verified cryptographic keys remain authoritative.
- No silent permission escalation, hidden microphone use, or automatic relay participation without clear consent and Android permissions.
- Keep the Android 8+ compatibility target in mind.
- Keep file-transfer implementation owned by the file-sharing workstream; messaging may call the shared transfer interface but must not fork or replace its engine.
- Every feature must have protocol/persistence tests before UI claims it is complete.
