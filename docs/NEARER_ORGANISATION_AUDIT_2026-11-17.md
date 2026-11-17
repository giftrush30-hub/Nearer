# NeaRer Organisation Audit — 17 November 2026

**Audit branch:** `chore/nearer-organisation-audit-2026-11-17`  
**Audited source:** `main` at `9cd557ce3cf1fb4de65a9985cf0ef1bdd42f0270`  
**Application version:** 0.5.5 (versionCode 14)  
**Scope:** Repository structure, source inventory, Gradle configuration, CI workflows, existing product/engineering documentation, current open pull requests, and responsibility concentration in the two largest coordinators.

This is a repository-structure and engineering-process audit. It is not a line-by-line security audit, a complete functional audit of every screen, or physical-device certification.

## Executive assessment

NeaRer has grown into a broad native Android product while retaining a single Gradle application module and a predominantly flat Kotlin package. Its most important organisation problem is not a lack of feature code: it is the concentration of responsibilities in a few coordinators, combined with parallel branches and documentation that is not consistently present on `main`.

The safe response is **incremental organisation around existing behaviour**. Do not rewrite the app, move hundreds of files at once, introduce cloud infrastructure, or create empty Gradle modules.

## Measured repository baseline

The recursive Git tree for the audited commit reports. The inventory was refreshed after PR #269 merged Studio Audio project-library work into main; the two largest coordinators were unchanged by that PR:

- 487 tree entries and 454 files.
- 221 production Kotlin files.
- 172 JVM test Kotlin files.
- 2 Android instrumentation test Kotlin files.
- 211 of 221 production Kotlin files directly in `com.nearer.app`; the remaining 10 are under `com.nearer.app.games.ludo`.
- One Gradle module: `:app`.
- Three workflow files under `.github/workflows`.
- `MainActivity.kt`: 5,655 lines (approximately 323 KB).
- `NearbyConnectionsTransport.kt`: 3,298 lines (approximately 182 KB).

Largest production files by current byte size include:

| File | Approx. size |
|---|---:|
| MainActivity.kt | 323 KB |
| NearbyConnectionsTransport.kt | 182 KB |
| FileCentre.kt | 66 KB |
| PremiumChatsScreen.kt | 59 KB |
| games/ludo/LudoGameScreen.kt | 58 KB |
| FastShareController.kt | 48 KB |
| StudioAudioWorkspaceScreen.kt | 44 KB |
| StudioVideoTimelineScreen.kt | 38 KB |
| LudoGamePanel.kt | 38 KB |
| BusinessCenterWorkspaceScreen.kt | 34 KB |

These measurements are structural signals, not a quality score. A large file is not automatically wrong; the concern is unrelated ownership and lifecycle coupling inside it.

## What is already working well

1. The project is a native Kotlin/Jetpack Compose Android app with a single application module and a clearly defined Android minimum (API 26 / Android 8.0).
2. Core product direction is offline-first; no Supabase, Firebase or Vercel dependency is required by the current Android project configuration.
3. The test tree is substantial: 171 JVM test files plus two instrumentation test files.
4. Existing policy, codec and persistence classes provide seams that can be tested independently.
5. Documentation already exists for Messaging, HyperTransfer, mesh relay, product experience, Business Center, Movies/Video Center and Studio Audio.
6. The README documents important limitations, including that mesh relay is disabled and real-device acceptance remains outstanding for several radio and lifecycle scenarios.
7. The main Android workflow runs JVM tests, manifest processing, diagnostic lint, debug APK assembly and artifact upload for pull requests.

## Organisation findings and priorities

### P0 — Coordinator responsibility concentration

`MainActivity.kt` currently combines service binding and transport callbacks, lifecycle and permissions, nearby discovery, Wi-Fi Direct compatibility, profile/local data, messaging state and persistence, community/game coordination, file sharing and large Compose UI entry points.

`NearbyConnectionsTransport.kt` combines the public listener contract, durable outgoing message queue, Nearby SDK callbacks, peer authentication, mesh routing and receipts, HyperTransfer control/streams, authenticated message dispatch, and file receive/finalization.

