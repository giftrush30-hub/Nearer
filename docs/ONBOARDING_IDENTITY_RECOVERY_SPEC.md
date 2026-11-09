# NeaRer onboarding, identity and recovery specification

Status: product decisions approved. First-run onboarding, local profile persistence, profile editing and the More-tab profile preview are implemented on the `feature/premium-onboarding-identity` branch. Peer-to-peer profile propagation, recovery and cross-device migration remain separate implementation milestones.

## Product promise

NeaRer is local-first. A person can create and use a basic identity without a phone number, email address, password-based sign-in, Supabase, Firebase or a mandatory internet connection.

The public NeaRer ID is an identifier, not a password, secret, recovery key or proof of ownership. Nearby connection approval and authentication remain separate security steps.

## First-run journey

1. **Welcome** — branded midnight-blue welcome, blue accents and gold highlights; concise explanation that the nearby-first core works without mobile data.
2. **Display name** — required, 1–24 visible characters after whitespace cleanup; editable later. Do not ask for phone number or email.
3. **Personalisation** — six optional, skippable questions:
   - Which words describe your personality?
   - What brings you to NeaRer?
   - What are you interested in?
   - What matters most to you here?
   - What do you enjoy in your free time?
   - How should NeaRer welcome you?
   Answers are local preferences. Do not expose them to nearby peers by default or use them as a condition of access.
4. **Choose your look** — choose a premium illustrated avatar, upload a profile photo from the gallery, or skip. Photo and avatar are optional and editable later. A photo replaces the avatar as the active visible image; retain the selected avatar choice as a fallback.
5. **ID reveal** — reveal the existing locally generated public NeaRer ID with a brief celebratory animation, explain that it can be shared to identify the person, and clearly say it is not a recovery secret.
6. **Enter NeaRer** — finish setup and open the main experience.

## Profile model and local storage

- Keep the current public ID format and compatibility rules in `NeaRerIdentity`; do not silently change existing IDs or break peer records.
- Store display name, selected avatar ID, onboarding completion, optional personalisation answers and local profile-photo path in app-private storage/preferences.
- Copy picked photos into app-private storage rather than relying on a temporary gallery URI.
- Resize/compress profile photos for nearby transfer and cached display; preserve a user-selected original only if they explicitly opt in.
- When profile data is shared with a nearby peer, send only the public profile fields needed for the interaction. Do not send personalisation answers.
- Provide clear controls for photo visibility and profile reset in a later profile-settings milestone.

## Phone change and recovery

NeaRer must distinguish identity recovery from message-history recovery.

### Transfer from the old phone

When the old phone is available, support a direct, authenticated local migration:
1. Install NeaRer on the new phone and choose **I already use NeaRer**.
2. Open **Move my NeaRer** on the old phone.
3. Discover the new phone over Nearby Connections / Bluetooth and establish a secure local transfer session.
4. Show a short-lived pairing code on both devices and require the person to compare and approve it.
5. Transfer identity keys, profile, contacts, conversation records, group history, media manifests and selected media over the encrypted session.
6. Verify record counts and cryptographic checksums before marking the migration complete.
7. Let the person confirm the new device is ready before they retire the old device.

Never authorise a migration using the public NeaRer ID alone. Do not delete the source data automatically after transfer.

### Recovery Kit

Provide a user-controlled recovery kit containing the cryptographic recovery material, not merely the public ID. The recovery material must be generated with a cryptographically secure random source and be exportable as:
- a human-readable recovery phrase with checksum and clear offline storage guidance; and
- an encrypted recovery file that the user can keep on removable storage or another device.

The phrase/file must be shown only during a deliberate setup/export action. Do not log it, send it to nearby peers, or store it unencrypted in preferences. Recovery should require explicit confirmation and should warn before replacing an already active local identity.

### Encrypted backups

Offer both automatic local backups and manual **Back up now** / **Export backup** actions. The automatic schedule should be conservative and configurable; notify only when the most recent backup becomes outdated, not with constant nags.

A backup should include a versioned manifest, profile, contacts, conversation metadata and messages, group data, and media references. Media inclusion should be selectable because it can make backups large. Encrypt backup contents with authenticated encryption (AES-GCM); derive a wrapping key from a user-chosen backup password using a reviewed, salted, deliberately expensive KDF, or use the recovery key. Keep format versioning and integrity checks so future versions can restore older backups.

Allow a restore preview showing conversations, contacts and media before writing data. Never overwrite current data without explicit confirmation. Keep at least one prior valid backup until a new backup has been verified.

### Honest recovery boundary

If a phone is lost or destroyed, no backup exists, and the old phone cannot be accessed, data that existed only on that phone cannot be recovered by an offline-only system. Explain this plainly during setup. Encourage users to create and verify a first backup; do not promise magical recovery.

## Device management

- Show authorised device names and last-used dates once multi-device identity is implemented.
- Provide **Remove device** with a confirmation step and a revocation event that is included in future syncs.
- A lost-device action should rotate/revoke credentials where possible and explain that an offline device cannot receive revocation until it reconnects.
- Keep account/device recovery separate from optional future cloud services.

## Privacy and safety requirements

- Do not require phone contacts permission, phone number, email or location for basic onboarding.
- Profile photo and avatar are optional.
- Personalisation answers remain private local preferences unless the user deliberately shares a specific field.
- Never treat a public ID as a secret or authentication credential.
- Nearby transfer requires authenticated session setup, explicit approval and integrity verification.
- Do not claim end-to-end encrypted group history or secure cloud recovery until those designs are implemented and independently reviewed.

## Implementation sequence

1. First-run onboarding, name, optional answers, avatar/photo choice, ID reveal and local persistence — implemented.
2. Local profile editor and profile preview in More — implemented; peer profile propagation and rendering across discovery, chats and groups remain pending.
3. Recovery Kit generation and secure key storage.
4. Encrypted local backup/export and restore, including verification tests.
5. Old-to-new phone migration with pairing, integrity checks and rollback-safe completion.
6. Device management and credential revocation.
7. Optional cloud backup only if later approved; never make it a prerequisite for the offline-first core.
