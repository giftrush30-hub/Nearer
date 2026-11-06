# Resume checkpoint foundation

NeaRer now has a pure Kotlin `TransferResumeCheckpoint` model, a versioned binary codec, and a durable local journal with unit tests.

It binds a checkpoint to:
- bundle transfer ID and item ID;
- safe relative destination path;
- expected source byte length;
- optional source modification timestamp;
- committed byte offset;
- SHA-256 digest of the retained prefix.

Partial offsets must be aligned to 4 MiB durable boundaries. A completed file may use its exact final byte length. The alignment helper rounds down to the greatest safe boundary not beyond the number of bytes already synced.

Before a future resume implementation trusts a checkpoint, it must:
1. confirm that the selected source still matches transfer/item/path/size/version identity;
2. confirm that the destination temporary file exists and is at least as long as the checkpoint;
3. re-hash the retained prefix and compare it with the checkpoint digest;
4. negotiate the offset with the peer;
5. resume from that offset and verify the final full-file digest before finalisation.

IMPORTANT: the local checkpoint journal and prefix verifier are implemented and tested as storage primitives, but they are not yet wired into the live file-receive pipeline. The application does not yet persist partial-file checkpoints during transfer, re-hash an on-disk retained prefix, exchange the resume proposal/decision with another phone, call Nearby Payload.setOffset, or recover an interrupted transfer after process death. This is not yet a working pause/resume feature. No user-facing resume claim should be made until those steps are integrated and tested on two physical devices.

The receive pipeline also has an endpoint-scoped completion-order tracker: if Nearby reports a FILE payload's SUCCESS before delivering the corresponding FILE descriptor callback, the completion is retained and the later descriptor can still enter the save and verification path. The tracker is bounded to prevent stale callback state from growing indefinitely. This is callback-order resilience, not durable resume.

References:
- Google Nearby Payload API documents getOffset/setOffset for FILE and STREAM payloads: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
- Google Nearby strategy documentation describes P2P_POINT_TO_POINT as the highest-throughput one-to-one topology, with a single-connection constraint: https://developers.google.com/nearby/connections/strategies
- LocalSend protocol v2 is a useful metadata-first and SHA-256 reference, but its published upload flow does not define byte-offset resume: https://github.com/localsend/protocol


## Tested resume negotiation policy

`TransferResumeNegotiation` now defines the safe decision boundary independently of Nearby callbacks. It proposes an offset only when both peers advertise offset support, the receiver has re-verified the retained prefix, and the checkpoint is a valid non-zero partial checkpoint. The sender accepts only an exact transfer/item/path/size/source-modification match, a 4 MiB-aligned offset, and a prefix SHA-256 that matches a hash independently computed from its own selected source. A missing, malformed, or mismatched sender-side prefix hash falls back to byte zero. Unsupported peers, missing checkpoints, changed sources and malformed offsets all fall back to byte zero.

The bounded, versioned `TransferResumeMessageCodec` now serializes proposals with a payload-ID binding and strict field/UTF-8/length validation. The codec and policy unit tests pass in GitHub Actions run 42231603485. This is a tested protocol decision component, not a live wire handshake. It intentionally does not claim that the receiver has actually transmitted a proposal or that the sender has called `Payload.setOffset`. The remaining integration must carry the proposal and decision over Nearby BYTES control messages, bind them to the correct endpoint/payload, apply the accepted offset before payload dispatch, checkpoint durable received bytes, and verify the final full-file digest.
