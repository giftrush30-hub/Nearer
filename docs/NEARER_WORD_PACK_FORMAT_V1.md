# NeaRer Word pack format v1

NeaRer does not bundle Scripture text in the APK. Publishers or authorised distributors prepare ZIP archives and provide them to users. Do not create a pack from copyrighted text unless the distributor has granted NeaRer the necessary reproduction, offline storage, display and distribution rights.

## Required archive entries

- `manifest.json` — translation metadata, canon, licence, counts and checksum
- `books.ndjson` — one book object per line
- `scripture.ndjson` — one verse object per line

Archive entry names must be exactly those names, at archive root. Nested paths and extra files are rejected. The importer never extracts using archive-provided paths.

## Manifest example

```json
{
  "packId": "sample-en-protestant-v1",
  "version": 1,
  "translationId": "SAMPLE-EN",
  "translationName": "Sample English Translation",
  "languageTag": "en",
  "languageName": "English",
  "abbreviation": "SET",
  "canonId": "protestant-66",
  "sourceId": "publisher-id",
  "licenseId": "publisher-approved-license",
  "copyrightNotice": "Use the exact publisher-required copyright notice.",
  "coverage": "COMPLETE_BIBLE",
  "bookCount": 66,
  "verseCount": 31102,
  "sha256": "64 lowercase hexadecimal characters: SHA-256 of the exact scripture.ndjson bytes",
  "minimumReaderVersion": 1,
  "attribution": "Publisher and source attribution exactly as supplied.",
  "packFileName": "sample-en-protestant-v1.zip"
}
```

Supported complete-canon IDs currently validated by the reader: `protestant-66` (66 books), `catholic-73` (73 books), and `ethiopian-81` (81 books). Other canons must be explicitly added and reviewed before being labelled complete. Partial, Old Testament and New Testament packs must declare the correct coverage.

## Preparing the public-domain World English Bible (WEBP) seed

The official eBible.org page lists a USFM ZIP for WEBP and states that the text is public domain. Download the source from:

`https://ebible.org/Scriptures/engwebp_usfm.zip`

Then prepare the NeaRer pack with the repository utility:

```bash
python tools/build_nearer_bible_pack_from_usfm.py engwebp_usfm.zip build/nearer-word-webp.zip \
  --pack-id webp-eng-1 \
  --translation-id WEBP \
  --translation-name "World English Bible" \
  --language-tag en \
  --language-name English \
  --abbreviation WEBP \
  --canon-id protestant-66 \
  --source-id ebible.org \
  --license-id public-domain \
  --attribution "World English Bible text from eBible.org; public domain; preserve the text faithfully and observe trademark conditions."
```

The builder currently supports the 66-book Protestant canon and intentionally rejects verse bridges for manual versification review. The output still needs validation with the app importer and a verse-by-verse spot check before being published. The World English Bible name is trademarked and may only identify faithful copies of the source text; do not edit the text and keep the source conditions visible.

## Book records

Each line in `books.ndjson` is a JSON object:

```json
{"bookId":"GEN","canonicalNumber":1,"name":"Genesis","testament":"OT","chapterCount":50}
```

Book IDs must be stable within a translation. `testament` is one of `OT`, `NT` or `DEUTEROCANONICAL`.

## Verse records

Each line in `scripture.ndjson` is a JSON object:

```json
{"translationId":"SAMPLE-EN","bookId":"GEN","bookName":"Genesis","chapter":1,"verse":1,"text":"Publisher-approved verse text."}
```

The importer verifies translation ownership, chapter/verse ranges, non-empty text, the exact declared verse count, and SHA-256 before commit. The SQLite transaction rolls back the whole import if any record fails.

## Original-language dataset format

Use `BibleLexiconDatasetImporter` for separately licensed lexicon/alignment files. Each entry must include a stable `id`, `languageCode` (`hbo`, `arc` or `grc`), original lemma, transliteration, gloss and definition. The importer requires source name, HTTPS source URL, licence ID and attribution.

Each alignment must identify the translation, book, chapter, verse, zero-based token index, exact surface form and (when available) lexical entry ID. The importer checks that the surface form matches the indexed token in the installed verse. Never infer a Greek/Hebrew/Aramaic lemma from a translated word without reliable alignment data.

## Security and release notes

- ZIP and expanded sizes are bounded; entry count is bounded.
- Unknown, duplicate, nested, traversal-like or directory entries are rejected.
- Pack content is data only. SQL, scripts and executable payloads are never accepted.
- SHA-256 verifies integrity, not publisher authenticity. Production distribution should additionally use a publisher signature or a trusted catalogue signature.
- A checksum does not grant copyright permission.
- Never label a pack complete solely because its metadata says so; validate the canon and actual imported book/chapter/verse coverage.
