# NeaRer HyperTransfer — engineering plan

## Product promise

Make nearby Android file sharing as fast as the two phones and their wireless link can reliably sustain. Preserve original bytes and names. Never claim a fixed multiplier over Xender until repeated, controlled tests prove it.

## Current implementation in this branch

- The receive-save copy buffer is selected from available memory, low-RAM classification and Android thermal status.
- A capable, cool phone uses 256 KiB; constrained devices use 128 KiB; severe thermal pressure uses 32 KiB.
- Nearby Connections payload completion is timed and the observed average MB/s is reported.
- Existing P2P_CLUSTER discovery remains unchanged so current multi-device connections are not sacrificed.
- JVM tests cover the buffer policy, Unicode-safe filename handling, bundle-manifest validation/codec round-trips, and bounded stream-copy integrity/cancellation.
- A metadata-only bundle model and bounded binary wire codec validate IDs, destination paths, duplicate entries, item sizes, item kinds and protocol version before bulk data begins; malformed UTF-8 is rejected rather than silently replaced.
- Multi-select sends a manifest offer before file bytes; the receiver reviews and explicitly approves or declines the offer.
- Manifest fragments are transported as bounded Nearby BYTES payloads, reassembled per endpoint, and checked against the completed manifest transfer ID.
- Every bundle FILE metadata envelope carries its manifest transfer ID, item ID and relative path. The receiver requires an accepted per-endpoint session and exact item ID/path/name/size agreement before attaching the FILE payload.
- Receiver logic tolerates BYTES metadata and FILE payload callbacks arriving in either order, including a failure callback that arrives before its metadata. Rejected payload IDs are quarantined and their temporary Nearby files are closed/deleted.
- The sender computes a bounded streaming SHA-256 preflight for each bundle file and includes it in that file's metadata. The receiver independently hashes bytes while saving and rejects the file if the digest or declared size differs; the per-file bundle session records the verified digest.
- Bundle files are committed into a unique private received-bundle directory using canonicalized relative paths, safe parent-directory creation, temporary files and no-overwrite finalization. The current picker still produces leaf-name paths rather than importing original source-folder trees.
- `TransferStreamIO` copies exactly the declared number of bytes with a fixed-size buffer, supports cancellation, detects premature EOF and unexpected trailing bytes (including for zero-byte files), and returns a SHA-256 digest for receiver-side verification.
- Incoming Nearby FILE payloads now use the bounded exact-length stream copier when saved into NeaRer's private received-files area; the receiver syncs the temporary file, checks its declared size, and renames it into place only after the copy succeeds.
- The receiver now also binds the file metadata to the endpoint that supplied the FILE payload, checks currently available destination storage before copying, and deletes Nearby's temporary received-file URI on both success and failure paths.
- Before presenting a bundle for approval, the receiver totals the declared file sizes with overflow protection and checks that the destination filesystem currently has enough free space for the entire bundle. Each file is checked again immediately before saving because available space can change during a transfer. This is a preflight safeguard, not a reservation.
- Incoming metadata, payload handles, completion state and timing are now keyed by both sender endpoint and payload ID, preventing transfers from different connected phones from sharing the same receive-state slot.
- A UI callback failure after the final rename is reported as a screen-refresh problem; it no longer misleadingly labels an already-saved file as a failed save.
- Nearby transfer callbacks for chat and metadata BYTES payloads are excluded from bulk-file progress accounting, and cancelling an outgoing file clears its endpoint-scoped timing state.
- Completed-transfer MiB/s calculation is extracted into a pure Kotlin `TransferRateMetrics` helper and covered by tests for zero-byte files, zero-duration callbacks, negative measurements and locale-stable labels. It uses the total payload bytes and elapsed time between the first bulk progress callback and SUCCESS; it is a completed-payload average, not a live speed guarantee.
- Incoming and outgoing throughput timing begins with the bulk FILE payload's first actual IN_PROGRESS callback, not when metadata is sent or when `sendPayload` merely accepts the request, so setup/queue time is not misreported as radio-transfer time.
- Unsupported incoming payload types are closed immediately so unused file/stream descriptors are not left open.
- Disconnect cleanup removes only that endpoint's pending transfer metadata, timing and payload handles; app shutdown drains already-queued receive-save tasks instead of discarding them with `shutdownNow()`.
- SHA-256 is calculated on both sides for bundle files and compared before the receiver finalizes the destination file. The sender's preflight adds one sequential source read, so its performance cost must be measured; ordinary single-file transfers do not incur this extra preflight.
- Manifest path validation avoids a quadratic scan over all item pairs, keeping validation practical for large bundles near the 10,000-item protocol limit.
- Bundle manifests are fragmented into 24 KiB Nearby BYTES frames carrying transfer ID/index/count/total length. The receiver reassembles them out of order, accepts identical duplicates, rejects conflicting duplicates or mixed transfers, and bounds memory to the existing 1 MiB manifest limit. This is wired into the Nearby transport and receiver approval UI.
- The bundle-session state machine enforces explicit receiver acceptance, item ID/payload ID association, monotonic bounded progress, completion digest recording and skipped/failed item accounting. It is now connected to the Nearby metadata and FILE callback path.
- The bundle is currently one reviewed logical job containing sequential individual Nearby FILE payloads. It is not yet a single continuous stream or archive, and the picker does not yet preserve original folder hierarchy.
- The stream copier now handles a zero-progress bulk read without spinning forever, and its SHA-256 hex formatter treats digest bytes as unsigned.
- Google documents that `onPayloadReceived` for FILE/STREAM is an early notification, not proof that all bytes arrived; completion must be confirmed by the transfer-update SUCCESS event. STREAM input must be drained promptly on a background worker, and the owning `Payload` must be closed when finished or cancelled.
- Filename sanitization preserves Unicode names while stripping path components and reserved/control characters.
- The Android compile/test workflow does not assemble an APK.

