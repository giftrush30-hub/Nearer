# NeaRer for iPhone — engineering workspace

This is the isolated native iOS work area. Android's production Nearby messaging service and HyperTransfer route remain the source of truth and have not been replaced.

## Implemented on this branch

- [x] Research-backed Android/iPhone/iPhone transport matrix and platform limitations.
- [x] Swift codecs for the current NeaRer v3 hello/proof and secure packet envelopes, with canonical peer-ID validation.
- [x] CryptoKit P-256 signatures, ECDH, HKDF-SHA256 and AES-256-GCM.
- [x] Android-compatible domain-separated peer proof signing and verification.
- [x] Signed recipient identity-binding codec, expiry/fingerprint checks and monotonic sequence validation.
- [x] Device-only Keychain identity and peer-trust vault.
- [x] Two-sided v3 handshake over native Bonjour/TCP: hello, proof and signed recipient-key binding exchange.
- [x] Shared six-digit first-contact verification code and Swift/Kotlin golden vector.
- [x] Explicit six-digit code and signing-key fingerprint approval UI; discovered Bonjour names are never automatically trusted.
- [x] Session-key derivation only after proof, binding and explicit trust approval.
- [x] Direct encrypted text chat over approved sessions.
- [x] Bounded AES-GCM encrypted local message persistence, with a device-only Keychain database key, atomic writes and record/database size limits.
- [x] Swift/Kotlin golden vectors for handshake transcript, P-256/HKDF/AES-GCM, ECDSA proof, recipient binding, transfer manifest, resume proposal/decision, stream metadata and TCP framing.
- [x] Swift port of Android NRB1 transfer bundle manifest with safe-path validation.
- [x] Swift port of Android NRRS resume proposal, decision and no-resume envelopes.
- [x] Swift port of Android NRSM one-file stream metadata.
- [x] Bounded incremental TCP framing in Swift and matching Android big-endian frame codec.
- [x] Native iOS Network.framework Bonjour/TCP adapter with Apple's peer-to-peer Wi-Fi option.
- [x] Isolated Android NSD/Bonjour/TCP adapter using the same service type and frame format; not connected to the production Nearby service.
- [x] XcodeGen project definition, local-network privacy description, Bonjour declaration, Wi-Fi Aware entitlement example and CI workflows.

## Not complete yet

- [ ] Run live iPhone ↔ iPhone and Android ↔ iPhone handshake and text-chat tests on physical devices.
- [ ] Add Android-equivalent durable offline outbox/retry, delivery acknowledgements, group messaging and relay semantics.
- [ ] Implement iOS file-transfer streaming with back-pressure, receiver staging, resume recovery, prefix and whole-file digest verification, and atomic finalization.
- [ ] Implement the iOS 26 Wi-Fi Aware paired-device adapter and obtain Apple's entitlement approval.
- [ ] Prove Android ↔ iPhone Wi-Fi Aware interoperation on real devices; API support on both platforms does not prove pairing compatibility.
- [ ] Integrate the isolated Android LAN adapter behind a reviewed production transport-selection path.
- [ ] Benchmark large transfers repeatedly before making any speed claims.
- [ ] Complete the latest macOS Swift tests, iOS simulator build and Android JVM/build checks before merging.

## Run the wire tests

From `ios/NeaRerWire`:

```sh
swift test
```

The Foundation-first codecs can be exercised on Linux. CryptoKit and Network.framework implementations compile on Apple platforms. CI and physical-radio tests are separate validation gates.

## Transport constraints discovered

- Apple Wi-Fi Aware is available on iPhone 12 and later with iOS 26, but requires the Wi-Fi Aware entitlement and user-visible paired-device access. Treat Android interoperability as experimental until proven.
- Apple's Network.framework peer-to-peer Wi-Fi path is Apple-only; its over-the-air protocol is not a supported third-party Android interoperability contract.
- Google's current iOS/macOS Nearby Connections support matrix lists Wi-Fi LAN as the implemented medium. Do not assume Android's BLE, Wi-Fi Direct or Wi-Fi Aware transports exist in the iOS SDK.
- The broad fallback for Android ↔ iPhone is standard TCP/Bonjour over the same local Wi-Fi network or a user-enabled hotspot. No internet is required for that local route.
- iOS cannot silently enable Personal Hotspot or force-join arbitrary Wi-Fi networks.

## Apple prerequisites

- A Mac with current Xcode and the iOS 26 SDK for Wi-Fi Aware development.
- Apple Developer signing and approval for `com.apple.developer.wifi-aware` before the Wi-Fi Aware route can ship.
- Physical iPhone 12+ devices on iOS 26+ for Wi-Fi Aware tests.
- Physical iPhones for Network.framework peer-to-peer and LAN fallback tests.

A Swift Package can be developed without paid Apple Developer membership. Device signing, entitlement approval, TestFlight and App Store distribution have separate Apple account requirements.

## Safety and integration boundary

A Bonjour service name is discovery metadata, never authenticated identity. Do not add plaintext mode, a second identity format, a duplicate Android message store or an unreviewed replacement for the current Android Nearby service. The iOS direct-chat implementation is an engineering prototype until CI, physical-device tests, delivery reliability and privacy review pass.