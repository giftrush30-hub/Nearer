# NeaRer Phase 1 — SmartConnect

## Product promise

The user should not have to understand Bluetooth, BLE, Wi-Fi Direct, hotspot passwords, or transport negotiation. They open NeaRer, see nearby people, tap a person, and chat. The app uses Android's nearby permissions and available radios to establish the best supported direct connection.

## Implemented in this branch

1. **Automatic discovery startup**
   - After first-run profile setup, NeaRer requests the required Android nearby permissions.
   - A successful permission result starts the Nearby Connections engine.
   - Returning to the foreground restarts discovery when onboarding, permissions and at least one radio are ready.
   - Discovery uses the flexible multi-peer cluster strategy by default.

2. **Less manual configuration**
   - Removed the user-facing Ultra Speed Mode switch and matching/restart instructions.
   - The app no longer asks both users to choose the same connection mode.
   - The UI explains that SmartConnect selects the available supported transport.
   - Android still owns radio toggles and permission decisions.

3. **One-tap nearby people**
   - Nearby person cards are tappable and open the connection request flow.
   - First contact remains explicit: the recipient approves and compares the security code.
   - A previously trusted peer can be automatically accepted on reconnection only when both pinned identity keys are present.
   - Automatic acceptance does not skip the live authenticated handshake; changed or invalid keys still fail verification before application data is accepted.

4. **Deterministic policy tests**
   - Discovery cannot start before onboarding, permissions and radio readiness.
   - Discovery does not redundantly start while already searching or connected.
   - Trusted reconnection requires both the signing-key and recipient-encryption-key pins.

## Important technical boundaries

- Nearby Connections abstracts the supported Bluetooth/BLE/Wi-Fi transport selection. NeaRer should not claim that every phone exposes the same Wi-Fi Direct or hotspot behavior.
- NeaRer does not silently switch on Bluetooth or Wi-Fi.
- The current activity-owned discovery lifecycle automatically starts while the app is foregrounded and restarts on foreground return. It is not a guarantee of continuous discovery after Android kills the app or the user swipes it away.
- First-contact approval is retained to avoid silently connecting to unknown devices.
- Existing app minimum is API 26 (Android 8.0). Android 7/API 24 is not supported by the current build configuration.

## Verification still required

- Exact-branch CI: JVM tests, debug APK assembly and emulator launch tests.
- Two physical Android phones: first-contact approval, key pinning, trusted automatic reconnect, messages, disconnect/reconnect and radio-toggle recovery.
- Test on the user's Android 10 Infinix device and at least one additional Android manufacturer.
- Verify Android background/Doze behavior before promising always-on presence.

## Phase 1 completion rule

This is a source implementation milestone, not a release certification. Phase 1 is not complete until exact-head CI passes and the physical-device discovery/reconnect checks pass. Background always-on discovery must be either implemented and validated or explicitly scoped as a later phase before the product promise is finalized.
