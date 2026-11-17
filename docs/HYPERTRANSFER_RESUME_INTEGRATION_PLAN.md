# HyperTransfer: live resume integration and validation plan

Status: NeaRer's mainline contains an active Nearby STREAM resume path, receiver-owned durable checkpoints, sender-side durable staging/recovery, and receiver completion receipts. It remains experimental until the SDK offset contract and interruption/reconnect behaviour are proven on physical Android devices.

## Current implementation

### Sender
- A selected content URI is staged on a bounded I/O executor into the app-private `filesDir/hypertransfer-outgoing` directory; it is not kept only in the evictable cache.
- The staged source is measured and SHA-256 hashed. A stable transfer ID binds sender, recipient, item metadata and source digest; the Nearby payload ID remains session-scoped.
- A durable journal is synced and atomically committed only after the staged source has been synced and re-verified.
- After service/process recreation, a matching authenticated peer's STREAM capability triggers recovery. NeaRer validates the saved source again, allocates a fresh Nearby STREAM payload ID, and resumes negotiation using the stable transfer identity.
- Explicit cancellation, verified completion and bounded receipt-retention expiry remove the durable record and source. Ordinary transport shutdown preserves them.
- Duplicate sends with the same stable transfer identity cannot overwrite an existing durable source.

### Receiver
- The receiver offers only authenticated, verified partial checkpoints matching the transfer and item identity.
- The Nearby STREAM exchange controller correlates metadata, proposal/decision and payload by authenticated endpoint/peer and payload ID despite callback reordering.
- The stream bridge writes on a bounded I/O executor. It syncs bytes before journal checkpoints and verifies final size and full-file SHA-256 before publication.
- A private completion-receipt journal records the authenticated peer, stable transfer/item identity, digest, size and published file path. If a sender retries metadata after losing its receipt, the receiver can acknowledge the already-published file instead of publishing a second copy.
- The receiver still retains the legacy FILE path for peers or sessions that do not negotiate STREAM capability.

## Important limitations and unresolved risks

1. **Nearby STREAM offset semantics are not yet proven.** The current sender validates the receiver's prefix and opens its source stream at the accepted byte offset. The active STREAM send path does not currently call `Payload.setOffset()`. Google's reference documents offsets for STREAM as well as FILE, but blindly combining an already-positioned InputStream with `setOffset()` could double-skip bytes. Do not change this until an SDK-backed test or two-device experiment establishes the exact contract and verifies the emitted bytes.
2. **Completion publication and its receipt journal are separate durable writes.** A process crash in the small interval after the final file is published but before the receipt record is committed could still permit a duplicate publication on retry. A future transaction-like finalization step should close this gap.
3. **Source modification time is not part of the current STREAM metadata identity.** Exact size and full digest are used; modification time remains optional/unset on this path.
4. **Device behaviour remains unverified.** JVM tests cannot prove Nearby radio behaviour, callback timing under real disconnections, process death, OEM power management, or STREAM offset semantics.
5. **No speed multiplier is claimed.** Throughput comparisons require controlled measurements on the same device pair, file set and radio conditions.
6. **This is a one-file STREAM transfer path.** Do not claim that a folder is resumed as one atomic job or that source folder hierarchy is restored until the bundle workflow is integrated and tested.

## API and lifecycle facts

- `Payload.getOffset()/setOffset()` is documented for FILE and STREAM payloads.
- `onPayloadReceived()` is an early payload notification, not proof that all bytes have arrived. Completion must wait for successful transfer updates and NeaRer's own final size/digest verification.
- `sendPayload()` success means Nearby accepted the request, not that the recipient saved the file.
- STREAM consumption must stay off the main thread, and payload/stream resources must be closed on success, failure, cancellation, endpoint loss and shutdown.
- BYTES, FILE and STREAM callbacks can arrive in different orders. The active exchange controller uses endpoint, authenticated peer and payload ID to associate them.

## Required automated validation

- Legacy peers safely use the FILE fallback.
- Metadata, proposal/decision and STREAM callbacks associate correctly regardless of callback order.
- Wrong peer, wrong payload ID, stale IDs, duplicate controls and malformed versions fail closed.
- Prefix mismatch, changed source, invalid alignment, wrong transfer/item identity and unsupported peer restart at byte zero rather than appending.
- Interrupted writes preserve only synced checkpoints; restart revalidates the prefix.
- Exact-length and full-file digest checks gate publication.
- Duplicate metadata after a committed completion receipt returns a saved receipt and does not publish a second copy.
- Duplicate sender jobs cannot overwrite an existing durable source.
- Cancellation removes the journal/source; ordinary transport shutdown preserves them.
- Empty files, short files, zero-progress reads, overflow and executor rejection are covered.

## Required two-device Android acceptance

Test sender-side and receiver-side interruption separately, including:
- disconnect before the first checkpoint;
- disconnect just before and just after a 4 MiB checkpoint;
- disconnect near EOF;
- kill/restart the sender process while a transfer is pending;
- kill/restart the receiver process while a partial is pending;
- kill/restart the sender after receiver publication but before receipt delivery;
- reconnect to the same authenticated peer and verify exact final bytes and SHA-256;
- repeat on a lower-memory Android phone and a newer phone.

Record the negotiated offset, retransmitted bytes, elapsed time, final digest, failure reason and whether a duplicate publication occurred. Do not call resume release-ready until these tests pass.

## References

- Google Nearby `Payload`: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
- Google Nearby `Payload.Stream`: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload.Stream
- Google Nearby exchange-data guide: https://developers.google.com/nearby/connections/android/exchange-data
- Google Nearby `PayloadCallback`: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/PayloadCallback
- Android `AtomicFile`: https://developer.android.com/reference/android/util/AtomicFile
