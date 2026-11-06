# Resume checkpoint foundation

NeaRer now has a pure Kotlin `TransferResumeCheckpoint` model with unit tests.

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

IMPORTANT: this is a validated protocol primitive, not a working pause/resume feature. The application does not yet persist checkpoints, negotiate offsets with another phone, call Nearby Payload.setOffset, or recover a transfer after process death. No user-facing resume claim should be made until those steps are implemented and tested on two physical devices.

References:
- Google Nearby Payload API documents getOffset/setOffset for FILE and STREAM payloads: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
- Google Nearby strategy documentation describes P2P_POINT_TO_POINT as the highest-throughput one-to-one topology, with a single-connection constraint: https://developers.google.com/nearby/connections/strategies
- LocalSend protocol v2 is a useful metadata-first and SHA-256 reference, but its published upload flow does not define byte-offset resume: https://github.com/localsend/protocol
