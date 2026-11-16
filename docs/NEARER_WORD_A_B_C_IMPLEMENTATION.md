# NeaRer Word — A/B/C engineering batch

Status: first source foundation on `feature/nearer-word-a-b-c`  
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
- Display required copyright notice whenever copyrighted Scripture is shown.
- No scraping or redistribution without written permission.
- Bible Society of Nigeria reports full Bibles in 27 Nigerian languages and additional work in progress; confirm edition availability and distribution rights directly with BSN.
- API.Bible is an optional adapter. Its Starter plan is non-commercial; monetized NeaRer use requires the applicable commercial plan and translation-specific permissions. Never assume offline caching/redistribution is permitted.
- Pack manifest fields: packId, version, translationId, languageTag, canonId, sourceId, licenseId, copyrightNotice, bookCount, verseCount, sha256, minimumReaderVersion, attribution, packFileName.

## C — Greek, Hebrew and Aramaic study
- Cover Biblical Hebrew, Biblical Aramaic and Koine Greek.
- Keep surface form, lemma, transliteration, Strong number, morphology, gloss, definition and verse alignment separate.
- A translated word opens an original-language entry only if trusted alignment data maps it to an actual source token. Otherwise say mapping unavailable; never invent an equivalent.
- Search original script, transliteration, Strong number, lemma and English gloss.
- Show grammar, contextual senses and occurrence lists only when supported by imported source data.
- Candidate: STEPBible-Data. Repository states CC BY 4.0 and asks users to credit “STEP Bible” with a link to STEP Bible. Preserve attribution and record changes; verify individual dataset provenance before import.

## Local schema
translations, books, verses, verse_search (FTS4), lexical_entries, word_occurrences, bookmarks, study_notes. Personal notes/bookmarks must survive content-pack refresh and database migrations.

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

## Known gaps in this first source batch
This branch provides models, SQLite foundation, an initial reader composable and lexical lookup contract. It does not yet contain licensed Bible text, a pack catalogue/importer, every canon/version, audio assets, or MainActivity navigation wiring. The page animation is a first transition, not the final realistic curled-paper effect. No APK/build result is claimed until CI validates the exact branch head.

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
- BSN: https://www.biblesociety-nigeria.org/bible-downloads/
- BSN translation partnership: https://www.biblesociety-nigeria.org/bible-translation-partnership/
- API.Bible licensing: https://care.api.bible/article/369-understanding-api-bible-licensing
- API.Bible policies: https://care.api.bible/article/396-licensing-policies-quick-reference
- API.Bible catalogue: https://www.api.bible/bibles
- STEPBible data: https://github.com/STEPBible/STEPBible-Data
- Android SQLiteOpenHelper: https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper
