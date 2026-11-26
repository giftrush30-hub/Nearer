# HyperTransfer live wiring audit

Audit basis: current `main` source, especially `NearbyConnectionsTransport.kt`, `NearbyStreamPayloadBridge.kt`, `NearbyStreamExchangeController.kt`, `TransferResumeCoordinator.kt`, `TransferResumeStreamWriter.kt`, `TransferResumeStore.kt`, and the related codecs/policies.

Audit date: 2026-11-16

## Executive status

HyperTransfer resume is **partially integrated into the live Nearby transport**. It is no longer accurate to say that the checkpoint store, writer, codecs, and exchange tracker are entirely unwired. The main branch has a live STREAM handshake and receive path. However, this is not yet a verified production guarantee: two-device interruption/reconnect tests, process-death recovery tests, and SDK offset semantics still need explicit validation.

Do not advertise "resume always works" or a measured speed advantage until those gates pass.

## Live path observed in source

### Sender preparation

1. `sendHyperTransferStream` accepts a selected content URI and authenticated endpoint identity.
2. A deferred Nearby STREAM payload is created first so its payload ID can be included in control metadata.
3. The selected URI is copied to an app-cache staging file while SHA-256 and byte count are calculated off the UI thread.
4. The sender creates a stable transfer identity from sender/recipient stable IDs, file size, full digest, and display name. A new Nearby payload ID is not used as the durable transfer identity.
5. The sender stores an `OutgoingHyperStream`, sends bounded stream metadata, and arms a negotiation timeout.

### Receiver negotiation

1. Incoming STREAM payloads are rejected unless the endpoint is authenticated and has a valid stable NeaRer identity.
2. `NearbyStreamExchangeController` joins metadata, resume proposal/decision, and STREAM payload by endpoint, authenticated peer, and payload ID. Its tracker handles callback reordering and bounded pending state.
3. The receiver discovers verified partial checkpoints belonging to that authenticated sender and matching transfer/item metadata.
4. If a candidate exists, the receiver sends a versioned resume proposal; otherwise it sends an explicit no-resume response.
5. The sender validates peer identity, source identity, offset alignment, and the receiver's prefix digest before choosing a resume offset or a zero-byte restart.

### Stream consumption and finalization

1. The sender supplies either a source opened at the validated offset or a fresh source at byte zero to the deferred STREAM payload.
2. The receiver consumes the stream through `NearbyStreamPayloadBridge` on the configured I/O executor.
3. `TransferResumeStreamWriter` appends only to a peer-bound, re-verified checkpoint; it syncs file bytes before journal checkpoint commits, rejects short/long streams, and preserves only durable progress on interruption.
4. `TransferResumeCoordinator.receiveStreamAndFinalize` performs final size/digest checks and publication.
5. The receiver sends a completion receipt. The sender retains its staged source temporarily while waiting for the receipt and retries on a failed/missing receipt.

### Compatibility path

The existing FILE-based path remains available for peers that do not negotiate STREAM capability or when stream preparation/negotiation falls back. Do not conflate that SDK FILE offset path with the receiver-owned STREAM checkpoint path; they have different resume mechanics.

## What is implemented in source

- Authenticated peer binding for resume exchanges and receiver checkpoints.
- Stable transfer identity independent of Nearby payload ID.
- Versioned, bounded metadata and proposal/decision envelopes.
- Callback-order-tolerant metadata / control / STREAM association.
- Verified checkpoint discovery and sender prefix re-hashing.
- Resume-aware bounded stream writing with durable checkpoints.
- Exact-length and whole-file SHA-256 verification before publication.
- Explicit receiver completion receipts and sender retry/retention handling.
- Resource cleanup paths for pending/active stream payloads and endpoint loss.
- A legacy FILE fallback path.

## Important gaps and risks still open

### 1. Nearby STREAM offset semantics need an explicit integration test

The sender's resumed source is already positioned at the accepted byte offset by `openValidatedSourceAtOffset`. The live STREAM send path does not visibly call `Payload.setOffset` before `sendPayload`, while Google's current Payload reference documents `setOffset` for both FILE and STREAM payloads.

Do not blindly add `setOffset` without confirming whether the SDK also skips bytes from an InputStream that has already been positioned. A double-skip would corrupt the resumed transfer. Add an SDK-backed test or two-device experiment that proves the exact bytes emitted and the receiver's reported offset. Record whether the correct contract is (a) seek source + setOffset, (b) seek source only, or (c) full source + setOffset.

### 2. Outgoing sender state is not durable across process death

The sender stages the selected content URI into the app cache and holds active state in memory. After process death, the sender may need the user to reselect the same file and the app to stage/hash it again. The stable identity can then find the receiver's checkpoint, but the sender's staged copy and pending receipt state are not themselves a durable job journal. Do not claim unattended sender-side process-death continuation.

### 3. Source modification timestamp is currently absent on the STREAM path

The current preparation code constructs the item with a null modification timestamp and sends null in stream metadata. The source is protected by exact size and SHA-256 identity, which is strong content identity, but modification-time validation is not currently participating in this path. Keep the protocol field optional and document this accurately.

### 4. Device validation is still required

Automated JVM tests cannot prove Nearby radio behavior, stream offset behavior, callback timing under real disconnections, Android process death, vendor-specific power management, or actual throughput. Test at least one lower-memory Android phone and one newer phone, with sender-side and receiver-side interruption separately.

### 5. Do not overstate folder transfer

The audited STREAM resume path is a one-file transfer identity per stream. The broader bundle/FILE path has manifest and multi-item handling, but this does not prove original source-folder hierarchy is preserved or that a folder transfer resumes as one atomic job.

## Required next validation batch

1. Add focused tests around the outgoing stream offset contract, or record a clear SDK limitation if it cannot be simulated in JVM tests.
2. Add transport-level tests for stream exchange callback order, duplicate metadata/control, stale payload IDs, and endpoint replacement where the current abstractions permit.
3. Verify process-death recovery on the receiver and reselect/re-stage recovery on the sender.
4. Run two-device tests: disconnect each side before a checkpoint, immediately after a checkpoint, and near EOF; verify final SHA-256 and no duplicate publication.
5. Measure throughput separately for STREAM and FILE fallback using identical files, devices, and radio conditions. Report measured results only.

## Official API references checked

- Google Nearby `Payload`: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload
- Google Nearby `Payload.Stream`: https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/Payload.Stream
- Google Nearby exchange-data guide: https://developers.google.com/nearby/connections/android/exchange-data

The Google exchange-data guide states that FILE/STREAM `onPayloadReceived` is an early notification rather than proof of completed transfer; successful completion is reported through `PayloadTransferUpdate.Status.SUCCESS`. The Payload reference documents `setOffset` for FILE and STREAM. These facts are why offset behavior and completion receipts must be validated separately.
