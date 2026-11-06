# HyperTransfer: live resume integration blueprint

Status: engineering plan grounded in the current transport and Google Nearby Connections API. Resume remains experimental until the end-to-end acceptance tests below pass on two physical Android devices.

## Current implementation boundary

NeaRer already has:
- A durable checkpoint journal and receiver-owned partial-file store.
- A 4 MiB checkpoint boundary; partial bytes are synced before journal commits.
- Prefix SHA-256 verification and cleanup of stale, truncated, oversized, or corrupted partials.
- A bounded stream writer that resumes from a verified checkpoint, rejects early EOF and excess bytes, and preserves only the last durable checkpoint on cancellation.
- Versioned BYTES proposal and decision codecs plus pure negotiation rules.
- Unit tests for interrupted writes, restart recovery, corruption, changed source identity, zero-progress reads, overflow, cancellation, and empty files.

The active Nearby transport still uses FILE payloads and copies completed Nearby-managed temporary files into NeaRer storage. The checkpoint store/writer/codecs are not wired into that path. Do not describe the feature as live resume yet.

## API facts that constrain the design

1. Google documents Payload.getOffset()/setOffset() for FILE and STREAM payloads. The sender must apply the accepted offset to the actual outgoing payload before calling sendPayload().
2. For STREAM receives, the app reads payload.asStream().asInputStream() (or the ParcelFileDescriptor) in a background worker. The payload must be closed when the transfer is stopped.
3. BYTES, FILE, and STREAM callbacks do not have a cross-type ordering guarantee. Every control record must include a transfer identity and payload ID; metadata arriving late must be safely joined with pending payload state.
4. sendPayload() success means Nearby accepted the send request, not that delivery completed. Completion must be driven by PayloadTransferUpdate status.
5. Stream progress can have an unknown total, so the UI must use byte counts from the manifest and its own receive writer rather than assuming update.totalBytes is populated.

References:
- https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
- https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload.Stream
- https://developers.google.com/nearby/connections/android/exchange-data
- https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/PayloadCallback
- https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Connections

## Required protocol sequence

### A. Capability negotiation
- Advertise a new capability version with explicit booleans for receiver-managed STREAM resume and sender offset support.
- Treat absent, malformed, or older capability records as no-resume; continue with the existing FILE transfer path.
- Never infer support merely from the app version or from a peer's ability to transfer ordinary files.

### B. Stable transfer identity
- The bundle manifest transfer ID and item ID identify a logical file across reconnects.
- The sender must include stable source identity (size, relative path, modification timestamp where reliable, and full-file digest when available).
- The receiver stores partials under an app-private deterministic key derived from transfer ID + item ID; it must not rely on a Nearby payload ID surviving reconnects.

### C. Resume proposal and decision
- Receiver loads its checkpoint, verifies the partial prefix again, and proposes only a non-zero, aligned, durable offset.
- Sender reopens the selected source, validates its identity, hashes exactly the proposed prefix, and compares it with the receiver's prefix SHA-256.
- Sender returns an explicit decision. Any mismatch, unsupported peer, invalid offset, source change, or protocol error means restart at byte zero—not append.
- Decision and proposal records are bounded and versioned. Unknown versions are rejected safely.

### D. Stream lifecycle
- Sender opens the source at the accepted offset and creates the outgoing STREAM payload; apply Payload.setOffset(offset) before sendPayload().
- Receiver associates the incoming STREAM payload with its approved manifest item using the control metadata/payload ID association, regardless of callback order.
- Receiver consumes the stream on a bounded I/O executor through TransferResumeStreamWriter. It must not read on the main thread.
- The writer syncs data before recording each checkpoint. Cancellation/disconnect closes the stream and leaves only the last valid checkpoint reusable.
- On stream completion, verify exact declared length and the full-file SHA-256, then atomically promote the partial into the bundle destination. Do not expose an unverified partial as a completed file.
- If the final digest fails, discard the partial and report failure; never silently mark the item complete.

