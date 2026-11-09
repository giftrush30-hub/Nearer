# NeaRer Product Architecture
## Messaging-first product blueprint

**Status:** Product direction approved; implementation plan  
**Branch:** `product/messaging-first-blueprint`  
**Brand:** NeaRer — “NeaRer You, NeaRer Everything.”

## 1. Product definition

NeaRer is a **messaging app first**. Its primary job is helping people communicate. Two powerful destinations extend that experience:

1. **Chats** — private and group conversations.
2. **Share** — a dedicated, high-capability file-transfer experience.
3. **Games** — social games that people can invite friends to play.

NeaRer Mesh is connection technology underneath the experience, not a fourth destination.

**Short public explanation:** “NeaRer is a messaging app where you can chat, share anything and play with friends — including supported offline features.”

## 2. Approved navigation

Use three primary bottom-navigation destinations, in this order:

| Destination | Purpose | Priority |
|---|---|---|
| Chats | Default launch destination; conversations and contact discovery | Primary |
| Share | Dedicated send/receive file-transfer workspace | Primary |
| Games | Games with friends and computer opponents | Supporting |

Do not add a separate Mesh tab, Bluetooth tab, Wi-Fi tab, relay centre, or Status/Updates tab in this product phase. Status/Updates is explicitly deferred.

Profile and settings are accessed from the Chats header/profile affordance, not as another primary tab.

## 3. Chats information architecture

The Chats home should provide:
- Conversation list, unread state, last-message preview and timestamp.
- Search across conversations and people.
- New-chat action and NeaRer ID lookup.
- Individual and group conversations.
- Clear, unobtrusive online / nearby / waiting connectivity status when relevant.

A conversation should support, as capabilities become implemented and verified:
- Text messages and delivery state.
- Photos, videos, documents and other ordinary attachments.
- Voice notes.
- Group messages and group administration.
- Voice/video calls when a supported online calling service exists.
- A small expandable Mesh activity visual when it helps explain offline delivery.

Do not expose transport selection (Bluetooth, Wi-Fi Direct, Wi-Fi Aware, etc.) in normal chat flow. NeaRer chooses the available transport.

## 4. Share information architecture

Share is a dedicated destination for substantial transfers, distinct from lightweight chat attachments.

Its first screen should make two actions unmistakable:
- **Send**
- **Receive**

Supporting areas:
- File picker and category shortcuts (photos, videos, audio, documents, apps where Android permits, and folders where supported).
- Nearby recipient/device selection.
- Active transfer queue with per-file progress, speed where measured, pause/cancel where supported.
- Transfer history and received files.
- Clear errors and recovery instructions.

Product requirements:
- No arbitrary application-level 200 MB cap.
- Be explicit that practical limits still depend on free storage, Android providers/filesystems, device capabilities and transport reliability.
- Do not claim resumable transfers until byte-range persistence and recovery are implemented and tested.
- Queueing multiple files is not the same as simultaneous multi-device transfer. Benchmark both separately.
- Early target: validate at least five simultaneous receiving devices on real hardware before advertising that capability. Scale tests must report physical-device results separately from simulations.

## 5. Games information architecture

Games is a social destination, not a separate social network.
- Show available games and recent/active games.
- Invite a person or group from an existing conversation.
- Keep game invitations and results connected to Chats.
- Support in-game text chat where implemented.
- Add voice communication only after the underlying calling capability is reliable.

Ludo is the first planned flagship game. Do not present a game as complete unless its rules, dice, token movement, reconnect behaviour and multiplayer state have been implemented and tested. Existing game code and README descriptions must be reconciled during the next source audit before deciding what is already complete.

## 6. NeaRer ID and contacts

- NeaRer ID is the primary app identity; a display name is editable and can be duplicated.
- A phone number should not be compulsory for the product identity.
- IDs must be unique and generated securely; examples in design documents are placeholders, not assigned IDs.
- Contact discovery should support adding/searching by NeaRer ID and exchanging a contact card through supported nearby methods.
- Separate a durable identity key from temporary Nearby Connections endpoint IDs and device network addresses.
- Define secure account/identity recovery before promising that identity survives reinstall or phone replacement.

## 7. NeaRer Mesh: invisible complexity, visible life

Mesh is one coherent connection experience. Its engineering may include discovery, route selection, relaying, deduplication, retries, store-and-forward and transport switching, but users should not need to learn those terms.

Visual direction:
- Midnight/deep navy foundation with NeaRer blue, electric cyan and restrained gold.
- Soft breathing device nodes.
- Luminous connection paths and travelling particles.
- A route grows into view as real relay peers are discovered.
- A relay node illuminates only when the device actually forwards the message.
- A broken path dims; a replacement route appears only after it is actually established.
- Destination node responds when the destination acknowledges receipt.

