# HyperTransfer integration audit and completion plan

Status: architecture audit completed against `main` at `7d8d8b2` and the HyperTransfer feature branch. This document records integration constraints before changing production routing. It does not enable Wi-Fi Direct TCP or claim a measured speed improvement.

## Existing NeaRer ownership that must be preserved

### Nearby messaging and file transfer

- `NearbyConnectionsTransport` owns peer authentication, encrypted Nearby message sessions, mesh routing, file payloads, HyperTransfer resumable streams, and file-transfer callbacks.
- The existing transfer path includes URI preparation/staging, file metadata, SHA-256, resumable offsets/checkpoints, receipt/timeout handling, cancellation/fallback, and completion callbacks. The fast path is attempted only for peers advertising stream capability; otherwise the standard Nearby FILE payload path is used.
- Preserve the existing durable incoming-message commit/acknowledgement boundary and UI reattachment behavior when integrating any new transport owner.

### Existing Wi-Fi Direct code in MainActivity

- `MainActivity` already initializes a `WifiP2pManager`, registers P2P broadcasts, discovers peers, connects, reads group-owner information, and hosts/joins a socket on port 8988.
- Its current socket exchange is a legacy `NEARER_HELLO|...` / six-digit pairing / ACCEPT-REJECT protocol. It is not the authenticated HyperTransfer protocol and must not carry bulk-file bytes.
- A second independently initialized Wi-Fi Direct manager must not compete with the Activity-owned manager. Migrate ownership in one controlled change, not by adding a parallel coordinator.

## New HyperTransfer pieces on this branch

- `WifiDirectConnectionCoordinator`: permission-gated P2P discovery/group/link lifecycle, interface-bound socket creation, and cleanup.
- `WifiDirectTcpTransferEngine`: bounded-memory TCP streaming, transfer/session framing, SHA-256 verification, temporary-file staging, cancellation and safe publication.
- `HyperTransferSessionTranscript`, `HyperTransferSessionAuthenticator`, and `HyperTransferSecureChannel`: canonical session binding, signatures checked against NeaRer device identity pins, explicit first-contact approval, transfer approval gate, ECDH-derived directional AES-GCM records, and replay/order checks.
- `HyperTransferTransportPolicy` and `HyperTransferPerformanceGate`: pair/direction/capability/evidence-based selection and strict 280 MB/s + 7x Xender acceptance criteria.

These pieces are foundations, not an active production route. The legacy plaintext TCP methods remain isolated from the secure methods. The authenticated channel and coordinator are not yet connected to the actual transfer-approval state or the production transfer lifecycle.

## Required integration design

1. **One Wi-Fi Direct owner.** Move P2P manager initialization, broadcast registration, discovery, group negotiation, link state, and teardown behind one owner. The Activity becomes a UI client of that owner. Do not leave two independent managers responsible for the same P2P group.
2. **Keep Nearby as the default/fallback.** Do not change the existing Nearby Messaging route. HyperTransfer's one-to-one route is selected only for a transfer after both devices exchange compatible capabilities and the policy finds a qualified route for the exact pair and direction.
3. **Reuse NeaRer identity and approval.** Build the HyperTransfer transcript from the same stable peer IDs, signing identity, recipient encryption key, nonces, and ephemeral keys used by the established Nearby identity system. Verify both signatures before deriving the AEAD channel. First-contact pinning must be tied to the existing in-person trust/approval UX; a Boolean supplied by an untrusted network message is not approval.
4. **Gate file bytes on existing transfer approval.** The receiver must approve the exact transfer metadata (peer, filename, size, transfer ID) before the secure channel is used for file content. Neither group formation nor peer authentication alone authorizes a file.
5. **Preserve durable transfer semantics.** Keep outgoing/incoming transfer IDs, file metadata, resume checkpoints, retry/receipt state, progress callbacks, cancellation, destination collision behavior, SHA-256, and completion notification. For the first integration, disable resume on the new TCP route unless its checkpoint protocol is itself authenticated and tested; safely fall back to the existing Nearby resumable path rather than silently restarting or losing state.
6. **Service-owned lifecycle.** The production owner must own active sockets, coordinator, session keys, cancellation tokens, timeout handling, and transfer jobs. Activity detach/recreation must not close them. Shutdown, peer disconnect, permission revocation, Wi-Fi Direct unavailability, or process restart must close sockets and key material and leave a recoverable transfer state.
7. **No plaintext downgrade.** The secure handshake must fail closed. Do not fall back from an authentication, signature, pin, AEAD, or digest failure to the legacy plaintext TCP path. A capability mismatch or unavailable Wi-Fi Direct link may fall back to the existing authenticated Nearby transport.
8. **Bounded and validated framing.** Validate protocol version, transfer ID, declared size, record size, file length, and every resume offset before allocation or writing. Use socket read/connect timeouts and cancellation-by-close. Publish a received file only after authenticated digest verification; never overwrite an existing destination.
9. **Capability negotiation.** Advertise only routes the local device can currently use. Wi-Fi Direct support, runtime permission, radio state, API constraints, and link readiness are dynamic; a hardware feature flag alone is insufficient. Wi-Fi Aware is a separate later implementation and must not be advertised until its network callback/socket lifecycle exists on both devices.
10. **Benchmark instrumentation.** Record preparation, discovery/group setup, handshake, first-byte, active payload, verification, and finalization separately. Record device pair, direction, exact file SHA-256/size, transport, Android/API/radio capabilities, buffer, concurrency, failures, thermal state, and battery impact.

