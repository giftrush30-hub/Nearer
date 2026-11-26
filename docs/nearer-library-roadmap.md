# NeaRer Library — Phase 4 completion roadmap

Status: active recovery and integration branch `phase4/library-nearer-word-recovery`
Product contract: one Library department containing books, Bible, dictionary, documents and audio; offline-first; content packs are optional downloads; no cloud backend is required for local reading.

## Product experience

NeaRer Library is the user's single reading and knowledge home. Its shelves are:
- Books: EPUB and other supported ebook formats, with a paper-like page experience.
- Bible: translations, chapter/verse reading, search, comparison, bookmarks, highlights, notes, reading plans and licensed audio.
- Dictionary: offline word lookup, alphabetical browsing, meanings, pronunciation, examples, saved words and history.
- Documents: PDF, text and supported office/document formats.
- Audio: user-owned audiobooks and recordings, with remembered playback position.

The home screen should be beginner-friendly: Continue Reading, My Books, Bible, Dictionary, Audio, Downloads and Saved Words. Advanced study tools should be inside the relevant shelf, not exposed as a cluttered dashboard.

## Current repository audit

### Already on main before this phase
- `LibraryWorkspaceScreen.kt`: local collection of user-selected files, search/filter/favourites and persisted document URI permissions.
- `LibraryReaderScreens.kt`: native PDF rendering plus a text reader.
- PDF page position and a page bookmark are stored locally.
- Shared URI grant registry prevents one NeaRer department from revoking a document permission still used by another known department.

### Recovered from NeaRer Word PR #247
The original PR branch had become 255 commits behind current main. It was not merged wholesale. The Bible package, its focused tests, pack tooling, validation workflow and rights/provenance documents have been transplanted onto a fresh branch based on current main.

Recovered components include:
- SQLite Scripture database and full-text verse search.
- Translation/book/verse models and a validated pack manifest.
- Archive import with checksums, safe entry names, size/expanded-size bounds and transactional database installation.
- Bible reader, translation comparison, bookmarks, highlights, private notes and reading-position persistence.
- Hebrew/Greek lexical study with exact source-token alignment; unavailable mappings must remain unavailable rather than guessed.
- Reading plans and user-selected offline audio import/playback.
- Separate pack-generation and provenance tooling.

The Library screen now has an entry point to NeaRer Word. This integration is pending exact-head CI and code review; it is not yet declared merged or release-ready.

## Explicit gaps — do not claim these are complete

1. General EPUB 2/3 reader integrated into the ordinary Books shelf.
2. Realistic, accessible page-turn animation for ordinary books; current PDF renderer is not a general ebook page-turn engine.
3. General-purpose English dictionary and language dictionaries with an offline data-pack installer.
4. Word selection from ordinary EPUB/PDF/text content linked to dictionary lookup, saved words and annotations.
5. Generic book highlights, notes and bookmarks across all supported formats.
6. Audiobook chapter navigation, sleep timer, background playback policy, chapter/book synchronisation and reliable resume.
7. Local reading reminders and reading goals.
8. Online catalogue discovery, publisher/author upload, moderation and rights verification.
9. Paid book access, sponsored reading and author payouts. These must use the later NeaRer Wallet/payment phase; do not build a fake balance or collect money in this phase.
10. A rights-cleared and curated catalogue of Nigerian-language books and Bible editions.

## Reader-engine research and decision

Readium Kotlin Toolkit is the leading candidate for EPUB 2/3 and accessible reading, because its documented feature matrix includes reflowable EPUB pagination, RTL, search, highlighting and TTS. It also supports PDF and audiobook-related formats. It is a toolkit, not a complete Library product: NeaRer still owns the shelf UI, local catalogue, user progress and storage policy.

The official Readium documentation lists Android/Kotlin/Gradle requirements by release. Before adding it, verify the exact version against NeaRer's current Kotlin, Gradle, compile SDK, min SDK and dependency constraints. Do not blindly upgrade the Android build stack or increase APK size. A small dependency spike and CI build must precede adoption.

EPUB reflowable content should adapt to screen size and user font preferences; fixed-layout content is a different reading mode and cannot always be reflowed. Keep both modes honest. Page turning is a presentation preference, not a reason to break accessibility, text selection, RTL or reading position.

References:
- Readium Kotlin Toolkit: https://readium.org/kotlin-toolkit/latest/
- Readium setup and integration guidance: https://github.com/readium/kotlin-toolkit
- W3C EPUB 3.3: https://www.w3.org/TR/epub-33/
- Android TextToSpeech API: https://developer.android.com/reference/android/speech/tts/TextToSpeech

## Dictionary data and licensing

The preferred initial English dictionary source is a versioned, locally installable dataset derived from Wiktionary or another rights-cleared source. Wiktionary contributions are licensed under CC BY-SA and GFDL; reuse must preserve attribution and comply with share-alike obligations. A dataset import must retain entry-level/source provenance where supplied and include a clear attribution/licence screen. Do not bundle an unreviewed third-party dictionary file or scrape definitions without checking its licence.

