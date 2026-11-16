#!/usr/bin/env python3
"""Build a NeaRer Word ZIP pack from a publisher-authorised USFM ZIP."""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

BOOKS = [
    ("GEN","Genesis","OT"),("EXO","Exodus","OT"),("LEV","Leviticus","OT"),
    ("NUM","Numbers","OT"),("DEU","Deuteronomy","OT"),("JOS","Joshua","OT"),
    ("JDG","Judges","OT"),("RUT","Ruth","OT"),("1SA","1 Samuel","OT"),
    ("2SA","2 Samuel","OT"),("1KI","1 Kings","OT"),("2KI","2 Kings","OT"),
    ("1CH","1 Chronicles","OT"),("2CH","2 Chronicles","OT"),("EZR","Ezra","OT"),
    ("NEH","Nehemiah","OT"),("EST","Esther","OT"),("JOB","Job","OT"),
    ("PSA","Psalms","OT"),("PRO","Proverbs","OT"),("ECC","Ecclesiastes","OT"),
    ("SNG","Song of Solomon","OT"),("ISA","Isaiah","OT"),("JER","Jeremiah","OT"),
    ("LAM","Lamentations","OT"),("EZK","Ezekiel","OT"),("DAN","Daniel","OT"),
    ("HOS","Hosea","OT"),("JOL","Joel","OT"),("AMO","Amos","OT"),
    ("OBA","Obadiah","OT"),("JON","Jonah","OT"),("MIC","Micah","OT"),
    ("NAM","Nahum","OT"),("HAB","Habakkuk","OT"),("ZEP","Zephaniah","OT"),
    ("HAG","Haggai","OT"),("ZEC","Zechariah","OT"),("MAL","Malachi","OT"),
    ("MAT","Matthew","NT"),("MRK","Mark","NT"),("LUK","Luke","NT"),
    ("JHN","John","NT"),("ACT","Acts","NT"),("ROM","Romans","NT"),
    ("1CO","1 Corinthians","NT"),("2CO","2 Corinthians","NT"),("GAL","Galatians","NT"),
    ("EPH","Ephesians","NT"),("PHP","Philippians","NT"),("COL","Colossians","NT"),
    ("1TH","1 Thessalonians","NT"),("2TH","2 Thessalonians","NT"),
    ("1TI","1 Timothy","NT"),("2TI","2 Timothy","NT"),("TIT","Titus","NT"),
    ("PHM","Philemon","NT"),("HEB","Hebrews","NT"),("JAS","James","NT"),
    ("1PE","1 Peter","NT"),("2PE","2 Peter","NT"),("1JN","1 John","NT"),
    ("2JN","2 John","NT"),("3JN","3 John","NT"),("JUD","Jude","NT"),("REV","Revelation","NT")
]
BOOK_MAP = {code: (i + 1, name, testament) for i, (code, name, testament) in enumerate(BOOKS)}
EXPECTED_CHAPTERS = {
    "GEN":50,"EXO":40,"LEV":27,"NUM":36,"DEU":34,"JOS":24,"JDG":21,"RUT":4,
    "1SA":31,"2SA":24,"1KI":22,"2KI":25,"1CH":29,"2CH":36,"EZR":10,"NEH":13,
    "EST":10,"JOB":42,"PSA":150,"PRO":31,"ECC":12,"SNG":8,"ISA":66,"JER":52,
    "LAM":5,"EZK":48,"DAN":12,"HOS":14,"JOL":3,"AMO":9,"OBA":1,"JON":4,
    "MIC":7,"NAM":3,"HAB":3,"ZEP":3,"HAG":2,"ZEC":14,"MAL":4,"MAT":28,
    "MRK":16,"LUK":24,"JHN":21,"ACT":28,"ROM":16,"1CO":16,"2CO":13,"GAL":6,
    "EPH":6,"PHP":4,"COL":4,"1TH":5,"2TH":3,"1TI":6,"2TI":4,"TIT":3,"PHM":1,
    "HEB":13,"JAS":5,"1PE":5,"2PE":3,"1JN":5,"2JN":1,"3JN":1,"JUD":1,"REV":22
}

