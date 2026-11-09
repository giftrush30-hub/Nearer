# NeaRer nearby connection security — current implementation

## What the current source now does

- Each installation can create a P-256 ECDSA signing key in Android Keystore.
- The private key is not exported by the app; only its encoded public key is sent to a nearby peer.
- On a Nearby Connections link, each side sends a versioned identity hello containing its self-reported NeaRer ID, public key, and a fresh 32-byte random challenge.
- The other side signs the challenge with its own Keystore private key and returns its ID, public key, and signature.
- The receiver checks that the response ID and public key match the hello, then verifies the ECDSA signature against the one-use challenge.
- Challenges and temporary peer key state are cleared when that endpoint disconnects.
- After a valid proof, the first public key seen for a self-reported NeaRer ID is saved locally (trust on first use). Later connections must present the same key; a changed key is rejected and the saved pin is never silently overwritten.
- A first-seen pin is a continuity warning mechanism, not proof that the ID belongs to a particular person. Users still need a future explicit verification / recovery UX.
- The Wi-Fi Direct socket fallback now performs a mutual P-256 challenge-response after the six-digit connection approval: both phones exchange an ID, public key and fresh 32-byte challenge, sign the other phone's challenge, and verify that the proof key matches the announced key.
- Wi-Fi Direct peers use the same local first-seen key pin store as Nearby Connections. A changed key blocks the session; the previous pin is not silently replaced.
- Nearby Connections chat is now held closed until the current endpoint completes v2 identity proof and its key/ID continuity checks pass. Queued messages are released only after that proof; the queue is bound to both the nearby transport code and the previously verified NeaRer ID. A remembered transport code alone cannot authenticate a fresh connection.
- Older queued messages that lack a verified NeaRer ID binding are deliberately not auto-delivered. The user may need to resend them after a verified connection.
- The Wi-Fi Direct socket fallback now has its own AES-GCM-encrypted local outbox. Each queued message is bound to the NeaRer ID authenticated during that direct session, is sent again after that same ID reconnects, and is removed only after the sender durably saves the recipient acknowledgement.
- The direct fallback outbox is capped at 500 pending messages. If its encrypted data cannot be unlocked, the implementation refuses new writes rather than overwriting the unreadable queue.
- The Wi-Fi Direct message frames themselves are still not end-to-end encrypted in transit; the outbox encryption protects only the sender's local stored copy.
- The Wi-Fi Direct handshake authenticates possession of a key, not a person's real-world identity. The displayed NeaRer ID remains self-reported, and the six-digit comparison remains a separate human connection-approval step.
- Older version-1 identity messages remain displayable as self-reported metadata for compatibility.

## Important security boundary

A valid signature proves that the connected device controls the private key corresponding to the public key it announced. It does **not** prove that the self-reported `NR-...` ID belongs to a particular human or to the same person who previously used that ID. The current random installation ID is not derived from the signing public key and there is no global identity registry or verified-human identity system. Local trust-on-first-use key pins and stable-code-to-ID continuity checks are present, but there is not yet a user-managed trusted-contact review or safe key recovery/rotation flow.

The UI must therefore continue to describe the ID as self-reported. Do not call this verified human identity, end-to-end encrypted chat, or a secure mesh.

## Not yet covered

- Chat and file payload encryption in transit has not been added by this change.
- Nearby Connections sender outbox entries are retained until the sender's Delivered receipt state is durably saved to encrypted chat history. If saving fails, the message stays queued and can be retried; receiver-side message-ID deduplication permits a repeated send to receive a fresh acknowledgement without duplicating the chat entry.
- The Wi-Fi Direct fallback now also avoids displaying Delivered when its local receipt update cannot be saved. Its older text-socket send queue is not yet a durable, identity-bound outbox equivalent to Nearby Connections.
- The local chat-history JSON store is now encrypted at rest with the same Android Keystore AES-256-GCM key used for the pending outbox; older plaintext chat history is migrated on successful load/save.
- Received files are still stored in the app's private received folder without application-level file encryption.
- No cross-phone identity recovery or user-managed trusted-contact/key-change approval flow exists. A changed key is blocked, but this version has no safe in-app recovery/rotation flow.
- No Gradle compilation, automated Android instrumentation test, or physical-device interoperability test has been run for this change.

## Next engineering gates

1. Add a user-facing trusted-peer review and safe key-change/recovery UX; current first-seen pinning blocks silent key changes but cannot safely approve a legitimate key rotation.
2. Design and test end-to-end message/file encryption independently of the nearby transport.
3. Exercise simultaneous discovery, approval, reconnect, stale challenge, replay, key mismatch, and mixed-version cases on real Android devices.


## Additional message-session gate

- Incoming direct chat messages and delivery acknowledgements are now accepted only after the current Nearby Connections endpoint has completed the v2 challenge-response and local key/ID continuity checks.
- A successful connection approval or a legacy v1 self-reported ID alone is not sufficient to inject chat or mark a message Delivered.
- This is an authentication gate, not end-to-end encryption: message bodies are still not encrypted in transit.


## File-control and group-session gates

- Nearby file metadata and file-resume requests are ignored unless the endpoint completed the v2 identity proof and key-continuity checks for its current session.
- Outgoing Nearby file transfers require a verified endpoint session.
- Group-name fan-out includes only endpoints with a verified session; unverified nearby devices are not sent group chat payloads.
- These checks authenticate the session's possession of a pinned key. They do not encrypt file bytes or message bodies in transit.
