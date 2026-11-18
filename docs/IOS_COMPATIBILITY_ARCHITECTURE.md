# NeaRer iPhone compatibility architecture

Status: engineering foundation / research-backed design (18 November 2026)
Owner: iOS compatibility track
Android source of truth: `main`
Working branch: `platform/ios-compatibility-foundation`

## Product requirement

NeaRer must support all three device pairings using the same NeaRer identity, conversation semantics and transfer formats:

- Android ↔ Android (preserve the existing Android product).
- iPhone ↔ iPhone.
- Android ↔ iPhone in both directions.

The core nearby experience must continue to work without internet. Online delivery, if/when added, remains a separate transport and must not become a prerequisite for local messaging. Do not introduce Supabase or make cloud infrastructure a dependency of offline communication.

## Repository findings

The current repository is a native Kotlin / Jetpack Compose Android application. There is no iOS or Swift source tree on `main` yet. The current Android baseline includes:

- `NearbyTransportService` owning the Nearby Connections lifecycle, with the Activity attached as a UI client.
- `NearbyConnectionsTransport` carrying direct chat, group/mesh control and file payload callbacks.
- `NearbyPeerHandshake` v3: `peer_hello_v3` and `peer_proof_v3` JSON envelopes.
- `NearbyMessageCrypto`: authenticated P-256 ECDH handshake transcript, HKDF-SHA256 directional keys and AES-256-GCM message packets (`secure_packet_v1`).
- Stable NeaRer IDs: current 12-character IDs plus legacy six-character hexadecimal IDs.
- Existing encrypted outbox/inbox, identity pinning, message IDs, delivery acknowledgements and group semantics.
- Existing transfer manifest, chunk/stream, integrity and resume codecs. These are Android implementations today; they are not yet a proven cross-platform wire contract.

Do not replace the Android Nearby service, create a second Android chat store, or change the active Android transfer route as part of the iOS port. The iOS work must adapt to the current Android contract first, then propose versioned protocol changes through a separate reviewed integration.

## Transport research and decisions

### 1. Wi-Fi Aware: preferred Apple-native path; Android interoperability remains experimental

Apple's Wi-Fi Aware framework arrived on iOS 26 and is supported on iPhone 12 and later. It provides secure device-to-device discovery, pairing and high-throughput peer-to-peer networking without a router, cellular link, internet or cloud server. Apple documents pairing through DeviceDiscoveryUI and data connections through Network.framework.

Android has Wi-Fi Aware APIs from Android 8.0 / API 26. Android describes publish/subscribe discovery and bidirectional data paths without an access point. However, Android explicitly says not every Android device supports Wi-Fi Aware. Runtime capability checks are mandatory; API level alone is not enough.

Decision:
- Prototype Wi-Fi Aware on iOS 26+ and Android devices whose Wi-Fi chipset/firmware reports support.
- Use a single NeaRer service identifier and a versioned capability advertisement.
- Pair/authenticate the NeaRer peer at the application layer after the platform pairing step.
- Carry NeaRer chat/control frames and a separate bulk-transfer stream over the negotiated data path.
- Never assume all Android phones in the current minSdk range support Wi-Fi Aware.
- Never advertise this route as available until both endpoints report support and the actual connection is established.

Important: Apple requires the Wi-Fi Aware entitlement and explicit service declarations. DeviceDiscoveryUI pairing is a user-visible system flow. It is not a silent, unrestricted replacement for Android's current Nearby discovery bubbles. Product design must explain this difference.

### 2. Google Nearby Connections on iOS: useful LAN adapter, not the whole solution

Google publishes a Swift package for Nearby Connections. Its current iOS/macOS support table lists Wi-Fi LAN as the implemented medium. The iOS guide requires Bluetooth/local-network usage descriptions and describes Hotspot Configuration setup for high-speed hotspot connections, but the current upstream support matrix does not show iOS BLE or Wi-Fi Aware discovery/data as implemented.

Decision:
- Keep it as a candidate adapter for devices already on the same local Wi-Fi network or a user-enabled hotspot.
- Do not assume the iOS SDK gives Android-equivalent Bluetooth discovery or fully infrastructure-free peer-to-peer behavior.
- Do not make it the only iPhone transport.
- Verify the exact upstream SDK revision and test on physical iPhones before adopting it for production.

### 3. Network.framework peer-to-peer Wi-Fi: iPhone-to-iPhone fallback

Apple documents Network.framework peer-to-peer Wi-Fi using `includePeerToPeer`. Apple also states that the underlying on-the-wire protocol is undocumented for third-party use and therefore this mechanism only works between Apple devices.

