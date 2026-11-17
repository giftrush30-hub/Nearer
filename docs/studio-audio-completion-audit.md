# Studio Audio completion audit — 17 November 2026

This audit is based on the latest shared `main` at `366f5bdcbbbc4bc52a0abb8fecfcdb9e205aa8c2`. Work in this branch is intentionally isolated from the other Studio Audio workstream.

## Five-part completion target

| Work item | Repository status | Evidence / remaining gate |
|---|---|---|
| 1. Vocal quality audit | DSP refinement added in this branch | Smoothed sibilance-band envelope detection, sample-rate-safe filter cutoffs, and new signal-level tests. Still requires exact-head CI and listening on real voices/microphones. |
| 2. Timeline zoom and navigation | Already merged | PR #275 adds 1×/2×/4× zoom and horizontal scrolling. Do not duplicate it. |
| 3. Tempo automation | Not implemented yet | Existing BPM is a beat-grid/snap setting, not a playback-speed automation lane. The parallel workstream is reconciling Media3 1.11.1 on branch `feat/studio-media3-111-final`; earlier PR drafts #277 and #278 were closed without merging. Its current base must be reconciled before using the new API. |
| 4. Crossfades | Equal-power envelope implementation added in this branch | Existing track offsets allow two sequences to overlap. Matching the outgoing fade-out and incoming fade-in durations now uses complementary cosine/sine curves, maintaining constant combined power in the ideal aligned overlap. The Media3 Composition API itself still does not provide native crossfades. |
| 5. Export quality audit | Mix-export validation added in this branch | Output is named as an AAC-in-MP4 `.m4a`; after render, metadata inspection verifies a readable audio track and positive duration off the UI thread before exposing the file. Invalid output is deleted. Exact-head CI and device playback checks remain required. |

## Vocal Pro changes in this branch

- De-essing now follows a smoothed sibilance-band envelope (2 ms attack / 55 ms release) instead of applying gain reduction directly to every waveform peak. This is intended to reduce harsh, buzzy modulation.
- Filter cutoff calculations are clamped below Nyquist (45% of the actual input sample rate), avoiding invalid high-frequency filter settings on low-rate sources.
- Regression coverage now checks stronger attenuation of a 7 kHz sibilance test tone than a 220 Hz vocal fundamental, stronger compression on a sustained loud tone, centre collapse at zero stereo width, and 8 kHz input configuration.
- The processor documentation explicitly avoids claiming spectral denoising, a physical condenser microphone model, or true-peak mastering. The current noise-control stage remains a gate/expander, and the limiter remains sample-peak based.

## Equal-power crossfade behaviour

The mixer already exports parallel audio sequences and applies each track's fade envelope in `StudioTrackGainProcessor`. This branch changes the envelope shape:

- Fade-in: `sin(progress × π/2)`.
- Fade-out: `cos(progress × π/2)`.

When an outgoing track ends exactly one overlap duration after the incoming track starts, and both fade durations equal that overlap, the squared gains sum to one throughout the overlap. The new JVM test checks this at 101 points. Users still need to position the incoming track at the intended overlap point and set matching fade durations; this is not yet an automatic clip-pairing wizard.

Android's Media3 Composition documentation explicitly lists crossfading audio/video tracks as unsupported. This implementation therefore uses the already-supported per-track PCM gain processors rather than advertising a native Composition crossfade feature.

## Export verification

The Sound Desk previously treated a completed export as usable when the path existed and its byte length was non-zero. This branch adds a second validation layer using Android `MediaMetadataRetriever` after Transformer reports completion:

- the container must be readable;
- `METADATA_KEY_HAS_AUDIO` must report an audio track;
- duration must be greater than zero;
- metadata inspection runs on `Dispatchers.IO`, not the Compose UI thread;
- invalid output is deleted and never shown as playable/shareable.

The export still uses Media3's AAC encoder defaults. This branch does not claim a fixed bitrate, lossless output, loudness normalization, or true-peak compliance. Those need an explicit product-quality target and device/listening validation rather than invented specifications.

## Coordination with the parallel Studio Audio work

- The parallel Studio Audio workstream owns `feat/studio-media3-111-final`. It upgrades Media3 1.5.1 to 1.11.1, updates the Android build toolchain and fixes Media3 1.11.1 source imports; this branch does not modify those dependency or workflow files.
- That branch is one commit behind current `main` and must be reconciled and validated before using its new API. The earlier PR drafts #277 and #278 were closed without merging.
- The zoom/navigation work from PR #275 is already merged and is treated as complete here.
- No changes in this branch should be force-pushed into the other chat's feature branches.

## Required validation before declaring completion

1. Run the repository's canonical JVM tests, manifest validation, Android lint and debug APK build on this branch's exact head.
2. Resolve any CI failures before merging.
3. Listen to Vocal Pro A/B exports with spoken voice, sustained vowels and sibilant-heavy phrases; compare clean, warm, bright and podcast profiles.
4. Test equal-power overlaps with two real audio files, matched overlap offsets and fade lengths; listen for dips, peaks, clicks and phase interactions.
5. Export and play AAC mixes on Android speakers, wired headphones and Bluetooth; inspect the actual sample rate/bitrate where available and verify share/playback.
6. The project owner performs physical-device acceptance. No device test or studio-grade sound guarantee is claimed by this branch.
