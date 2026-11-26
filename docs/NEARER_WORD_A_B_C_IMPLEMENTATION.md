# NeaRer Word — A/B/C engineering batch

Status: expanded implementation batch on `feature/nearer-word-a-b-c`  
Target: native Kotlin + Jetpack Compose; offline-first; Android API 26+; no mandatory Firebase/Supabase; preserve midnight navy, mint and gold.

## A — Reader and offline foundation
- Isolated `com.nearer.app.bible` package; keep Bible implementation out of the monolithic MainActivity.
- Provider-neutral models for translations, books, verses, bookmarks, notes and lexical entries.
- Local SQLite database, FTS4 verse search, lexical tables and word-occurrence alignment.
- Reader surface with book/page, scroll and study modes, chapter navigation, verse selection and word-tap callbacks.
- User reading state belongs in a lifecycle-aware ViewModel/repository integration.
- No Scripture text is hard-coded or assumed licensed.

## B — Translation and language library
- Every translation has stable ID, language tag, canon ID, source, licence and copyright notice.
- Mark full versus partial Bible coverage accurately; never call an NT-only pack a complete Bible.
- Search by language, region, script, canon and completeness.
- Versioned, checksummed, transactionally imported packs; selected downloads only; no large text corpus in base APK.
- Added a pack importer for `manifest.json`, `books.ndjson` and `scripture.ndjson`, a SQLite transaction adapter, archive-entry/path/size checks, SHA-256 verification, and a real Bible destination in the existing More navigation.
- The reader now supports installed translation selection, book/chapter navigation, verse search, verse bookmarks, highlights, private study notes and visible publisher copyright/licence/attribution notices.
- Side-by-side comparison now joins two installed translations by canonical book number and verse number.
- Reading position (translation, book and chapter) is saved locally and restored on next open.
- Reader layout switches to RTL for Arabic, Hebrew, Persian and Urdu language tags.
- Nigerian-language discovery leads are displayed separately from NeaRer-approved downloadable packs; all remain rights-gated.
- Display required copyright notice whenever copyrighted Scripture is shown.
- No scraping or redistribution without written permission.
- Bible Society of Nigeria reports complete Bibles in 27 Nigerian languages, but its current Bible-download page lists a smaller set of Nigerian-language editions available through YouVersion (17 editions on one page; a newer BSN guide says 18). Treat these as discovery leads, not as permission to redistribute them inside NeaRer. Contact BSN and each rights holder for exact edition files and offline redistribution rights.
- BSN also describes New Testament and Scripture portions in additional Nigerian languages; the catalogue must distinguish full Bible, New Testament and portions rather than marking all as complete.
- API.Bible is an optional adapter. Its Starter plan is non-commercial; monetized NeaRer use requires the applicable commercial plan and translation-specific permissions. Never assume offline caching/redistribution is permitted.
- Pack manifest fields: packId, version, translationId, languageTag, canonId, sourceId, licenseId, copyrightNotice, bookCount, verseCount, sha256, minimumReaderVersion, attribution, packFileName.

## C — Greek, Hebrew and Aramaic study
- Cover Biblical Hebrew, Biblical Aramaic and Koine Greek.
- Keep surface form, lemma, transliteration, Strong number, morphology, gloss, definition and verse alignment separate.
- A translated word opens an original-language entry only if trusted alignment data maps it to an actual source token. Otherwise say mapping unavailable; never invent an equivalent.
- Search original script, transliteration, Strong number, lemma and English gloss.
- Added an attributed lexicon/alignment dataset importer. It verifies the referenced verse and token index against the installed translation before accepting a surface-word-to-lemma mapping.
- Word taps now pass the exact selected token index and query only imported alignment records; absent mappings are explicitly reported as unavailable.
- Original-language lexicon search can search lemma, transliteration, gloss and Strong number.
- Show grammar, contextual senses and occurrence lists only when supported by imported source data.
- Greek candidate: STEPBible-Data TBESG is declared CC BY 4.0 and requires STEP Bible attribution. Important: TBESH includes an explicit header notice saying its brief definitions are based on Online Bible and permission should be gained before applying them in another project; NeaRer excludes TBESH unless separate written permission is obtained. For Hebrew, use the Open Scriptures HebrewStrong.xml dataset under CC BY 4.0 with attribution to the Open Scriptures Hebrew Bible Project. This source-specific choice is documented in the new conversion workflow.

