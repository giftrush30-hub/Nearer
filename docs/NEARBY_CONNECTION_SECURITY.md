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
- Older version-1 identity messages remain displayable as self-reported metadata for compatibility.

## Important security boundary

A valid signature proves that the connected device controls the private key corresponding to the public key it announced. It does **not** prove that the self-reported `NR-...` ID belongs to a particular human or to the same person who previously used that ID. The current random installation ID is not derived from the signing public key and there is no global identity registry or trusted key-pinning/recovery flow yet.

The UI must therefore continue to describe the ID as self-reported. Do not call this verified human identity, end-to-end encrypted chat, or a secure mesh.

## Not yet covered

- Wi-Fi Direct fallback has not been given this challenge-response protocol.
- Chat and file payload encryption in transit has not been added by this change.
- Existing chat history and received files are not encrypted at rest by this change.
- No cross-phone identity recovery or user-managed trusted-contact/key-change approval flow exists. A changed key is blocked, but this version has no safe in-app recovery/rotation flow.
- No Gradle compilation, automated Android instrumentation test, or physical-device interoperability test has been run for this change.

## Next engineering gates

1. Add a user-facing trusted-peer review and safe key-change/recovery UX; current automatic first-seen pinning only detects later key changes.
2. Apply equivalent authentication to the Wi-Fi Direct transport.
3. Design and test end-to-end message/file encryption independently of the nearby transport.
4. Exercise simultaneous discovery, approval, reconnect, stale challenge, replay, key mismatch, and mixed-version cases on real Android devices.
