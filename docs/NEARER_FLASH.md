# NeaRer Flash — experimental feature proposal

**Status:** Proposed; not implemented in the APK  
**Product area:** Long-distance signalling  
**Priority:** Prototype and validate before production integration

## Product intent

NeaRer Flash explores short, opt-in messages between NeaRer users over ordinary cellular voice-call signalling. It complements (but does not replace) NeaRer's offline Nearby Connections chat and file-transfer features.

The feature must never be marketed as guaranteed free, universally compatible, or available without cellular coverage. Ordinary cellular voice service and carrier policies apply.

## Initial scope

1. **Flash Codes (first prototype):** a small set of user-configurable preset signals such as “Call me”, “I'm outside”, “Come to my room”, and “Emergency”.
2. **Flash Text (later experiment):** investigate whether short custom text can be encoded reliably. Do not promise this until measured on real devices and networks.
3. **Flash Alerts:** distinct in-app notification and vibration for verified, opted-in senders.

## Technical investigation

Build a separate Android prototype before changing the main communication flow.

- Investigate Android's supported incoming-call screening role and CallScreeningService behavior across supported Android versions and OEMs.
- Do not assume an app can silently observe every ring, measure precise ring duration, or end calls using ordinary runtime permissions.
- Investigate supported outgoing-call initiation and call-disconnect control. If user interaction or a system dialer flow is required, design around that rather than bypassing Android restrictions.
- Prefer distinguishing separate call events over relying on ring duration, but treat event sequences as experimental: carriers and devices may delay, suppress, merge, or otherwise alter calls.
- Test foreground/background behavior, Doze, reboot, dual-SIM selection, caller-ID presentation, blocked/private numbers, call waiting, and concurrent senders.
- Check current Google Play policy and permission eligibility before choosing any call-log, phone-state, or call-screening permissions.

## Safety, privacy, and abuse prevention

- Both parties must explicitly enable Flash.
- Receiver maintains an allowlist of contacts or verified NeaRer peers; unknown callers are never decoded as messages by default.
- Never suppress or interfere with emergency calls or ordinary calls outside a clearly disclosed, user-enabled screening behavior.
- Add replay/duplicate protection, rate limits, cooldowns, sender confirmation, and a clear disable switch.
- Do not upload call metadata or contact lists to a server. Explain locally what phone permissions are needed and why.
- Do not use repeated calls at abusive volume or attempt to evade carrier anti-spam controls.

## Validation gates

Do not integrate or advertise Flash as working until all gates pass:

1. Incoming signal events can be identified consistently on two or more physical Android phones.
2. Preset codes decode correctly under foreground and background conditions.
3. Ordinary incoming calls remain usable and are not mistaken for Flash.
4. Sender/receiver behavior is tested across MTN, Airtel, Glo and 9mobile where available, including dual-SIM cases.
5. Actual outgoing-call charges, bundle deductions, carrier restrictions, and anti-spam behavior are measured. “₦0” is not a product claim unless independently verified for the exact supported conditions.
6. Compatibility, battery use, permission UX, accessibility, and Play policy are reviewed.

## Suggested delivery phases

- **Phase 0 — feasibility:** document Android API and policy constraints; test manual and supported call flows.
- **Phase 1 — two-phone prototype:** send and receive one fixed test signal with explicit user action and visible diagnostics.
- **Phase 2 — preset codebook:** add a small configurable set only if Phase 1 is reliable.
- **Phase 3 — field validation:** test carriers, Android versions, OEMs, dual SIM and real-world conditions.
- **Phase 4 — product integration:** add a polished NeaRer Flash screen only after validation; keep it separate from Nearby Connections.

## Product positioning

- Nearby mode: local chat and file sharing without mobile network or internet, using supported nearby radios.
- Flash mode: experimental long-distance signalling over cellular voice service; requires cellular coverage and may incur charges.

**Current truth:** this document records the intended investigation and safeguards. It does not mean the feature has been implemented or tested in the APK.
