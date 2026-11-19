# NeaRer for iPhone — engineering workspace

This is the isolated native iOS work area. Android's production Nearby messaging service and HyperTransfer route remain the source of truth and have not been replaced.

## Implemented on this branch

- [x] Research-backed Android/iPhone/iPhone transport matrix and platform limitations.
- [x] Swift codecs for the current NeaRer v3 hello/proof and secure packet envelopes.
- [x] Native v3 handshake coordinator and Android-compatible domain-separated proof verification.
- [x] Signed recipient identity-binding codec and Android signature verification vector.\n- [x] Shared six-digit first-contact verification code and Swift/Kotlin parity vector.\n- [x] TOFU/key-continuity policy plus device-only Keychain peer-pin vault; approval UI and live handshake wiring remain pending.
- [x] CryptoKit P-256 signatures, ECDH, HKDF-SHA256 and AES-256-GCM adapter.
- [x] Deterministic Android-generated P-256 / HKDF / AES-GCM and ECDSA fixtures verified by Swift and Kotlin test suites.
- [x] Swift/Kotlin golden vector for the v3 handshake transcript.
- [x] Swift port of the Android NRB1 transfer bundle manifest, including safe-path checks and a shared Kotlin/Swift golden vector.
- [x] Swift port of Android NRRS resume proposal, decision and no-resume envelopes, with Kotlin/Swift golden vectors.
- [x] Swift port of Android NRSM single-file stream metadata, with Kotlin/Swift golden vector.
- [x] Bounded incremental TCP framing in Swift and matching Android big-endian frame codec.
- [x] Native iOS Network.framework Bonjour/TCP adapter with Apple's peer-to-peer Wi-Fi option.
- [x] Isolated Android NSD/Bonjour/TCP adapter using the same service type and frame format; not yet connected to the active production messaging service.
- [x] Native SwiftUI iPhone app shell, device-only Keychain identity and nearby discovery screen.
- [x] Connected nearby discovery to the v3 hello/proof handshake, signed recipient-binding exchange and session-key derivation.
- [x] Added explicit first-contact fingerprint approval UI; discovered Bonjour names are never automatically trusted.
- [x] XcodeGen project definition, local Swift package dependencies, local-network privacy description and Bonjour declaration.
- [x] macOS Swift CI and Android build/test CI workflows.

## Not complete yet

- [ ] Verify live Android ↔ iPhone handshake and encrypted message exchange on physical devices; deterministic fixture parity is covered by automated tests.
- [ ] Wire the Keychain trust vault and six-digit approval prompt into the live Bonjour handshake, including a monotonic local binding-sequence counter.\n- [ ] Connect authenticated sessions to the native iOS transport and implement the same durable inbox/outbox, ACK and conversation semantics as Android.
- [ ] Implement a bounded, back-pressured file stream with receiver staging, prefix verification, whole-file digest verification and atomic finalization on iOS.
- [ ] Implement the iOS 26 Wi-Fi Aware paired-device adapter and obtain Apple's entitlement approval.
- [ ] Prove Wi-Fi Aware interoperation with an actual Android device; API support on both platforms is not proof of compatible pairing.
- [ ] Integrate the isolated Android LAN adapter behind a reviewed transport selection path.
- [ ] Run iPhone ↔ iPhone, Android ↔ iPhone and iPhone ↔ Android tests on physical devices.
- [ ] Benchmark large transfers repeatedly before making speed claims.

## Run the wire tests

From `ios/NeaRerWire`:

```sh
swift test
```

The wire package is Foundation-first. CryptoKit and Network.framework sections are compiled on Apple platforms. The macOS workflow validates the Apple-specific code; physical radio behavior still requires real devices.

## Transport constraints discovered

- Apple Wi-Fi Aware is available on iPhone 12 and later with iOS 26, but requires the Wi-Fi Aware entitlement and user-visible paired-device access. Treat Android interoperation as experimental until proven.
- Apple's Network.framework peer-to-peer Wi-Fi path is Apple-only; its on-air protocol is not documented for third-party Android interoperability.
- Google's current Nearby Connections iOS/macOS support matrix lists Wi-Fi LAN as the implemented medium. Do not assume Android's BLE/Wi-Fi Direct/Wi-Fi Aware transports are available through the iOS SDK.
- The practical broad-compatibility fallback is a shared local Wi-Fi network or user-enabled hotspot with standard TCP/Bonjour, plus explicit local-network permission. No internet is required for that local path.
- iOS cannot silently enable Personal Hotspot or force-join arbitrary Wi-Fi networks.

## Apple prerequisites

- A Mac with a current Xcode and iOS 26 SDK for Wi-Fi Aware development.
- Apple Developer signing and approval for `com.apple.developer.wifi-aware` before the Wi-Fi Aware route can ship.
- Physical iPhone 12+ devices on iOS 26+ for Wi-Fi Aware tests.
- Older iPhones for Network.framework peer-to-peer and LAN fallback tests.

A Swift Package can be developed without paid Apple Developer membership. Device signing, entitlement approval, TestFlight and App Store distribution have separate Apple account requirements.

## Safety and integration boundary

Do not add plaintext mode, a second identity format, a duplicate Android message store, or an unreviewed replacement for the current Android Nearby service. A Bonjour service name is discovery metadata, never authenticated identity. The NeaRer v3 handshake, first-contact verification, key-change rejection and durable message semantics must pass before private messaging is enabled.