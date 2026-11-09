# Fast Share transfer hardening

## PC-to-phone uploads

- Browser uploads are admitted through a dedicated bounded gate: at most two upload bodies are written concurrently.
- The gate reserves each declared Content-Length against the free space observed in the private upload directory. This prevents concurrent requests from independently spending the same free-space estimate.
- Admission failures are distinguished: invalid length returns 400, saturated upload slots return a retryable 429, and insufficient reserved storage returns 507.
- Upload bodies are streamed in 64 KiB blocks to a temporary file; no whole-file in-memory copy is created.
- The upload body has a size-aware overall deadline (two-minute grace plus an allowance based on a conservative minimum transfer rate, capped at 24 hours), in addition to the per-read idle timeout. This prevents slow-trickle clients from holding upload slots indefinitely without imposing a per-file byte ceiling. An idle body read timeout returns HTTP 408 with `Retry-After: 2` when the connection is still writable; the incomplete staging file is discarded.
- The temporary file is synced before publication and is renamed into the private upload directory only after the declared body has been received completely.
- A SHA-256 digest is computed while streaming. The 201 response includes X-Content-SHA256 and X-Received-Bytes so the browser can validate the server receipt.
- Duplicate filenames are kept distinct by adding a numeric suffix under a publication lock.
- A failed or interrupted upload leaves no published partial file; temporary files are deleted and the admission reservation is released in finally. If Android kills the process mid-upload, the next Fast Share server startup removes only the private `.nearer-upload-*.part` staging files left by the previous session.
- If a browser sends `If-Range`, the server ignores the accompanying range and sends the complete file because this endpoint does not yet publish a stable file validator. This prevents a stale validator from causing bytes from a changed source file to be appended to an old partial download.
- The receiver page uses XMLHttpRequest for byte-level progress and cancellation. It validates the server's byte-count and digest-header shape before reporting success; it does not independently recompute the digest.
- Multi-file browser uploads continue after an individual file fails, report the failed names, and retain only failed or not-yet-attempted files in the picker for a direct retry. Successful files are not automatically resent.

## HTTP request validation

- Request parsing bounds request-line length, individual header length, aggregate header bytes, and header count.
- HTTP/1.1 requires one valid Host header. Duplicate Host, Content-Length, Range, filename, or MIME headers are rejected.
- Transfer-Encoding is rejected because this endpoint only accepts a fixed Content-Length body; malformed, negative, overflowing, missing, and duplicate upload lengths fail closed.
- Upload requests require a filename and a Content-Length. Zero-byte uploads are valid; ordinary GET/HEAD routes reject non-zero request bodies.
- Header parsing rejects bare-LF line endings, embedded carriage returns, control characters, invalid header names, and oversized input.

## Limits and verification boundary

- No arbitrary per-file size ceiling is introduced here; available storage and the HTTP Content-Length are the admission constraints.
- The two-upload limit applies to browser-to-phone uploads only. Phone-to-browser download concurrency is governed separately.
- The checksum response is an integrity receipt, not transport encryption or authentication of the browser client. Fast Share remains a local HTTP transfer.
- JVM test fixtures were corrected to use real CRLF request delimiters and to match the receiver page's XMLHttpRequest implementation and actual Allow response. Additional cases cover duplicate/negative Content-Length and valid zero-byte uploads.
- The relevant JVM test source currently contains 45 HTTP-session tests and 9 receiver-page tests. They have not been executed in this source-only workflow.
- No Gradle build, APK build, or physical-device test has been run.

## Pure helper coverage

- Filename decoding and sanitization live in a pure helper, with JVM tests for URL-encoded names, path traversal separators, unsupported characters, empty names and length bounds.
- Upload SHA-256 is computed incrementally in fixed-size chunks through a dedicated helper. JVM tests check the standard SHA-256 digest for "abc" and compare chunked updates with a single update.
