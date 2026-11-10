# NeaRer

**No Data? We Are NeaRer.**

NeaRer is a native Android app for nearby communication, file sharing and local community features. Direct Nearby Connections sessions are designed to discover and connect devices without requiring an internet connection.

## Current source milestone: 0.5.3

### Existing nearby foundation
- Kotlin + Jetpack Compose Android app.
- Nearby Connections discovery, connection requests and approval.
- One-to-one text chat.
- Nearby Connections and Wi-Fi Direct file transfer code, progress and cancellation.
- Local recent-message history and received-file storage.
- Stable public nearby peer identities.
- Persistent direct-message outbox and duplicate-message protection for direct delivery.

### Mesh development status

**Multi-hop relay is still disabled in the app.** This milestone adds several security and reliability building blocks, but they are not yet connected into an operational mesh transport:

- `MeshCrypto.kt`: P-256 ECDH, HKDF-SHA256, AES-256-GCM recipient encryption and ECDSA packet signatures. Packets are bound to message/origin/destination metadata. The recipient must supply a trusted sender signing key.
- `MeshIdentityVault.kt`: persists P-256 identity keys with private-key material encrypted by an Android Keystore AES-GCM key. App startup creates/loads this local identity and the Nearby screen displays its public signing-key fingerprint. Peer identity exchange and user-facing fingerprint comparison are not yet implemented.
- `MeshRelayPolicy.kt`: four-hop ceiling, TTL decrement, visited-peer loop prevention, duplicate/expiry checks, consent gate and local destination delivery.
- `MeshRouting.kt`: bounded in-memory route learning, direct-neighbour routes, shortest-route preference, stale-route expiry and next-hop invalidation.
- `MeshWireProtocol.kt`: versioned signed identity-hello and route-advertisement frames, plus bounded encrypted relay-frame serialisation. The Nearby Connections transport now exchanges signed identity hellos after an approved connection and propagates authenticated-by-direct-peer route hints. A signature proves possession of a key; the Nearby connection authentication code still needs to be compared by users to verify the session, and a durable peer-key trust/pinning flow is not yet complete.
- `MeshDeadDropStore.kt`: SQLite persistence for opaque encrypted packets, duplicate insertion protection, expiry pruning and retry counters.
- `MeshStatusPanel.kt`: premium blue-and-gold mesh status display. Nearby peers reflect discovery state; route count comes from the in-memory route table and Dead Drop count comes from the SQLite store. Neither is populated by transport forwarding yet.
- JVM tests cover encryption/decryption, wrong-recipient and tamper rejection, route selection/expiry, and relay-policy limits.

**Not yet operational:** trusted-key verification and peer identity exchange, a versioned wire serialiser between phones, packet callbacks in Nearby Connections, authenticated route advertisements, automatic multi-hop forwarding, real Dead Drop retry/acknowledgement integration, and real-device testing. The current app intentionally keeps multi-hop relay disabled. Do not describe the current APK as a working mesh network.

### NeaRer visual identity
The app's existing brand palette is midnight navy, deep blue panels, electric/clear blue accents and gold. Mesh visuals should use that same blue-and-gold identity, with cyan reserved for transmission effects. Network animations must reflect real discovered peers, actual forwarding and confirmed delivery; simulated activity must not be presented as live network state.

### Community and multiplayer additions
- Create a local group with a six-digit join code.
- Join a group using its code.
- Persist local group membership and up to 300 group messages on the phone.
- Send group messages directly to connected peers whose names are in the locally stored group member list.
- Send a nearby broadcast to currently connected NeaRer peers.
- Invite a nearby peer to a live two-player tic-tac-toe match.
- Synchronise legal board moves over the Nearby Connections message channel.
- In-game text chat while the match is active.

## Important operating details and limitations

- These group and game features currently use the Nearby Connections transport. They are not available over the legacy single-peer Wi-Fi Direct chat socket.
- Group join codes are manually shared. This milestone does not provide a public group directory, accounts, cloud sync, group admin roles, moderation, or end-to-end group identity verification.
- Group messages are sent to the sender's currently connected Nearby Connections endpoints. Only devices that have locally joined the matching code display those group messages.
- Nearby broadcasts reach currently connected peers; they are not public internet posts.
- The game is a real two-player tic-tac-toe implementation, not a catalogue of finished multiplayer games. A match is live and does not currently recover after either phone disconnects.
- No in-game voice chat, voice notes, voice calls, video calls, game matchmaking service, or cross-device message queue has been implemented yet.
- File transfers have no fixed application-level size cap, but available storage, Android document providers, hardware and connection stability still constrain actual transfers. Interrupted transfers cannot yet resume.
- Nearby Connections provides encrypted peer connections, but that does not by itself provide end-to-end encryption across multiple relay devices. Recipient-key management, message authentication, replay protection, relay privacy and group identity need a reviewed design before forwarding is enabled.
- Two-physical-phone testing is required before these features can be described as field-verified.

## Build policy

GitHub Actions runs JVM unit tests only. It does not compile the Android app, assemble an APK, or upload APK artifacts. APK creation is intentionally deferred until the project owner explicitly declares development complete and authorizes an APK build.

## Suggested two-phone test

1. Install the same APK on two Android phones once an authorized build is available.
2. Enable Bluetooth and Wi-Fi on both phones and grant NeaRer's nearby-device permissions.
3. Search from both phones, connect and compare the displayed authentication code before accepting.
4. Create a group on one phone, share the six-digit code, and join from the other phone.
5. Send messages both ways in the group and send a nearby broadcast.
6. Start a tic-tac-toe invitation, accept it on the other phone, play alternating turns and exchange in-game chat.
7. Repeat after disconnecting and reconnecting to identify lifecycle and recovery problems.

## Technical references

- [Nearby Connections overview](https://developers.google.com/nearby/connections/overview)
- [Nearby Connections: exchange data](https://developers.google.com/nearby/connections/android/exchange-data)
- [Android Room persistence guidance](https://developer.android.com/training/data-storage/room)
