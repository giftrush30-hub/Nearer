# NeaRer

**Your people. Nearby. Offline.**

NeaRer is an offline-first Android communication and file-sharing app. The goal is to let people discover, approve and connect to nearby devices, then exchange messages and files over a direct local connection without relying on mobile data.

## Project status

This repository now contains the first native Android application scaffold:

- Kotlin + Jetpack Compose Android app
- Initial NeaRer home screen and visual identity
- Android Gradle build configuration
- GitHub Actions workflow to build and upload a debug APK

The current home screen is an early foundation. Nearby discovery, connection approval, messaging, delivery receipts and file transfer still need to be implemented and tested between real Android devices. The buttons are not yet connected to those networking features.

## Build

The GitHub Actions workflow runs on pushes to `main` and can also be started manually from the **Actions** tab. When a build succeeds, download the `nearer-debug-apk` artifact from that workflow run.

To build locally with Android SDK installed:

```bash
gradle assembleDebug
```

The APK output is:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Planned development sequence

1. Nearby device discovery using Android's local peer-to-peer networking APIs.
2. Explicit connection approval and secure session setup.
3. One-to-one chat with message IDs, acknowledgements and duplicate protection.
4. Direct file transfer with progress, cancellation and integrity checks.
5. Multi-device testing, permissions review and release APK preparation.

## Technical direction

NeaRer is being built as a native Android app so it can use Android's device-to-device networking capabilities. A local connection should not require mobile internet, although Android may request Bluetooth, nearby-device or Wi-Fi permissions depending on the feature and OS version.
