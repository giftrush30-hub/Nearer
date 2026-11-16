# NeaRer Studio low-latency audio engine

## Current implementation

Studio now has two live-monitor backends behind a Kotlin selection policy:

- **Oboe native duplex engine (new prototype):** used on Android API 27+ for the default input, and API 28+ when a specific USB input is selected. It opens a low-latency output stream first, then opens the input at the output stream's negotiated sample rate. It requests exclusive sharing and falls back to shared mode if exclusive is unavailable.
- **Java compatibility backend:** the existing AudioRecord/AudioTrack path remains available when native startup fails, on Android API 26, and when explicit USB device routing is not supported by the native API level.

The Oboe callback path uses separate single-producer/single-consumer float PCM rings for output monitoring and Kotlin pitch analysis. The input callback copies samples into the rings; the output callback drains the playback ring and fills any missing frames with silence. Callback code performs no file/network I/O, memory allocation, sleeping, or mutex locking. Pitch estimation remains outside the real-time callback on the Kotlin coroutine.

The native diagnostics report the negotiated sample rate, output burst and buffer size, Oboe xrun count when available, callback-level output underruns, dropped capture frames, and disconnection state. The UI must not interpret buffer duration as measured round-trip latency.

## Android and Oboe compatibility

- The app minimum SDK is 26. Oboe supports older Android versions, but this integration deliberately uses the native path from API 27 (AAudio) and preserves Java fallback on API 26.
- Oboe's AAudio setDeviceId routing is respected from API 28; therefore explicit USB routing stays on the Java path below API 28. On API 28+, the selected Android input-device ID is passed to Oboe.
- Oboe 1.11.0 is consumed as a Google Maven Prefab dependency. The app enables Prefab, CMake, NDK 27.2.12479018 and packages arm64-v8a, armeabi-v7a and x86_64.
- The engine requests Float mono streams and uses the output stream's actual negotiated sample rate for input and pitch detection. When Android reports unprocessed-input support, the native stream requests Oboe's Unprocessed input preset; otherwise it requests VoiceRecognition. Actual stream properties are queried after opening; requested low-latency/exclusive modes are not treated as guaranteed.

## Lifecycle and recovery

A route disconnect triggers Oboe's error callback and marks the engine disconnected. The Kotlin screen stops the native session and asks the user to reconnect and restart. Automatic route reopen is not enabled yet; a safe reopen needs a serialized control-thread state machine that handles both streams and re-queries device properties. The Java fallback remains available when initial native stream creation fails.

## Validation still required

The code is integrated into the Android build and automated checks will compile the NDK target as well as run JVM tests and build the APK. This does not prove physical-device performance. Required device testing includes:

1. Phone built-in microphone and speaker/headphones.
2. USB microphone plus wired/USB headphones on Android 28+.
3. USB disconnect/reconnect while monitoring.
4. Screen lock/unlock and app background/foreground.
5. Competing microphone apps and microphone permission revocation.
6. At least one older Android device and one newer device.
7. Acoustic or electrical loopback measurements for round-trip latency and glitch rate.

Do not publish a millisecond latency claim until those measurements exist. Bluetooth output can add substantial latency even when the input and processing path are native.

## Architecture sources

- Android low-latency checklist: https://developer.android.com/games/sdk/oboe/low-latency-audio
- Android app audio latency guidance: https://source.android.com/docs/core/audio/latency/app
- Oboe integration guide: https://github.com/google/oboe/blob/main/docs/GettingStarted.md
- Oboe stream and callback guide: https://github.com/google/oboe/blob/main/docs/FullGuide.md
- Oboe stream disconnect/error callbacks: https://github.com/google/oboe/blob/main/include/oboe/AudioStreamCallback.h
