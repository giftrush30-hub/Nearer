# Encrypted backup container format

Status: cryptographic file-container primitive implemented on `feature/premium-onboarding-identity`; not yet connected to chat/profile/media export or restore UI, and not yet build/device verified.

- Magic: 8-byte ASCII `NEARERBK`
- Version: unsigned byte, currently `1`
- KDF work factor: PBKDF2-HMAC-SHA-256, 310,000 iterations
- Salt: 16 random bytes from `SecureRandom`
- Encryption: AES-256-GCM, 12-byte random nonce, 128-bit authentication tag
- Authenticated additional data: the complete fixed header
- File processing: streamed in 64 KiB blocks; decryption writes only to a private temporary file and publishes the destination only after the GCM tag verifies.
- Destination safety: existing files are never overwritten by this primitive; callers must choose a new destination or explicitly manage replacement outside this API.
- Password handling: minimum 12 characters; caller-provided char array is cleared before return.
- Defensive parsing: only the supported version and a bounded KDF iteration range are accepted.

This primitive is not, by itself, a complete backup feature. A future backup layer must add a versioned manifest, include selected app data, validate all extracted paths and sizes, preview restore contents, and apply restored data transactionally with rollback. A public NeaRer ID must never be accepted as a recovery secret.