Decision:
- It is a viable iPhone ↔ iPhone route for compatible iOS versions.
- It is not an Android ↔ iPhone transport.
- Keep the NeaRer application protocol above this route so iPhone-to-iPhone conversations remain compatible with the same iOS app and identity.
- Do not reverse engineer Apple's private radio protocol or depend on it for Android interoperation.

### 4. Multipeer Connectivity: do not start new work on it

Apple now marks Multipeer Connectivity deprecated and recommends migrating to Network.framework. It historically provided nearby discovery, messages, streams and file resources over Wi-Fi/Bluetooth. Use it only as a temporary compatibility reference if a tested legacy-device requirement demands it; do not make it the foundation of the new iOS app.

### 5. Local Wi-Fi / hotspot fallback

For iOS versions before Wi-Fi Aware, and for devices without compatible Wi-Fi Aware hardware:
- Support peers on the same local Wi-Fi LAN using Network.framework / Bonjour discovery and a bounded TCP stream.
- Support a user-assisted hotspot path where one phone explicitly enables its hotspot and the other joins it through system settings or an approved system configuration flow.
- Keep this internet-independent: a router or hotspot may provide only the local link; no internet access is required.
- Respect iOS local-network privacy prompts, Bonjour service declarations, hotspot capability entitlements and user consent.
- Do not claim that an ordinary iOS app can silently enable Personal Hotspot or force-join arbitrary Wi-Fi networks.

### 6. Bluetooth Low Energy

CoreBluetooth can provide a low-bandwidth discovery/control fallback on iOS, but it is not a substitute for a high-throughput file data path. Google Nearby's current iOS transport matrix does not implement its BLE mediums. If NeaRer builds a custom BLE GATT adapter, it must use a documented NeaRer service UUID and explicit version/capability negotiation; use BLE for discovery, pairing bootstrap and small control frames only. Move bulk bytes to Wi-Fi Aware or a local IP stream.

## Proposed platform matrix

| Pairing | Preferred route | Fallback | Important condition |
|---|---|---|---|
| Android ↔ Android | Existing Nearby Connections | Existing tested Android local transfer routes | Preserve current behavior |
| iPhone ↔ iPhone, iOS 26+ | Wi-Fi Aware | Network.framework peer-to-peer Wi-Fi / local LAN | Wi-Fi Aware entitlement and user pairing |
| iPhone ↔ iPhone, earlier iOS | Network.framework peer-to-peer Wi-Fi | Same local Wi-Fi / user-assisted hotspot | Apple-only peer-to-peer route |
| Android ↔ iPhone | Same local Wi-Fi / user-assisted hotspot with standard TCP/Bonjour | Experimental Wi-Fi Aware pairing; future BLE bootstrap + Wi-Fi data | Do not assume NAN capability alone guarantees Apple pairing compatibility |
| Android ↔ iPhone, no shared Wi-Fi Aware | Same local Wi-Fi / hotspot | Future custom BLE bootstrap + user-assisted Wi-Fi data path | No universal automatic offline route is promised |

This matrix is a design target, not a statement that any iOS transport has already been implemented or physically verified.

## Implemented iOS foundation on the compatibility branch

The isolated branch now contains more than the initial wire layer:

- Swift bounded codecs for `peer_hello_v3`, `peer_proof_v3`, and `secure_packet_v1`.
- A Swift v3 handshake coordinator that creates hello/proof envelopes, verifies the Android-compatible domain-separated peer proof and derives directional session keys after caller-controlled trust gates.
- A Swift port of Android's signed `mesh_identity_binding_v1` recipient-key binding, including the exact signed-byte layout, P-256 verification, expiry and monotonic sequence fields.\n- A shared six-digit short authentication string (SAS) derived from the v3 transcript, asserted by Swift and Kotlin tests.\n- A pure first-contact trust policy and device-only Keychain peer-pin vault. The vault exposes explicit approval, key-change rejection and monotonic remote binding-sequence persistence; its UI/handshake integration remains outstanding.
- CryptoKit P-256 signature verification/signing, P-256 ECDH, HKDF-SHA256 directional key derivation and AES-GCM packet sealing/opening. Deterministic P-256, HKDF, AES-GCM and ECDSA fixtures are asserted by both Swift and Kotlin test suites; live device interoperability remains separate.
- Swift v3 transcript encoder with a deterministic vector asserted by Kotlin and Swift.
- Swift port of Android's `NRB1` version-1 transfer bundle manifest, with safe-path validation and a shared golden byte vector.
- Bounded, incremental, four-byte-length-prefixed TCP frame codec. It handles TCP fragmentation/coalescing and caps control frames at 1 MiB; large file contents must not be buffered through this codec.
- A Network.framework adapter that advertises/browses the `_nearer-v1._tcp` Bonjour service and opts into Apple's peer-to-peer Wi-Fi path. The endpoint handle is explicitly untrusted until the NeaRer cryptographic handshake succeeds.
- iOS local-network privacy and Bonjour declarations plus an example Wi-Fi Aware entitlement plist.

