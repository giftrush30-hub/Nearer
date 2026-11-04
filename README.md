# NeaRer

**Your people. Nearby. Offline.**

NeaRer is a native Android app for nearby communication and file sharing over a direct local connection. Its goal is to let compatible Android phones connect and exchange information without mobile data or an internet server.

## Implemented in version 0.2.0

- Kotlin + Jetpack Compose app with a midnight-blue and mint visual identity and custom launcher icon.
- Wi-Fi Direct peer discovery and connection requests.
- App-level identity greeting, matching six-digit pairing code and explicit accept/reject approval.
- One-to-one text chat over the local Wi-Fi Direct socket.
- Message IDs, duplicate suppression and delivery acknowledgements.
- Recent chat history saved locally (up to 500 messages).
- Direct file selection and chunked file transfer.
- Sending and receiving progress indicators, plus sender-side cancellation.
- SHA-256 and byte-count verification before a received file is accepted.
- Received files saved in NeaRer's private app storage and can be opened with a compatible Android app.
- File transfer size limit of 200 MB per file.
- Disconnect control and connection retry handling.
- GitHub Actions workflow builds a debug APK and uploads it as `nearer-debug-apk`.

## Testing and known limitations

The source builds and the debug APK has been packaged by GitHub Actions. **Two-physical-phone testing is still required** before the networking features can be called fully verified.

To test:
1. Install the same NeaRer APK on two Android phones.
2. Turn on Wi-Fi on both phones and allow NeaRer's Nearby Devices permission.
3. Open NeaRer on both phones. On one phone, tap **Find people nearby** and connect to the other.
4. Compare the six-digit code shown on both screens. The receiving phone should accept only when the codes match and the person is recognised.
5. Send messages in both directions, then share a small photo or document and verify it opens on the receiving phone.

Current limitations:
- One peer at a time; no group chats yet.
- No offline message queue or cross-device sync. Local history is stored on each phone.
- File transfers can be cancelled but cannot resume after a disconnect.
- The six-digit code is a manual check, not cryptographic identity verification. No end-to-end encryption has been added at the app protocol layer. Wi-Fi Direct provides link-level WPA2 protection, but this is not a substitute for a reviewed end-to-end security design.
- Discovery and connection behavior can vary by phone hardware, Android release and manufacturer customizations. Phones without Wi-Fi Direct support can install the app but cannot use this transport.

## Build and download

The GitHub Actions workflow runs on pushes to `main` and can also be started manually from the **Actions** tab. After a successful run, download the `nearer-debug-apk` artifact from that workflow run.

To build locally with Android SDK installed:

```bash
gradle assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Remaining release milestones

1. Test discovery, approval, two-way messaging and file transfers on two physical Android devices.
2. Add stronger peer verification and a reviewed end-to-end security design.
3. Improve compatibility, battery use, accessibility and UI polish.
4. Prepare a signed release APK and privacy/data-handling notice.

## Technical direction

NeaRer uses Android Wi-Fi Direct for local peer-to-peer networking. Wi-Fi Direct connects compatible devices without a conventional access point; applications exchange data using sockets after connection. It does not require mobile internet for the direct session, though Wi-Fi, Android permissions and device support are required.