These changes tune the local file-save stage and expose real measurements. They do not, by themselves, increase the Wi-Fi radio's throughput.

## Transport strategy to evaluate

Keep discovery and control traffic separate from the bulk-data decision.

1. Preserve the current Nearby Connections cluster session for nearby presence, chat and multi-peer features.
2. Prototype a dedicated one-to-one bulk-transfer session using Nearby Connections P2P_POINT_TO_POINT. Google documents this strategy as the highest-throughput Nearby strategy for one-to-one large-file transfer; cluster is more flexible but has lower bandwidth.
3. Benchmark a direct local TCP/Wi-Fi Direct data path against Nearby's file payload path on the same devices.
4. Keep the current proven route as fallback. Only select a new route after both phones negotiate compatibility and a short real transfer probe.
5. Treat QUIC, UDP, parallel sockets and multiple simultaneous file streams as experiments. More streams are not automatically faster and can overload weak phones or congest the link.

Do not assume an app can force 5 GHz, 160 MHz channels, MLO, 2x2 MIMO or a particular Wi-Fi generation. Measure the connection that Android and the hardware actually provide.

## Bundle transfer protocol

A multi-file selection should become one logical transfer job, not one huge in-memory archive.

- Send a compact manifest first: stable item ID, safe relative path, display name, byte size, media type and optional metadata.
- Let the receiver accept, reject or selectively skip items before bulk data begins.
- Stream file bytes progressively; never read the whole collection into RAM.
- Preserve source bytes and folder structure. Do not recompress media or APKs.
- Compress only formats for which lossless compression is measured to reduce total elapsed time; skip already-compressed media and archives.
- Use bounded queues and receiver backpressure so a fast sender cannot overwhelm a slower phone's storage.
- Use temporary destination files and atomic finalisation only after size and integrity checks pass.
- Design resumable transfers around acknowledged, durable offsets or independently verifiable chunks. Persist resume state and make retries idempotent.
- Never silently overwrite an existing destination. Offer keep both, replace or skip.
- Keep a clear per-file result list for completed, skipped, failed and resumed items.

LocalSend is a useful protocol reference for metadata-first acceptance, parallel file uploads and SHA-256 verification. Its published v2 protocol does not define resumable byte offsets, so NeaRer should not copy that limitation.

## Adaptive device profiles

Negotiate from both endpoints, not the sender alone.

- LITE: low available memory, low-end CPU/storage, weak link or thermal pressure. Bounded buffers, conservative concurrency and backpressure.
- FAST: normal devices and stable link. Balanced buffering and measured concurrency.
- TURBO: only when both phones, storage and link demonstrate headroom. Increase work gradually and back off immediately when throughput drops, memory tightens or thermal status rises.

The profile is a policy choice, not a marketing speed label. Re-evaluate during the transfer.