The Network.framework adapter is a native iOS implementation foundation, not yet a complete app feature. The new SwiftUI shell still does not run the handshake coordinator or present the SAS approval prompt. The Android adapter now advertises the same TCP/Bonjour service in an isolated class, but it is not integrated into the production messaging service. No iPhone has yet been physically tested against it. The Wi-Fi Aware path still needs the iOS 26 paired-device API adapter, Android Wi-Fi Aware implementation, entitlement approval and real-device interoperability validation.

### LAN transport contract

- Bonjour service type: `_nearer-v1._tcp`.
- Bonjour instance name: current NeaRer peer ID (display name is learned only after authenticated handshake).
- Stream framing: unsigned 32-bit big-endian payload length followed by exactly that many bytes.
- Maximum framed control/chat payload: 1,048,576 bytes.
- The transport endpoint is not a NeaRer identity and must never be auto-trusted.
- The NeaRer v3 hello/proof handshake is the first application frame before private message traffic.
- File data uses a separate bounded streaming path; never load a whole file into memory or treat a single control frame as a large-file transfer.

## Shared NeaRer protocol contract

The native apps must share application semantics, not platform SDK internals.

### Identity and trust
- Keep the existing NeaRer public ID format: 12-character current IDs and six-character legacy hexadecimal IDs.
- Keep the display name separate from authorization.
- iOS should generate a P-256 signing identity in Keychain / Secure Enclave where available; the private key must never leave the device.
- The Android identity remains the current Android Keystore identity.
- Exchange DER-encoded P-256 public keys in the current v3 hello envelope. Verify that CryptoKit / Security can parse Android's X.509 SubjectPublicKeyInfo representation byte-for-byte before relying on it.
- Preserve the current signed transcript, peer proof, first-contact verification code and trust-on-first-use pinning semantics. Do not silently auto-trust a first contact.
- A public NeaRer ID is not proof of ownership. A successful cryptographic key check proves possession of a key, not a person's real-world identity.

### Current v3 wire envelopes
- Hello: `peer_hello_v3`, fields `peerId`, `publicKey`, `nonce`, `ephemeralPublicKey`, `recipientEncryptionPublicKey`.
- Proof: `peer_proof_v3`, fields `peerId`, `signature`.
- Chat cipher envelope: `secure_packet_v1`, fields `version=1`, `id`, `nonce`, `ciphertext`.
- Base64 is standard RFC 4648 without line breaks.
- The iOS wire module validates bounded envelope shape and field sizes. It reproduces the Android v3 handshake transcript byte-for-byte; the same golden vector is asserted by Swift and Kotlin tests. A CryptoKit adapter implements P-256 signatures, ECDH, HKDF-SHA256 and AES-GCM. The transfer manifest, resume messages and single-file stream metadata now have matching Swift/Kotlin golden-vector tests. The deterministic crypto fixtures, two-sided Swift handshake test, peer-proof vector and signed recipient-binding vector establish algorithm/wire compatibility for the covered cases. They do not replace durable messaging integration or end-to-end Android↔iPhone device tests.

### Cryptographic interoperability gate

The Android implementation uses P-256 ECDH, SHA-256, HKDF-SHA256, AES-256-GCM and ECDSA/SHA-256. The exact transcript is domain-separated and length-prefixed; peer ordering is by stable ID. The HKDF info string is `NeaRer-chat-E2E-v1`, and the transcript SHA-256 digest is the salt. Directional keys are assigned consistently by the lexicographic order of peer IDs.

Before enabling iOS messaging:
1. Extend the existing Swift/Kotlin transcript vector with deterministic Android-generated ECDH/HKDF keys and ECDSA signature fixtures.
2. Verify iOS DER public-key import and DER ECDSA signature verification against Android-generated vectors.
3. Verify HKDF output bytes on both platforms.
4. Verify AES-GCM packets in both directions, including modified AAD, nonce, ciphertext and sender/recipient rejection.
5. Verify recipient-addressed mesh encryption separately; do not assume direct-session encryption is interchangeable with recipient encryption.
6. Keep first-contact trust and key-change rejection mandatory.

Do not invent a new crypto protocol or downgrade to plaintext to make an early demo connect.