## Local schema
translations, books, verses, verse_search (FTS4), lexical_entries, word_occurrences, bookmarks, study_notes, verse_highlights, installed_packs. Database v5 migrations preserve personal notes/bookmarks, add unique source-token alignment indexing, persist publisher attribution and add verse highlights. Personal notes/bookmarks must survive content-pack refresh and database migrations.

## Integration contract
1. Add a Word destination to the existing navigation model without removing or reordering existing destinations.
2. Open `NeaRerWordReaderScreen` and provide selected translation/book/chapter plus installed verses from a ViewModel/repository.
3. Route a word tap to `BibleWordStudyEngine`; show results in a bottom sheet or side panel.
4. Keep global chat/connection flows and bottom navigation intact.
5. Do not touch unrelated messaging, Studio, Game Zone or transport logic in this branch.

## Reliability and privacy
- Reading, search, notes and bookmarks work without account or network after packs are installed.
- Notes remain local unless user opts into future end-to-end encrypted sync.
- Validate pack signatures/checksums, size, path traversal, schema, duplicate IDs and SQL errors. Never execute pack-supplied SQL/scripts.
- No reading history is sent to content providers by default.
- Parse/import large packs off the main thread.
- Claim offline support only after airplane-mode tests pass.

## Current implementation status
- Bible destination is connected to the existing More menu without changing the existing bottom navigation.
- A user can select a publisher-supplied ZIP pack, install it in the background, and read installed chapters offline.
- The pack importer validates the manifest, named canon book count, checksum, declared verse count, safe archive entry names, archive size, expanded size, and translation ownership; database writes are transactional.
- Reader includes translation selection, book/chapter navigation, full-text verse search, local reading-position restoration, RTL layout, side-by-side edition comparison, verse action sheet (bookmark/highlight/note), word tap to original-language detail, and a private study journal.
- Lexical dataset importer accepts Hebrew, Aramaic and Koine Greek records with source URL, licence and attribution; it validates every verse/token alignment against the installed translation before linking a word to a lemma.
- Search query is tokenized and quoted to avoid user-supplied FTS operators.
- The page transition has directional 3D hinge and shadow treatment; it is still not a true paper-curl shader.

## Remaining work before calling Bible complete
- No Scripture text is bundled in the APK. The Android validation workflow now has a separate job configured to fetch the official World English Bible (WEBP) USFM source from eBible.org, run the pack builder, and publish the generated ZIP as a separate artifact. This artifact is not considered validated until that workflow job passes and the pack imports successfully on a device. eBible.org identifies the text as public domain; its name is trademarked, so the text must remain faithful and the naming conditions must be respected.
- BSN reports 27 complete Nigerian-language Bibles, while its public Bible-app download guides list a smaller subset; the app shows discovery leads only and does not imply NeaRer has distribution permission. The installer expects publisher-approved pack files; it does not fetch copyrighted translations from the web.
- The lexicon importer exists, but no lexical corpus is bundled and no automatic downloadable lexical catalogue is connected yet.
- The first WEBP pack workflow downloaded the official USFM source and exposed a ZIP filename-regex bug; that bug has been fixed. The Python parser tests passed in the same run. A fresh run must confirm the fix and the Android build.
- Original-language study detail is live only where trusted alignment records have been imported.
- Add audio Bible playback, reading plans, pronunciation audio, a true paper-curl page animation and a rights-cleared global translation catalogue.
- The latest Android compile is blocked by errors in `NearbyTransportService.kt` on the updated main/PR merge ref: it calls listener/snapshot/acknowledgement APIs not present in `NearbyConnectionsTransport`. These errors are in the separate messaging workstream; this Bible branch deliberately does not modify messaging. Re-run Android tests/APK build after that API integration is reconciled.
- Validate the generated WEBP pack through the Android importer on a physical device and conduct airplane-mode and low-end-device tests.
- Confirm per-translation distribution, offline caching and monetization rights in writing before publishing any pack.