def clean_inline(text: str) -> str:
    text = re.sub(r"\\f\b.*?\\f\*", " ", text)
    text = re.sub(r"\\x\b.*?\\x\*", " ", text)
    text = re.sub(r"\\fig\b.*?\\fig\*", " ", text)
    # Keep the visible surface word and discard USFM word attributes.
    text = re.sub(r"\\\+?w\s+([^|]+?)\|.*?\\\+?w\*", r"\1", text)
    # Markers are formatting/control codes, not spaces; remove them without
    # inserting gaps before punctuation or between a word and its closing marker.
    text = re.sub(r"\\\+?[a-zA-Z0-9]+\*?", "", text)
    text = re.sub(r"\s+([,.;:!?])", r"\1", text)
    text = re.sub(r"([([{])\s+", r"\1", text)
    text = re.sub(r"\s+([)\]}])", r"\1", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()

def parse_usfm(raw: str, expected_code: str):
    code_match = re.search(r"(?m)^\\id\s+([A-Z0-9]{3})\b", raw)
    code = code_match.group(1) if code_match else expected_code
    if code not in BOOK_MAP:
        raise ValueError(f"Unsupported or unknown USFM book code: {code}")
    verses = {}
    chapter = 0
    current_verse = None
    chunks = []

    def flush():
        nonlocal chunks, current_verse
        if chapter > 0 and current_verse is not None:
            text = clean_inline(" ".join(chunks))
            if text:
                key = (chapter, current_verse)
                verses[key] = (verses.get(key, "") + " " + text).strip()
        chunks = []
        current_verse = None

    for line in raw.replace("\r", "").split("\n"):
        chapter_match = re.match(r"^\s*\\c\s+(\d+)", line)
        if chapter_match:
            flush()
            chapter = int(chapter_match.group(1))
            continue
        verse_match = re.match(r"^\s*\\v\s+(\d+[a-z]?(?:-\d+[a-z]?)?)\s*(.*)$", line)
        if verse_match:
            flush()
            verse_token = verse_match.group(1)
            if "-" in verse_token:
                raise ValueError(f"Verse bridge {verse_token} needs manual versification review in {code}")
            current_verse = int(re.match(r"\d+", verse_token).group(0))
            chunks = [verse_match.group(2)]
            continue
        if current_verse is not None:
            if re.match(r"^\s*\\(?:s\d*|ms\d*|r|d|sp|cl|cp|qa|qc|qac|mt\d*)\b", line):
                continue
            cleaned = clean_inline(line)
            if cleaned:
                chunks.append(cleaned)
    flush()
    if not verses:
        raise ValueError(f"No Scripture verses parsed from {code}")
    return code, verses

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("usfm_zip", type=Path)
    parser.add_argument("output_zip", type=Path)
    parser.add_argument("--translation-id", required=True)
    parser.add_argument("--translation-name", required=True)
    parser.add_argument("--language-tag", required=True)
    parser.add_argument("--language-name", required=True)
    parser.add_argument("--abbreviation", required=True)
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--license-id", required=True)
    parser.add_argument("--attribution", required=True)
    parser.add_argument("--copyright-notice", default="")
    parser.add_argument("--canon-id", default="protestant-66")
    parser.add_argument("--pack-id", required=True)
    args = parser.parse_args()

    if not args.usfm_zip.is_file():
        parser.error("Input USFM ZIP does not exist.")
    if not args.attribution.strip() or not args.license_id.strip():
        parser.error("Attribution and licence ID are mandatory.")
    if args.canon_id != "protestant-66":
        parser.error("This builder currently validates only the Protestant 66-book canon.")
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,120}\.zip", args.output_zip.name, re.IGNORECASE):
        parser.error("Output pack filename must be a safe .zip filename.")
    if not re.fullmatch(r"[A-Za-z0-9._-]{2,100}", args.pack_id):
        parser.error("Pack ID contains unsupported characters.")
    if not re.fullmatch(r"[A-Za-z0-9._-]{2,100}", args.translation_id):
        parser.error("Translation ID contains unsupported characters.")

    parsed_books = {}
    with zipfile.ZipFile(args.usfm_zip) as archive:
        infos = archive.infolist()
        if len(infos) > 100:
            raise ValueError("Source archive contains too many files.")
        if sum(info.file_size for info in infos) > 300 * 1024 * 1024:
            raise ValueError("Expanded source archive exceeds the permitted size.")
        for info in infos:
            if info.is_dir() or not info.filename.lower().endswith((".usfm", ".sfm")):
                continue
            if ".." in Path(info.filename).parts or "\\" in info.filename:
                raise ValueError("Unsafe path in source USFM archive.")
            if info.file_size > 12 * 1024 * 1024:
                raise ValueError("An individual USFM file exceeds the permitted size.")
            raw = archive.read(info).decode("utf-8-sig")
            id_match = re.search(r"(?m)^\\id\s+([A-Z0-9]{3})\b", raw)
            if not id_match:
                continue
            code = id_match.group(1)
            if code in BOOK_MAP:
                _, verses = parse_usfm(raw, code)
                parsed_books[code] = verses

    missing = [code for code, _, _ in BOOKS if code not in parsed_books]
    if missing:
        raise ValueError("Incomplete Protestant canon; missing books: " + ", ".join(missing))
    books_lines = []
    verse_lines = []
    for code, name, testament in BOOKS:
        canonical_number = BOOK_MAP[code][0]
        chapters = sorted({chapter for chapter, _ in parsed_books[code]})
        if not chapters or chapters != list(range(1, max(chapters) + 1)):
            raise ValueError(f"Missing chapter(s) in {name}")
        if max(chapters) != EXPECTED_CHAPTERS[code]:
            raise ValueError(f"{name} has {max(chapters)} chapters; expected {EXPECTED_CHAPTERS[code]} for the Protestant canon.")
        books_lines.append(json.dumps({
            "bookId": code, "canonicalNumber": canonical_number, "name": name,
            "testament": testament, "chapterCount": max(chapters)
        }, ensure_ascii=False, separators=(",", ":")))
        for (chapter, verse), text in sorted(parsed_books[code].items()):
            verse_lines.append(json.dumps({
                "translationId": args.translation_id, "bookId": code, "bookName": name,
                "chapter": chapter, "verse": verse, "text": text
            }, ensure_ascii=False, separators=(",", ":")))

    args.output_zip.parent.mkdir(parents=True, exist_ok=True)
    books_bytes = ("\n".join(books_lines) + "\n").encode("utf-8")
    scripture_bytes = ("\n".join(verse_lines) + "\n").encode("utf-8")
    manifest = {
        "packId": args.pack_id, "version": 1,
        "translationId": args.translation_id, "translationName": args.translation_name,
        "languageTag": args.language_tag, "languageName": args.language_name,
        "abbreviation": args.abbreviation, "canonId": args.canon_id,
        "sourceId": args.source_id, "licenseId": args.license_id,
        "copyrightNotice": args.copyright_notice or None,
        "coverage": "COMPLETE_BIBLE", "bookCount": len(BOOKS),
        "verseCount": len(verse_lines),
        "sha256": hashlib.sha256(scripture_bytes).hexdigest(),
        "minimumReaderVersion": 1, "attribution": args.attribution,
        "packFileName": args.output_zip.name
    }
    manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    with zipfile.ZipFile(args.output_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        out.writestr("manifest.json", manifest_bytes)
        out.writestr("books.ndjson", books_bytes)
        out.writestr("scripture.ndjson", scripture_bytes)
    print(f"Created {args.output_zip} — {len(BOOKS)} books, {len(verse_lines)} verses.")
    print("Verify the output and publisher rights before distributing it.")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Bible pack build failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
