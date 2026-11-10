# NeaRer integration checkpoint

This branch is a protected integration workspace. It is not a release and must not be merged to main until CI and device tests pass.

## Integrated in this checkpoint
- Latest crash/launcher branding and chat-history baseline from the current design/refined-slate-blue-brand candidate.
- First-run onboarding flow wired into app startup; display name, avatar choice and optional profile photo are stored locally.
- Premium animated Ludo board and visible dice UI from the onboarding/Ludo feature branch.
- Updated File Centre selection and browsing implementation.
- Android validation workflow now includes this integration branch and a direct Gradle emulator-test command.

## Still to integrate and validate
- Phone-free identity and encrypted local chat history (including wiring the cipher and key store into persistence).
- HyperTransfer resume/checkpoint engine and transport integration.
- File digest/hash staging and zero-read-safe receive-copy fix.
- OpenSend/AppShare foundations.
- Premium Chats navigation/profile polish from its dependent branch.
- Full cross-feature integration and tests, including Android 12 / API 31 launch.

## Known limitations to keep visible
- Read receipts are not implemented by delivery acknowledgements.
- Multi-hop mesh relay remains paused until recipient routing and end-to-end encryption are integrated.
- Internet multiplayer and live voice calls are not part of the current offline Ludo implementation.
- No speed or unlimited-size claims should be made without device measurements.

## Release gate
Only publish an APK after unit tests, Android emulator launch tests, and real-device two-phone chat/file-transfer tests pass.