Dictionary pack design should include:
- manifest: pack ID/version, language and locale, source, licence, attribution, entry count, uncompressed size, SHA-256, minimum reader version;
- entries: headword, normalized lookup key, pronunciation/IPA where sourced, part of speech, senses, examples, synonyms/antonyms only when licensed/source-backed, cross-references and source identifiers;
- bounded archive import, path traversal rejection, decompression limits, duplicate handling, checksum verification and atomic install;
- local indexed lookup, prefix search, diacritic/case normalization, history and favourites;
- optional voice playback only when an installed offline TTS voice supports that language. Android reports voice/language availability; NeaRer must not imply that every device has an offline voice.

References:
- Wiktionary copyright and contributor licensing: https://en.wiktionary.org/wiki/Wiktionary:Copyrights
- Android TextToSpeech language and voice availability: https://developer.android.com/reference/android/speech/tts/TextToSpeech

## Content rights and commerce

- A Bible translation being viewable in another app or website does not grant NeaRer permission to redistribute, cache, sell or bundle it.
- Keep public-domain editions and licensed editions clearly labelled with their source, licence and required copyright notice.
- Nigerian Bible Society listings are research leads only until each edition's exact rights holder and offline redistribution terms are confirmed.
- Book uploads require uploader identity, proof of rights or a valid distribution licence, takedown/report handling and moderation.
- Digital book sales, sponsored reading and author payouts are deferred until Phase 7 Wallet and applicable store/payment rules have been implemented and reviewed.
- Do not use a local preference value as proof of payment. Paid access must be granted only from a verified server-side/payment-partner event in the later online commerce architecture.

## Storage and offline rules

- User-imported source files remain where they are unless the user explicitly chooses to copy them into NeaRer.
- Installed content packs live in app-private managed storage with versioned manifests.
- Temporary import files are deleted on success/failure; failed imports roll back and never replace the last good pack.
- Cache covers/thumbnails separately from originals and make them safely evictable.
- Never automatically delete a user's books, notes, highlights, recordings or exported files.
- Reading, dictionary lookup, bookmarks and notes must work in airplane mode after the relevant content is installed.
- Online catalogue refresh must be optional and must never silently fetch remote files while the user is reading.

## Phased engineering work

### 4A — Recover and integrate Bible/NeaRer Word
- Port the existing implementation onto current main without importing unrelated stale branch history.
- Add it inside the single Library destination.
- Run exact-head JVM, manifest, pack-tooling and Android build checks.
- Resolve any current-main integration failures before merge.

### 4B — General ebook engine
- Verify Readium compatibility with current build configuration.
- Add EPUB import, metadata/cover extraction, TOC navigation, paginated and scroll modes, font/theme controls, reading progress and local bookmarks.
- Keep original document URIs and avoid copying large source files unless required.
- Add regression fixtures for malformed EPUBs, missing resources, RTL, large chapters and process restart.

### 4C — Unified reading state
- Use one repository/model for book identity, last location, bookmarks, highlights, notes and favourites across formats where the format permits.
- Preserve Bible's own verse-level semantics and keep Bible study records separate from generic book annotations.
- Migrate preferences safely; unknown future schemas must be read-only, not overwritten.

### 4D — Dictionary
- Finalise a licence-cleared dictionary dataset and pack manifest.
- Build an indexed local dictionary database and installer.
- Add word lookup from Library text selection, saved words, history, pronunciation and cross-reference panels.
- Include clear missing-data/voice-availability states.

### 4E — Audio and reminders
- Improve audiobook resume, chapter/track selection, sleep timer and user-controlled background playback.
- Integrate device TTS where supported and show when voice data is missing or online-only.
- Add opt-in local reading reminders and quiet hours.

### 4F — Publishing and paid reading (after Wallet phase)
- Author/publisher profiles, rights verification, content moderation, preview pages, licensing metadata and versioned distribution.
- Sponsored reading and book gifting.
- Payment and author settlement through the approved NeaRer Wallet partner only after Phase 7.

## Required automated validation

- Pack parser: malformed JSON, unknown versions, duplicate IDs, unsafe paths, zip bombs/expanded-size limits, checksum mismatch, empty packs, truncated files and transactional rollback.
- Database: schema migration, search, unique indexes, large imports, concurrent access and preservation of notes/bookmarks.
- Reading state: chapter/page restoration, format changes, deletion/revoked URI permissions and app process restart.
- Rights: every pack has a source, licence, attribution and copyright notice policy.
- Build: exact PR-head JVM tests, Bible tooling tests, Android manifest validation, lint diagnostics and debug APK assembly.
- No physical-device test is claimed by CI. Owner performs the airplane-mode, low-storage, accessibility, performance and real-device acceptance checklist.

## Physical acceptance remains with the project owner

Use `docs/NEARER_WORD_DEVICE_SMOKE_TEST.md` for Bible/Word checks. Add ordinary EPUB/PDF, dictionary, audio, airplane-mode, low-memory, large-file, font scaling, TalkBack and RTL checks before declaring Library complete.
