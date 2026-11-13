# Group B — Nearby discovery and connection readiness

## Runtime behavior

- The default SmartConnect path uses Google Nearby Connections with the cluster strategy, so the SDK can negotiate available nearby Bluetooth/Wi-Fi transports.
- The app considers discovery radio-ready when Bluetooth **or** Wi-Fi is enabled. Both are not required by the app's readiness gate.
- NeaRer never silently turns either radio on and never bypasses Android runtime permissions. If neither radio is available, the user is told what to enable.
- Android 8–12 discovery uses the location permission model; Android 12+ also needs the granular Bluetooth permissions; Android 13+ uses `NEARBY_WIFI_DEVICES` for Wi-Fi discovery. Android 8–12 devices may also need Location services switched on by the user.
- The separate Ultra Speed mode uses point-to-point strategy and a different service ID. Both phones must deliberately use the same mode. SmartConnect defaults to the multi-peer cluster mode.

## Trust and reconnection

- A stable NeaRer ID is the identity. Nearby endpoint IDs are temporary session addresses and are never treated as persistent identity.
- Only a peer with its previously pinned signing and recipient-encryption keys is eligible for automatic reconnection.
- First-time peers still go through the in-person pairing-code check and explicit approval.
- Duplicate automatic requests are suppressed per temporary endpoint. The suppression is cleared when the endpoint is lost, connects, or its connection request fails, allowing a later discovery attempt.
- Each discovery run has its own callback generation. Late found/lost callbacks from an earlier scan or a stopped scan are ignored, and discovery loss is not misreported as a direct disconnect.
- A key change or failed identity proof remains a hard failure; reconnect convenience must not weaken authentication.

## Multi-peer boundary

The Nearby transport uses the cluster strategy and maintains multiple active endpoint identities. The current app surface still presents one selected direct-chat peer at a time; simultaneous multi-peer chat/group behavior is handled by the separate messaging/community work and must not be inferred from discovery alone.

## Device validation still required

Automated JVM tests cover radio-readiness decisions, trust gating, endpoint deduplication and retry release. They cannot prove OEM radio behavior, scan latency, background survival, or real Bluetooth/Wi-Fi interoperability. Validate on physical Samsung, Tecno, Infinix, itel, Xiaomi and Oppo phones, including Android 8–16 where supported. Keep battery optimization and nearby permissions user-controlled. No claim of universal manufacturer compatibility or fixed discovery time is made until those tests are run.
