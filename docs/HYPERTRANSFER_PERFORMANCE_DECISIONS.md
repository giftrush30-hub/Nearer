# HyperTransfer performance decision: 280 MB/s stretch target

Status: the one-to-one Point-to-Point mode is implemented, but no physical-device throughput result is yet available. The 280 MB/s and 7× Xender goals are engineering targets, not measured claims.

## The target, stated precisely

- Absolute target: **280 MB/s decimal application goodput** = 280,000,000 payload bytes per second = **267.03 MiB/s**.
- Competitive target: at least **7.0× the measured Xender median** on the same physical device pair and under the same test conditions.
- Both conditions must pass. A high peak, a Wi-Fi PHY/link rate, a short burst, or a single best run does not count.
- Each app must have at least five successful runs. Failed attempts remain in the report; they are never discarded to make the result look faster.
- Compare actual received file bytes divided by wall-clock transfer time. Report decimal MB/s and MiB/s explicitly so units cannot be confused.

The code-level acceptance gate is `HyperTransferPerformanceGate`. It converts the 280 MB/s decimal target to MiB/s and requires both the absolute target and 7× measured baseline, with at least five successful runs for each app.

## Hardware feasibility: the 280 MB/s target is not universal

280 MB/s decimal is **2.24 Gbit/s of application payload**. A device's advertised Wi-Fi PHY rate is not its file-transfer goodput, and both phones must support a sufficiently fast direct link.

For a concrete compatibility example, the Galaxy A40 (SM-A405FN family) is specified as dual-band Wi-Fi 5 / 802.11ac with VHT80 and Wi-Fi Direct. Even a theoretical two-spatial-stream 802.11ac link at 80 MHz tops out at about **866.7 Mbit/s PHY** (about 108 MB/s before protocol overhead), well below the 2.24 Gbit/s application target. The exact spatial-stream capability of a particular handset must be queried; do not assume MIMO means two streams.

Consequences:
- Keep the app compatible with older Wi-Fi 5 phones, but do not promise 280 MB/s on them.
- Use them for compatibility, stability, resume, and realistic baseline testing.
- Reserve the 280 MB/s acceptance test for phone pairs whose reported radio capabilities can plausibly exceed the target, with substantial PHY headroom.
- Query actual Android capabilities and current radio availability on both devices; never infer capability from model name alone.
- If either endpoint is the bottleneck, the pair cannot pass the absolute target regardless of software tuning.

Reference specifications:
- Galaxy A40 Wi-Fi 5 / VHT80 / Wi-Fi Direct: https://www.phonemore.com/specs/samsung/galaxy-a40-sm-a405fnds/
- 802.11ac 80 MHz, two-stream theoretical ceiling: https://www.tec.gov.in/pdf/TSTP/TSTP_WiFiAP_GR_2022_v3.0_final_copy_May_2022_issue_f.pdf

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

- It checks capability on **both** phones, including Android API level, hardware support, relevant permission state, and current Wi-Fi Direct / Wi-Fi Aware radio availability. Static hardware support alone is not enough.
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

Loopback JVM tests cover a 512 KiB transfer, empty files, wrong transfer IDs, corrupted payload rejection, preservation of an existing destination, and stalled-peer read-timeout cleanup. The engine uses a configurable 30-second socket read timeout by default; this is an idle-read bound, not a total transfer-duration limit. These tests exercise the TCP framing/data plane on a local socket; they do not prove Wi-Fi Direct radio performance, Android lifecycle correctness, or interoperability with the current Nearby protocol.

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
- Checks the device's Wi-Fi Direct feature and the relevant runtime permission (Nearby Wi-Fi Devices on Android 13+, fine location on earlier versions) before initializing the P2P channel.
- Checks Android's Location-mode requirement before peer discovery and explains that NeaRer does not read the user's location; Android's P2P discovery APIs can require Location mode even though the app only needs nearby-device access.
- Binds the client socket to an ephemeral local address on the P2P interface, not an arbitrary Wi-Fi/mobile interface.
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


## Transfer-bound authenticated-session transcript foundation (implemented, not wired)

