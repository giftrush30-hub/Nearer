# NeaRer chat transport integration

## Product rule

NeaRer has one conversation experience. Transport is an implementation detail shown as a helpful status, not a separate inbox for Bluetooth, Wi-Fi, mesh or Flash.

## Implemented in this source milestone

- Nearby Connections is the primary nearby messaging transport. It discovers and connects phones using the available Bluetooth, BLE and Wi-Fi radios, without requiring internet access.
- Wi-Fi Direct remains an available peer-to-peer path for the existing socket/file-transfer flow.
- Outgoing chat messages keep their original message ID across Nearby Connections.
- Incoming Nearby chat messages are deduplicated against the locally persisted message history.
- A receiver returns a delivery receipt only after the chat callback has saved the message locally.
- Duplicate message arrivals receive another receipt, so a lost receipt can recover without showing the message twice.
- The sender changes from Sending/Sent to Delivered after the receipt arrives. If Nearby reports a send failure, the message returns to the local Queued state.
- Existing offline drafts and queued messages remain local until a connection is ready.

## Important distinctions

- Nearby Connections is the Bluetooth/BLE/Wi-Fi hybrid path. Android's Nearby API abstracts radio choice and can upgrade to a faster available medium; NeaRer should not claim that every packet is Bluetooth-only or Wi-Fi-only.
- Wi-Fi Direct is a separate P2P/socket path. The current chat socket and Nearby chat now have different wire implementations, even though they feed the same local chat list.
- The existing optional mesh relay currently belongs to community/broadcast packets. Private one-to-one chat is deliberately not blindly broadcast through relays: the current UI does not yet select a remote recipient by stable identity, and relays must not expose private message content.
- Mesh chat requires per-contact stable IDs, addressed envelopes, route/TTL handling, end-to-end encryption, duplicate protection across hops, and receipts routed back to the origin. Relay must remain opt-in and transparent.
- NeaRer Flash is a separate experimental cellular signalling concept. Its intended result is a normal, typed event/message in the recipient's NeaRer conversation—not an unexplained missed-call artifact. The call-signalling decoder is not active in the current manifest and must not be described as working until incoming-call access/role requirements, carrier behaviour, call charges and device compatibility are tested. No mobile-data-free long-distance message can be promised without a functioning signal path and compatible recipient.

## Next integration sequence

1. Move local chat history and pending delivery state into a durable conversation database with per-contact threads.
2. Bind each thread to a verified stable NeaRer contact identity, not only the last connected endpoint.
3. Unify Nearby and Wi-Fi Direct socket frames behind one versioned message envelope and shared ACK/retry rules.
4. Add recipient-addressed mesh routing with end-to-end encryption and relay consent before enabling private chat relaying.
5. Prototype NeaRer Flash as an opt-in experimental signal adapter, decode it into a typed chat event, and validate across MTN, Airtel, Glo and 9mobile before integration or public claims.
6. Add durable retry scheduling and verify both directions on at least two physical Android phones, including disconnects and app restarts.

## Research references

- [Nearby Connections overview](https://developers.google.com/nearby/connections/overview)
- [Nearby Connections Android connection management](https://developers.google.com/nearby/connections/android/manage-connections)
- [Android Wi-Fi Direct](https://developer.android.com/develop/connectivity/wifi/wifip2p)
- [Android persistent background work](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Android CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService)
