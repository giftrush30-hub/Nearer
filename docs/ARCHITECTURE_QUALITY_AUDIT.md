# NeaRer Architecture and Quality Audit

**Audit baseline:** `main` at `445e58616765e5fdc5bcd0e0f89e708ee92958b8` (16 November 2026)  
**Working branch:** `chore/architecture-quality-foundation`  
**Scope:** Repository tree, Gradle configuration, Android manifest, selected large source files, existing tests, workflows and project documentation. This is a source-structure audit, not a full line-by-line security review or physical-device certification.

## Executive finding

NeaRer has meaningful engineering foundations and a substantial automated JVM test suite. Its largest maintainability risk is concentration: a very broad product is still built as one Gradle application module, and several critical files have grown to thousands of lines. The immediate recommendation is **incremental modularisation and extraction behind stable contracts**, not a disruptive rewrite.

## Measured repository baseline

The recursive Git tree at the audit baseline contained:

- 424 tree entries and 392 files.
- 192 Kotlin production source files under `app/src/main/java`.
- All 192 production Kotlin files are under the `com.nearer.app` package root, except the Ludo package subtree. This is a strong sign that package ownership has not kept pace with product growth.
- One Gradle module: `:app`.
- Two GitHub Actions workflows.
- 148 JVM test source files at this baseline (counted from the recursive Git tree).

Largest inspected source files:

| File | Approximate lines |
|---|---:|
| `MainActivity.kt` | 5,097 |
| `NearbyConnectionsTransport.kt` | 3,111 |
| `FileCentre.kt` | 1,573 |
| `games/ludo/LudoGameScreen.kt` | 1,393 |
| `PremiumChatsScreen.kt` | 1,248 |
| `FastShareController.kt` | 936 |
| `LudoGamePanel.kt` | 904 |
| `StudioAudioWorkspaceScreen.kt` | 821 |
| `NeaRerOnboarding.kt` | 636 |
| `StudioVideoTimelineScreen.kt` | 594 |

Line counts are based on the current file contents retrieved from GitHub and are approximate indicators, not quality scores.

## What is already good

1. Native Kotlin + Jetpack Compose Android implementation.
2. Clear offline-first product direction and no mandatory cloud backend in the MVP architecture.
3. Many narrowly scoped policy/codec classes and corresponding JVM tests already exist.
4. Messaging and transfer work documents important security and recovery boundaries rather than claiming every capability is production-ready.
5. Android manifest uses a non-exported FileProvider and disables automatic app backup.
6. The build workflow runs JVM unit tests and assembles an installable debug APK.
7. Existing documentation covers messaging, HyperTransfer, mesh relay, product experience and integration status.

## Findings requiring action

### P0 — MainActivity is too large

`MainActivity.kt` is approximately 5,097 lines. The Activity should own Android lifecycle and host app-level UI/navigation; it should not become the home for unrelated feature workflows, data access, storage logic, transfer operations and product-specific state.

**Action:** map its responsibilities and extract one cohesive vertical slice at a time. Keep the existing launch contract and navigation stable. Do not split by arbitrary line ranges.

### P0 — Nearby transport is too large

`NearbyConnectionsTransport.kt` is approximately 3,111 lines and combines discovery/connection lifecycle with messaging, cryptographic session handling, file payloads, retries and mesh-related behavior.

**Action:** define explicit internal interfaces and extract independently testable collaborators for session/security, message delivery/outbox, file payload handling and discovery/connection lifecycle. Preserve wire formats and compatibility until tests prove a migration safe.

### P1 — One Gradle module for a large ecosystem

The current settings include only `:app`. This keeps build configuration simple, but gives no Gradle-enforced boundaries between Messaging, HyperTransfer, Studio, Bible, Games and shared platform capabilities.

**Action:** first establish package ownership and dependency rules inside the existing module. Then move stable slices into feature and core Gradle modules. Do not create empty modules just to make the project look modular.

### P1 — Flat package structure

Most production Kotlin sources sit directly in `com.nearer.app`, with Ludo as a notable nested package.

**Action:** organise by feature and responsibility (for example `feature.messaging`, `feature.transfer`, `feature.studio`, `feature.bible`, `feature.games.ludo`, `core.security`, `core.storage`, `core.designsystem`). Package moves must preserve public entry points and be verified by tests.

### P1 — CI workflow duplication and scope drift

There are two Android workflows. `android-build.yml` is the broader validation workflow; `android-apk.yml` targets a specific design branch and duplicates SDK/Gradle setup and APK upload steps.

**Action:** review triggers and artifact naming; converge on one canonical validation/build workflow unless the second has a clearly documented purpose. Do not delete or disable a workflow until its consumers and branch requirements are checked.

### P1 — Documentation source of truth

The README contains historical milestone updates and a group-delivery status statement that conflicts with a later section describing a group fan-out reliability gap.

**Action:** designate one current status register as authoritative; move historical milestones into dated history and link feature-specific design documents. Each capability must distinguish planned, implemented, merged, CI-validated, device-verified and release-ready.

### P1 — Main branch protection is not enabled