## D — Guided reading and audio (new extension)
- Added four original seven-day reading plans: Beginning with God, Learning to Pray, Wisdom for Daily Life, and Hope in Difficult Seasons.
- Day completion is stored in private app SharedPreferences on this device. Plans are prompts with Scripture references and reflection questions; they do not copy or redistribute a Bible translation.
- Added an audio import/player screen using the existing Media3 ExoPlayer dependency. A user-selected audio file is copied into the app's private files directory and can be played offline after import. The selected filename and day completion set are persisted locally.
- This is a local-file playback foundation, not a hosted streaming service, automatic chapter-to-audio synchronizer, background playback service, or bundled audio pack. The user must have rights to the recording.
- eBible.org identifies the Michael Paul Johnson World English Bible recordings as public domain and says they may be copied and shared. Winfred W. Henson's WEB audio is also described as freely downloadable/listenable. NeaRer does not automatically fetch these files yet; source selection, chapter segmentation, metadata, and download packaging require a separate verified content pipeline.
- Audio source references: https://ebible.org/webaudio/ and https://ebible.org/eng-web/audio/

## E — Languages, lexicons and canon gates
- STEPBible-Data is a strong candidate for the Hebrew and Greek lexical/morphology catalogue. Its current repository describes CC BY 4.0, permits reuse/adaptation, asks for attribution to STEP Bible, and requires changes to be recorded and made available. Dataset-specific provenance still needs review.
- Existing importer accepts NeaRer JSONL lexical entries and exact translation-token alignments. STEPBible TSV formats are not yet converted automatically. Do not claim every English/Yoruba/Igbo word has an original-language mapping; mappings must be exact and source-backed.
- Nigerian catalogue remains a rights-clearance lead list only. BSN's reported 27 full Nigerian-language Bibles are not 27 NeaRer-cleared downloadable packs.
- Complete canon validation remains Protestant 66 only. Catholic, Orthodox and Ethiopian canon metadata must be independently sourced and tested before any pack can be called complete.
- A true paper-curl page turn remains unfinished. The current reader is a directional 3D hinge/rotation animation with shadow; do not describe it as a physical page curl.

## Synchronisation and verification
- PR #222 is currently 14 commits behind main and diverged at the last comparison. Keep this Bible branch isolated until the shared navigation and messaging changes are reconciled. Do not overwrite MainActivity or messaging files from this branch.
- The new study extension adds no new Gradle dependency: Media3 ExoPlayer was already declared in app/build.gradle.kts.
- Required validation for this extension: Android compile, install/run on a device, import a permitted WEB audio file, force-close/reopen and confirm the selected audio remains available, play/pause/stop, and verify reading-plan completion survives restart.
- No physical-device or airplane-mode validation is claimed.

## Acceptance gates
1. JVM tests and debug APK build pass on exact branch head.
2. Import one legally reusable sample pack; verify book/chapter/verse counts.
3. Test offline full-text search with punctuation and accents.
4. Upgrade/reinstall preserves notes and bookmarks.
5. Verify copyright footer for each selected translation.
6. Import sample Greek and Hebrew lexical entries with attribution.
7. Unknown word alignment never fabricates an original-language mapping.
8. Test RTL and fonts on API 26 and current Android.
9. Airplane-mode test after pack installation.
10. Physical-device storage, memory and performance test on a low-end phone.

## Research sources
- WEBP public-domain status, trademark notice and official USFM file listing: https://ebible.org/details.php?id=engwebp
- World English Bible public-domain and trademark conditions: https://ebible.org/engwebp/copyright.htm
- World English Bible official USFM download listing: https://ebible.org/details.php?id=engwebp
- BSN current Nigerian-language Bible downloads and YouVersion edition list: https://www.biblesociety-nigeria.org/bible-downloads/
- BSN translation partnership and 27 full-Bible languages claim: https://www.biblesociety-nigeria.org/bible-translation-partnership/
- BSN global scripture access and Nigerian translation context: https://www.biblesociety-nigeria.org/what-we-do/
- BSN: https://www.biblesociety-nigeria.org/bible-downloads/
- BSN translation partnership: https://www.biblesociety-nigeria.org/bible-translation-partnership/
- API.Bible licensing: https://care.api.bible/article/369-understanding-api-bible-licensing
- API.Bible policies: https://care.api.bible/article/396-licensing-policies-quick-reference
- API.Bible catalogue: https://www.api.bible/bibles
- STEPBible data: https://github.com/STEPBible/STEPBible-Data
- Android SQLiteOpenHelper: https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper

## Additional references
- STEPBible-Data licence and dataset catalogue: https://github.com/STEPBible/STEPBible-Data
- STEPBible-Data documentation mirror: https://stepbible.github.io/STEPBible-Data/


## D — Reading plans and offline audio follow-through
- Four original seven-day plans are available: Beginning with God, Learning to Pray, Wisdom for Daily Life, and Hope in Difficult Seasons. Completion state is stored in app-private preferences.
- Plan passages route to the matching installed Bible book/chapter. The current passage parser opens the chapter; it does not yet scroll to the exact verse range.
- Offline audio uses the existing Media3 ExoPlayer dependency. Imported audio is copied into private app storage, the selected file is remembered, and the user can play, pause and stop.
- Audio imports now have a 256 MB per-file ceiling, reject empty files, write to a temporary file before finalising, and remove partial files on failure. This is a deliberate per-file safety limit, not a limit on Bible Scripture pack size.
- This is not yet a complete audio Bible library: no automatic provider download, chapter indexing, synchronised verse highlighting, background playback service, sleep timer or audio content pack is claimed.

## E — Dataset conversion and original-language integrity
- Added a TSV-to-JSONL conversion tool for STEPBible TBESG (Greek) and an XML-to-JSONL converter for Open Scriptures HebrewStrong.xml (Hebrew/explicit Aramaic metadata), with source attribution, Unicode preservation, HTML/XML text cleanup, duplicate-row removal and stable per-entry IDs. TBESH is deliberately excluded pending the permission stated in its own file header.
- Added converter tests for Greek and Hebrew header variants, Unicode script preservation, HTML definition cleanup, duplicate handling and invalid headers.
- CI now downloads the official TBESG Greek file and Open Scriptures HebrewStrong.xml, converts them to separate Greek and Hebrew JSONL artifacts, and includes CC BY 4.0 attribution plus a change description.
- The converter intentionally emits lexical entries only. It does not fabricate word-to-English-translation alignment. Tap-to-lemma results remain unavailable unless an exact, version-specific alignment source is imported and validated.
- The original STEPBible files are authoritative source material; the conversion pipeline must still be reviewed against their actual headers and content during CI before the artifacts are considered production-ready. The dataset's attribution and any source-specific restrictions must travel with the artifact.
- The importer schema supports explicit Aramaic rows only when the source data identifies them as Aramaic. A generic Hebrew Strong's number is not enough evidence to label a row Aramaic.

## Nigerian translation rights
- Expanded the catalogue into two distinct BSN discovery sets: the 17 edition rows shown on BSN's Bible-download page and additional language names from its newer 18-language guide. Hausa Ajami is kept distinct from Hausa; Eggon is explicitly identified as a New Testament lead.
- Added docs/NEARER_WORD_NIGERIAN_TRANSLATION_RIGHTS.md with edition identifiers, coverage caveats, BSN official references, public contact routes and a written-permission checklist.
- No Nigerian-language translation is approved for NeaRer redistribution. YouVersion availability or a free personal download is not a licence for NeaRer to copy, cache or redistribute it.

## Page-turn visual treatment
- Replaced the previous 3D hinge rotation with a directional animated page-curl overlay drawn over the reader page. It uses a curved fold path, paper gradient and shaded underside; first page display does not animate.
- This is a Compose-rendered page-curl visual effect, not a physically simulated paper mesh or drag-to-turn gesture. Device performance and accessibility still need validation.

## Latest-main CI and device-test boundary
- The validation workflow now separates JVM tests from the APK build, so an unrelated test failure cannot prevent the app APK compile from running.
- A previous shared test run failed at StudioCustomVocalPresetCodecTest.kt because MAX_NAME_LENGTH is private. This is an existing Studio workstream issue, not a Bible source-file diagnostic. Bible branch does not change Studio code.
- The dedicated Android APK job must complete before claiming an installable APK for this latest branch. No physical Android device or airplane-mode run is available to this remote repository workflow; those tests remain explicitly pending.

