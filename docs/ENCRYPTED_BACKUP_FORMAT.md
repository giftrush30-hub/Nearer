# Encrypted backup container format

Status: encrypted snapshot creation and read-only restore preview are implemented on `feature/premium-onboarding-identity`; no user-facing backup screen or restore-apply flow yet, and not yet build/device verified.

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

The archive layer is still not a complete user-facing backup feature. The chat database now has portable JSON-lines export and validated merge-import methods: export reads the full SQLite archive without copying a live database/WAL file, and import validates every record and duplicate ID before applying the batch through the database transaction. Import merges by message ID and preserves unrelated local messages. `NeaRerBackupSnapshot` builds a portable snapshot from the current local stores: it exports all SQLite chat records, copies allow-listed profile/onboarding/community/transfer-history preferences, copies the profile photo without retaining its old device path, and includes received files. It also preserves recently-seen peers, the pending offline message outbox and ordered message de-duplication IDs. It excludes device-specific pending file-picker URIs, transient transport state and the public NeaRer ID as a recovery credential. Its `inspect` method validates the snapshot and returns chat/file counts and profile preview details. The `NeaRerBackupManager` now coordinates encrypted backup creation and read-only preview. No restore-apply transaction currently imports profile/media/preferences with rollback. The current app has no separate address-book/contacts database; recently-seen peers are a short-lived discovery history. A public NeaRer ID must never be accepted as a recovery secret.

Automated unit tests have been added for encryption round-trips, wrong passwords, tampering, archive round-trips and refusing to overwrite existing restore destinations. They have not yet been run in an Android/Gradle build.


## Snapshot coverage audit and coordinator

The app's two current receive paths both store completed files in the app-private
`files/received` directory. The snapshot builder copies that directory, which is
also the directory used when the app reloads its received-file list.

The portable profile snapshot now also preserves the bounded recently-seen peer
list, the pending offline message outbox, and ordered message de-duplication IDs.
These values are included inside the encrypted archive. Device-specific pending
file-picker URIs are deliberately excluded: Android content URIs and temporary
provider grants are not portable to a replacement phone. A future restore screen
must explain that any queued file selections need to be chosen again.

`NeaRerBackupManager` now coordinates encrypted backup creation from a temporary
private snapshot and a read-only encrypted-backup preview. Preview decrypts and
validates into a private temporary directory, returns only counts/profile metadata,
and deletes the plaintext staging directory afterwards. It does not alter the
installed app's data.

Snapshot preview validation now checks duplicate message IDs and the full portable
chat field bounds before a restore preview can be shown. It also validates the
profile name, avatar allowlist, onboarding flag type, and photo containment.

**Still not complete:** there is no user-facing backup/restore screen and no
restore-apply transaction. Applying a backup must be designed as a rollback-safe
operation across SQLite chats, SharedPreferences, profile photo and received files;
the transport must also reload restored pending outbox state safely. Until that
exists, restore remains preview-only. No contacts database exists in the current
app; recently-seen peers are a short-lived discovery history, not an address book.
