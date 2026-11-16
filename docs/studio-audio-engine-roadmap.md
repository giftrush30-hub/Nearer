# NeaRer Studio low-latency audio engine

## Current implementation

Studio now has two live-monitor backends behind a Kotlin selection policy:

- **Oboe native duplex engine:** used on Android API 27+ for default input and selected USB input. AAudio device-ID routing is available from API 26; Oboe's AAudio backend begins at API 27. After opening, the engine verifies the actual input device ID and refuses to silently substitute another microphone. It opens a low-latency output stream first, then opens the input at the output stream's negotiated sample rate. It requests exclusive sharing and falls back to shared mode if exclusive is unavailable.
- **Java compatibility backend:** the existing AudioRecord/AudioTrack path remains available when native startup fails, on Android API 26, and when explicit USB device routing is not supported by the native API level.

The Oboe callback path uses separate single-producer/single-consumer float PCM rings for output monitoring and Kotlin pitch analysis. The input callback copies samples into the rings; the output callback drains the playback ring and fills any missing frames with silence. The playback consumer discards stale queued frames above four output bursts, and the pitch-analysis consumer discards stale analysis frames above a bounded queue depth, preventing old audio from accumulating into hundreds of milliseconds of perceived delay. The engine starts input before output to avoid avoidable startup underruns. Callback code performs no file/network I/O, memory allocation, sleeping, or mutex locking. Pitch estimation remains outside the real-time callback on the Kotlin coroutine.

The native diagnostics report the negotiated sample rate, output burst and buffer size, actual low-latency and exclusive-sharing grants, actual input/output device IDs, Oboe xrun count when available, callback-level output underruns, capture-ring drops, stale-playback drops, stale-analysis drops and disconnection state. The UI must not interpret buffer duration as measured round-trip latency.

## Android and Oboe compatibility

- The app minimum SDK is 26. Oboe supports older Android versions, but this integration deliberately uses the native path from API 27 (AAudio) and preserves Java fallback on API 26.
- Oboe uses AAudio on Android API 27+ when recommended. The selected Android input-device ID is passed to Oboe; the opened stream's actual device ID is checked against the requested ID. If Android/Oboe cannot honour it, native startup fails safely and the existing Java path tries Android's preferred-device routing.
- Oboe 1.11.0 is consumed as a Google Maven Prefab dependency. The app enables Prefab, CMake, NDK 27.2.12479018 and packages arm64-v8a, armeabi-v7a and x86_64.
- The engine requests Float mono streams, enables Oboe's medium-quality sample-rate converter where needed, and uses the output stream's actual negotiated sample rate for input and pitch detection. The package name is explicitly attributed on both streams for modern Android capture permission checks. When Android reports unprocessed-input support, the native stream requests Oboe's Unprocessed input preset on API 28+; API 27 uses the supported VoiceRecognition preset, and the UI labels this accurately. The Java compatibility path can request Android's unprocessed source where supported. Actual stream properties are queried after opening; requested low-latency/exclusive modes are not treated as guaranteed.

## Lifecycle and recovery

A route disconnect triggers Oboe's error callback and marks the engine disconnected. The Kotlin control coroutine stops and closes both streams outside the real-time callback, then makes one bounded native reopen attempt when a headphone output is still present. If native restart fails, it falls back to the Java compatibility path. A selected USB input is re-verified by device ID on every native open. USB input removal is also observed by the parent recorder's Android device callback, which updates the selected input and restarts the monitor with the new selection.

The monitor observes headphone add/remove events while it is open and stops capture when the Activity leaves the foreground; returning to the app requires the user to explicitly enable monitoring again. It refuses to start mic-to-output monitoring without a detected headset/headphone/USB audio output, and stops if that output disappears, preventing speaker-to-microphone feedback. This is a device-class check rather than a guarantee that every connected accessory is physically worn or acoustically isolated.

The Java capture path is marked privacy-sensitive on Android 11/API 30+; microphone capture still starts only after the user's explicit in-screen opt-in and is never saved by the pitch monitor. Oboe's current builder API does not expose the same privacy-sensitive switch, so this is not claimed for the native backend.

## Validation still required

The code is integrated into the Android build. Automated checks compile the NDK target, run JVM tests (including PCM frame assembly and signal-level vocal processor tests), and build/upload the debug APK. This does not prove physical-device performance. Required device testing includes:

1. Phone built-in microphone and speaker/headphones.
2. USB microphone plus wired/USB headphones on Android API 27+; verify the displayed input device ID matches the selected device.
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