**Action:** use the current source ownership map. Extract one cohesive responsibility at a time behind existing contracts. Keep Android lifecycle and permission launchers in the Activity until their replacement owners are fully wired. Keep protocol/security state and callback ordering unchanged during structural work.

### P0 — Flat package ownership

210 production files sit directly in `com.nearer.app`. Only the Ludo implementation has a dedicated nested feature package.

**Action:** establish feature and core package boundaries inside `:app` first. Move only cohesive files with their imports and tests. Enforce dependency direction before creating Gradle modules.

### P1 — Architecture documentation is fragmented across branches

The earlier architecture documents exist on `chore/architecture-quality-foundation`, not in the audited `main` tree. That branch is not a safe drop-in: its baseline predates current main and it overlaps later code. A second open draft, PR #250, also includes architecture extraction work alongside HyperTransfer functionality.

**Action:** maintain one current organisation audit and ownership map against a named main SHA. Reconcile useful older material selectively; do not merge either architecture branch wholesale.

### P1 — Open pull request coordination

At the initial branch inventory, 11 pull requests were open. The latest refresh found 12 open pull requests, including this organisation PR and newly opened Studio Audio work. PR counts are time-sensitive; use the live pull request list for current status. The relevant coordination set includes:
- PR #234 — architecture and quality foundation (draft, old baseline, reported merge conflicts).
- PR #250 — architecture cleanup combined with durable HyperTransfer work (draft, older base).
- PR #269 — Studio Audio projects/timeline (merged into main at `9cd557ce3cf1fb4de65a9985cf0ef1bdd42f0270`; its exact-head CI run completed successfully).
- PR #270 — Android 8 compatibility guards (draft; latest recorded validation succeeded).
- PR #268 — durable HyperTransfer sender work (not draft).

Several other open branches also target older main snapshots. A PR being open, green, or mergeable does not by itself make it current or safe to merge.

**Action:** keep a PR integration register with exact base/head SHAs, overlap areas, CI state and device-test requirements. Rebase or reconcile only with the owning workstream; never force-push or discard parallel work.

### P1 — Concrete file overlap between open PRs

A changed-file comparison of the open architecture and transfer PRs found real overlap:

| PR pair | Shared files / concern | Organisation decision |
|---|---|---|
| #234 and #250 | `MainActivity.kt`, `NearbyConnectionsTransport.kt`, and the same extracted helper classes/tests (including AppInstallResultActivity, NearbyPerson, TransferByteFormatter, WifiDirectDeviceStatus, ChatTimeFormatter, IncomingFilePayloadPolicy, NeaRerAppBundleValidator and NearbyEndpointNameCodec) | Treat the architecture extraction batch as duplicated work. Select and reconcile one canonical implementation; do not merge both wholesale. |
| #250 and #268 | `NearbyConnectionsTransport.kt`, `OutgoingHyperTransferStore.kt`, `VerifiedTransferReceiptStore.kt`, their tests, and `HYPERTRANSFER_RESUME_INTEGRATION_PLAN.md` | Coordinate the durable transfer integration with its dedicated PR. Review the combined final diff and exact-head tests before either branch is integrated. |
| #234 and #270 | `StudioAudioWorkspaceScreen.kt` | Review the Android 8 compatibility changes against the current Studio work before merging; do not overwrite one branch with the other. |

This overlap is why the organisation programme must coordinate PRs by changed-file ownership and exact base/head commits, not merely by PR titles.

### P1 — CI workflow ownership and triggers need clarification

There are three workflow files:
- `.github/workflows/android-build.yml` — canonical full validation and debug APK workflow.
- `.github/workflows/messaging-jvm-tests.yml` — scoped Messaging JVM/manifest/lint workflow.
- `.github/workflows/android-apk.yml` — separate APK workflow with a narrow branch/manual trigger.

The canonical workflow's lint step is explicitly `continue-on-error: true`. Therefore a green workflow does not mean lint is clean. The messaging workflow intentionally does not assemble an APK.