### Messaging and durable state
- iOS must implement the same stable message IDs, encrypted durable outbox/inbox, duplicate suppression, authenticated delivery ACK semantics and group recipient authorization.
- ACK means the encrypted message was durably committed to the recipient inbox; it does not mean read.
- Read receipts, typing, edits, reactions and delete-for-everyone remain separate bounded control events with the current authentication and authorization checks.
- Keep the same logical conversation identity across transport changes.
- Multi-hop relay remains disabled until the existing relay security and physical multi-device gates are passed. Do not add iOS as a relay transit node until it can validate and persist the exact signed forwarding proof chain.

### File transfer and HyperTransfer
- Reuse the existing metadata-first transfer concepts: receiver approval, bounded manifest, file identity, exact declared size, integrity digest, temporary staging and publish-after-verification.
- Port the existing manifest/resume codecs only after golden vectors establish byte-for-byte compatibility.
- Use bounded streaming and receiver backpressure. Never buffer a whole video/file in RAM.
- Use a temporary destination and atomic finalization after digest/size verification.
- Preserve safe filenames and do not overwrite silently.
- The HyperTransfer 280 MB/s and 7× Xender figures are stretch acceptance targets, not achieved results. Benchmark iPhone and Android hardware separately and together; do not claim a speed until repeated physical-device tests support it.

## iOS application structure (target)

Create a native SwiftUI app in a dedicated `ios/` directory. Keep platform code separated:

- `NeaRerApp`: app lifecycle, onboarding, identity presentation and navigation.
- `NeaRerWire`: platform-neutral envelope codecs and golden-vector tests.
- `NeaRerCrypto`: CryptoKit / Security implementation of P-256, transcript, HKDF and AES-GCM interoperability.
- `NeaRerTransport`: protocol abstraction plus Wi-Fi Aware, Network.framework peer-to-peer and local-LAN adapters.
- `NeaRerMessaging`: local encrypted store, outbox/inbox, conversations, ACK and group policy.
- `NeaRerTransfer`: manifest, stream, integrity, resume and storage.
- `NeaRerUI`: SwiftUI views using the same NeaRer blue/gold brand system.

The transport contract should expose capabilities and lifecycle events, not leak Wi-Fi Aware, Nearby Connections or Network.framework types into messaging state. All adapters must feed the same authenticated session boundary.

## Implementation sequence and gates

1. **Wire compatibility foundation (this branch):** Swift Package, v3 envelope codecs, ID compatibility tests and macOS Swift CI. No Android runtime changes.
2. **Crypto parity:** transcript builder, ECDSA proof verification, ECDH/HKDF/AES-GCM parity and cross-language golden vectors. No live messaging until both directions pass.
3. **iOS transport prototypes:** Wi-Fi Aware on supported iPhones and Android test devices; Network.framework peer-to-peer for iPhone-only; local-LAN fallback.
4. **Messaging core:** iOS durable encrypted inbox/outbox and direct-chat interoperability.
5. **Groups and controls:** verified roster, per-recipient encryption, ACK/read receipt distinction, typing and message actions.
6. **File transfer:** metadata/manifest, bounded stream, digest, safe save, interruption and resume.
7. **Native product UI and release:** onboarding, nearby discovery, permissions, privacy, accessibility and App Store signing.
8. **Physical acceptance:** at least two iPhones, two Android devices, and mixed pairs; test foreground/background, screen lock, radio interruption, older iOS fallback, unsupported Wi-Fi Aware hardware, large files, integrity and battery/thermal behavior.

## Hard release gates

- No change to the active Android route without a separate Android regression review.
- No iOS release claim based only on Swift unit tests or simulator.
- No internet dependency for the core local messaging and file-transfer flows.
- No plaintext fallback.
- No silent permission or hotspot behavior.
- No mesh relay activation without its separate security review and physical acceptance.
- No performance claims without repeatable, same-device-pair measurements.

## Primary platform references

- Apple Wi-Fi Aware overview: https://developer.apple.com/documentation/wifiaware
- Apple Wi-Fi Aware adoption and entitlements: https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware
- Apple Wi-Fi Aware paired-device connections: https://developer.apple.com/documentation/wifiaware/connecting-paired-devices
- Apple DeviceDiscoveryUI: https://developer.apple.com/documentation/devicediscoveryui
- Apple Network framework selection guide: https://developer.apple.com/documentation/technotes/tn3151-choosing-the-right-networking-api
- Apple Multipeer Connectivity deprecation: https://developer.apple.com/documentation/multipeerconnectivity
- Android Wi-Fi Aware: https://developer.android.com/develop/connectivity/wifi/wifi-aware
- Google Nearby Connections Swift setup: https://developers.google.com/nearby/connections/swift/get-started
- Google Nearby Connections upstream transport matrix: https://github.com/google/nearby/blob/main/connections/README.md
