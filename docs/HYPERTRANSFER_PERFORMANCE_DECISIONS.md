# HyperTransfer performance decision: 280 MB/s stretch target

Status: the one-to-one Point-to-Point mode is implemented, but no physical-device throughput result is yet available. The 280 MB/s and 7× Xender goals are engineering targets, not measured claims.

## The target, stated precisely

- Absolute target: **280 MB/s decimal application goodput** = 280,000,000 payload bytes per second = **267.03 MiB/s**.
- Competitive target: at least **7.0× the measured Xender median** on the same physical device pair and under the same test conditions.
- Both conditions must pass. A high peak, a Wi-Fi PHY/link rate, a short burst, or a single best run does not count.
- Each app must have at least five successful runs. Failed attempts remain in the report; they are never discarded to make the result look faster.
- Compare actual received file bytes divided by wall-clock transfer time. Report decimal MB/s and MiB/s explicitly so units cannot be confused.

The code-level acceptance gate is `HyperTransferPerformanceGate`. It converts the 280 MB/s decimal target to MiB/s and requires both the absolute target and 7× measured baseline, with at least five successful runs for each app.

## What the research says

1. **Google Nearby strategy guidance.** Cluster is designed for many-to-many connectivity and is lower bandwidth; Star is one-to-many and higher bandwidth; Point-to-Point is one-to-one and explicitly the highest-throughput topology. This supports using Point-to-Point for a dedicated single-recipient bulk-transfer session while preserving Cluster for messaging/mesh.
   - https://developers.google.com/nearby/connections/strategies
   - https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Strategy

2. **Independent Nearby Connections measurements.** A peer-reviewed empirical study using five Android phones and repeated large-file transfers reported Point-to-Point results up to roughly 600 Mbit/s (about 75 MB/s), while Star peaked around 79.91 Mbit/s in its setup. It also found strong device-to-device asymmetry: which phone acts as the advertiser/access point materially changed throughput. This is valuable evidence for P2P and role testing, but it is nowhere near proof of 280 MB/s.
   - https://www.sciencedirect.com/science/article/pii/S2542660523002184

3. **Google's payload API guidance.** FILE payloads backed by a `ParcelFileDescriptor` can minimize copying; FILE supports arbitrary file sizes. STREAM is useful for generated data but requires prompt background draining to avoid pipe back-pressure. Keep direct FILE payloads as the first high-speed path; do not replace them with streams without an A/B result.
   - https://developers.google.com/nearby/connections/android/exchange-data
   - https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
   - https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload.Stream

4. **Wi-Fi Direct custom sockets.** Android documents Wi-Fi Direct as device-to-device Wi-Fi without an access point and shows transferring bytes over TCP sockets after group formation. This is a credible alternative transport to prototype and benchmark against Nearby, not an automatic speed upgrade: OEM firmware, channel width, spatial streams, congestion, routing and power/thermal limits still govern real goodput.
   - https://developer.android.com/develop/connectivity/wifi/wifip2p

5. **Wi-Fi Aware.** Android's current developer documentation says Wi-Fi Aware can discover peers and establish a bidirectional Wi-Fi data path without an access point; it is intended for large data exchange and supports higher throughput than Bluetooth. Android 13 devices with hardware support can enable instant communication mode to reduce discovery/data-path setup latency for a short 30-second window, at extra power cost. AOSP's platform documentation makes clear that support depends on the device's Wi-Fi hardware/firmware and framework, so this is a capability-gated route, not a universal Android baseline.
   - https://developer.android.com/develop/connectivity/wifi/wifi-aware
   - https://developer.android.com/develop/connectivity/wifi/wifi-aware
- https://source.android.com/docs/core/connect/wifi-aware
- https://www.mdpi.com/2078-2489/11/4/180
- https://arxiv.org/abs/2604.07889

6. **Wi-Fi 6 ceiling context.** A 2024 experimental study using Galaxy S21 Ultra phones and an external Wi-Fi 6/6E router measured roughly 1.6 Gbit/s at 160 MHz in its setup. That is below 2.24 Gbit/s even before application overhead, and it was not a phone-to-phone Nearby test. It demonstrates why a 280 MB/s application target requires exceptional radio hardware and carefully chosen transport conditions.
   - https://dl.ifip.org/db/conf/networking/networking2024/1571003562.pdf

7. **Other Wi-Fi Direct measurements reinforce the hardware caveat.** An open-access opportunistic-network study measured up to 54 Mbit/s on Motorola G4 Wi-Fi Direct links, while a 2026 stock-Android multi-group Wi-Fi Direct study on Galaxy A10s reported peak receiver throughput around 19.7 Mbit/s in its evaluated topology. These are not maximum single-link limits, but they show that results vary dramatically by hardware, architecture and workload; choosing Wi-Fi Direct does not magically guarantee multi-gigabit throughput.
   - https://www.mdpi.com/2078-2489/11/4/180
   - https://arxiv.org/abs/2604.07889

