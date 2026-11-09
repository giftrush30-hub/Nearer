# NeaRer identity model

## The simple identity promise

A NeaRer profile needs only:
- A display name chosen by the person.
- A persistent public NeaRer ID.

The nearby-first experience must not require a phone number, email address, password, or mandatory account sign-in. The display name is editable; the ID is generated locally and remains stable across app restarts.

## Public ID format

New installations receive a 12-character ID using an unambiguous 32-symbol alphabet. This represents 60 random bits and avoids visually confusing characters. The ID is persisted in the app's local preferences.

Existing six-character hexadecimal IDs are retained for compatibility with existing peer records and conversation history. The nearby discovery decoder accepts both formats.

A random ID makes accidental collisions extremely unlikely, but it is not a central registry guarantee: two offline devices cannot coordinate to prove global uniqueness. If a future online directory is added, it must detect collisions and provide a safe reassignment flow without silently losing local conversations.

## Privacy and security boundaries

- The public ID is an identifier, not a password, secret, or proof of ownership.
- Never use it as a transfer access key or as the sole authorization for a connection.
- Nearby connection approval and the existing authentication-token comparison remain separate security steps.
- Do not expose phone contacts, phone numbers, or location as a prerequisite for a basic profile.
- Nearby discovery is local; a public ID alone cannot find a person across the internet while the device is offline.

## UX

Show the person's name and public ID in the identity area. Provide a copy action so they can share the ID outside the app. Keep the profile setup short: choose a name, then start using NeaRer.
