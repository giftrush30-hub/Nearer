# Fast Share transfer hardening

## PC-to-phone uploads

- Browser uploads are admitted through a dedicated bounded gate: at most two upload bodies are written concurrently.
- The gate reserves each declared Content-Length against the free space observed in the private upload directory. This prevents concurrent requests from independently spending the same free-space estimate.
- Admission failures are distinguished: invalid length returns 400, saturated upload slots return a retryable 429, and insufficient reserved storage returns 507.
- Upload bodies are streamed in 64 KiB blocks to a temporary file; no whole-file in-memory copy is created.
- The temporary file is synced before publication and is renamed into the private upload directory only after the declared body has been received completely.
- A SHA-256 digest is computed while streaming. The 201 response includes X-Content-SHA256 and X-Received-Bytes so a browser client can verify the completed upload receipt.
- Duplicate filenames are kept distinct by adding a numeric suffix under a publication lock.
- A failed or interrupted upload leaves no published partial file; temporary files are deleted and the admission reservation is released in finally.

## Limits and verification boundary

- There is no arbitrary per-file size ceiling introduced here; available storage and the HTTP Content-Length are the admission constraints.
- The two-upload limit applies to browser-to-phone uploads only. Nearby Connections transfer concurrency is governed separately.
- The checksum response is an integrity receipt, not transport encryption or authentication of the browser client.
- JVM unit tests were added for upload admission and the distinct retry response. They have not been executed in this source-only workflow.
- No Gradle build, APK build, or physical-device test has been run.

## Pure helper coverage

- Filename decoding and sanitization now live in a small pure helper, with JVM tests for URL-encoded spaces, path traversal separators, unsupported/control-like characters, empty names and length bounds.
- Upload SHA-256 is computed incrementally in fixed-size chunks through a dedicated helper. JVM tests check the standard SHA-256 digest for "abc" and verify chunked updates produce the same digest as one update.