### E. Cleanup and compatibility
- Release Payload, InputStream, and descriptor resources on success, failure, cancellation, endpoint loss, executor rejection, and app shutdown.
- Keep existing FILE path unchanged for peers without the negotiated capability.
- Ensure a failed resume attempt can restart from zero without creating duplicate destination files or double-completing the bundle session.
- Persist the receiver's stable bundle destination mapping or reconstruct it safely; the current in-memory bundle destination map is lost on process death and must not be treated as durable resume state.

## Acceptance tests before calling resume complete

### Automated
- Both peers advertise resume support; legacy peer falls back to FILE.
- Proposal/decision arrives before or after stream callback and still associates by ID.
- Accepted offset is applied before outgoing send.
- Prefix mismatch, changed source, invalid alignment, wrong transfer ID, wrong item ID, and unsupported peer all restart at zero.
- Disconnect at 1 MiB, just before a 4 MiB checkpoint, just after a checkpoint, and near EOF; only committed bytes survive.
- Process death/recreation restores partial + journal and revalidates prefix.
- Full digest mismatch never promotes the partial.
- Executor rejection, cancellation, endpoint loss, and app shutdown close all streams and leave no false completion.
- Empty files and files smaller than one checkpoint complete correctly.

### Two-device Android tests
- Same file sent Cluster and Point-to-Point; resume test is performed separately from throughput comparison.
- Interrupt sender and receiver independently, reconnect, and verify the final byte-for-byte file.
- Repeat on low-memory Android device and a newer device.
- Record resume offset, bytes retransmitted, elapsed time, failure reason, and final SHA-256.

## Completion gate

Do not raise the project completion estimate for resume until the protocol is integrated into NearbyConnectionsTransport, the Android unit/instrumentation suite passes, and a two-device interruption/reconnect test proves that a partially transferred large file finishes correctly without restarting from byte zero.


## Additional implementation guard: finalization must be gated

The source/API review also confirms two important lifecycle details:

- `PayloadCallback.onPayloadReceived()` can fire at the start of a FILE/STREAM transfer, before the complete bytes are present. Promotion must wait for the successful transfer update and the receiver's own exact-length/digest verification.
- Android's `AtomicFile` provides sync-before-commit semantics for atomic file replacement, but does not provide locking. NeaRer must retain per-transfer serialization if it adopts AtomicFile for journals or destination metadata.

A pure `TransferResumeFinalizationPolicy` now rejects a partial unless it is a FILE, has exactly the manifest-declared byte count, has a well-formed expected SHA-256, and its computed full-file SHA-256 matches. This policy is a guard for the future transport integration; it is not yet wired into live Nearby transfer promotion.

Research references:
- Google Nearby Payload API: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
- Google Nearby PayloadCallback lifecycle: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/PayloadCallback
- Google Nearby exchange-data guide: https://developers.google.com/nearby/connections/android/exchange-data
- Android AtomicFile: https://developer.android.com/reference/android/util/AtomicFile


## Callback replay hardening

The exchange tracker now keeps a bounded, short-lived tombstone for completed or conflicted endpoint/payload exchanges. Late BYTES/FILE/STREAM callbacks for those IDs are ignored instead of creating a fresh incomplete exchange. Tombstones are removed on endpoint cleanup and expire using the same monotonic TTL.

A repeated identical proposal/decision/data callback does not refresh an incomplete exchange's lease. Only a newly accepted event advances its expiry timestamp, preventing a peer from keeping stale pending state alive indefinitely by replaying duplicates. Automated tests cover consumed-exchange replay, conflicted-exchange replay, tombstone expiry, endpoint cleanup, and duplicate retry expiry.

This remains a transport-independent safeguard. The active transport still does not invoke the tracker, so it must not be mistaken for end-to-end resume integration.


## Monotonic expiry and signed-clock wraparound

Resume exchange entries and terminal replay tombstones use elapsed-time subtraction (now minus createdAt) rather than comparing absolute monotonic-clock readings. Java's monotonic clock has an arbitrary origin and its signed-long reading may wrap; the elapsed difference remains the valid comparison for realistic timeout windows. A negative elapsed difference is treated as an injected-clock rollback and expires the stale entry conservatively. Unit coverage exercises both pending entries and terminal tombstones across the signed-long boundary.

Reference: Java System.nanoTime() documentation explains that readings have an arbitrary origin and recommends comparing elapsed differences rather than absolute values: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()