8. **Xender baseline evidence is weak and dated.** Public tests vary by device, file mix and age; an older comparison reported only a few MB/s. That is not a valid present-day competitor baseline. NeaRer must benchmark the currently installed Xender version on the same pair of phones rather than relying on internet claims.
   - https://www.ijcaonline.org/archives/volume156/number12/irfan-2016-ijca-912582.pdf
   - https://blogs.windows.com/devices/2015/09/22/xender-transfer-files-via-wi-fi/

## Engineering decisions

- Keep Cluster as the multi-peer default. Messaging and mesh topology must not be broken for a bulk-transfer speed experiment.
- Use the already implemented, explicit one-to-one Point-to-Point mode for the first high-speed benchmark. Both phones must select the same mode before discovery.
- Keep direct Nearby FILE payloads backed by the selected URI's `ParcelFileDescriptor` wherever available to avoid an unnecessary app-level staging copy.
- Preserve the existing bounded copy buffers and checksum guarantees until controlled profiling proves a change helps. Bigger buffers alone cannot increase radio throughput and may waste memory or worsen thermal pressure.
- Benchmark sender and receiver roles in both directions. The published Nearby study found strong asymmetry, so the phone that advertises/hosts must be varied.
- Do not increase parallel payload count by guesswork. First measure one large file; then compare 2 and 3 concurrent payloads while monitoring median goodput, failures, memory and thermal throttling.
- Prototype a separate Wi-Fi Direct + TCP socket transport and, where both phones support it, a Wi-Fi Aware path. Keep the existing Nearby transport as the compatibility fallback. Only promote an alternative if real devices show higher sustained goodput and acceptable reconnect/security behavior.
- Keep integrity verification and safe destination finalization. Optimize hashing/staging overhead separately from radio goodput; do not weaken correctness to inflate a speed number.
- Investigate source-provider behavior. Seekable file descriptors should stay zero-copy/direct; non-seekable providers may require staging. Record preparation time separately from radio-transfer time.
- Collect device model, Android version, Wi-Fi capabilities, selected strategy, direction, file size, first-byte latency, elapsed time, median/peak throughput, failure reason, thermal status and battery impact.

## Controlled benchmark protocol

For each phone pair and each direction:

1. Use the same 1 GiB or larger test file with a known SHA-256; repeat with a mixed small-file bundle as a separate workload.
2. Put phones close together, keep cases/placement consistent, disable unrelated large network traffic, and record Wi-Fi conditions. Do not compare results from different environments.
3. Run at least five successful NeaRer transfers in Cluster, then at least five in Point-to-Point. Alternate which phone initiates/advertises.
4. Run at least five Xender transfers of the exact same file in the same direction and setup. Keep failures in the log.
5. Separate preparation/hash time, connection setup, first-byte latency, active payload transfer and final save/verification time.
6. Report median and range, not only peak. Repeat after thermal cooldown; note throttling and battery drain.
7. Repeat on a low/mid-range phone and a modern high-end phone. A result on one pair is not a universal claim.



## Evidence-based transport selection policy (implemented on the feature branch)

`HyperTransferTransportPolicy` now provides a pure Kotlin decision layer for the next transport phase:

- It checks capability on **both** phones, including Android API level, hardware support and the relevant permission state.
- It treats Wi-Fi Direct TCP and Wi-Fi Aware TCP as candidates, not assumed winners.
- An alternative can be selected only when there are at least five successful runs for the exact device-pair key and transfer direction, all with integrity verification, and a positive measured median byte rate.
- It chooses the fastest qualified profile for that exact pair/direction. A result measured on another pair or in the opposite direction cannot influence selection.
- If no alternative qualifies, it falls back to the existing Nearby Point-to-Point route when both phones support it; otherwise it reports that no mutually available route exists.

This policy is deliberately **not yet wired to a Wi-Fi Direct or Wi-Fi Aware socket transport**. It prevents premature route promotion while those transports are still being built and measured. The next implementation stage is the actual Wi-Fi Direct group-owner/client lifecycle, socket connection, bounded streaming, cancellation, integrity verification and cleanup, with permission/lifecycle handling tested on real Android devices.

## Implementation sequence

- [x] Explicit High-Speed one-to-one mode and Point-to-Point selection.
- [x] Existing throughput meter and repeated-run median summary.
- [x] Report both MiB/s and decimal MB/s in benchmark summaries.
- [x] Add a testable 280 MB/s + 7× + five-run acceptance gate.
- [x] Add capability- and evidence-based transport selection policy with unit tests.
- [ ] Add capability/version negotiation and connect the policy to real transport implementations.
- [ ] Add a benchmark screen/export that captures the complete test record.
- [ ] Build and compare a Wi-Fi Direct TCP prototype against Nearby Point-to-Point.
- [ ] Evaluate Wi-Fi Aware only on devices reporting the required feature.
- [ ] Tune buffers, concurrency and hashing only from benchmark evidence.
- [ ] Achieve and independently repeat the 280 MB/s + 7× target on supported device pairs before making the claim.