**Action:** document each workflow's purpose, supported branches, artifacts and required status. Avoid creating competing APK workflows or declaring lint passed when it is only diagnostic.

### P1 — Documentation status vocabulary needs one standard

README and feature roadmaps contain both current status and milestone history. Product design documents are not proof that code is integrated. Source integration is not device verification.

**Action:** use the evidence labels below consistently: Planned, In development, Source integrated, Automated checks passed, Device verified, Release ready. Record the exact commit and test scope for every claim.

### P2 — Android permission and policy traceability

The earlier lint review identified permission/API findings, including Wi-Fi Direct permission call sites, Android-version guards, location permission declaration behaviour and broad package visibility. These need a permission-to-feature-to-call-site matrix and Play policy review. Do not remove permissions or add blanket suppressions without tracing all call sites and user flows.

### P2 — Quality gates beyond JVM tests and APK assembly

Automated JVM tests and APK assembly are valuable but do not establish UI navigation correctness, radio interoperability, process-death recovery, OEM background behaviour, startup performance or accessibility.

**Action:** add stable UI/instrumentation checks incrementally and keep a reproducible physical-device acceptance matrix separate from CI.

## Target organisation (direction, not current implementation)

Keep one Gradle module while the package boundaries are being proven:

- `app`: Android entry points, app composition, top-level navigation.
- `core.common`: small shared primitives with no feature ownership.
- `core.designsystem`: shared typography, colour, spacing, shape and components.
- `core.security`: identity, cryptographic and trust contracts.
- `core.storage`: durable local persistence contracts and common storage utilities.
- `core.nearby`: Nearby SDK adapter and transport interfaces.
- `core.transfer`: transfer lifecycle/protocol shared by product surfaces.
- `feature.messaging`, `feature.communities`, `feature.games`, `feature.files`, `feature.business`, `feature.studio`, `feature.bible`, `feature.movies`.

These are proposed package ownership areas, not a requirement to create all of them immediately. Features should depend on stable core APIs. Core must not depend on feature packages. Avoid feature-to-feature implementation dependencies; connect features through app composition or narrow interfaces.

## Safe execution sequence

1. Freeze the exact main baseline and CI evidence.
2. Maintain current ownership maps for MainActivity and NearbyConnectionsTransport.
3. Inventory the largest feature files and identify which responsibilities are UI, domain policy, storage or platform lifecycle.
4. Reconcile overlapping architecture PRs and preserve all parallel feature work.
5. Choose a pure, low-risk extraction with a complete call-site review and focused tests.
6. Move files into feature-oriented packages in small, buildable batches.
7. Add dependency rules and architecture tests before Gradle modularisation.
8. Clarify workflow triggers and lint's diagnostic status.
9. Make README and feature registers link to one current status source.
10. Only introduce Gradle modules when boundaries are stable and useful.

## Evidence labels

- **Planned** — not implemented.
- **In development** — implementation exists but is incomplete.
- **Source integrated** — merged into the named target branch.
- **Automated checks passed** — tests/build passed on the exact named commit.
- **Device verified** — tested on named physical device(s), Android version(s) and scenarios.
- **Release ready** — product, security, compatibility, licensing and release gates passed.

## Explicit non-goals

- No wholesale rewrite.
- No mass package move.
- No new backend or cloud dependency.
- No protocol, encryption, persistence-format or transport changes bundled with file organisation.
- No automatic merge of stale architecture branches.
- No claim of device testing without evidence.

## External engineering references

- Android Developers — [Guide to app architecture](https://developer.android.com/topic/architecture)
- Android Developers — [Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations)
- Android Developers — [Guide to app modularization](https://developer.android.com/topic/modularization)

The guiding principle is to improve separation of concerns and testability while preserving the offline-first app, existing data and working user journeys.


## Department ownership follow-up

A separate [Department and Package Ownership Plan](NEARER_DEPARTMENT_OWNERSHIP_PLAN_2026-11-17.md) now maps the current source into proposed feature/core ownership areas and defines dependency direction. It is deliberately a target map, not a bulk-move instruction.
