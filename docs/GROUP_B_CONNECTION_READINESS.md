# Group B — Nearby discovery and connection readiness

## Runtime behavior
- The default SmartConnect path uses Google Nearby Connections with the cluster strategy, allowing the SDK to negotiate available Bluetooth/Wi-Fi transports.
- Discovery is radio-ready when Bluetooth **or** Wi-Fi is enabled. NeaRer never silently turns either radio on or bypasses Android runtime permissions.
- Android 8–12 discovery uses the location permission model; Android 12+ also needs granular Bluetooth permissions; Android 13+ uses `NEARBY_WIFI_DEVICES` for Wi-Fi discovery. Android 8–12 devices may also need Location services enabled by the user.
- Ultra Speed uses point-to-point strategy and a separate service ID; both phones must deliberately choose the same mode. SmartConnect defaults to cluster mode.

## Trust and reconnection
- A stable NeaRer ID is identity. Nearby endpoint IDs are temporary session addresses.
- Only peers with previously pinned signing and recipient-encryption keys reconnect automatically. First-time peers still require in-person code comparison and explicit approval.
- Duplicate reconnect requests are suppressed per endpoint and released when that endpoint is lost, connects, or the SDK request fails (including synchronous SDK errors).
- Discovery callbacks carry a per-scan generation. Late callbacks from stopped or superseded scans are ignored; discovery loss is not treated as a real disconnect.
- Connection timing is measured with a monotonic clock from request/offer until authenticated identity and recipient-key binding complete. Failed attempts are discarded.

## Multi-peer boundary
The Nearby transport uses cluster strategy and maintains multiple active endpoint identities. The current app surface still presents one selected direct-chat peer at a time; the separate messaging/community work owns multi-peer chat behavior.

## Device validation
JVM tests cover radio readiness, trust gating, duplicate suppression/retry release, stale callback generations and timing cleanup. They cannot prove OEM radio behavior, scan latency, background survival or real Bluetooth/Wi-Fi interoperability. Validate on physical Samsung, Tecno, Infinix, itel, Xiaomi and Oppo phones, including supported Android 8–16 devices. Keep battery optimization and nearby permissions user-controlled. No universal compatibility or fixed discovery-time claim is made until device testing.
