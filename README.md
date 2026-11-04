# NeaRer

**Your people. Nearby. Offline.**

NeaRer is a native Android app being built for nearby communication and file sharing over a direct local connection. Its design goal is to let two compatible Android phones connect without mobile data or an internet server.

## Current implementation

- Kotlin + Jetpack Compose Android application with a dark midnight / mint visual identity.
- Wi-Fi Direct peer discovery and connection requests.
- App-level identity greeting and explicit accept/reject approval.
- Direct one-to-one text messages over the Wi-Fi Direct socket.
- Message IDs and delivery acknowledgements.
- Direct file transfer using chunked Base64 frames over the same local socket.
- Received files are stored in NeaRer's private app storage and can be opened through Android's file-sharing provider.
- GitHub Actions workflow builds a debug APK and uploads it as `nearer-debug-apk`.

## Important testing status

The features above are implemented in source code, but they are **not yet certified as working on physical phones**. The app needs two compatible Android devices with NeaRer installed, Wi-Fi enabled, and nearby-device permission granted. Android may also show its own Wi-Fi Direct invitation prompt.

The current prototype supports one peer at a time. Chat history is currently held in memory and is not yet restored after the app is closed. File transfer currently has no resume-after-disconnect support or end-to-end encryption layer. Wi-Fi Direct provides link-level protection, but the app still needs stronger peer authentication and a security review before public release.

## Build

The GitHub Actions workflow runs on pushes to `main` and can also be started manually from the **Actions** tab. After a successful run, download the `nearer-debug-apk` artifact from that workflow run.

To build locally with Android SDK installed:

```bash
gradle assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Next engineering milestones

1. Confirm clean APK build and test discovery, approval, messages and file transfers on two physical Android devices.
2. Add durable local message/file history, retry handling, transfer progress and cancellation.
3. Add stronger peer verification and secure session design before public release.
4. Improve compatibility, accessibility, battery use and UI polish.
5. Prepare signed release APK and a clear privacy/data-handling notice.

## Technical direction

NeaRer uses Android Wi-Fi Direct for local peer-to-peer networking. Wi-Fi Direct can connect compatible devices without a conventional access point; after connection, apps exchange data using sockets. Actual support and behavior can vary by phone hardware, Android version and manufacturer software.
