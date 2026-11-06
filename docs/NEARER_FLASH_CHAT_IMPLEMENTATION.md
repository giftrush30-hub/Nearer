# NeaRer Flash Chat — implementation plan

## Product decision
Flash belongs inside the existing NeaRer conversation experience. The user should choose a person, compose a message, and tap Send. NeaRer should manage the cellular signalling sequence and show message status in the conversation.

## Android integration requirements
The current app has a CallScreeningService for receiving incoming signal calls. That service does not provide outgoing call control. Automatic placement and hang-up requires a user-approved call-control implementation.

The planned Android integration is an optional default phone app role:
- An ACTION_DIAL activity with a usable dial pad.
- An InCallService that handles every call, including normal incoming and ongoing calls.
- A visible incoming/ongoing call interface and clear controls.
- TelecomManager.placeCall for outgoing signal calls.
- Call.disconnect through the InCallService for ending each signal call.
- A user-visible opt-in before requesting ROLE_DIALER. Never silently replace the user's phone app.

Do not request the default dialer role until the dial pad and incoming/ongoing call handling are implemented and tested. Normal calls and emergency calls must continue to work.

## Message protocol reality
A missed call carries no arbitrary text payload. The receiver can observe call events and the caller number, not the text the sender typed. Therefore arbitrary chat text cannot be transmitted in one missed call.

The first reliable version should send named preset phrases using a versioned, framed burst protocol. A burst needs:
- An agreed sender identity and recipient number.
- A clear start marker, message identifier and checksum/error detection.
- Bounded call count, retry rules, duplicate suppression and timeout.
- A per-contact rate limit and a visible airtime/cost warning.
- Explicit sending/received/failed states; never claim delivery merely because calls were initiated.

The current prototype's simple 1–10 burst count is not yet robust enough for production: it has no explicit start/end framing, acknowledgement, checksum, or collision handling. Replace it rather than presenting it as unrestricted text chat.

## UX
- Add Flash as a delivery mode within the normal conversation, not a separate destination.
- Keep nearby/offline chat as the default local transport.
- Add a clear Flash mode indicator for long-distance messages.
- Display message bubbles with timestamps and honest states: Queued, Sending, Received (only when an acknowledgement exists), Failed.
- Offer a small preset phrase composer first. Do not pretend arbitrary text is supported until a feasible, affordable encoding is proven.
- Let users disable Flash receiving and manage trusted senders.

## Engineering gates
1. Build and validate the complete default-dialer implementation without changing the current default phone app.
2. Install on two Android phones and test ordinary incoming/outgoing calls, emergency-call handoff, screen locked/background behavior, call waiting and SIM/network variations.
3. Verify the incoming signal protocol, burst framing, duplicates, loss and false positives.
4. Integrate the tested sender/receiver into the existing chat UI.
5. Test real airtime costs on participating Nigerian networks. Never advertise missed calls as free without verified tariff evidence.

## Current baseline
The Android debug APK for commit 1a0bfcc01d63661f7c3283fb943adda8cb650011 built successfully and was uploaded by GitHub Actions on 2026-11-06. This is a baseline only; the automatic in-chat call sender and full dialer integration are not implemented yet.
