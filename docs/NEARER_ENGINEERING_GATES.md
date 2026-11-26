# NeaRer Engineering and Organisation Gates

Use this register for architecture, integration and release decisions. Update it with evidence; never mark an item complete because code exists or a PR is open.

## Repository organisation

- [ ] Record exact main SHA and CI result before every structural batch.
- [ ] Keep the current source ownership map for large coordinators.
- [ ] Maintain one authoritative architecture/status document on main.
- [ ] Keep Activity classes focused on Android lifecycle and app composition.
- [ ] Keep Compose screens focused on rendering and interaction.
- [ ] Put business rules in independently testable policies/use cases.
- [ ] Put persistence behind repositories/data-source boundaries.
- [ ] Define feature-to-core dependency direction.
- [ ] Avoid feature-to-feature implementation dependencies.
- [ ] Add architecture/dependency tests before Gradle modularisation.
- [ ] Introduce Gradle modules only for stable, cohesive boundaries.
- [ ] Avoid empty modules and mass package moves.

## Branch and pull request coordination

- [ ] Every PR records exact base SHA and head SHA.
- [ ] Every PR has one primary responsibility; avoid mixing architecture cleanup with unrelated feature delivery where practical.
- [ ] Check overlapping files and commits before integrating parallel branches.
- [ ] Reconcile stale branches with their owners; never force-update or overwrite work.
- [ ] Require exact-head tests/build before integration.
- [ ] Verify main again after every merge.
- [ ] Keep a record of unresolved draft PRs and their reason for remaining draft.

## Messaging and Nearby

- [ ] Preserve authenticated peer handshake and key continuity.
- [ ] Preserve durable encrypted outbox and duplicate suppression.
- [ ] Verify ACK means durable recipient commit, not read receipt.
- [ ] Verify reconnect/process lifecycle separately.
- [ ] Test two-device discovery, approval, send, receive and reconnect.
- [ ] Keep unauthenticated legacy socket message frames disabled.
- [ ] Keep mesh relay disabled until security review and multi-hop device validation.

## HyperTransfer and OpenSend

- [ ] Preserve transfer identity and recipient binding.
- [ ] Verify stream checkpoints and resume offsets.
- [ ] Verify SHA-256 integrity before publishing received files.
- [ ] Test interruption, process death, low storage and retry.
- [ ] Test multi-file/folder transfer, path traversal and filename collisions.
- [ ] Benchmark real devices under comparable conditions.
- [ ] Do not claim unlimited practical size or guaranteed speed.

## Studio and media

- [ ] Preserve original recordings and non-destructive export.
- [ ] Test raw/default/USB microphone routing on physical devices.
- [ ] Test monitoring latency and feedback risk on supported routes.
- [ ] Distinguish pitch analysis from audible pitch correction.
- [ ] Listen to exported audio on multiple devices/headphones.
- [ ] Test video preview/export parity, memory pressure and large source files.

## Bible and content licensing

- [ ] Keep content packs separate from the base APK where appropriate.
- [ ] Verify pack integrity, completeness and transactional install.
- [ ] Record translation/lexicon copyright and distribution permissions.
- [ ] Do not invent original-language mappings where alignment data is absent.
- [ ] Test fully offline import, reading and search.

## UI, accessibility and performance

- [ ] Use shared design tokens for colour, typography, spacing and shape.
- [ ] Test compact phone layouts, large font sizes and dark/light themes.
- [ ] Provide loading, empty, error, offline and permission-denied states.
- [ ] Check touch target size, contrast and accessibility semantics.
- [ ] Add navigation regression tests for major journeys.
- [ ] Measure startup and critical-screen performance on representative devices.

## CI and release

- [ ] Document the purpose and trigger scope of all three current workflows.
- [ ] Keep one canonical workflow for full PR validation and APK assembly.
- [ ] Make lint blocking only after the current baseline is understood and repaired.
- [ ] Add UI/instrumentation tests for critical journeys where stable.
- [ ] Tie every APK artifact to the exact validated commit.
- [ ] Keep release signing separate from debug APK validation.
- [ ] Require PR review and passing checks on main when repository settings and owner policy permit.
- [ ] Release notes separate implemented, automated-tested, device-verified and limited capabilities.

## Evidence labels

1. **Planned** — not implemented.
2. **In development** — implementation exists but is incomplete.
3. **Source integrated** — merged into the named target branch.
4. **Automated checks passed** — tests/build passed on the exact named commit.
5. **Device verified** — tested on named physical device(s), Android version(s) and scenarios.
6. **Release ready** — product, security, compatibility, licensing and release gates passed.

A successful JVM suite and APK build do not establish radio interoperability, OEM background behaviour, process-death recovery, or physical-device compatibility.
