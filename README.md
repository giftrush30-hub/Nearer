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
- Optional community mesh text relay (up to four hops; relaying devices can read relayed text).

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
- The community feature layer is not a substitute for a reviewed end-to-end security design. Nearby Connections provides encrypted peer connections, but group identity, membership verification and relay privacy need further security work.
- Two-physical-phone testing is required before these features can be described as field-verified.

## Build and download

The GitHub Actions workflow builds a debug APK on pushes to `main` and uploads an artifact named `nearer-debug-apk` after a successful run. Open the repository's **Actions** tab, select the latest successful **Android debug APK** run, and download its artifact.

Local build:

```bash
gradle assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Suggested two-phone test

1. Install the same APK on two Android phones.
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


## OpenSend and AppShare foundation (draft branch)

The `feature/opensend-appshare-foundation` branch adds reusable Android building blocks:
- `AppShareCatalog` lists visible installed apps and prepares base + split APK sets for sharing.
- `OpenSendHotspot` requests an Android local-only hotspot and exposes the OS-provided network credentials.
- `OpenSendHttpServer` serves a local branded download page with a session token and HTTP Range support.
- Unit tests cover OpenSend session-token access control and byte-range downloads.

These are engine foundations only. The Share hub screens, QR presentation, Nearby transfer wiring, receiving-side APK verification and PackageInstaller flow, foreground lifecycle, and physical-device testing are still required before the features are ready for users.

The installed-app inventory permission (`QUERY_ALL_PACKAGES`) is policy-sensitive and requires a Google Play eligibility review if used in a Play-distributed build. OpenSend captive-portal auto-open and long-distance hotspot coverage are not guaranteed.