The GitHub branch metadata reported `main` as unprotected, with required status checks disabled.

**Action:** enable branch protection/rules requiring pull requests and successful validation before merge when repository permissions and owner policy permit. This audit does not change repository settings.

### P2 — Android permission review

The manifest includes nearby-radio, audio, media, contacts, package visibility/install, network and foreground-service permissions.

**Action:** map each permission to a user-visible feature and runtime request; remove unused declarations; document why sensitive permissions are needed; verify Android-version behaviour and Play policy. Do not remove a permission until all call sites and feature flows are audited.

### P2 — Quality and performance gates

Current CI validates JVM tests and debug APK assembly, but this is not equivalent to UI regression testing, instrumentation coverage, startup/jank benchmarking, security review or real-device compatibility.

**Action:** add the gates incrementally: lint/static analysis, navigation/UI tests, targeted instrumentation tests, Macrobenchmark for startup and critical journeys, and a repeatable two-device field-test matrix.

## Target architecture (direction, not a claim of current implementation)

- `:app`: application composition, Android entry points and top-level navigation only.
- `:core:common`: shared primitives with no feature-specific dependencies.
- `:core:designsystem`: NeaRer typography, colour, spacing, components and motion.
- `:core:security`: identity, cryptography and trust contracts.
- `:core:storage`: database, preferences and durable local persistence contracts.
- `:core:nearby`: platform transport abstractions and Nearby Connections implementation.
- `:core:transfer`: shared transfer protocol and stream lifecycle.
- `:feature:messaging`, `:feature:transfer`, `:feature:bible`, `:feature:studio`, `:feature:games`, `:feature:communities`, `:feature:files`, `:feature:business`: independently owned user-facing capabilities.

Dependency direction: features depend on stable core APIs; core modules must not depend on feature modules. Avoid feature-to-feature dependencies; use app composition or narrow contracts where cross-feature integration is required. Keep the MVP offline-first and do not introduce a cloud backend as a prerequisite.

## Safe execution sequence

1. Freeze a reproducible baseline: record current main SHA, workflow result and APK artifact identity.
2. Produce a source ownership map for the largest files and identify lifecycle, UI, storage and protocol responsibilities.
3. Add/strengthen tests around the first extraction seam.
4. Extract one cohesive collaborator at a time without changing protocol behaviour.
5. Move source into feature-oriented packages while the project remains one module.
6. Introduce Gradle modules only after package boundaries and dependency direction are proven.
7. Consolidate CI and add architecture/static-analysis checks.
8. Refresh README/status documentation and keep a feature verification register current.
9. Validate every merge on the exact commit; reserve real-device claims for actual device testing.

## Definition of done for this programme

- No new unrelated feature logic is added to `MainActivity` or the large transport coordinator.
- Core protocol/security behaviour has regression tests before refactoring.
- Feature ownership and module dependency direction are documented and enforced.
- One canonical CI workflow validates tests and assembles the APK on pull requests to main.
- Every release claim identifies the exact source commit and distinguishes automated from physical-device validation.
- No existing feature, offline behaviour or user data is intentionally removed during structural cleanup.

## External engineering references

These are the primary standards used for this audit:

- Android Developers — [Guide to app architecture](https://developer.android.com/topic/architecture)
- Android Developers — [Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations)
- Android Developers — [Guide to app modularization](https://developer.android.com/topic/modularization)
- Android Developers — [Domain layer guidance](https://developer.android.com/topic/architecture/domain-layer)

The Android guidance recommends clear separation of concerns, UI/data layers, repositories, unidirectional data flow, lifecycle-aware state, testing and modularisation where it improves maintainability. NeaRer should apply these principles in a way that protects its offline-first transport and device constraints.


## First refactoring batch completed on the working branch

The architecture-quality branch has now moved eight small responsibilities out of `MainActivity.kt`: the app-install result Activity, the local nearby-person model/last-seen formatter, the shared transfer-byte formatter, Wi-Fi Direct peer-status labels, and the NeaRer app-bundle structure validator. Seven of these extracted helpers have focused JVM tests. The validator tests cover valid and malformed ZIP structures but do not establish APK signature trust or installation safety. These extractions are deliberately small; the larger lifecycle, messaging, community, game and transfer coordinators remain mapped for later work rather than being split without adequate seams.

## Integration and validation note — 16 November 2026

The architecture pull request is intentionally still a draft. Its latest recorded validation attempt (run 43825252477) compiled the PR merge ref and failed in the Studio custom-vocal-preset test because that merge snapshot exposed MAX_NAME_LENGTH as private. The current main source has since changed the constant to module-visible const val in StudioCustomVocalPresets.kt (main head at the time of this note: 0892c0e6ee30905e52ae87c8acec2cc3fec2ea63). This is a base-branch timing issue to be revalidated against a newly generated PR merge ref; it is not evidence that the architecture extraction tests passed or failed.

The working branch and main have diverged. Do not force-update or merge this branch blindly: review overlapping Activity/transport changes and feature commits first. A green validation result must be recorded against the exact current PR head and merge base before the draft is considered ready. No physical-device verification is implied by CI.
