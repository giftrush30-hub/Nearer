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
- The reader now supports installed translation selection, book/chapter navigation, verse search, verse bookmarks and private study journal access.
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
- Word taps now query only imported alignment records; absent mappings are explicitly reported as unavailable.
- Show grammar, contextual senses and occurrence lists only when supported by imported source data.
- Candidate: STEPBible-Data. Its repository states CC BY 4.0, allows reuse and adaptation with changes recorded, and asks users to credit “STEP Bible” linked to STEP Bible. It contains Hebrew OT and Greek NT morphology/lexical resources; inspect individual dataset formats and provenance before mapping to the importer.

## Local schema
translations, books, verses, verse_search (FTS4), lexical_entries, word_occurrences, bookmarks, study_notes, installed_packs. Database v3 migrations preserve personal notes/bookmarks and add unique source-token alignment indexing. Personal notes/bookmarks must survive content-pack refresh and database migrations.

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
- Reader includes translation selection, book/chapter navigation, full-text verse search, verse tap to bookmark, word tap to an original-language detail surface, and a local notes/bookmarks journal.
- Lexical dataset importer accepts Hebrew, Aramaic and Koine Greek records with source URL, licence and attribution; it validates every verse/token alignment against the installed translation before linking a word to a lemma.
- Search query is tokenized and quoted to avoid user-supplied FTS operators.
- The page transition remains a simple 3D turn, not a true paper-curl shader.

## Remaining work before calling Bible complete
- No licensed Scripture pack or full multilingual catalogue has been distributed in this branch. The installer expects publisher-approved pack files; it does not fetch copyrighted translations from the web.
- The lexicon importer exists, but no lexical corpus is bundled and no automatic downloadable lexical catalogue is connected yet.
- Original-language study detail is live only where trusted alignment records have been imported.
- Add audio Bible playback, side-by-side translation comparison, reading plans, highlights, pronunciation audio, and robust reading-position restoration.
- Run CI on this exact branch, resolve any compile/test errors, and conduct airplane-mode and low-end-device tests.
- Confirm per-translation distribution, offline caching and monetization rights in writing before publishing any pack.

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
