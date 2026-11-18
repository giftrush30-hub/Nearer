# NeaRer for iPhone — engineering workspace

This directory is the isolated native iOS work area. It does not alter the Android app or its transport.

## Current status

- [x] Research-backed platform and transport decision recorded in `docs/IOS_COMPATIBILITY_ARCHITECTURE.md`.
- [x] Swift Package wire-envelope foundation added under `NeaRerWire/`.
- [x] Existing NeaRer v3 hello/proof and secure-packet envelope names and bounded fields represented in Swift.
- [x] Six Swift unit tests pass on Swift 6.2.1 / Linux (Foundation-only wire layer).
- [ ] CryptoKit/Security P-256 interoperability and deterministic Android↔iOS golden vectors.
- [ ] Native SwiftUI iOS app target and signing configuration.
- [ ] Wi-Fi Aware / Network.framework / LAN transport adapters.
- [ ] Physical iPhone testing.

## Run the wire tests

From this directory:

```sh
swift test
```

The package intentionally uses Foundation only. This allows protocol envelope tests to run on Linux CI. It does not validate public-key mathematics or encryption. Those checks belong in the later Apple CryptoKit/Security adapter and must be run on macOS/iOS.

## Planned native app prerequisites

- A Mac with a current Xcode version and iOS 26 SDK for Wi-Fi Aware development.
- Apple Developer signing and the approved `com.apple.developer.wifi-aware` entitlement for the Wi-Fi Aware adapter.
- Explicit local-network, Bluetooth (if a custom BLE bootstrap is added), and other required usage descriptions.
- Physical iPhone 12+ devices running iOS 26+ for Wi-Fi Aware tests.
- Additional iPhones on older supported iOS versions for Network.framework and local-LAN fallback tests.

A Swift Package can be developed and tested without a paid Apple Developer account. Device signing, entitlement approval, TestFlight and App Store distribution have separate Apple account requirements.

## Safety boundary

Do not add a plaintext mode, a second identity format, a duplicate Android transport, or an unreviewed replacement for the existing Android messaging/file-transfer path. iOS must pass the same authenticated session and durable-delivery rules before it is allowed to exchange private NeaRer messages.
