# NeaRer Flash — source implementation and compatibility notes

**Status:** Integrated into the Android source tree; physical-device and carrier validation still outstanding  
**Product area:** Long-distance preset signalling over cellular voice calls

## What is implemented

- A dedicated blue-and-gold Flash screen is linked from the NeaRer home screen.
- Ten preset messages are available: call me, come outside, on my way, waiting, Room 8, Room 10, food ready, lecturer coming, help, and emergency.
- The sender selects a preset and opens the Android system dialler for each separate call attempt. The user must place and end each call manually.
- The receiver can save one trusted sender number and request Android's call-screening role.
- Only calls matching that locally saved trusted number are processed. Other calls are passed through unchanged.
- Trusted calls have their ringtone silenced and are counted into a preset code. The notification is updated as the count increases.
- The receiver can request Contacts permission (needed for Android to pass calls from saved contacts to the screening service) and notification permission.
- No mobile-data connection is used by the Flash signalling flow.

## Current protocol

A code is the number of distinct incoming calls from the trusted number within a two-minute burst window. The receiver maps counts 1–10 to the preset list. The user must wait two minutes before beginning a new message so the next burst starts at one.

## Important limitations

- This is preset signalling, not free-form text chat. Arbitrary custom text has not been implemented.
- The sender flow opens the system dialler; it does not automatically place or hang up calls.
- The screening service silences the ringtone for a trusted incoming call; it does not hide the call from the system dialler or call history.
- Android call screening is available through a user-granted role on supported Android versions (the in-app role request targets Android 10+).
- Calls may incur airtime charges. Missed calls are not guaranteed to be free on MTN, Airtel, Glo, or 9mobile.
- Carrier routing, call waiting, dual SIM selection, spam controls, OEM power management, and incoming-call presentation can affect reliability.
- The source has not yet been confirmed by a successful final APK build or real two-phone testing.

## Privacy and abuse safeguards

- Flash is opt-in and uses a single locally stored trusted sender number.
- Unknown callers are not decoded or silenced.
- No call metadata or contact list is uploaded to a server.
- Users can disable the receiver by turning off the receiver setting in NeaRer or removing its call-screening role.
- The feature must not be advertised as universally compatible, guaranteed free, or a replacement for emergency calling.

## Next engineering gates

1. Obtain a successful CI APK build for the current main branch.
2. Install on two physical Android phones and confirm one through ten call events decode correctly.
3. Confirm ordinary calls from trusted and unknown numbers behave as clearly disclosed.
4. Test with contacts permission both granted and denied, notification permission granted and denied, and app in background.
5. Test MTN, Airtel, Glo and 9mobile, dual-SIM routing, call waiting and carrier restrictions.
6. Measure actual charges and reliability before making any ₦0 or compatibility claim.
7. Design a reliable custom-text protocol only after the preset code path is dependable.

## Product positioning

- **Nearby mode:** local chat and file sharing without mobile network or internet, using supported nearby radios.
- **Flash mode:** low-bandwidth preset signalling over cellular voice service; requires cellular coverage and may incur charges.
