# NeaRer OpenSend + AppShare engineering foundation

This branch introduces the first reusable Android implementation pieces for the no-app receiver and app-transfer workflows. It does **not** yet expose finished screens or claim physical-device validation.

## Current branch scope

- `AppShareCatalog`: lists visible installed applications, excludes unmodified system apps by default, detects base and split APK paths, and copies all readable APK components into app-private cache for transfer.
- `OpenSendHotspot`: requests a temporary Android `LocalOnlyHotspot` and returns the SSID/password supplied by Android. The reservation must remain open while sharing.
- `OpenSendHttpServer`: hosts a NeaRer-branded browser download page on a local TCP port, requires a random per-session token, supports multiple clients, and implements HTTP byte ranges for resumable downloads.
- Manifest declares `QUERY_ALL_PACKAGES` for the complete installed-app catalogue. This permission is restricted by Google Play policy; distribution eligibility must be reviewed before Play Store release.

## Intended user experience

### Main Share hub

Use one uncluttered hub with three primary actions:

1. **Files** — existing NeaRer file picker and nearby transfer engine.
2. **Apps** — AppShare catalogue, search, multi-select, package details, and Send.
3. **OpenSend** — browser-based receiving for phones without NeaRer.

Place **Share NeaRer** as a highlighted shortcut inside Apps and in the receive/invite area. Use NeaRer's established midnight-blue and gold identity. Keep technical settings behind a clear "Connection details" disclosure.

### AppShare flow

1. Load installed applications using `AppShareCatalog.listApps()`.
2. Show icon, label, package size, version, and a clear System/Updated-system classification.
3. Allow multi-selection, but explain that a split application is a package set.
4. Prepare every selected app with `preparePackage()`.
5. Send each app's base APK and all split APK components as one logical package set. Do not send only the base APK when split APKs exist.
6. On the receiving NeaRer phone, verify package name, version, split consistency and signing-certificate continuity before installation.
7. Stage the complete set with Android `PackageInstaller.Session`; Android may require the receiver to confirm installation. Never attempt silent installation.
8. Never copy app-private data, login sessions, account tokens, or paid entitlements.

For an Android receiver without NeaRer, provide APK download in OpenSend and let Android's package installer handle the user-confirmed install. iPhones cannot install Android APKs; show a platform-appropriate link instead.

## OpenSend flow

1. Sender chooses files and taps **Send to any phone**.
2. Request the Android local-only hotspot permission as needed and call `OpenSendHotspot.start()`.
3. Keep the returned hotspot reservation alive. Show Android-provided SSID and password; do not assume an open network or invent credentials.
4. Start `OpenSendHttpServer` with the selected files. The server binds to an ephemeral port and returns the session token and available local IPv4 addresses.
5. Present the Wi-Fi details and a Wi-Fi QR code. After connection, present a separate URL QR code and a manual URL fallback. A single QR containing Wi-Fi credentials and a URL is not guaranteed to perform both actions on all Android/iOS camera apps.
6. Receiver opens the page in a browser and downloads selected files. The HTTP server supports Range requests so a browser/download manager can resume when it retries with a Range header.
7. Sender sees connected/download status where it can be reliably observed, can revoke the session, and can stop sharing.
8. On stop, close the HTTP server and hotspot reservation, invalidate the token, and clear prepared cache files when safe.

### Important compatibility boundaries

- A local-only hotspot is a local network without internet; it is not the same as silently toggling the phone's normal tethering hotspot.
- Android can restrict hotspot creation when another hotspot, Wi-Fi Direct group, or tethering mode is active. The UI must explain the conflict and offer a retry.
- Captive-portal popups are best-effort only. Always show a manual browser URL.
- The sender's hotspot range is hardware- and environment-dependent. Never promise 100m, 500m, or kilometre coverage.
- Wi-Fi QR recognition varies. Treat QR as a convenience, not a required transport.
- HTTP Range support allows resumption only when the receiving browser/client retries the same URL with a valid Range header. It does not guarantee that every mobile browser automatically resumes.
- OpenSend uses local HTTP, not TLS. The temporary WPA-protected local network plus high-entropy token limits casual access, but this is not equivalent to end-to-end encrypted Nearby Connections. Do not expose the server on a public interface or run it beyond an explicit user-controlled session.
- The local-only hotspot API and package visibility behavior must be tested on Samsung One UI and other OEM Android versions.

## Required follow-up implementation

- Add the Share hub and AppShare/ OpenSend screens to `NeaRerHome`.
- Wire AppShare selection to the existing nearby file-transfer engine; preserve package grouping metadata for receiving-side install.
- Implement receiving-side package verification and `PackageInstaller.Session` staging.
- Implement QR generation with a maintained Android QR library, plus distinct Wi-Fi and URL QR actions.
- Add an OpenSend transfer session manager to coordinate hotspot reservation, HTTP server, foreground notification, timeout, stop/revoke, and cleanup.
- Add an explicit receiver handshake/acceptance flow and sender-visible download lifecycle.
- Implement file hashing and compare before marking downloads complete.
- Add Range/resume integration tests and tests for multiple simultaneous receivers.
- Add runtime permission UX and Play policy review for `QUERY_ALL_PACKAGES`.

## Validation checklist before calling it ready

### AppShare
- [ ] Samsung A12 / Android 12 installed-app catalogue
- [ ] Android 13+ package visibility and permissions
- [ ] Single APK export and re-install
- [ ] Split APK export and complete installation
- [ ] Same-package update with matching signing certificate
- [ ] Incompatible Android version / ABI warning
- [ ] Corrupt or truncated APK rejection
- [ ] No app-private data copied

### OpenSend
- [ ] Android sender to Android browser
- [ ] Android sender to iPhone Safari
- [ ] Local-only hotspot credentials shown correctly
- [ ] Wi-Fi QR and URL QR each tested separately
- [ ] Manual URL fallback
- [ ] Small file, multi-GB file, and zero-byte file
- [ ] Range request returns correct 206 response and byte content
- [ ] Invalid token returns 403
- [ ] Unknown file ID returns 404
- [ ] Two or more receivers downloading concurrently
- [ ] Sender stop immediately revokes access
- [ ] App backgrounding / screen-off / process death behavior
- [ ] Conflicts with existing hotspot and Wi-Fi Direct session
- [ ] No mobile data is consumed

## Reference documentation

- Android local-only hotspot: https://developer.android.com/develop/connectivity/wifi/localonlyhotspot
- Android WifiManager: https://developer.android.com/reference/android/net/wifi/WifiManager
- Google Nearby Connections: https://developers.google.com/nearby/connections/overview
- Android PackageManager: https://developer.android.com/reference/android/content/pm/PackageManager
- Android PackageInstaller: https://developer.android.com/reference/android/content/pm/PackageInstaller
- Android PackageInstaller.Session: https://developer.android.com/reference/android/content/pm/PackageInstaller.Session