## Licence correction and source provenance
- During source-header inspection, the TBESH Hebrew file was found to contain a specific notice: its brief definitions are based on Abridged BDB by Online Bible and permission should be gained from Online Bible before those definitions are applied in another project. NeaRer has excluded TBESH from conversion and distribution unless separate written permission is obtained.
- The Hebrew lexical source is now Open Scriptures HebrewStrong.xml, which the Open Scriptures Hebrew Lexicon project documents as CC BY 4.0; attribution must credit the Open Scriptures Hebrew Bible Project. The Hebrew source schema is available in the same repository.
- STEPBible TBESG remains the Greek lexical candidate under the repository's CC BY 4.0 statement; the actual upstream file format is being validated by CI rather than guessed from third-party examples.


## BSB interlinear alignment — new end-to-end path
- Research confirmed the Free Use Bible API provides the Berean Standard Bible text and a separate BSB interlinear dataset. The API provides each original-language word's English gloss phrase and bsbPos rank; the interlinear tables are public domain. NeaRer matches gloss phrases in BSB English order against the verse text and omits unmatched phrases rather than guessing.
- Added a builder that downloads the 66-book BSB, checks the expected verse count, creates a separate BSB Bible pack, and converts source-provided gloss phrases plus Strong numbers into NeaRer token-index alignment records. It checks interlinear chapter coverage and gloss-match rate and refuses unexpectedly low mapping counts.
- Added a word-study archive format with source metadata, entry/alignment counts, SHA-256 checksums and strict ZIP validation. The app now exposes a separate “Word-study data” import action.
- Added a schema v6 migration so a single translated token can point to multiple original-language Strong entries, and updated the study detail surface to show all returned entries.
- Added an Android instrumentation test for multiple Strong entries on one token and an emulator CI job. An emulator run is not a physical-phone test.
- Import order: install the BSB text pack, install the Greek and Hebrew lexicon packs, then import the BSB word-alignment archive. The lexicon packs may be imported before or after the text pack; the alignment archive requires its exact BSB translation to be installed.
- This word mapping is BSB-only. WEBP remains untagged until a separately licensed, exact WEBP-to-original-language alignment dataset is found. Do not apply BSB gloss mappings to WEBP/KJV.

## Latest generated content and performance notes
- Latest successful pack generation: 10,848 Greek TBESG lexical entries and 8,435 Open Scriptures HebrewStrong entries, packaged as separate attributed ZIPs.
- BSB generation: 66 books, 31,086 verses, 712,491 token-to-Strong links. The builder matched 378,400 of 381,948 eligible gloss phrases (99.07%) and found interlinear data for all 1,189 chapters. Unmatched phrases are intentionally omitted.
- The BSB text pack is approximately 1.5 MB; the separate text-plus-alignment CI artifact is approximately 15 MB. The 113 MB raw alignment JSONL is compressed for download and expanded only during import.
- The Android importer streams alignments, caches each verse/token list, and inserts in batches of 1,000 inside a SQLite transaction. The word-study archive is bounded to 100 MB compressed / 300 MB expanded.
- Bible packs, lexicon packs and alignment packs remain separate downloads; none are bundled into the APK.
- The BSB interlinear pack is generated manually through workflow_dispatch or a commit tagged [rebuild-bsb-pack], rather than downloading thousands of chapter files on every ordinary code change.

## Reading-plan and audio refinements
- Reading-plan passage references now parse the chapter and starting verse separately. Opening a plan passage opens the correct installed chapter and scrolls the reader to the referenced starting verse rather than leaving the reader at the top of the chapter.
- Offline audio playback now remembers its last position when paused or when the screen is disposed, restores that position on the next play, and resets to the beginning after completion or an explicit stop.
- Audio still remains user-imported only; no copyrighted recordings are bundled or automatically downloaded. Background playback, chapter-aware audio indexing and timed sleep controls remain future work.
