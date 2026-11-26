# NeaRer Word — physical-device and airplane-mode smoke test

**Status: not yet run on a physical Android phone.** This checklist is for the first APK that passes the independent Android build. A GitHub Actions build is not a substitute for a real device test.

## Test preparation
- Use a spare/test phone if possible; back up existing NeaRer data first.
- Install the debug APK produced by the latest successful nearer-word-synced-debug-apk workflow artifact. If Android reports a signature conflict with an existing install, do not uninstall a data-bearing app without backing it up.
- Download the separate nearer-word-webp-bible-pack artifact and copy it to the phone.
- Keep a note of phone model, Android version, app version, APK size, free storage, and test date.

## Offline Scripture pack
1. Turn on Airplane mode and confirm Wi-Fi/mobile data are off.
2. Open NeaRer → More → Bible.
3. Import the WEBP pack from local storage.
4. Confirm the pack is accepted and World English Bible appears in the translation library.
5. Open Genesis 1 and John 1; verify verse numbering, punctuation, and chapter navigation.
6. Search for a distinctive word such as "beginning" and open a result.
7. Force-stop NeaRer, reopen it, and confirm the translation and last reading position remain available offline.

Expected: pack import, reading, navigation, search and saved position work without network access.

## Personal study data
1. Add a bookmark, highlight and private note to a verse.
2. Open one of the four reading plans and mark a day complete.
3. Force-stop and reopen the app.
4. Confirm the bookmark, highlight, note and completed day remain present.

Expected: personal study data persists locally and is not lost when the app process is stopped.

## Offline audio
1. Select a locally stored audio Bible recording that you have permission to use and that is no larger than 256 MB.
2. Import it through Plans & Audio.
3. Confirm the file is copied into NeaRer private app storage.
4. With Airplane mode still enabled, play, pause, resume and stop.
5. Force-stop and reopen the app, then play the remembered recording again.
6. Attempt an empty file and a file over 256 MB if test fixtures are available; verify a clear error and no partial file is left behind.

Expected: imported audio plays offline and the selection persists. This does not test background playback or chapter synchronisation, which are not implemented yet.

## Reader presentation and accessibility
- Turn forward and backward through several chapters; confirm the page-curl direction matches navigation and the first page does not animate unexpectedly.
- Test long chapters, small screens, large system font sizes and screen-reader focus.
- Confirm Hebrew/Arabic RTL pages do not reverse English UI controls incorrectly.
- Confirm copyright and attribution notices remain visible when a translation requires them.

## Stability and resource checks
- Repeat Bible open/import/search/navigation after several app restarts.
- Watch for crashes, ANRs, UI freezes, excessive battery use, and unexpected network activity.
- Confirm the debug APK remains under the project target of 30 MB; Bible text/audio packs remain separate from the base APK.
- Record failures with phone model, Android version, exact steps, logcat and the installed APK commit SHA.

## Pass criteria
Do not mark NeaRer Word as physically verified until every applicable step above has been run on a real phone and the results are recorded. An emulator or CI build may provide additional confidence but does not satisfy the physical-device requirement.


## Original-language word study
1. Install the Berean Standard Bible text pack.
2. Import the Greek lexicon pack and Hebrew lexicon pack.
3. Import the BSB word-alignment archive after the BSB text is installed.
4. Open Genesis 1:1 in BSB and tap "beginning"; verify that the Hebrew Strong entry appears, with its lemma, transliteration, gloss, source and attribution.
5. Tap "created"; verify the linked Hebrew entry appears.
6. If one English token has multiple source links, verify all entries appear rather than silently hiding all but the first.
7. Switch to WEBP and confirm NeaRer does not invent a Greek/Hebrew mapping when no WEBP-specific alignment pack is installed.
8. Attempt to import the BSB alignment archive before the BSB text pack on a clean test install; expect a clear instruction to install the matching translation first and no partial database writes.

## Large word-study import
- On a real phone, import the BSB alignment archive (approximately 712,000 token-to-Strong links) after installing the BSB text and the Greek/Hebrew lexicons.
- Record import duration, free storage before/after, any Android ANR or low-memory warning, and whether the import survives force-stop/reopen.
- Confirm the UI remains responsive while the import runs and that a failed import rolls back rather than leaving partial alignments.
