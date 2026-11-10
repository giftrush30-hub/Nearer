# NeaRer integration checkpoint

This branch is a protected integration workspace. It is not a release and must not be merged to main until CI and device tests pass.

## Integrated into this branch

- Latest launcher crash fix, approved blue/gold branding, chat-history baseline and File Centre work from the current design candidate.
- First-run onboarding wired into app startup; display name, avatar choice, optional profile photo and onboarding answers are stored locally.
- More screen now displays the saved NeaRer profile, local identity ID, avatar choice and optional photo, with display-name editing.
- Premium animated Ludo board and visible dice UI.
- Premium Chats extracted into a dedicated Compose screen and used by the Chats navigation tab.
- HyperTransfer foundation classes: bundle manifest/codec/session, manifest fragments, bounded transfer policies, memory/thermal buffer tuning, resume checkpoints, checkpoint journal, prefix verification, resume negotiation codecs/policies and resumable stream writer.
- HyperTransfer unit-test suite and engineering notes copied into this branch.
- Receive stream-copy fix: handles providers that return zero from bulk reads, computes SHA-256 while copying, validates the staged byte count, and only deletes the provider payload after validation.
- Provider-backed outgoing files are now hashed during the staging copy so they do not need a second full read for hashing.
- Local security primitives added: Android Keystore AES-GCM data cipher, device-bound P-256 signing identity, trust-on-first-use peer-key pin store, and encrypted direct-chat outbox.
- OpenSend/AppShare engine files added: app catalogue and APK archive validation, Android package installer, temporary local hotspot, token-protected HTTP download server, QR encoder, foreground service, session manager and Share Hub Compose screen. Required FileProvider paths, service/receiver manifest entries and QR dependency are present.

## Important integration work still required

- HyperTransfer resume/checkpoint primitives are present, but the Nearby transport does not yet negotiate and apply resume offsets to the actual FILE payload lifecycle. Do not describe interrupted transfers as fully resumable yet.
- Identity and encryption primitives are present, but the Nearby handshake, peer key pinning, message queue migration and message persistence are not yet fully wired end-to-end. Do not claim authenticated or end-to-end encrypted chat yet.
- Share Hub components and service are present, but the new Share Hub screen is not yet connected to the main navigation and app-share receive/install flow. Existing File Centre and system sharing remain available.
- Full integration of remaining Fast Share hardening and dependent PR changes is not complete.
- Run the complete unit-test/build workflow against this exact branch head, then resolve every failure.
- Real-device testing is still required for two-phone discovery, chat, large-file send/receive, interrupted transfers, OpenSend hotspot compatibility, AppShare installation and Ludo.

## Known limitations to keep visible

- Delivery acknowledgements are not read receipts.
- Multi-hop mesh relay remains paused until recipient routing and end-to-end encryption are integrated.
- Internet multiplayer and live voice calls are not part of the current offline Ludo implementation.
- No speed or unlimited-size claims should be made without device measurements.
- AppShare's complete installed-app catalogue uses Android package-visibility access and may require Google Play policy review before Play Store publication.
- Android still controls installation confirmation and compatibility; NeaRer cannot silently install APKs.

## Release gate

Only publish an APK after unit tests, Android emulator launch tests, and real-device two-phone chat/file-transfer tests pass. A workflow run from an earlier commit is not evidence for this branch head.
