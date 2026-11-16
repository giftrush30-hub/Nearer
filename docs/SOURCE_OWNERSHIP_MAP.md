# NeaRer Source Ownership Map — First Refactoring Pass

**Baseline:** `445e58616765e5fdc5bcd0e0f89e708ee92958b8`  
**Branch:** `chore/architecture-quality-foundation`

This map is based on source declarations and section boundaries in the two largest coordinators. It is an extraction plan, not a claim that the target collaborators already exist.

## 1. MainActivity.kt

Approx. 5,097 lines; 125 function declarations found by source scan. It currently combines Android lifecycle, Nearby callbacks, Wi-Fi Direct fallback, persistence, chat, groups, game networking, file operations, app installation and Compose UI.

| Approx. lines | Current responsibility | Candidate owner |
|---|---|---|
| 126–267 | Activity state, callbacks and app-level mutable state | App state holder / feature state holders |
| 269–957 | Activity lifecycle, Nearby callbacks, message/file events | App lifecycle coordinator + Nearby event adapter |
| 978–1239 | Always-ready discovery, permissions and Nearby discovery | Nearby discovery coordinator |
| 1255–1536 | Wi-Fi Direct fallback, approval socket and peer lifecycle | Legacy direct-transfer adapter (keep chat disabled) |
| 1537–1676 | Restore local data, received files and transfer history | Repositories / local data sources |
| 1677–1997 | Chat edits/reactions/deletes, history, persistence and direct send | Messaging domain + message repository |
| 1998–2285 | Community persistence, roster sync, group sends and broadcast | Community feature coordinator/repository |
| 2286–2822 | Community packet handling, Ludo invitations/state/actions/game chat | Community and game feature coordinators |
| 2823–3127 | App-bundle install, external share intents, file open/save/folder preparation | App sharing + file provider services |
| 3128–3451 | Multi-file queues, transfer dispatch and queue persistence | Transfer queue coordinator |
| 3452–3669 | Legacy socket message reader / protocol handling | Isolated legacy file-transfer adapter; never restore plaintext chat |
| 3670–4875 | `NeaRerHome` Compose UI | Home/navigation feature UI and smaller screen composables |
| 4876–4956 | `CommunityHub` Compose UI | Community feature UI |
| 4957–5097 | Feature models, formatters and `AppInstallResultActivity` | Feature model packages + dedicated Android component |

### Highest-value safe extractions

1. Move pure formatting/model/policy code out first where it has no Activity dependency.
2. Move file queue persistence and queue state into a tested transfer coordinator.
3. Move community and game packet coordination behind interfaces while retaining existing transport callbacks.
4. Move Compose screen functions into feature UI files without changing state behaviour.
5. Keep Activity lifecycle, permission launchers and app composition in MainActivity until the new owners are wired and verified.

**Do not** perform a single giant MainActivity rewrite. Preserve callback ordering, persistence timing and the existing authenticated transport boundary.

## 2. NearbyConnectionsTransport.kt

Approx. 3,111 lines; 124 function declarations found by source scan. The class is a high-risk convergence point for radio lifecycle, authentication, messaging, mesh forwarding, HyperTransfer and file payloads.

| Approx. lines | Current responsibility | Candidate owner |
|---|---|---|
| 20–170 | Public listener contract, identity/display name, initial connection setup | Narrow transport API + peer identity model |
| 170–535 | Pending message outbox, retries, ACK history and deduplication | Durable message delivery engine |
| 536–804 | Nearby discovery/connection/payload callbacks | Nearby SDK adapter |
| 805–1050 | Peer hello/proof, identity binding and authenticated session completion | Peer authentication/session manager |
| 1051–1680 | Mesh route sequence, advertisement/withdrawal, hop ACK, receipts and forwarding | Mesh routing/relay engine (remain disabled until release gates) |
| 1681–1728 | Recipient-addressed encrypted mesh message send | Messaging-over-mesh adapter |
| 1738–2020 | HyperTransfer control, completion receipts, stream resume and payload admission | HyperTransfer session coordinator |
| 2021–2200 | Control/data frame dispatch and authenticated message processing | Protocol dispatcher + message receiver |
| 2201–2285 | File finalization, digest and safe filename | File receive pipeline |
| 2286–3111 | Advertising/start/stop, public send APIs, multi-file and file-payload handling | Nearby lifecycle facade + file payload sender/receiver |

### Highest-value safe extractions

1. Keep the current class as a compatibility facade while collaborators are introduced.
2. Extract pure codecs/policies first; many already exist and have JVM tests.
3. Extract the authenticated peer session manager next, preserving the exact handshake transcript and key derivation.
4. Extract message outbox/retry behind a durable repository contract.
5. Extract file payload handling only after current resume and completion receipt tests cover the lifecycle.
6. Extract mesh routing last; keep relay disabled and preserve replay-protection/receipt persistence ordering.

## 3. Guardrails for all extraction work

- No protocol or packet-format changes bundled with a structural refactor.
- No message ACK before durable recipient commit.
- No unauthenticated legacy socket message transport.
- No mesh relay enablement as a side effect of refactoring.
- No removal of durable transfer checkpoints or file integrity checks.
- Every moved class retains or gains focused tests.
- Run the existing JVM suite and debug APK build on the exact PR head.
- Record real-device tests separately; automated tests do not establish radio compatibility.

## 4. Next code batch

Before moving production code, choose the first low-risk seam from the ownership map and inspect its complete call graph. Preferred starting candidates are pure formatters and small transfer queue persistence policies. Avoid extracting the transport's cryptographic handshake or Activity lifecycle first.

## First extraction completed on the working branch

The initial baseline map included `AppInstallResultActivity` in the final lines of `MainActivity.kt`. That Android package-install result activity has now been moved into `AppInstallResultActivity.kt` on `chore/architecture-quality-foundation`. Its manifest registration and logic were retained; this is a small responsibility-boundary extraction, not a broader MainActivity refactor. The baseline line counts above intentionally describe the pre-refactor main commit.


## Second extraction: nearby presence model

The small `NearbyPerson` model and `seenAgo` presentation formatter have been moved from the end of `MainActivity.kt` into `NearbyPerson.kt`. The formatter accepts an optional `nowMillis` value (defaulting to the system clock) so its minute/hour/day boundaries and future-timestamp clamping can be tested deterministically. `NearbyPersonTest.kt` covers those cases. No discovery protocol, storage format, presence expiry policy, or UI wording was changed.
