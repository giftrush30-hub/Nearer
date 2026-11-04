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


## NeaRer Game Zone — Ludo foundation (0.6.0 development branch)

The Game Zone is being developed as a collection of games with one shared presentation and match architecture.

### Locked product decisions
- Game collection, delivered one complete game at a time.
- First game: Ludo.
- Traditional rules, with computer opponents available.
- Premium, colourful board presentation and smooth play.
- Nearby offline multiplayer is the next multiplayer milestone; internet multiplayer follows later.
- Offline play must not require account registration or mobile data.

### Current Ludo implementation
- A standalone Ludo rules engine for 2–4 seats.
- Four colours and four tokens per player.
- Six required to bring a token out of the yard.
- Exact roll required to finish.
- Safe squares, captures, extra turns on six/capture/home, and win detection.
- Basic computer move selection.
- A premium board screen with dice control, token selection, player-count selection and computer opponents.
- Game Zone entry integrated into the app home screen.

### Not yet implemented or verified
- Nearby Ludo invitations, room hosting, multiplayer state synchronisation and in-game Ludo chat.
- Online matchmaking, accounts, server-authoritative dice and global leaderboards.
- Advanced Ludo AI, custom rule variants, sound, haptics, reconnect recovery and persistent match resume.
- Automated unit tests and two-device gameplay verification.

### Planned Game Zone delivery
1. Complete and test Ludo rules and board layout.
2. Polish animations, responsive layouts, sound settings and computer strategy.
3. Add nearby 2–4 player rooms over Nearby Connections with authoritative host validation and in-game chat.
4. Test on multiple real Android phones, including disconnect/reconnect cases.
5. Add the next complete games (Chess/Checkers, Quiz Battle, Snake and Ladders, Word Battle, Mini Racing).
6. Add online multiplayer using a server-backed match service, player identities and matchmaking.
7. Add cosmetic achievements and leaderboards without pay-to-win mechanics.

The current 0.6.0 work is a development milestone, not a claim that nearby or online Ludo multiplayer is already available.
