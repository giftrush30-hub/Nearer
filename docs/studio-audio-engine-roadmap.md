# NeaRer Studio low-latency audio engine decision

## Current Java/Kotlin path

The live pitch monitor now reads Android's reported output sample rate and frames-per-buffer property, checks the selected USB input's advertised sample rates when available, requests a conservative buffer based on Android's minimum, three bursts, and the pitch-processing block, and reports the active input/output route, estimated output-buffer time, and AudioTrack underrun count.

This is a diagnostic and compatibility improvement, not proof of low round-trip latency. Blocking Java reads/writes and coroutine scheduling still add variable delay. AudioTrack's low-latency mode is only a request; Android notes that route and device support determine the actual path.

## Oboe evaluation

Android's official low-latency guidance recommends Oboe for the lowest-latency native audio path. Oboe uses AAudio on Android 8.1/API 27 and newer and falls back to OpenSL ES on earlier supported versions. The recommended design is:
- request low-latency performance and exclusive sharing, while handling a shared-mode fallback;
- use the device-native sample rate and frames-per-burst;
- use input and output data callbacks, not blocking operations in callbacks;
- use a small single-producer/single-consumer PCM ring buffer between capture and playback;
- monitor xrun/underrun counts and grow the buffer conservatively when needed;
- recreate streams on route changes and keep the Java/Compose UI outside the real-time callback.

## Why Oboe is not enabled in this patch

The existing app is a Kotlin/Media3 application with no current NDK/CMake native audio module. Introducing Oboe requires the Android NDK toolchain, CMake, ABI packaging, native stream lifecycle, USB route handling, and device-specific testing. Adding only a Maven dependency or a native stub would not improve the actual monitor and could create a false claim. The correct next implementation is a small isolated native monitor engine behind a Kotlin interface, with the current AudioRecord/AudioTrack implementation retained as fallback.

## Validation still required

Automated Android build and JVM policy tests can verify selection logic and app integration. Only physical-device loopback tests can measure round-trip latency and glitch rate. Test phone built-in mic, wired headset, USB microphone plus wired/USB headphones, route disconnect/reconnect, screen lock, competing recorder apps, and at least one older and one newer Android device. Do not advertise a millisecond latency figure until those measurements exist.
