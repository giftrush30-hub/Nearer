# NeaRer

**Your people. Nearby. Offline.**

NeaRer is a native Android app being built for nearby communication and file sharing over a direct local connection. Its goal is to let compatible Android phones connect and exchange information without mobile data or an internet server.

## Implemented in the current source

- Kotlin + Jetpack Compose Android app with a midnight-blue and mint visual identity.
- Wi-Fi Direct peer discovery and connection requests.
- App-level identity greeting and explicit accept/reject approval.
- One-to-one text chat over the local Wi-Fi Direct socket.
- Message IDs, duplicate suppression and delivery acknowledgements.
- Recent chat history saved locally (up to 500 messages).
- Direct file selection and chunked file transfer.
- SHA-256 and byte-count verification before a received file is accepted.
- Received files saved in NeaRer's private app storage, with an action to open them using a compatible Android app.
- File transfer size guardrail of 200 MB.
- GitHub Actions workflow builds a debug APK and uploads it as `nearer-debug-apk`.

## Testing and known limitations

The code has **not yet been fully validated on two physical Android phones**. A successful automated build confirms compilation and APK packaging, not real-world Wi-Fi Direct compatibility or successful transfers. Device testing is still required.

Current prototype limitations:
- One peer at a time; no group chats yet.
- Chat works only while the two devices are connected. Recent local history is retained, but there is no offline message queue or cross-device sync.
- File transfers do not yet support resume or user cancellation.
- No end-to-end encryption or cryptographic identity verification has been added at the app protocol layer. Wi-Fi Direct provides link-level protection, but this is not a substitute for a reviewed end-to-end security design.
- Device discovery and connection behavior can vary by phone hardware, Android release and manufacturer customizations.

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

1. Confirm a clean APK build from the latest commit.
2. Test discovery, approval, two-way messaging and file transfers on two physical Android devices.
3. Add transfer cancellation/resume, stronger peer verification and an end-to-end security design.
4. Improve compatibility, battery use, accessibility and UI polish.
5. Prepare a signed release APK and privacy/data-handling notice.

## Technical direction

NeaRer uses Android Wi-Fi Direct for local peer-to-peer networking. Wi-Fi Direct can connect compatible devices without a conventional access point; after connection, applications exchange data using sockets. It does not require mobile internet for the direct session, though Wi-Fi must be enabled and Android permissions and device support are required.