The animation must be driven by real network events, not decorative fiction. Distinguish:
- Searching for recipient/route.
- Route found.
- Forwarding to a specific next hop.
- Waiting for another relay/path.
- Destination received and acknowledged.
- Expired/failed delivery.

Never show “Delivered” merely because the sender handed a packet to the first relay. Do not reveal other people's identities or precise locations unnecessarily. Avoid exposing a detailed route map by default; expand it only when useful and privacy-safe.

## 8. Offline and online behaviour

The offline-first foundation remains independent of any future online service:
- Nearby direct communication works only when a compatible reachable peer path exists.
- Multi-hop delivery requires a real chain of participating, compatible relay devices.
- Store-and-forward can hold encrypted packets until a route becomes available; it cannot guarantee immediate delivery across disconnected regions.
- Android background execution, permissions, radio concurrency and manufacturer battery restrictions must be handled honestly.
- Relay participation requires clear explanation, informed consent and Android permissions. Never silently enroll devices or bypass operating-system controls.

When internet-backed capabilities are introduced, make the online service path explicit and keep it architecturally separate from local offline delivery.

## 9. Security and trust requirements

Before enabling broad multi-hop private messaging:
- Define authenticated NeaRer identities and key lifecycle.
- Implement and review end-to-end encryption; relays should forward ciphertext, not read private message content.
- Authenticate sender and recipient, prevent replay and tampering, and bind acknowledgements to message IDs.
- Use bounded hop count / TTL, deduplication and expiry.
- Protect relay queues at rest and apply retention limits.
- Add block/report and abuse controls without exposing private content.
- Make relay consent, battery impact and privacy controls understandable.

A secure Nearby Connections session is not, by itself, proof that end-to-end encrypted multi-hop messaging is complete.

## 10. Visual and interaction system

- One coherent NeaRer visual language across Chats, Share and Games.
- Premium blue-and-gold identity, readable typography, generous spacing and accessible contrast.
- Motion should communicate state and remain lightweight; respect reduced-motion and battery-saving conditions.
- Use clear empty states, loading states, permission explanations and failure recovery.
- Do not add dashboard clutter or duplicate the same feature in multiple destinations.

## 11. Implementation sequence

1. **Repository reconciliation:** inspect current screens, state ownership, navigation, manifest, transport, persistence, tests and README. Resolve inconsistencies before refactoring.
2. **App shell:** introduce a stable three-destination navigation shell without rewriting working transport logic.
3. **Chats foundation:** separate conversation list, conversation view, contact discovery and profile/ID responsibilities.
4. **Share workspace:** dedicated Send/Receive flows; preserve existing transfer protocol and add tests before changing it.
5. **Connection/Mesh engine:** separate transport events from message routing; implement authenticated recipient addressing, route discovery, relay forwarding, deduplication, bounded TTL and delivery receipts.
6. **Living Mesh visualisation:** animate only verified discovery, forwarding and delivery events.
7. **Games integration:** invite and return to conversations cleanly; validate the actual game implementation before expanding it.
8. **Quality gates:** automated tests, APK build, two-phone tests, then 5/10/20-device staged tests. Record hardware, Android version, transport, delivery rate, latency, throughput, battery and recovery.

## 12. Engineering guardrails

- Preserve the existing working Android app; avoid a wholesale rewrite.
- Keep UI, identity, messaging, file transfer, games and transport responsibilities separate.
- Do not claim a feature is implemented because it appears in a design or roadmap.
- Do not report a competitive progress percentage without defined, measured criteria.
- Do not advertise “unlimited”, “instant”, “100 devices” or “works everywhere” without verified device testing.
- Coordinate parallel work through small, clearly scoped branches and review changes before merging.
- Keep README, version number, tests and APK build status aligned with actual source.

## 13. Current-source observations to verify

The current README identifies source milestone 0.5.3 and describes a Kotlin/Jetpack Compose Android app with Nearby Connections, Wi-Fi Direct, chat, file transfer, local groups and a small multiplayer game. It also documents important limitations including no end-to-end group identity verification and no resumable interrupted transfers.

The current MainActivity is a large activity containing transport lifecycle, connection state, chat/group/game/transfer state and UI-related state. This is a maintainability concern, but refactoring must follow a full source and test audit.

The README and current source descriptions of the multiplayer game should be reconciled before any game feature is advertised as complete.

These observations are an initial source review, not a successful build or real-device verification.

---
**Product decision:** NeaRer is a messaging app first. Chats is home. Share is dedicated. Games is social. Mesh is the living technology underneath.
