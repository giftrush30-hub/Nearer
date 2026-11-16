# NeaRer Movies / Video Center — current implementation and roadmap

## Current local-first library

The Movies workspace is a local video library and player. It does not claim to be a public streaming service.

Current capabilities:
- Select multiple local videos through Android's Storage Access Framework.
- Keep persisted read access to selected document URIs; the original video is not copied into app storage.
- Search the local library and filter all videos, favourites or videos with a saved continue-watching position.
- Keep favourite state, added time, last-watched time and resume position in a versioned bounded catalogue.
- Save resume position periodically while playing, on pause, on app background and when leaving the player.
- Pause video playback when the app moves to the background; audio focus and headphone-disconnect handling are delegated to Media3.
- Playback speed choices: 1.0x, 1.25x, 1.5x and 2.0x.
- Generate small video-frame thumbnails off the UI thread, use Android's scaled frame extraction where supported, and retain a 4 MB in-memory thumbnail cache.
- Show a protected read-only state for unsupported future catalogue versions instead of overwriting them.

The library stores up to 500 URI references. Resume positions are bounded to seven days to protect against malformed or corrupted values. The existing unversioned JSON array and legacy per-URI resume keys are migrated when the library is next saved.

## Shared document permission safety

Android persisted URI permissions belong to the application, not to one individual screen. The Movies and Library delete actions now use `MediaUriGrantRegistry` to avoid revoking a content URI while another known NeaRer feature still references it:
- Movies local library.
- Library collection.
- Studio Video project clips and background audio.

If a related feature contains malformed or unknown future data, the registry errs on the side of keeping the grant. This can leave an unused grant behind in rare cross-feature deletion orders, but it avoids breaking another feature's saved file reference. A future central media-reference database can replace these preference scans.

## Honest limits

- A selected video remains at its original location. If it is moved, deleted, or Android revokes access, it must be selected again.
- Media3 can play only formats/codecs supported by the Android device and the configured Media3 renderers.
- Thumbnails may be unavailable for unsupported, damaged or protected files.
- This does not publish creator videos, host licensed movies, transcode uploads, provide public search/discovery, handle DRM/licensing, process payments, manage subscriptions or stream content over the internet.
- No public movie catalogue is fabricated and no video is uploaded automatically.

## Validation still required

Automated JVM tests cover versioned persistence, legacy migration, resume/favourite round-trip, field bounds, malformed rows, duplicate URIs, maximum library size and unknown-version preservation.

Physical-device validation is still required for:
1. MP4/H.264/AAC and other common local formats on older and newer Android phones.
2. Subtitles and alternate audio tracks where the container supplies them.
3. USB storage, SD cards and cloud-backed document providers.
4. Resume accuracy after pause, app background, process termination and device restart.
5. Headphone disconnect, audio focus interruptions and screen wake behavior.
6. Large 4K files and devices with low memory.
7. File deletion or permission revocation after a video has been added.

## Android platform references

- Media3 ExoPlayer: https://developer.android.com/media/media3/exoplayer
- Media3 UI / PlayerView: https://developer.android.com/media/media3/ui
- Android Sharesheet and content URIs: https://developer.android.com/develop/ui/compose/sharing/send
- Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- MediaMetadataRetriever: https://developer.android.com/reference/android/media/MediaMetadataRetriever

The current design is intentionally offline-first: selected local files play without a network connection. Online creator publishing and licensed streaming require a separate rights-aware hosting, transcoding, moderation and bandwidth architecture.