`HyperTransferSessionTranscript` now creates a canonical, length-prefixed transcript for a single file-transfer session. It binds:
- The protocol version and the stable HyperTransfer transfer ID.
- Explicit sender and receiver identities (the roles are not sorted away).
- Both peers' stable signing public keys, fresh 32-byte nonces, ephemeral ECDH public keys and recipient encryption public keys.
- A domain-separation label specific to HyperTransfer authenticated sessions.

Both peers must construct the transcript with the same sender/receiver roles, then sign the exact bytes with their device-bound `SecurePeerIdentity.signDetached` key and verify the other signature against the signing public key already trusted for that stable peer identity. A public key merely supplied by the connecting peer is not trusted just because its signature verifies; identity-key continuity / first-contact confirmation remains a required part of the caller.

The new helper has JVM tests for deterministic encoding, transfer-ID binding, sender/receiver role binding, peer nonce/key binding, invalid IDs, invalid nonce lengths and unsupported protocol versions. It is a transcript-construction foundation only: no TCP socket handshake, trust-store lookup, SAS confirmation UI, capability negotiation, replay state or transfer-service integration is claimed complete. Bulk data must remain blocked until both identity proofs are verified and the user's existing peer-trust / transfer-approval policy has been satisfied.


## Wi-Fi Aware implementation constraints (verified against current Android documentation)

- The app must declare `CHANGE_NETWORK_STATE` in addition to its existing Wi-Fi, network, Internet and nearby-device permissions before calling `ConnectivityManager.requestNetwork()`. Do not add this permission until the Wi-Fi Aware implementation is actually introduced.
- Capability detection must check both `PackageManager.FEATURE_WIFI_AWARE` and the runtime `WifiAwareManager.isAvailable()` state. Hardware feature support alone is not enough.
- Register for `WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED` before checking availability. If availability changes, discard all sessions and rebuild the route state rather than reusing stale discovery handles.
- Android's documented data path is discovery publish/subscribe, peer message exchange, a publisher-side `ServerSocket`, then a `WifiAwareNetworkSpecifier` request from each peer. The subscriber obtains the publisher IPv6 address and port from `WifiAwareNetworkInfo` in `onCapabilitiesChanged()`.
- Create the client socket with the specific `Network.getSocketFactory()` supplied by the Aware callback. Do not bind the whole app process to that network; NeaRer messaging and other connectivity must remain independent.
- Always unregister the `ConnectivityManager.NetworkCallback` and close sockets/discovery sessions on cancellation, link loss and service shutdown.
- Wi-Fi Aware can become unavailable when Wi-Fi is disabled or when Wi-Fi Direct, SoftAP or tethering is active on some hardware/firmware. The route selector must re-check availability at connection time and fall back without disturbing the active Nearby messaging route.
- If a PSK is configured on the Aware network specifier, treat it as link-layer protection only. Still perform NeaRer's pinned signing-key proof and the transfer-bound transcript verification before accepting file bytes.

Official implementation reference: https://developer.android.com/develop/connectivity/wifi/wifi-aware


## Mandatory security gate before connecting the TCP prototype

The original TCP methods remain a plaintext loopback prototype and must not be used for production transfers. A separate encrypted path has now been implemented:
- `HyperTransferSecureChannel` derives independent sender-to-receiver and receiver-to-sender AES-256-GCM keys and nonce prefixes from ephemeral ECDH plus the transfer-bound transcript.
- Every encrypted record authenticates the transfer ID, direction, sequence number and plaintext length as associated data. Strict monotonically increasing sequence checks reject replay, skip and reordering. Record allocation is bounded to 256 KiB plus framing/tag overhead.
- `WifiDirectTcpTransferEngine.sendSecure/receiveSecure` encrypt metadata, file chunks, SHA-256 digest and final ACK. The receiver publishes only after AEAD authentication and full-file digest verification.
- Tests cover complementary peer keys, both directions, tampering, replay, skipped/reordered records, cross-session rejection, record-size limits, framed-stream length rejection and an encrypted 768 KiB loopback file transfer.

