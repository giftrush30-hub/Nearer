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
- The Wi-Fi Direct handshake authenticates possession of a key, not a person's real-world identity. The displayed NeaRer ID remains self-reported, and the six-digit comparison remains a separate human connection-approval step.
- Older version-1 identity messages remain displayable as self-reported metadata for compatibility.

## Important security boundary

A valid signature proves that the connected device controls the private key corresponding to the public key it announced. It does **not** prove that the self-reported `NR-...` ID belongs to a particular human or to the same person who previously used that ID. The current random installation ID is not derived from the signing public key and there is no global identity registry or verified-human identity system. Local trust-on-first-use key pins and stable-code-to-ID continuity checks are present, but there is not yet a user-managed trusted-contact review or safe key recovery/rotation flow.

The UI must therefore continue to describe the ID as self-reported. Do not call this verified human identity, end-to-end encrypted chat, or a secure mesh.

## Not yet covered

- Chat and file payload encryption in transit has not been added by this change.
- The local chat-history JSON store is now encrypted at rest with the same Android Keystore AES-256-GCM key used for the pending outbox; older plaintext chat history is migrated on successful load/save.
- Received files are still stored in the app's private received folder without application-level file encryption.
- No cross-phone identity recovery or user-managed trusted-contact/key-change approval flow exists. A changed key is blocked, but this version has no safe in-app recovery/rotation flow.
- No Gradle compilation, automated Android instrumentation test, or physical-device interoperability test has been run for this change.

## Next engineering gates

1. Add a user-facing trusted-peer review and safe key-change/recovery UX; current first-seen pinning blocks silent key changes but cannot safely approve a legitimate key rotation.
2. Design and test end-to-end message/file encryption independently of the nearby transport.
3. Exercise simultaneous discovery, approval, reconnect, stale challenge, replay, key mismatch, and mixed-version cases on real Android devices.
