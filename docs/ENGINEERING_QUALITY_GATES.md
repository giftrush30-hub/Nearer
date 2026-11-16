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

## Nearby background-service integration gate

The current `main` branch has begun introducing `NearbyIncomingMessageInbox` and `NearbyTransportService`. These are promising boundaries, but the presence of the classes alone is not proof that background messaging is integrated or usable. At the audit snapshot, the manifest and `MainActivity` did not yet declare/bind/start `NearbyTransportService`; treat the work as an integration-in-progress until the wiring lands.

Before calling this complete, verify all of the following on the final integrated branch:

- [ ] Declare the service in the manifest with the correct foreground-service type and `exported=false`.
- [ ] Match every foreground-service type to its required manifest permission and Android-version rules.
- [ ] Start only from a user-authorized, user-visible flow; persist the user's choice and provide a clear stop/disable control.
- [ ] Bind/unbind across Activity recreation without replacing or losing the service-owned transport listener.
- [ ] Verify process death/recreation, sticky restart, and explicit stop behaviour; do not restart indefinitely after the user disables nearby.
- [ ] Confirm Android 13+ notification permission denial does not create a false "service is visible" promise, and explain the persistent system foreground-service notification.
- [ ] Verify Android 14+ connected-device foreground-service prerequisites and permission/type declarations.
- [ ] Drain the encrypted inbox after Activity rebind; remove an entry only after the chat/community repository confirms durable commit.
- [ ] Preserve the original transport acknowledgement callback for live events and use the persisted-event acknowledgement path after process recreation.
- [ ] Test duplicate message IDs, inbox-full behaviour, unreadable ciphertext, failed persistence, failed UI commit, detach during delivery, and process death at each acknowledgement boundary.
- [ ] Verify files arriving with no Activity attached are safely catalogued on rebind and do not silently disappear.
- [ ] Run JVM tests, debug APK build, and physical-device lifecycle tests across at least two Android versions.

**Important:** A successful JVM suite and APK build do not establish background execution reliability, OEM battery-policy compatibility, or radio reconnection behaviour. Those require real-device testing.