## Wi-Fi Direct TCP data-plane prototype (implemented, not yet connected to Android group management)

A first isolated TCP data-plane engine now exists in `WifiDirectTcpTransferEngine`. It accepts a connected socket supplied by a future Wi-Fi Direct group coordinator; it does not create groups, negotiate owner/client roles, request permissions, or alter the current Nearby transport.

The protocol includes:
- A versioned frame header with a bounded transfer identifier and declared payload length.
- Streaming with a configurable bounded buffer (8 KiB–1 MiB; default 256 KiB), without loading the complete file into memory.
- SHA-256 calculated while sending and receiving, with a receiver acknowledgement only after successful verification.
- Receiver-side temporary-file staging and publication only after integrity verification.
- Rejection of negative sizes, mismatched transfer IDs, truncated streams, insufficient free storage, and destination collisions.
- Cancellation by closing the active socket, plus socket and temporary-file cleanup on exit.

Loopback JVM tests cover a 512 KiB transfer, empty files, wrong transfer IDs, and preservation of an existing destination. These tests exercise the TCP framing/data plane on a local socket; they do not prove Wi-Fi Direct radio performance, Android lifecycle correctness, or interoperability with the current Nearby protocol.

The sender and receiver must use this exact protocol version and transfer ID. A later integration must add authenticated peer/session negotiation before opening the data socket; the transfer ID alone is not authentication. The coordinator must also apply socket read/connect timeouts and ensure the socket is bound/routed through the intended Wi-Fi Direct network.

## Updated implementation sequence

- [x] Add isolated versioned TCP data-plane engine with bounded streaming, cancellation, SHA-256 acknowledgement and safe receive staging.
- [x] Add loopback tests for normal, empty, wrong-session and destination-collision cases.
- [ ] Implement Android Wi-Fi Direct group lifecycle and socket routing/binding.
- [ ] Add authenticated capability/version negotiation and connect the policy to actual transports.
- [ ] Add benchmark screen/export and persistent test records.
- [ ] Compare Wi-Fi Direct TCP against Nearby Point-to-Point on physical phones in both directions.
- [ ] Evaluate Wi-Fi Aware only on devices reporting the required feature.
- [ ] Tune buffers, concurrency and hashing only from measured evidence.
- [ ] Achieve and independently repeat the 280 MB/s + 7× target on supported device pairs before making the claim.

## Android Wi-Fi Direct group coordinator (implemented, integration still pending)

`WifiDirectConnectionCoordinator` now provides an explicit Android-side control layer:
- Registers for Wi-Fi Direct state, peer-list and connection-state broadcasts only after `start()`.
- Checks the relevant runtime permission (Nearby Wi-Fi Devices on Android 13+, fine location on earlier versions) before initializing the P2P channel.
- Exposes explicit peer discovery, group creation, and connection-to-selected-peer operations; it does not silently toggle Wi-Fi or start a hotspot.
- Reports group-owner/client role and group-owner address when Android reports a formed group.
- Provides server/client socket creation helpers that bind to an IPv4 address on a `p2p*` interface. It fails closed if that interface cannot be found, rather than listening on every network interface.
- Provides discovery stop, group removal and receiver/channel cleanup.

This is an isolated coordinator, not yet called by the app's current file-transfer UI or service. The socket protocol and coordinator are separate modules, and they still need authenticated session negotiation, real-device compatibility testing, timeout policy, reconnect handling and integration review. Some OEMs use different interface naming/routing behavior; the fail-closed interface lookup must be validated across target phones before rollout.

## Resume work remains separate

The receiver journal, prefix hashing, negotiation codecs and partial writer are foundations for reliable recovery. Sender recovery PR #268 is still open and is behind main; it must be synchronized and validated before merging. Resume reliability is important, but it is not itself a radio-throughput optimization.

## References

- https://developers.google.com/nearby/connections/strategies
- https://developers.google.com/nearby/connections/android/exchange-data
- https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
- https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Strategy
- https://www.sciencedirect.com/science/article/pii/S2542660523002184
- https://developer.android.com/develop/connectivity/wifi/wifip2p
- https://source.android.com/docs/core/connect/wifi-aware
- https://dl.ifip.org/db/conf/networking/networking2024/1571003562.pdf
- https://www.ijcaonline.org/archives/volume156/number12/irfan-2016-ijca-912582.pdf
- https://blogs.windows.com/devices/2015/09/22/xender-transfer-files-via-wi-fi/
