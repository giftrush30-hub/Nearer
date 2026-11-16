# NeaRer Engineering Quality Gates

This is the working checklist for architecture, integration and release quality. Update it as evidence becomes available; do not mark an item complete based on source integration alone.

## Repository and architecture

- [ ] Record exact baseline commit and CI result before each structural change.
- [ ] Maintain a source ownership map for large Kotlin files.
- [ ] Keep Activity classes focused on lifecycle and app composition.
- [ ] Keep Compose screens focused on rendering and user interaction.
- [ ] Put business rules in testable policies/use cases/repositories.
- [ ] Define feature-to-core dependency direction.
- [ ] Avoid feature-to-feature implementation dependencies.
- [ ] Add Gradle modules only for stable, cohesive boundaries.
- [ ] Add an architecture test or dependency rule preventing forbidden imports.

## Messaging and Nearby

- [ ] Preserve authenticated handshake and key continuity.
- [ ] Preserve durable outbox semantics and duplicate suppression.
- [ ] Verify ACK means durable recipient commit, not read receipt.
- [ ] Verify reconnect/process lifecycle behaviour separately.
- [ ] Test two-device discovery, approval, send, receive and reconnect.
- [ ] Keep unauthenticated legacy socket message frames disabled.
- [ ] Keep mesh relay disabled until security review and multi-hop device validation.

## HyperTransfer and OpenSend

- [ ] Preserve transfer identity and recipient binding.
- [ ] Verify stream checkpoints and resume offsets.
- [ ] Verify SHA-256 integrity before publishing received files.
- [ ] Test interruption, process death, low storage and retry.
- [ ] Test multi-file and folder transfer, including path traversal and collisions.
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

- [ ] Keep Bible content packs separate from the base APK.
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
- [ ] Measure startup and critical screen performance on representative devices.

## CI and release

- [ ] One canonical workflow runs JVM tests and debug APK assembly on PRs to main.
- [ ] Static analysis/lint is part of CI.
- [ ] UI/instrumentation tests are added for critical journeys where stable.
- [ ] APK artifact is tied to the exact validated commit.
- [ ] Release build is separately signed and verified.
- [ ] Main branch requires PR review and passing checks when owner settings allow.
- [ ] Release notes list implemented, tested and still-limited capabilities separately.

## Evidence labels

Use these exact status labels in project documentation:

1. **Planned** — not implemented.
2. **In development** — work exists but is incomplete.
3. **Source integrated** — merged into the target branch.
4. **Automated checks passed** — tests/build passed on the exact commit.
5. **Device verified** — tested on named physical device(s), Android version(s), and scenario(s).
6. **Release ready** — required product, security, compatibility, licensing and release gates passed.