## Seven completion tracks and evidence gates

| Track | Engineering completion condition | Evidence required |
|---|---|---|
| 1. Authenticated handshake | Both peer signatures verify over identical canonical transcript; pinned identity continuity and first-contact approval enforced; no file data before approval | Unit/instrumentation tests for changed identity, altered transcript, wrong transfer ID, first-seen/changed-key/storage-error cases |
| 2. Service integration | One owner controls P2P lifecycle and secure transfer jobs; Activity only observes/actions through its UI boundary | Lifecycle tests for Activity detach/rebind, peer loss, owner stop/restart, permission loss |
| 3. Preserve Messaging and transfer behavior | Nearby Messaging remains unchanged; existing file approval, progress, cancel, recovery, receipts and fallback continue to work | Regression suite for text, groups/mesh, small/large files, cancellation, resume and process restart |
| 4. Android validation | Exact code commit passes JVM tests and builds APK; lint findings reviewed separately | CI run tied to exact code SHA; no claim based on stale workflow |
| 5. Physical-device testing | Supported phone pairs connect and transfer in both directions; test interruption, low storage, app backgrounding, thermal and permission changes | Device logs and hashes for each pair/direction; no emulator-only substitution |
| 6. Xender comparison | Same phone pair, same file hash/size, same direction, same placement/conditions; at least five successful runs each | Raw per-run results, failures retained, medians and range |
| 7. Bottleneck and target verification | Tune only measured bottlenecks; absolute and competitive gates both pass | At least 280,000,000 received bytes/s median AND at least 7.0x Xender median, independently repeated |

## Current validation snapshot

- Latest Android validation workflow: [run 44316796827](https://github.com/giftrush30-hub/Nearer/actions/runs/44316796827), code commit `7edd0593ca388847649a992b54a1ea57865be89f`.
- JVM unit tests and Android manifest validation: **passed**.
- Android lint diagnostic and report upload: **completed**; the workflow explicitly identifies the repository's existing lint baseline as not yet clean. This is not a clean-lint claim.
- Debug APK assembly and artifact upload: **passed**. Artifact `NeaRer-debug-apk`, 17,744,706 bytes; artifact expires 2 December 2026.
- The feature branch also contains a later documentation-only commit; the validated Kotlin/Android code is unchanged from the tested code commit above.
- PR #287 remains open and unmerged; `main` was not modified by this work.
- No physical-device throughput or Xender comparison is available. The 280 MB/s and 7x targets remain unverified.
- Wi-Fi Aware TCP is not implemented. The existing Nearby transport remains the active transfer route.

## Safe next implementation order

1. Trace the actual current Activity/transport ownership and define a single-owner API before migrating Wi-Fi Direct.
2. Move P2P discovery/connect UI behind that owner and remove duplicate Activity management only after equivalent state behavior is covered.
3. Add a transfer-session object that couples peer identity, signed transcript, actual approval state, secure channel, socket, cancellation, and durable transfer ID.
4. Integrate one-file TCP transfer behind an explicit experimental capability flag, with Nearby fallback only for capability/link unavailability (never security failure).
5. Add regression coverage before enabling folder/multi-file and resume support on TCP.
6. Add benchmark capture/export and run the same workload on physical devices and Xender.
7. Tune buffer size, concurrency, hashing, and file preparation based on measured profiles; do not optimize based on peak PHY numbers.

**Do not merge or advertise HyperTransfer as faster than Xender until all applicable evidence gates pass.**

## Follow-up hardening on 18 November 2026

- Corrected benchmark speedup calculation to compare median measured application throughput (MiB/s), rather than dividing median elapsed durations. The distinction matters when sample files or byte counts differ; the new regression test deliberately uses different file sizes to prevent the old calculation from returning a misleading 20x result instead of the measured 2x.
- Added a secure sender regression test for a peer that receives the complete encrypted empty-file manifest and digest but never sends the encrypted acknowledgement. The sender must terminate on its configured socket read timeout rather than wait indefinitely.
- These source/test changes are committed after the previously successful Android CI run. **They have not yet been validated by a new CI run.** The prior passing JVM/manifest/APK results do not validate these new commits.
- Production service integration remains deliberately disabled: MainActivity currently owns an independent Wi-Fi Direct manager, discovery receiver, group state, and legacy approval socket. The new coordinator must not be started alongside it. A safe migration must move the complete lifecycle and UI contract together, not simply create a second P2P manager in NearbyTransportService.
- User will perform physical phone testing. Engineering-side work still needs to complete integration, add benchmark capture/export, run CI on the current head, and provide the physical-test protocol/results template.