The new `HyperTransferSessionAuthenticator` now bridges the transfer-bound transcript to NeaRer's existing device-bound signing identity and `TrustedPeerKeyStore`. It creates a local detached signature, verifies the remote signature over the exact same transcript, checks pinned signing + recipient-encryption keys, requires explicit first-contact approval before TOFU pinning, and refuses to return an AEAD channel unless the caller asserts that the user approved this transfer.

**Still mandatory before production integration:** the authenticator and secure engine are not yet connected to the app's actual transfer approval UI/state or service-owned transfer lifecycle. The caller's approval flags are a contract, not a UI implementation. The old plaintext `send/receive` methods must remain unavailable to production routing. Durable recovery, capability negotiation, cancellation across service death, and physical-device tests also remain outstanding.


## Strict controlled comparison gate (new)

The acceptance API now has a strict `evaluateControlled` entry point. It refuses to compare results unless both NeaRer and Xender evidence use the same:
- stable device-pair key,
- transfer direction,
- exact file SHA-256 and byte length,
- benchmark-condition key (room/placement, radio settings and other controlled setup).

Each side must contain at least five successful runs, and each successful run must have transferred the exact comparison-file byte count. Failed attempts remain in the run list and are excluded from the median goodput rather than silently converted into fast successes. The 280 MB/s absolute target and 7× measured Xender target must both pass. This prevents mixing a NeaRer result from one phone pair/file/direction with an unrelated Xender baseline.


## High-bandwidth TCP window tuning (18 November 2026)

- The secure and compatibility TCP engines now request a 4 MiB send/receive socket buffer by default, with a bounded configurable range of 64 KiB to 16 MiB. The streaming buffer remains independently configurable from 8 KiB to 1 MiB (default 256 KiB); secure AEAD records remain capped at 256 KiB.
- Wi-Fi Direct client sockets request send and receive windows before connect. The group-owner listener requests a receive window before bind/accept. The engine re-applies the configured socket hints once the connected socket is handed to it.
- Bulk TCP keeps Nagle enabled because the writer batches large records through buffered streams; interactive messages remain on Nearby. These socket options are kernel hints, not guaranteed allocations or throughput promises. Record effective getSendBufferSize()/getReceiveBufferSize() values on each device during physical profiling.
- This is a measured-bottleneck candidate, not evidence of a 280 MB/s result. The production app still does not route file bytes through the authenticated Wi-Fi Direct engine; service-owned P2P lifecycle integration and exact-device benchmark runs remain hard blockers.


## 2026-11-19 throughput-pipeline optimization (JVM-validated; radio unmeasured)

The current performance branch applies a conservative, capability-aware data-plane profile:

- Raise the default userspace stream buffer from 256 KiB to 1 MiB. The AEAD record maximum remains 256 KiB, so the buffered streams can batch roughly four encrypted records before flushing to the underlying socket/disk. The wire format and security record size are unchanged.
- Select 64 KiB / 256 KiB / 1 MiB stream buffers according to severe thermal pressure, low memory/low-RAM hardware, and a cool device with memory headroom.
- Request 1 MiB / 4 MiB / 8 MiB TCP send and receive buffers under the same conditions. These are socket hints: Android/OEM kernels can clamp or reinterpret them, so only device-side effective values and measured goodput can validate the result.
- Keep Nagle enabled for the bulk stream; chat/control remains on the existing Nearby channel.
- Coalesce record-level progress callbacks to at most one callback per 100 ms, while forcing the final byte count. This reduces service/UI synchronization in the data loop without reducing transfer bytes or weakening verification.
- Add deterministic unit tests for throttle timing and adaptive buffer decisions.
- Add a 32 MiB encrypted loopback smoke benchmark in both directions with SHA-256 verification. It prints MB/s for CI diagnostics, but loopback is CPU/JVM-only and is not a Wi-Fi Direct performance claim.

No checksum, authenticated record, approval, cancellation, destination safety, or bounded-memory guarantees were relaxed. Do not promote these defaults solely because the loopback test is faster: compare multiple buffer profiles on physical Wi-Fi Direct links, inspect the effective socket window values, and retain the previous profile if the new one regresses sustained throughput, memory, thermal behavior, or failure rate.
