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

## Archive layer

`NeaRerBackupArchive` now packages a caller-prepared snapshot directory into a ZIP archive and encrypts it with the container above.

- The ZIP starts with `nearer-backup-manifest.json`, listing every file's relative path, byte count and SHA-256 digest.
- Restore rejects unsafe paths, duplicate manifest records, unlisted or missing files, size mismatches, hash mismatches and oversized archives.
- Limits: 10,000 files, 512 MiB per file, 2 GiB total expanded data and a 1 MiB manifest.
- Restore extracts to a private staging directory and publishes it only after all files verify. It refuses to merge into an existing destination directory.

The archive layer is still not a complete user-facing backup feature. The chat database now has portable JSON-lines export and validated merge-import methods: export reads the full SQLite archive without copying a live database/WAL file, and import validates every record and duplicate ID before applying the batch through the database transaction. Import merges by message ID and preserves unrelated local messages. `NeaRerBackupSnapshot` now builds a portable snapshot from the current local stores: it exports all SQLite chat records, copies allow-listed profile/onboarding/community/transfer-history preferences, copies the profile photo without retaining its old device path, and includes received files. It excludes transient transport state and does not treat a public NeaRer ID as a recovery secret. Its `inspect` method validates the snapshot and returns chat/file counts and the profile name/photo presence for a restore preview. This builder is not yet connected to a user-facing backup flow, and no restore-apply transaction currently imports profile/media/preferences with rollback. Contacts and any other unlocated stores remain to be audited. A public NeaRer ID must never be accepted as a recovery secret.

Automated unit tests have been added for encryption round-trips, wrong passwords, tampering, archive round-trips and refusing to overwrite existing restore destinations. They have not yet been run in an Android/Gradle build.