## Competitor baseline note

Xender's current support page advertises transfer speeds of up to 40 Mb/s (megabits per second) and describes Wi-Fi Direct/hotspot-based local transfer. This is a vendor-stated upper claim, not an independently verified result or a fair comparison for every phone. The benchmark protocol below must measure NeaRer and Xender on the same two devices and conditions; do not convert this advertised figure into a promise or target without measuring real throughput.

## Benchmark protocol

Compare NeaRer with Xender, LocalSend and Android Quick Share where available.

For every run, record device models, Android versions, source and destination storage, wireless route, distance, signal/environment notes, battery state and thermal status. Keep the same phones and conditions for each app; repeat each case at least five times and report median plus spread.

Test cases:
- One 1 GB video.
- 500 small photos.
- A 5 GB mixed folder with nested directories.
- Large APK and document files.
- Big phone to low-end phone, then reverse direction.
- Weak/interfered wireless conditions.
- Mid-transfer app backgrounding, connection loss and recovery.

Measure:
- Time to discover and connect.
- Time to first byte.
- Total elapsed time and sustained MB/s (not just peak speed).
- CPU, memory, thermal status, battery impact and receiver write speed.
- Integrity failures, retries, resumed bytes and final folder/name preservation.

A result is publishable only when the test conditions and repeated measurements are recorded. Never compare different source files, storage devices, wireless bands or phone models and call the result a fair speed test.

## Phone-to-phone migration boundary

Migration can transfer user-accessible content selected by the owner: photos, videos, audio, documents, downloads, supported folders, contacts with permission and supported APK installation packages.

It cannot promise a complete Android clone. Ordinary apps cannot copy arbitrary private app data, protected system settings or another app's private storage without the required platform/app support. Explain skipped and unsupported items before the user starts.

## External technical references

- Google Nearby Connections overview: https://developers.google.com/nearby/connections/overview
- Google Nearby Android exchange-data guide: https://developers.google.com/nearby/connections/android/exchange-data
- Google Nearby ConnectionsClient reference (current BYTES payload size limit): https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/ConnectionsClient
- Google Nearby Connections strategies: https://developers.google.com/nearby/connections/strategies
- Android wireless connectivity and Wi-Fi P2P: https://developer.android.com/develop/connectivity/wifi
- LocalSend protocol v2: https://github.com/localsend/protocol
- Android thermal mitigation: https://source.android.com/docs/core/power/thermal-mitigation

## Adaptive profile policy implementation status

- A pure Kotlin `TransferProfilePolicy` now selects LITE, FAST or TURBO from both phones' reported available memory, low-RAM classification, measured sustained receiver write rate, thermal status and a measured/stable link rate.
- The weaker endpoint controls the decision. A missing link measurement, unstable link, low-RAM endpoint or severe thermal pressure selects LITE; moderate constraints select FAST.
- Thresholds are conservative initial heuristics, not universal hardware facts or promised throughput. They must be tuned against repeatable device benchmarks.
- Unit tests cover strongest/weakest endpoint selection, low-RAM fallback, missing/unstable link, thermal pressure and invalid negative measurements.
- **Integration boundary:** this policy is currently a tested decision component, not yet exchanged over the Nearby connection or wired to change live transfer concurrency/buffer settings. Do not label adaptive profiles as active in the user interface until capability exchange and runtime application are implemented and tested on two devices.

## Nearby strategy constraint

Google's current Nearby strategy documentation describes P2P_POINT_TO_POINT as the highest-throughput topology for one-to-one transfers, but it allows only one connection at a time. NeaRer's current live service advertises and discovers with P2P_CLUSTER so its existing multi-peer connection model remains intact. Nearby's strategy is chosen when advertising and discovering; the same strategy and service ID must be used by the peers for that discovery/connection flow. Therefore a point-to-point bulk path cannot safely be activated by changing only the sender or by switching strategy after a cluster connection is already established. A separate, negotiated connection lifecycle is required, and it must be validated on two physical Android devices before it replaces the current file path. The current cluster transfer path remains the compatibility fallback.


- A receive-side hardening pass now rejects payload streams that contain bytes beyond the manifest-declared file size. This closes a gap where the copy loop previously stopped after the declared length without checking for trailing data. Empty-file tests now use a genuinely empty source, and separate tests verify that extra bytes are rejected for both non-empty and zero-byte files.
