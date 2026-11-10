# NeaRer Fast Share performance and release-readiness

## Current transfer path

Fast Share creates an Android LocalOnlyHotspot, exposes a short-lived local HTTP download page protected by a random access key, and streams each selected ContentResolver URI directly to the receiving browser. Known-length sources support HTTP byte ranges; unknown-length sources use HTTP/1.1 chunked transfer coding. The sender does not make a second complete copy of the shared file.

## Throughput work in this batch

- Increased bounded source read buffers from 256 KiB to 512 KiB for known-length and chunked streams.
- Increased the HTTP response buffer to 256 KiB.
- Requested a 1 MiB TCP send buffer and 256 KiB receive buffer on accepted sockets; the operating system may clamp these values.
- Increased the listener backlog to 32 while keeping the worker pool bounded and the fair download gate at five active transfers.
- Throttled UI progress callbacks to 4 MiB intervals so a fast link does not flood the main thread with hundreds of UI updates per second.
- Added JVM tests for exact data preservation, interrupted transfers, and progress cadence.

The maximum planned application-level buffering for five simultaneous downloads is approximately 3.75 MiB (five 512 KiB source buffers plus five 256 KiB output buffers), excluding Android/provider/socket buffers and normal runtime overhead.

## What 280 MB/s means

280 MB/s is approximately 2.24 Gbit/s of application payload. This is an engineering target, not a measured result or a promise. The current Android LocalOnlyHotspot API lets Android and the phone vendor choose the operating band/channel and hotspot configuration. Real throughput is constrained by both phones' Wi-Fi radios, channel width, signal quality, interference, thermal state, storage read/write rates, and the receiving browser/device.

No source-code-only change can establish that NeaRer reaches 280 MB/s. That requires real sender/receiver measurements on supported phone pairs.

## Required real-device benchmark

1. Use two Android phones, record exact models, Android versions, Wi-Fi standards and storage types.
2. Disable mobile data on both phones and verify the transfer continues.
3. Transfer the same 1 GiB test file in each direction at least three times.
4. Record total bytes and elapsed time at the receiver; calculate payload MB/s as bytes divided by seconds and by 1,000,000.
5. Repeat at short range, moderate range, and with one competing Wi-Fi network nearby.
6. Repeat with one receiver and then multiple receivers. Report per-receiver and aggregate throughput separately.
7. Verify SHA-256 of source and received files, and repeat with a deliberately interrupted download using the browser's retry/range behaviour.
8. Record battery/temperature and test a large video plus a mixed batch of small files.

Do not publish a peak-speed claim until these tests show repeatable results on named device combinations. Xender comparisons must use the same phones, file, direction, location, and measurement method.

## APK status

The GitHub Actions workflow now builds an installable debug APK only after JVM tests pass and uploads it as the `nearer-debug-apk` artifact for 14 days. This is a phone-testing build, not a production-signed release. The workflow reports whether this debug APK exceeds the 30 MiB target; debug size is not a substitute for measuring a minified release APK.

Before a public release, still complete:
- real-device transfer integrity, interruption/resume, and throughput testing;
- restore transaction testing on Android devices;
- end-to-end nearby chat/file transfer regression tests across representative Android versions;
- a minified release build, size inspection, signing-key setup, and install/upgrade testing;
- review of user-visible permissions, relay consent, privacy copy, and data recovery behaviour.
